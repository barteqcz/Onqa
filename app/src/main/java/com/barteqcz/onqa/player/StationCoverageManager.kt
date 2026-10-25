package com.barteqcz.onqa.player

import com.barteqcz.onqa.data.model.RadioStation
import com.barteqcz.onqa.data.repository.SettingsRepository
import com.barteqcz.onqa.data.util.StationProcessor
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StationCoverageManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    sealed interface CoverageResult {
        data class SwitchTo(
            val name: String,
            val url: String,
            val logo: String?,
            val network: String?,
            val forceReload: Boolean = false,
        ) : CoverageResult
        data object StopPlayback : CoverageResult
        data object KeepCurrent : CoverageResult
    }

    suspend fun checkCoverage(
        stations: List<RadioStation>,
        currentUrl: String?,
        currentName: String?,
        currentNetwork: String?,
    ): CoverageResult {
        if (currentUrl.isNullOrBlank() || currentName.isNullOrBlank()) return CoverageResult.KeepCurrent

        val normalizedCurrent = currentUrl.trimEnd('/')
        val settings = settingsRepository.settingsFlow.first()
        val favorites = settings.favoriteStations
        val useHq = settings.useHqStream

        fun RadioStation.isInCoverage(): Boolean {
            val notZero = coverageKm == null || coverageKm > 0.0
            val distanceOk = distance == null || coverageKm == null || distance <= coverageKm
            return notZero && distanceOk
        }

        // 1. Check if the current station has ANY transmitter/entry in coverage at the new location
        val matchingStations = stations.filter { it.name == currentName || it.matchesUrl(normalizedCurrent) }
        val inCoverageMatch = matchingStations.find { it.isInCoverage() }

        if (inCoverageMatch != null) {
            val targetUrl = inCoverageMatch.getStreamUrl(useHq)
            if (targetUrl != null && targetUrl.trimEnd('/') != normalizedCurrent) {
                return CoverageResult.SwitchTo(
                    name = inCoverageMatch.name,
                    url = targetUrl,
                    logo = inCoverageMatch.logo,
                    network = inCoverageMatch.network,
                    forceReload = false,
                )
            }
            return CoverageResult.KeepCurrent
        }

        // 2. The station is genuinely OUT OF COVERAGE at the new location (e.g. regional station left behind)
        val effectiveNetwork = currentNetwork ?: matchingStations.firstOrNull()?.network
        val sortedStations = StationProcessor.groupAndSortStations(stations, currentUrl, favorites)

        // Prefer station in the SAME network that is in coverage
        val nextNetworkStation = if (effectiveNetwork != null) {
            sortedStations.firstOrNull { it.network == effectiveNetwork && it.isInCoverage() }
        } else null

        // Prefer favorite station that is in coverage
        val nextFavoriteStation = if (nextNetworkStation == null && favorites.isNotEmpty()) {
            sortedStations.firstOrNull { it.isFavorite && it.isInCoverage() }
        } else null

        // Fallback to the first station on the sorted list in coverage
        val fallbackStation = nextNetworkStation
            ?: nextFavoriteStation
            ?: sortedStations.firstOrNull { it.isInCoverage() }

        if (fallbackStation != null) {
            val targetUrl = fallbackStation.getStreamUrl(useHq)
            if (targetUrl != null && targetUrl.trimEnd('/') != normalizedCurrent) {
                return CoverageResult.SwitchTo(
                    name = fallbackStation.name,
                    url = targetUrl,
                    logo = fallbackStation.logo,
                    network = fallbackStation.network,
                )
            }
        }

        return CoverageResult.StopPlayback
    }
}
