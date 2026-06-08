package com.barteqcz.onqa.ui.main

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.barteqcz.onqa.data.model.*
import com.barteqcz.onqa.player.RadioPlayer
import com.barteqcz.onqa.data.repository.RadioRepository
import com.barteqcz.onqa.data.repository.SettingsRepository
import com.barteqcz.onqa.data.util.NetworkResult
import com.barteqcz.onqa.domain.GetSortedStationsUseCase
import com.barteqcz.onqa.ui.theme.OnqaGreen
import com.barteqcz.onqa.util.ConnectivityObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    ),
    val isNetworkAvailable: Boolean = true,
    val isScrollable: Boolean = false,
    val metadata: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RadioViewModel @Inject constructor(
    private val repository: RadioRepository,
    private val radioPlayer: RadioPlayer,
    private val settingsRepository: SettingsRepository,
    private val getSortedStations: GetSortedStationsUseCase,
    connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    private var lastNetworkId: String? = null
    private val _uiState = MutableStateFlow<RadioUiState>(RadioUiState.Loading)
    private val _selectedStationUrl = MutableStateFlow<String?>(null)
    private val _isScrollable = MutableStateFlow(value = false)

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
            ),
        )

    private val favoriteStations = settings.map { it.favoriteStations }.distinctUntilChanged()

    val currentStation: StateFlow<RadioStation?> = combine(
        radioPlayer.stationInfo,
        _uiState,
        favoriteStations,
    ) { info, state, favorites ->
        val url = info.url ?: return@combine null

        val stations = (state as? RadioUiState.Success)?.stations ?: emptyList()
        val station = stations.find { it.name == info.name }
            ?: stations.find { (it.streamUrl == url) || (it.streamUrlHq == url) }
            ?: RadioStation(
                name = info.name ?: "",
                streamUrl = url,
                logo = info.logo,
                network = info.network,
            )

        station.copy(isFavorite = station.name in favorites)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS), null)

    val viewState: StateFlow<RadioViewState> = combine(
        _uiState,
        _selectedStationUrl,
        currentStation,
        radioPlayer.isPlaying,
        radioPlayer.isBuffering,
        radioPlayer.playbackError,
        repository.locationInfo,
        settings,
        connectivityStatus,
        _isScrollable,
        radioPlayer.metadata,
    ) { args ->
        val state = args[0] as RadioUiState
        val selectedUrl = args[1] as String?
        val current = args[2] as RadioStation?
        val isPlaying = args[3] as Boolean
        val isBuffering = args[4] as Boolean
        val playbackError = args[5] as Boolean
        val locInfo = args[6] as LocationInfo
        val sett = args[7] as AppSettings
        val status = args[8] as ConnectivityObserver.Status
        val scrollable = args[9] as Boolean
        val meta = args[10] as String?

        RadioViewState(
            uiState = state,
            selectedUrl = selectedUrl,
            currentStation = current,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            playbackError = playbackError,
            locationInfo = locInfo,
            settings = sett,
            isNetworkAvailable = status is ConnectivityObserver.Status.Available,
            isScrollable = scrollable,
            metadata = if (isPlaying || isBuffering) meta else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS), RadioViewState())

    init {
        observeSettings()
        observeConnectivity()
        observeStations()
        setupLocationTracking()
        setupPlayerListeners()
        setupStationListUpdates()
    }

    private fun observeStations() {
        repository.stations
            .combine(repository.currentLocation) { stations, location -> 
                stations to location?.let { StableLocation(it.latitude, it.longitude) }
            }
            .flowOn(Dispatchers.Default)
            .onEach { (result, location) ->
                when (result) {
                    is NetworkResult.Loading -> {
                        if ((_uiState.value !is RadioUiState.Success) && (_uiState.value !is RadioUiState.Error)) {
                            _uiState.value = RadioUiState.Loading
                        }
                    }
                    is NetworkResult.Success -> {
                        val allStations = result.data

                        val currentPlaying = radioPlayer.stationInfo.value
                        if ((currentPlaying.url != null) && (radioPlayer.isPlaying.value || radioPlayer.isBuffering.value)) {
                            val updatedStation = allStations.find { it.name == currentPlaying.name }
                            val targetUrl = updatedStation?.getStreamUrl(settings.value.useHqStream)
                            
                            if (targetUrl != null && targetUrl != currentPlaying.url) {
                                radioPlayer.play(updatedStation.name, targetUrl, updatedStation.logo, updatedStation.network, forceReload = true)
                            }
                        }

                        val activeUrl = if (radioPlayer.isPlaying.value || radioPlayer.isBuffering.value) {
                            radioPlayer.stationInfo.value.url
                        } else null
                        
                        val groupedStations = getSortedStations(allStations, activeUrl, settings.value.favoriteStations)
                        val currentState = _uiState.value
                        
                        if (currentState is RadioUiState.Success) {
                            _uiState.value = currentState.copy(
                                stations = groupedStations,
                                allStations = allStations.toImmutableList(),
                                currentLocation = location ?: currentState.currentLocation,
                            )
                        } else if (location != null) {
                            val locInfo = repository.locationInfo.value
                            _uiState.value = RadioUiState.Success(
                                stations = groupedStations,
                                allStations = allStations.toImmutableList(),
                                currentLocation = location,
                                cityName = locInfo.city,
                                countryName = locInfo.country,
                                countryCode = locInfo.countryCode,
                            )
                        }
                    }
                    is NetworkResult.Error -> {
                        _uiState.value = RadioUiState.Error(result.message, isServerError = result.isServerError)
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settings
                .map { it.useHqStream }
                .distinctUntilChanged()
                .collect { useHq ->
                    val current = currentStation.value ?: return@collect
                    val info = radioPlayer.stationInfo.value
                    if (info.url != null && (radioPlayer.isPlaying.value || radioPlayer.isBuffering.value || radioPlayer.playbackError.value)) {
                        val newUrl = current.getStreamUrl(useHq)
                        if (newUrl != null && newUrl != info.url) {
                            radioPlayer.play(current.name, newUrl, current.logo, current.network, forceReload = true)
                        }
                    }
                }
        }
    }

    private fun observeConnectivity() {
        connectivityStatus
            .onEach { status ->
                if (status is ConnectivityObserver.Status.Available) {
                    val networkChanged = lastNetworkId != status.networkId
                    lastNetworkId = status.networkId

                    if ((_uiState.value is RadioUiState.Error) || (_uiState.value is RadioUiState.Success)) {
                        repository.currentLocation.value?.let { viewModelScope.launch { repository.updateNearbyStations(it) } }
                    }

                    val url = _selectedStationUrl.value
                    val isActuallyPlaying = radioPlayer.isPlaying.value || radioPlayer.isBuffering.value || radioPlayer.playbackError.value
                    if (url != null && isActuallyPlaying) {
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

        repository.locationInfo
            .onEach { info ->
                (_uiState.value as? RadioUiState.Success)?.let { currentState ->
                    _uiState.value = currentState.copy(
                        cityName = info.city,
                        countryName = info.country,
                        countryCode = info.countryCode,
                    )
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

        radioPlayer.stationInfo
            .onEach { info ->
                info.url?.let { _selectedStationUrl.value = it }
            }
            .launchIn(viewModelScope)
    }

    private fun setupStationListUpdates() {
        combine(
            _uiState,
            radioPlayer.isPlaying,
            radioPlayer.isBuffering,
            radioPlayer.stationInfo,
            favoriteStations,
        ) { state, playing, buffering, info, favorites ->
            if (state is RadioUiState.Success) {
                val activeUrl = if (playing || buffering) info.url else null
                activeUrl to favorites
            } else null
        }
        .filterNotNull()
        .distinctUntilChanged()
        .onEach { (activeUrl, favorites) ->
            val state = _uiState.value
            if (state is RadioUiState.Success) {
                val newStations = getSortedStations(state.allStations, activeUrl, favorites)
                if (newStations != state.stations) {
                    _uiState.value = state.copy(stations = newStations)
                }
            }
        }
        .flowOn(Dispatchers.Default)
        .launchIn(viewModelScope)
    }

    fun updateMaterialYou(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateMaterialYou(enabled) }
    fun updateThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.updateThemeMode(mode) }
    fun updateUseHqStream(useHq: Boolean) = viewModelScope.launch { settingsRepository.updateUseHqStream(useHq) }
    fun updateAccentColor(color: Color) = viewModelScope.launch { settingsRepository.updateAccentColor(color) }
    fun setScrollable(scrollable: Boolean) { _isScrollable.value = scrollable }
    fun completeOnboarding() = viewModelScope.launch { settingsRepository.updateOnboardingCompleted(completed = true) }
    fun resetOnboarding() = viewModelScope.launch { settingsRepository.updateOnboardingCompleted(completed = false) }

    fun refresh() {
        repository.currentLocation.value?.let { viewModelScope.launch { repository.updateNearbyStations(it) } }
    }

    fun toggleFavorite(station: RadioStation) {
        viewModelScope.launch { settingsRepository.toggleFavorite(station.name) }
    }

    fun toggleStation(url: String) {
        val stations = (_uiState.value as? RadioUiState.Success)?.stations ?: emptyList()
        val station = stations.find { it.streamUrl == url || it.streamUrlHq == url }

        val streamUrl = station?.getStreamUrl(settings.value.useHqStream) ?: url

        if (_selectedStationUrl.value == streamUrl) {
            if (radioPlayer.isPlaying.value || radioPlayer.isBuffering.value) radioPlayer.pause()
            else {
                _selectedStationUrl.value = streamUrl
                radioPlayer.play(station?.name, streamUrl, station?.logo, station?.network)
            }
        } else {
            _selectedStationUrl.value = streamUrl
            radioPlayer.play(station?.name, streamUrl, station?.logo, station?.network)
        }
    }

    fun nextStation() {
        val stations = (_uiState.value as? RadioUiState.Success)?.stations ?: return
        if (stations.isEmpty()) return
        val currentIndex = currentIndex()
        val nextIndex = if (currentIndex == -1 || currentIndex == stations.lastIndex) 0 else currentIndex + 1
        stations[nextIndex].let { s ->
            s.getStreamUrl(settings.value.useHqStream)?.let { url ->
                _selectedStationUrl.value = url
                radioPlayer.play(s.name, url, s.logo, s.network)
            }
        }
    }

    fun previousStation() {
        val stations = (_uiState.value as? RadioUiState.Success)?.stations ?: return
        if (stations.isEmpty()) return
        val currentIndex = currentIndex()
        val prevIndex = if (currentIndex <= 0) stations.lastIndex else currentIndex - 1
        stations[prevIndex].let { s ->
            s.getStreamUrl(settings.value.useHqStream)?.let { url ->
                _selectedStationUrl.value = url
                radioPlayer.play(s.name, url, s.logo, s.network)
            }
        }
    }

    private fun currentIndex(): Int {
        val stations = (_uiState.value as? RadioUiState.Success)?.stations ?: return -1
        return stations.indexOfFirst { (it.streamUrl == _selectedStationUrl.value) || (it.streamUrlHq == _selectedStationUrl.value) }
    }

    companion object {
        private const val FLOW_STOP_TIMEOUT_MS = 5000L
    }
}
