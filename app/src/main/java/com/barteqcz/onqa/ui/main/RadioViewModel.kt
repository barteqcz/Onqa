package com.barteqcz.onqa.ui.main

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.barteqcz.onqa.data.model.AppLanguage
import com.barteqcz.onqa.data.model.AppSettings
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import com.barteqcz.onqa.util.LanguageDownloadManager
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@Immutable
data class LanguageDownloadState(
    val isDownloading: Boolean = false,
    val targetLanguage: AppLanguage? = null,
    val progress: Float = 0f,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val error: String? = null
)

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
    val languageDownloadState: LanguageDownloadState = LanguageDownloadState(),
)

@Immutable
sealed interface RadioUiEvent {
    data object ScrollToTop : RadioUiEvent
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class RadioViewModel @Inject constructor(
    private val repository: RadioRepository,
    private val radioPlayer: RadioPlayer,
    private val settingsRepository: SettingsRepository,
    private val getSortedStations: GetSortedStationsUseCase,
    private val languageDownloadManager: LanguageDownloadManager,
    connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    private var lastNetworkId: String? = null
    private val _selectedStationUrl = MutableStateFlow<String?>(null)
    private val _selectedStationName = MutableStateFlow<String?>(null)
    private val _isScrollable = MutableStateFlow(value = false)
    private val _searchQuery = MutableStateFlow("")
    private val _isSearchActive = MutableStateFlow(value = false)
    private val _currentLanguage = MutableStateFlow(getCurrentAppLanguage())
    private val _languageDownloadState = MutableStateFlow(LanguageDownloadState())
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

    private val processedStations = combine(
        stationsResult,
        favoriteStations,
        _searchQuery.debounce(100.milliseconds),
        _isSearchActive,
        radioPlayer.state.map { it.stationInfo.url }.distinctUntilChanged(),
        repository.locationInfo,
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val (res, loc) = args[0] as Pair<NetworkResult<List<RadioStation>>, StableLocation?>
        @Suppress("UNCHECKED_CAST")
        val favorites = args[1] as Set<String>
        val query = args[2] as String
        val searchActive = args[3] as Boolean
        val activeUrl = args[4] as String?
        val locInfo = args[5] as LocationInfo

        if (loc == null) return@combine RadioUiState.Loading

        when (res) {
            is NetworkResult.Loading -> RadioUiState.Loading
            is NetworkResult.Success -> {
                val allStations = res.data
                val groupedStations = getSortedStations(allStations, activeUrl, favorites)
                
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
            is NetworkResult.Error -> RadioUiState.Error(res.message, isServerError = res.isServerError)
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

    val viewState: StateFlow<RadioViewState> = combine(
        processedStations,
        _selectedStationUrl,
        currentStation,
        radioPlayer.state,
        repository.locationInfo,
        settings,
        connectivityStatus,
        _isScrollable,
        _searchQuery,
        _isSearchActive,
        _currentLanguage,
        _languageDownloadState,
    ) { args ->
        val uiState = args[0] as RadioUiState
        val selectedUrl = args[1] as String?
        val currentStation = args[2] as RadioStation?
        val player = args[3] as PlayerState
        val locationInfo = args[4] as LocationInfo
        val settings = args[5] as AppSettings
        val connectivity = args[6] as ConnectivityObserver.Status
        val isScrollable = args[7] as Boolean
        val searchQuery = args[8] as String
        val isSearchActive = args[9] as Boolean
        val lang = args[10] as AppLanguage
        val langDownloadState = args[11] as LanguageDownloadState

        val stations = (uiState as? RadioUiState.Success)?.stations ?: emptyList()
        val allStations = (uiState as? RadioUiState.Success)?.allStations ?: emptyList()
        val isNoStationsSuccess = (uiState is RadioUiState.Success) && allStations.isEmpty()

        val selectedStation = selectedUrl?.let { url ->
            stations.find { (it.streamUrl == url) || (it.streamUrlHq == url) }
        }
        val displayStation = selectedStation ?: currentStation

        RadioViewState(
            uiState = uiState,
            selectedUrl = selectedUrl,
            currentStation = currentStation,
            isPlaying = player.isPlaying,
            isBuffering = player.isBuffering,
            playbackError = player.playbackError,
            locationInfo = locationInfo,
            settings = settings.copy(language = lang),
            isNetworkAvailable = connectivity is ConnectivityObserver.Status.Available,
            isScrollable = isScrollable,
            metadata = if (player.isPlaying || player.isBuffering) player.metadata else null,
            searchQuery = searchQuery,
            isSearchActive = isSearchActive,
            displayStation = displayStation,
            isMiniPlayerActive = (selectedUrl != null) && !isNoStationsSuccess,
            languageDownloadState = langDownloadState,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS), RadioViewState())

    init {
        validateLocationSettings()
        observeSettings()
        observeConnectivity()
        setupLocationTracking()
        setupPlayerListeners()
        setupLocationAwarePlayback()
    }

    private fun validateLocationSettings() {
        viewModelScope.launch {
            val currentSettings = settingsRepository.settingsFlow.first()
            if (currentSettings.locationSource == LocationSource.MANUAL && currentSettings.manualLatitude == null) {
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
                    if (currentState is RadioUiState.Error || currentState is RadioUiState.Success) {
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
                    repository.startLocationTracking()
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
                val currentName = _selectedStationName.value
                
                val allStations = state.allStations
                val stationInNewLocation = allStations.find { it.matches(currentName, currentUrl) }
                val isStillAvailable = stationInNewLocation != null

                if (isStillAvailable) {
                    if (isActuallyActive) {
                        val currentNetwork = stationInNewLocation.network
                        val bestInNetwork = state.stations.find { it.network == currentNetwork && it.name == currentName }
                        val bestUrl = bestInNetwork?.getStreamUrl(settings.value.useHqStream)
                        
                        if (bestUrl != null && bestUrl != currentUrl) {
                            Timber.i("Switching to better transmitter for $currentName: $bestUrl")
                            playStation(bestInNetwork, bestInNetwork.name, bestUrl)
                        }
                    }
                } else {
                    if (isActuallyActive) {
                        // Current station completely gone, but we were playing.
                        // Switch to the first station in the new list (closest)
                        val closest = state.stations.firstOrNull()
                        if (closest != null) {
                            val closestUrl = closest.getStreamUrl(settings.value.useHqStream)
                            if (closestUrl != null) {
                                Timber.i("Station $currentName gone. Switching to closest: ${closest.name}")
                                playStation(closest, closest.name, closestUrl)
                            }
                        } else {
                            // No stations available in this region at all
                            Timber.i("No stations available. Stopping.")
                            radioPlayer.pause()
                            _selectedStationUrl.value = null
                            _selectedStationName.value = null
                        }
                    } else {
                        // Current station gone and we were NOT playing. Hide miniplayer.
                        Timber.i("Station $currentName gone and not playing. Hiding miniplayer.")
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
    
    fun updateLocationSource(source: LocationSource) = viewModelScope.launch { 
        settingsRepository.updateLocationSource(source) 
    }
    
    fun updateManualLocation(city: String?, code: String?, latitude: Double, longitude: Double) = viewModelScope.launch {
        settingsRepository.updateManualLocation(city, code, latitude, longitude)
    }

    fun updateLanguage(language: AppLanguage) {
        viewModelScope.launch {
            languageDownloadManager.downloadAndApplyLanguage(language).collect { progress ->
                _languageDownloadState.value = LanguageDownloadState(
                    isDownloading = progress.isDownloading,
                    targetLanguage = progress.language,
                    progress = progress.progress,
                    bytesDownloaded = progress.bytesDownloaded,
                    totalBytes = progress.totalBytes,
                    error = progress.error
                )
                if (progress.isInstalled) {
                    _currentLanguage.value = getCurrentAppLanguage()
                }
            }
        }
    }

    fun dismissLanguageDownloadError() {
        _languageDownloadState.value = _languageDownloadState.value.copy(error = null)
    }

    fun syncLanguage() {
        _currentLanguage.value = getCurrentAppLanguage()
    }

    private fun getCurrentAppLanguage(): AppLanguage {
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        if (currentLocales.isEmpty) return AppLanguage.SYSTEM
        
        val tag = currentLocales[0]?.language ?: return AppLanguage.SYSTEM
        return AppLanguage.entries.find { it.code.equals(tag, ignoreCase = true) } ?: AppLanguage.SYSTEM
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
