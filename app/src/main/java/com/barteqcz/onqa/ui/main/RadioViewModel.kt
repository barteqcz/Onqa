/**
 * ViewModel for the main radio screen managing station list state, playback, filtering, search, and language sync.
 */
package com.barteqcz.onqa.ui.main

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.barteqcz.onqa.data.db.CatalogDatabaseManager
import com.barteqcz.onqa.data.model.AppLanguage
import com.barteqcz.onqa.data.model.AppSettings
import com.barteqcz.onqa.data.model.CatalogUpdateFrequency
import com.barteqcz.onqa.data.model.DistanceUnit
import com.barteqcz.onqa.data.model.FavoritesBackup
import com.barteqcz.onqa.data.model.FavoritesImportResult
import com.barteqcz.onqa.data.model.LocationInfo
import com.barteqcz.onqa.data.model.LocationSource
import com.barteqcz.onqa.data.model.RadioStation
import com.barteqcz.onqa.data.model.StableLocation
import com.barteqcz.onqa.data.model.ThemeMode
import com.barteqcz.onqa.player.RadioPlayer
import com.barteqcz.onqa.player.PlayerState
import com.barteqcz.onqa.data.repository.RadioRepository
import com.barteqcz.onqa.data.repository.SettingsRepository
import com.barteqcz.onqa.data.util.NetworkResult
import com.barteqcz.onqa.domain.GetSortedStationsUseCase
import com.barteqcz.onqa.ui.theme.OnqaGreen
import com.barteqcz.onqa.util.ConnectivityObserver
import com.barteqcz.onqa.util.unaccent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private fun <T> Flow<T>.debounceStable(timeout: Duration): Flow<T> = channelFlow {
    var job: Job? = null
    collect { value ->
        job?.cancel()
        job = launch {
            delay(timeout)
            send(value)
        }
    }
}

@Immutable
data class RadioViewState(
    val uiState: RadioUiState = RadioUiState.Loading,
    val selectedUrl: String? = null,
    val currentStation: RadioStation? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playbackError: Boolean = false,
    val locationInfo: LocationInfo = LocationInfo(),
    val settings: AppSettings = AppSettings(
        themeMode = ThemeMode.DARK,
        isMaterialYouEnabled = false,
        accentColor = OnqaGreen,
        useHqStream = true,
        favoriteStations = kotlinx.collections.immutable.persistentSetOf(),
        language = AppLanguage.SYSTEM,
    ),
    val isNetworkAvailable: Boolean = true,
    val isScrollable: Boolean = false,
    val metadata: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val displayStation: RadioStation? = null,
    val isMiniPlayerActive: Boolean = false,
)

@Immutable
sealed interface RadioUiEvent {
    data object ScrollToTop : RadioUiEvent
}

@HiltViewModel
class RadioViewModel @Inject constructor(
    private val repository: RadioRepository,
    private val radioPlayer: RadioPlayer,
    private val settingsRepository: SettingsRepository,
    private val getSortedStations: GetSortedStationsUseCase,
    connectivityObserver: ConnectivityObserver,
    private val catalogDatabaseManager: CatalogDatabaseManager,
) : ViewModel() {

    private var lastNetworkId: String? = null
    private val _selectedStationUrl = MutableStateFlow<String?>(null)
    private val _selectedStationName = MutableStateFlow<String?>(null)
    private val _isScrollable = MutableStateFlow(value = false)
    private val _searchQuery = MutableStateFlow("")
    private val _isSearchActive = MutableStateFlow(value = false)
    private val _currentLanguage = MutableStateFlow(getCurrentAppLanguage())
    private val _events = MutableSharedFlow<RadioUiEvent>()
    val events = _events.asSharedFlow()

    private val connectivityStatus = connectivityObserver.observe()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS),
            ConnectivityObserver.Status.Available(),
        )

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS),
            initialValue = AppSettings(
                themeMode = ThemeMode.DARK,
                isMaterialYouEnabled = false,
                accentColor = OnqaGreen,
                useHqStream = true,
                favoriteStations = kotlinx.collections.immutable.persistentSetOf(),
                language = AppLanguage.SYSTEM,
            ),
        )

    private val favoriteStations = settings.map { it.favoriteStations }.distinctUntilChanged()

    private val stationsResult = repository.stations
        .combine(repository.currentLocation) { stations, location -> 
            stations to location?.let { StableLocation(it.latitude, it.longitude) }
        }
        .flowOn(Dispatchers.Default)

    private val searchFlow = combine(_searchQuery.debounceStable(100.milliseconds), _isSearchActive) { query, active ->
        query to active
    }

    private val processedStations: StateFlow<RadioUiState> = combine(
        stationsResult,
        favoriteStations,
        searchFlow,
        repository.locationInfo,
    ) { (res, loc), favorites, (query, searchActive), locInfo ->
        if (loc == null) return@combine RadioUiState.Loading

        when (res) {
            is NetworkResult.Loading -> RadioUiState.Loading
            is NetworkResult.Success -> {
                val allStations = res.data
                val groupedStations = getSortedStations(allStations, favorites)
                
                val filteredStations = if (query.isBlank() || !searchActive) {
                    groupedStations
                } else {
                    val normalizedQuery = query.unaccent().lowercase()
                    groupedStations.filter { 
                        it.name.unaccent().lowercase().contains(normalizedQuery) 
                    }.toImmutableList()
                }

                RadioUiState.Success(
                    stations = filteredStations,
                    allStations = allStations.toImmutableList(),
                    currentLocation = loc,
                    cityName = locInfo.city,
                    countryName = locInfo.country,
                    countryCode = locInfo.countryCode,
                )
            }
            is NetworkResult.Error -> RadioUiState.Error(res.message)
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS), RadioUiState.Loading)

    val currentStation: StateFlow<RadioStation?> = combine(
        radioPlayer.state,
        processedStations,
        favoriteStations,
        _selectedStationUrl,
        _selectedStationName,
    ) { player, state, favorites, selectedUrl, selectedName ->
        val info = player.stationInfo
        val url = info.url ?: selectedUrl ?: return@combine null
        val name = info.name ?: selectedName

        val stations = (state as? RadioUiState.Success)?.allStations ?: emptyList()
        
        val station = stations.find { it.matches(name, url) }
            ?: RadioStation(
                name = name ?: "",
                streamUrl = url,
                logo = info.logo,
                network = info.network,
            )

        station.copy(isFavorite = station.name in favorites)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS), null)

    private data class AudioState(
        val uiState: RadioUiState,
        val selectedUrl: String?,
        val currentStation: RadioStation?,
        val player: PlayerState,
        val locationInfo: LocationInfo,
    )

    private data class EnvironmentState(
        val settings: AppSettings,
        val connectivity: ConnectivityObserver.Status,
        val isScrollable: Boolean,
        val searchQuery: String,
        val isSearchActive: Boolean,
        val lang: AppLanguage,
    )

    private val audioStateFlow = combine(
        processedStations,
        _selectedStationUrl,
        currentStation,
        radioPlayer.state,
        repository.locationInfo,
    ) { uiState, selectedUrl, currentStation, player, locationInfo ->
        AudioState(uiState, selectedUrl, currentStation, player, locationInfo)
    }

    private val environmentStateFlow = combine(
        settings,
        connectivityStatus,
        _isScrollable,
        combine(_searchQuery, _isSearchActive, _currentLanguage) { q, active, lang -> Triple(q, active, lang) },
    ) { settings, connectivity, isScrollable, (q, active, lang) ->
        EnvironmentState(settings, connectivity, isScrollable, q, active, lang)
    }

    val viewState: StateFlow<RadioViewState> = combine(
        audioStateFlow,
        environmentStateFlow,
    ) { audio, env ->
        val stations = (audio.uiState as? RadioUiState.Success)?.stations ?: emptyList()
        val allStations = (audio.uiState as? RadioUiState.Success)?.allStations ?: emptyList()
        val isNoStationsSuccess = (audio.uiState is RadioUiState.Success) && allStations.isEmpty()

        val selectedStation = audio.selectedUrl?.let { url ->
            stations.find { (it.streamUrl == url) || (it.streamUrlHq == url) }
        }
        val displayStation = selectedStation ?: audio.currentStation

        RadioViewState(
            uiState = audio.uiState,
            selectedUrl = audio.selectedUrl,
            currentStation = audio.currentStation,
            isPlaying = audio.player.isPlaying,
            isBuffering = audio.player.isBuffering,
            playbackError = audio.player.playbackError,
            locationInfo = audio.locationInfo,
            settings = env.settings.copy(language = env.lang),
            isNetworkAvailable = env.connectivity is ConnectivityObserver.Status.Available,
            isScrollable = env.isScrollable,
            metadata = if (audio.player.isPlaying || audio.player.isBuffering) audio.player.metadata else null,
            searchQuery = env.searchQuery,
            isSearchActive = env.isSearchActive,
            displayStation = displayStation,
            isMiniPlayerActive = (audio.selectedUrl != null) && !isNoStationsSuccess,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS), RadioViewState())

    init {
        syncInitialLanguage()
        validateLocationSettings()
        observeSettings()
        observeConnectivity()
        setupLocationTracking()
        setupPlayerListeners()
        setupLocationAwarePlayback()
    }

    private fun syncInitialLanguage() {
        viewModelScope.launch {
            val savedLang = settingsRepository.settingsFlow.first().language
            val currentAppLocales = AppCompatDelegate.getApplicationLocales()
            if (currentAppLocales.isEmpty && (savedLang != AppLanguage.SYSTEM)) {
                val appLocales = androidx.core.os.LocaleListCompat.forLanguageTags(savedLang.code)
                AppCompatDelegate.setApplicationLocales(appLocales)
                _currentLanguage.value = savedLang
            } else if (!currentAppLocales.isEmpty) {
                val systemAppLang = getCurrentAppLanguage()
                _currentLanguage.value = systemAppLang
                if (systemAppLang != savedLang) {
                    settingsRepository.updateLanguage(systemAppLang)
                }
            } else {
                _currentLanguage.value = AppLanguage.SYSTEM
            }
        }
    }

    private fun validateLocationSettings() {
        viewModelScope.launch {
            val currentSettings = settingsRepository.settingsFlow.first()
            if ((currentSettings.locationSource == LocationSource.MANUAL) && (currentSettings.manualLatitude == null)) {
                Timber.w("Manual location selected but coordinates are missing. Falling back to GPS.")
                settingsRepository.updateLocationSource(LocationSource.GPS)
            }
        }
    }

    private fun observeSettings() {
        settings
            .map { it.useHqStream }
            .distinctUntilChanged()
            .onEach { useHq ->
                val current = currentStation.value ?: return@onEach
                val playerState = radioPlayer.state.value
                val info = playerState.stationInfo
                if ((info.url != null) && (playerState.isPlaying || playerState.isBuffering || playerState.playbackError)) {
                    val newUrl = current.getStreamUrl(useHq)
                    if ((newUrl != null) && (newUrl != info.url)) {
                        radioPlayer.play(current.name, newUrl, current.logo, current.network, forceReload = true)
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeConnectivity() {
        connectivityStatus
            .onEach { status ->
                if (status is ConnectivityObserver.Status.Available) {
                    val networkChanged = lastNetworkId != status.networkId
                    lastNetworkId = status.networkId

                    val currentState = processedStations.value
                    if ((currentState is RadioUiState.Error) || (currentState is RadioUiState.Success)) {
                        repository.currentLocation.value?.let { 
                            viewModelScope.launch { repository.updateNearbyStations(it) } 
                        }
                    }

                    val playerState = radioPlayer.state.value
                    val url = _selectedStationUrl.value
                    val isActuallyActive = playerState.isPlaying || playerState.isBuffering || playerState.playbackError
                    if ((url != null) && isActuallyActive) {
                        val station = currentStation.value
                        radioPlayer.play(station?.name, url, station?.logo, station?.network, forceReload = networkChanged)
                    }
                } else {
                    lastNetworkId = null
                }
            }
            .launchIn(viewModelScope)
    }

    private fun setupLocationTracking() {
        settings
            .map { it.isOnboardingCompleted }
            .distinctUntilChanged()
            .onEach { completed ->
                if (completed) {
                    try {
                        repository.startLocationTracking()
                    } catch (_: SecurityException) {
                        Timber.w("Location permission not granted or revoked.")
                    }
                } else {
                    repository.stopLocationTracking()
                }
            }
            .launchIn(viewModelScope)
    }

    private fun setupPlayerListeners() {
        radioPlayer.requestNext
            .onEach { nextStation() }
            .launchIn(viewModelScope)

        radioPlayer.requestPrevious
            .onEach { previousStation() }
            .launchIn(viewModelScope)

        radioPlayer.state
            .map { it.stationInfo }
            .distinctUntilChanged()
            .onEach { info ->
                info.url?.let { 
                    _selectedStationUrl.value = it
                    _selectedStationName.value = info.name
                }
            }
            .launchIn(viewModelScope)
    }

    private fun setupLocationAwarePlayback() {
        processedStations
            .filterIsInstance<RadioUiState.Success>()
            .distinctUntilChanged()
            .onEach { state: RadioUiState.Success ->
                val playerState = radioPlayer.state.value
                val isActuallyActive = playerState.isPlaying || playerState.isBuffering || playerState.playbackError
                val currentUrl = _selectedStationUrl.value ?: return@onEach
                val currentName = _selectedStationName.value ?: return@onEach

                if (isActuallyActive) {
                    return@onEach
                }

                val allStations = state.allStations
                val playingStation = allStations.find { it.name == currentName || it.matchesUrl(currentUrl) }

                fun RadioStation.isInCoverage(): Boolean {
                    return distance != null && coverageKm != null && distance <= coverageKm
                }

                val isInCoverage = playingStation != null && playingStation.isInCoverage()

                if (!isInCoverage) {
                    val effectiveNetwork = (playingStation?.network ?: currentStation.value?.network)?.takeIf { it.isNotBlank() }

                    val nextNetworkStation = if (effectiveNetwork != null) {
                        allStations.asSequence()
                            .filter { it.network == effectiveNetwork && it.isInCoverage() }
                            .minByOrNull { it.distance ?: Double.MAX_VALUE }
                    } else null

                    val fallbackStation = nextNetworkStation 
                        ?: allStations.asSequence()
                            .filter { it.isInCoverage() }
                            .minByOrNull { it.distance ?: Double.MAX_VALUE }

                    if (fallbackStation != null) {
                        val url = fallbackStation.getStreamUrl(settings.value.useHqStream)
                        if (url != null) {
                            _selectedStationUrl.value = url
                            _selectedStationName.value = fallbackStation.name
                        } else {
                            _selectedStationUrl.value = null
                            _selectedStationName.value = null
                        }
                    } else {
                        _selectedStationUrl.value = null
                        _selectedStationName.value = null
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun updateMaterialYou(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateMaterialYou(enabled) }
    fun updateThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.updateThemeMode(mode) }
    fun updateUseHqStream(useHq: Boolean) = viewModelScope.launch { settingsRepository.updateUseHqStream(useHq) }
    fun updateShowLocationHeader(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateShowLocationHeader(enabled) }
    fun updateAccentColor(color: Color) = viewModelScope.launch { settingsRepository.updateAccentColor(color) }
    fun updateDistanceUnit(unit: DistanceUnit) = viewModelScope.launch { settingsRepository.updateDistanceUnit(unit) }
    fun updateCatalogFrequency(frequency: CatalogUpdateFrequency) = viewModelScope.launch { settingsRepository.updateCatalogUpdateFrequency(frequency) }
    fun updateCatalogDatabaseManually(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.updateCatalogManually()
            onResult(success)
        }
    }
    
    fun updateLocationSource(source: LocationSource) = viewModelScope.launch { 
        settingsRepository.updateLocationSource(source) 
    }
    
    fun updateManualLocation(city: String?, code: String?, latitude: Double, longitude: Double) = viewModelScope.launch {
        settingsRepository.updateManualLocation(city, code, latitude, longitude)
    }

    fun updateLanguage(language: AppLanguage) {
        _currentLanguage.value = language
        viewModelScope.launch {
            settingsRepository.updateLanguage(language)
        }
        val appLocales = if (language == AppLanguage.SYSTEM) {
            androidx.core.os.LocaleListCompat.getEmptyLocaleList()
        } else {
            androidx.core.os.LocaleListCompat.forLanguageTags(language.code)
        }
        AppCompatDelegate.setApplicationLocales(appLocales)
    }

    fun syncLanguage() {
        val currentAppLang = getCurrentAppLanguage()
        if (currentAppLang != _currentLanguage.value) {
            _currentLanguage.value = currentAppLang
            viewModelScope.launch {
                settingsRepository.updateLanguage(currentAppLang)
            }
        }
    }

    private fun getCurrentAppLanguage(): AppLanguage {
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        if (currentLocales.isEmpty) return AppLanguage.SYSTEM
        
        val locale = currentLocales[0] ?: return AppLanguage.SYSTEM
        val languageCode = locale.language
        val tag = locale.toLanguageTag()

        return AppLanguage.entries.find { it.code.equals(tag, ignoreCase = true) }
            ?: AppLanguage.entries.find { it.code.equals(languageCode, ignoreCase = true) }
            ?: AppLanguage.SYSTEM
    }
    fun updateAmoledMode(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateAmoledMode(enabled) }
    fun setScrollable(scrollable: Boolean) { _isScrollable.value = scrollable }
    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setSearchActive(active: Boolean, clearQuery: Boolean = true) { 
        _isSearchActive.value = active 
        if (!active && clearQuery) _searchQuery.value = ""
    }
    fun completeOnboarding() = viewModelScope.launch { settingsRepository.updateOnboardingCompleted(completed = true) }
    fun resetOnboarding() = viewModelScope.launch { settingsRepository.updateOnboardingCompleted(completed = false) }

    fun refresh() {
        repository.currentLocation.value?.let { viewModelScope.launch { repository.updateNearbyStations(it) } }
    }

    fun toggleFavorite(station: RadioStation) {
        viewModelScope.launch {
            val isCurrentlyFavorite = settings.value.favoriteStations.contains(station.name)
            settingsRepository.toggleFavorite(station.name)
            if (!isCurrentlyFavorite) {
                _events.emit(RadioUiEvent.ScrollToTop)
            }
        }
    }

    fun getFavoriteStations(): Set<String> {
        return settings.value.favoriteStations
    }

    suspend fun validateFavoritesImport(jsonText: String): FavoritesImportResult = withContext(Dispatchers.Default) {
        val trimmed = jsonText.trim()
        if (trimmed.isEmpty()) {
            return@withContext FavoritesImportResult.Error.EmptyFile
        }

        val json = Json { ignoreUnknownKeys = true }

        val stationList: List<String> = try {
            val backup = json.decodeFromString<FavoritesBackup>(trimmed)
            backup.favoriteStations
        } catch (_: Exception) {
            try {
                json.decodeFromString<List<String>>(trimmed)
            } catch (_: Exception) {
                return@withContext FavoritesImportResult.Error.InvalidFormat
            }
        }

        val rawSet = stationList.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (rawSet.isEmpty()) {
            return@withContext FavoritesImportResult.Error.EmptyFile
        }

        val catalogNames = catalogDatabaseManager.getAllStationNames()
        val activeStations = (processedStations.value as? RadioUiState.Success)?.stations?.map { it.name }?.toSet().orEmpty()
        val knownNames = catalogNames + activeStations

        if (knownNames.isEmpty()) {
            return@withContext FavoritesImportResult.Success(
                totalCount = rawSet.size,
                validStations = rawSet,
                unknownStations = emptySet(),
                rawStations = rawSet
            )
        }

        val valid = rawSet.filter { it in knownNames }.toSet()
        val unknown = rawSet - valid

        FavoritesImportResult.Success(
            totalCount = rawSet.size,
            validStations = valid,
            unknownStations = unknown,
            rawStations = rawSet
        )
    }

    fun importFavorites(stations: Set<String>, replace: Boolean = false) {
        viewModelScope.launch {
            val finalFavorites = if (replace) {
                stations
            } else {
                settings.value.favoriteStations + stations
            }
            settingsRepository.setFavorites(finalFavorites)
        }
    }

    fun toggleStation(url: String, stationName: String? = null) {
        val playerState = radioPlayer.state.value
        val stations = (processedStations.value as? RadioUiState.Success)?.stations ?: emptyList()
        val station = stations.find { it.matchesUrl(url) }

        val streamUrl = station?.getStreamUrl(settings.value.useHqStream) ?: url

        if (_selectedStationUrl.value == streamUrl) {
            if (playerState.isBuffering) return
            if (playerState.isPlaying) radioPlayer.pause()
            else {
                playStation(station, stationName, streamUrl)
            }
        } else {
            playStation(station, stationName, streamUrl)
        }
    }

    private fun playStation(station: RadioStation?, name: String?, url: String) {
        val finalName = station?.name ?: name
        _selectedStationUrl.value = url
        _selectedStationName.value = finalName
        radioPlayer.play(finalName, url, station?.logo, station?.network)
    }

    fun nextStation() = navigateStation(1)
    fun previousStation() = navigateStation(-1)

    fun emitNext() {
        radioPlayer.emitNext()
    }

    fun emitPrevious() {
        radioPlayer.emitPrevious()
    }

    private fun navigateStation(delta: Int) {
        val stations = (processedStations.value as? RadioUiState.Success)?.stations ?: return
        if (stations.isEmpty()) return
        
        val currentIndex = currentIndex()
        val nextIndex = when {
            currentIndex == -1 -> 0
            delta > 0 -> (currentIndex + 1) % stations.size
            else -> if (currentIndex == 0) stations.lastIndex else currentIndex - 1
        }
        
        stations[nextIndex].let { s ->
            s.getStreamUrl(settings.value.useHqStream)?.let { url ->
                playStation(s, s.name, url)
            }
        }
    }

    private fun currentIndex(): Int {
        val stations = (processedStations.value as? RadioUiState.Success)?.stations ?: return -1
        return stations.indexOfFirst { it.matches(_selectedStationName.value, _selectedStationUrl.value) }
    }

    companion object {
        private const val FLOW_STOP_TIMEOUT_MS = 5000L
    }
}
