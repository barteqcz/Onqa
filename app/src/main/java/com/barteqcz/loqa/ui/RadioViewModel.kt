package com.barteqcz.loqa.ui

import android.content.Context
import android.location.Location
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.barteqcz.loqa.R
import com.barteqcz.loqa.data.AppSettings
import com.barteqcz.loqa.data.LocationRepository
import com.barteqcz.loqa.data.RadioPlayer
import com.barteqcz.loqa.data.RadioRepository
import com.barteqcz.loqa.data.SettingsRepository
import com.barteqcz.loqa.data.model.RadioStation
import com.barteqcz.loqa.data.util.NetworkResult
import com.barteqcz.loqa.data.util.StationProcessor
import com.barteqcz.loqa.location.LocationClient
import com.barteqcz.loqa.ui.theme.LoqaGreen
import com.barteqcz.loqa.util.AddressRefiner
import com.barteqcz.loqa.util.ConnectivityObserver
import com.google.android.gms.location.Priority
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class RadioViewState(
    val uiState: RadioUiState = RadioUiState.Loading,
    val selectedUrl: String? = null,
    val currentStation: RadioStation? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playbackError: Boolean = false,
    val metadata: String? = null,
    val locationInfo: LocationInfo = LocationInfo(),
    val settings: AppSettings = AppSettings(
        isMaterialYouEnabled = false,
        accentColor = LoqaGreen,
    ),
    val isNetworkAvailable: Boolean = true,
)

data class LocationInfo(
    val city: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val distanceKm: Int? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RadioViewModel @Inject constructor(
    private val locationClient: LocationClient,
    private val repository: RadioRepository,
    private val radioPlayer: RadioPlayer,
    private val settingsRepository: SettingsRepository,
    private val locationRepository: LocationRepository,
    connectivityObserver: ConnectivityObserver,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private var lastGeocodedLocation: Location? = null
    private var lastNetworkId: String? = null
    private val _currentLocation = MutableStateFlow<Location?>(null)
    private val _uiState = MutableStateFlow<RadioUiState>(RadioUiState.Loading)
    private val _selectedStationUrl = MutableStateFlow<String?>(null)
    private val _locationInfo = MutableStateFlow(LocationInfo())

    private val connectivityStatus = connectivityObserver.observe()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ConnectivityObserver.Status.Available(),
        )

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MS),
            initialValue = AppSettings(
                isMaterialYouEnabled = false,
                accentColor = LoqaGreen,
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
        val station = stations.find { it.streamUrl == url } ?: RadioStation(
            name = info.name ?: "",
            streamUrl = url,
            logo = info.logo,
            network = info.network,
        )

        station.copy(isFavorite = station.name in favorites)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val viewState: StateFlow<RadioViewState> = combine(
        _uiState,
        _selectedStationUrl,
        currentStation,
        radioPlayer.isPlaying,
        radioPlayer.isBuffering,
        radioPlayer.playbackError,
        radioPlayer.metadata,
        _locationInfo,
        settings,
        connectivityStatus,
    ) { args ->
        RadioViewState(
            uiState = args[0] as RadioUiState,
            selectedUrl = args[1] as String?,
            currentStation = args[2] as RadioStation?,
            isPlaying = args[3] as Boolean,
            isBuffering = args[4] as Boolean,
            playbackError = args[5] as Boolean,
            metadata = args[6] as String?,
            locationInfo = args[7] as LocationInfo,
            settings = args[8] as AppSettings,
            isNetworkAvailable = args[9] is ConnectivityObserver.Status.Available,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RadioViewState())

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
            .combine(_currentLocation) { stations, location -> stations to location }
            .onEach { (result, location) ->
                when (result) {
                    is NetworkResult.Loading -> {
                        if ((_uiState.value !is RadioUiState.Success) && (_uiState.value !is RadioUiState.Error)) {
                            _uiState.value = RadioUiState.Loading
                        }
                    }
                    is NetworkResult.Success -> {
                        val allStations = result.data
                        val activeUrl = if (radioPlayer.isPlaying.value || radioPlayer.isBuffering.value) {
                            radioPlayer.stationInfo.value.url
                        } else null
                        
                        val groupedStations = StationProcessor.groupAndSortStations(allStations, activeUrl, settings.value.favoriteStations)
                        val currentState = _uiState.value
                        
                        if (currentState is RadioUiState.Success) {
                            _uiState.value = currentState.copy(
                                stations = groupedStations,
                                allStations = allStations,
                                currentLocation = location ?: currentState.currentLocation
                            )
                        } else if (location != null) {
                            _uiState.value = RadioUiState.Success(
                                stations = groupedStations,
                                allStations = allStations,
                                currentLocation = location,
                                cityName = _locationInfo.value.city,
                                countryName = _locationInfo.value.country,
                                countryCode = _locationInfo.value.countryCode,
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
                .filter { it.lastCity != null }
                .collect { s ->
                    if (_locationInfo.value.city == null) {
                        _locationInfo.value = LocationInfo(s.lastCity, null, s.lastCountryCode)
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
                        _currentLocation.value?.let { viewModelScope.launch { repository.updateNearbyStations(it) } }
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
            .flatMapLatest { completed ->
                if (completed) {
                    flow {
                        val lastLocation = locationClient.getLastLocation()
                        if (lastLocation != null) {
                            emit(lastLocation)
                        }
                        emitAll(
                            locationClient.getLocationUpdates(
                                interval = LOCATION_UPDATE_INTERVAL_MS,
                                minDistance = STATION_REFRESH_DISTANCE_METERS,
                                priority = Priority.PRIORITY_HIGH_ACCURACY,
                            )
                        )
                    }
                        .onEach { location ->
                            _currentLocation.value = location
                            repository.updateNearbyStations(location)
                            handleGeocoding(location)
                        }
                        .catch { e ->
                            _uiState.value = RadioUiState.Error(e.message ?: context.getString(R.string.error_unknown), isServerError = false)
                        }
                } else {
                    emptyFlow()
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
                val newStations = StationProcessor.groupAndSortStations(state.allStations, activeUrl, favorites)
                if (newStations != state.stations) {
                    _uiState.value = state.copy(stations = newStations)
                }
            }
        }.launchIn(viewModelScope)
    }

    private fun handleGeocoding(location: Location) {
        val distance = calculateDistance(lastGeocodedLocation, location)
        if (_locationInfo.value.city != null && distance <= GEOCODING_DISTANCE_THRESHOLD_METERS) return

        lastGeocodedLocation = location
        viewModelScope.launch {
            val result = locationRepository.getAddressesFromLocation(location)
            val addresses = (result as? NetworkResult.Success)?.data ?: return@launch
            val firstAddress = addresses.firstOrNull() ?: return@launch

            val bestCandidate = AddressRefiner.findBestCityCandidate(addresses)
            
            fun isValid(name: String?): Boolean = name != null && name.length > 2 && !name.all { it.isDigit() }

            val rawCity = bestCandidate 
                ?: firstAddress.locality?.takeIf { isValid(it) }
                ?: firstAddress.subAdminArea?.takeIf { isValid(it) }
                ?: firstAddress.adminArea?.takeIf { isValid(it) }
                ?: firstAddress.featureName?.takeIf { isValid(it) }
            
            val newCity = AddressRefiner.cleanCityName(rawCity)
            val searchTerms = listOfNotNull(newCity, firstAddress.subAdminArea, firstAddress.adminArea, firstAddress.countryName)
                .distinct()
            val citySearchQuery = if (searchTerms.isNotEmpty()) searchTerms.joinToString(", ") else null
            
            val isSpecificCity = bestCandidate != null || firstAddress.locality?.let { isValid(it) } == true
            val distKm = if (isSpecificCity && citySearchQuery != null) {
                calculateCityDistance(location, citySearchQuery)
            } else {
                null
            }

            if (newCity != _locationInfo.value.city || distKm != _locationInfo.value.distanceKm) {
                updateLocationInfo(
                    city = newCity ?: _locationInfo.value.city,
                    country = firstAddress.countryName ?: _locationInfo.value.country,
                    code = firstAddress.countryCode ?: _locationInfo.value.countryCode,
                    distance = distKm,
                )
            }
        }
    }

    private fun calculateDistance(from: Location?, to: Location): Float {
        if (from == null) return Float.MAX_VALUE
        val results = FloatArray(1)
        Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, results)
        return results[0]
    }

    private suspend fun calculateCityDistance(currentLocation: Location, cityName: String): Int? {
        val result = locationRepository.getCityLocation(cityName, proximity = currentLocation)
        val cityLoc = (result as? NetworkResult.Success)?.data ?: return _locationInfo.value.distanceKm
        val results = FloatArray(1)
        Location.distanceBetween(
            currentLocation.latitude, currentLocation.longitude,
            cityLoc.latitude, cityLoc.longitude,
            results,
        )
        return (results[0] / 1000).roundToInt()
    }

    fun updateMaterialYou(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateMaterialYou(enabled) }
    fun updateAccentColor(color: Color) = viewModelScope.launch { settingsRepository.updateAccentColor(color) }
    fun completeOnboarding() = viewModelScope.launch { settingsRepository.updateOnboardingCompleted(completed = true) }
    fun resetOnboarding() = viewModelScope.launch { settingsRepository.updateOnboardingCompleted(completed = false) }

    fun refresh() {
        _currentLocation.value?.let { viewModelScope.launch { repository.updateNearbyStations(it, force = true) } }
    }

    fun toggleFavorite(station: RadioStation) {
        viewModelScope.launch { settingsRepository.toggleFavorite(station.name) }
    }

    private fun updateLocationInfo(city: String?, country: String?, code: String?, distance: Int? = null) {
        _locationInfo.value = LocationInfo(city, country, code, distance)
        viewModelScope.launch { settingsRepository.updateLastLocation(city, code) }
        (_uiState.value as? RadioUiState.Success)?.let { currentState ->
            _uiState.value = currentState.copy(cityName = city, countryName = country, countryCode = code)
        }
    }

    fun toggleStation(url: String) {
        val station = (_uiState.value as? RadioUiState.Success)?.stations?.find { it.streamUrl == url }
        if (_selectedStationUrl.value == url) {
            if (radioPlayer.isPlaying.value || radioPlayer.isBuffering.value) radioPlayer.pause()
            else {
                _selectedStationUrl.value = url
                radioPlayer.play(station?.name, url, station?.logo, station?.network)
            }
        } else {
            _selectedStationUrl.value = url
            radioPlayer.play(station?.name, url, station?.logo, station?.network)
        }
    }

    fun nextStation() {
        val stations = (_uiState.value as? RadioUiState.Success)?.stations ?: return
        if (stations.isEmpty()) return
        val currentIndex = stations.indexOfFirst { it.streamUrl == _selectedStationUrl.value }
        val nextIndex = if (currentIndex == -1 || currentIndex == stations.lastIndex) 0 else currentIndex + 1
        stations[nextIndex].let { s ->
            s.streamUrl?.let { url ->
                _selectedStationUrl.value = url
                radioPlayer.play(s.name, url, s.logo, s.network)
            }
        }
    }

    fun previousStation() {
        val stations = (_uiState.value as? RadioUiState.Success)?.stations ?: return
        if (stations.isEmpty()) return
        val currentIndex = stations.indexOfFirst { it.streamUrl == _selectedStationUrl.value }
        val prevIndex = if (currentIndex <= 0) stations.lastIndex else currentIndex - 1
        stations[prevIndex].let { s ->
            s.streamUrl?.let { url ->
                _selectedStationUrl.value = url
                radioPlayer.play(s.name, url, s.logo, s.network)
            }
        }
    }

    companion object {
        private const val GEOCODING_DISTANCE_THRESHOLD_METERS = 500f
        private const val STATION_REFRESH_DISTANCE_METERS = 100f
        private const val LOCATION_UPDATE_INTERVAL_MS = 60000L
        private const val FLOW_STOP_TIMEOUT_MS = 5000L
    }
}

sealed interface RadioUiState {
    data object Loading : RadioUiState
    data class Success(
        val stations: List<RadioStation>,
        val allStations: List<RadioStation> = emptyList(),
        val currentLocation: Location,
        val cityName: String? = null,
        val countryName: String? = null,
        val countryCode: String? = null,
    ) : RadioUiState
    data class Error(val message: String, val isServerError: Boolean = false) : RadioUiState
}
