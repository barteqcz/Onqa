package com.barteqcz.onqa.player

import com.barteqcz.onqa.data.model.RadioStation
import com.barteqcz.onqa.data.repository.SettingsRepository
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
        isPlayerActive: Boolean,
    ): CoverageResult {
        if (currentUrl.isNullOrBlank() || currentName.isNullOrBlank()) return CoverageResult.KeepCurrent

        val normalizedCurrent = currentUrl.trimEnd('/')
        val settings = settingsRepository.settingsFlow.first()
        val favorites = settings.favoriteStations
        val useHq = settings.useHqStream

        // 1. Check if current station exists in the new location list (by exact name or stream URL)
        val updatedByName = stations.find { it.name == currentName }
        val updatedByUrl = stations.find { it.matchesUrl(normalizedCurrent) }
        val playingStation = updatedByName ?: updatedByUrl

        // Determine network name from current explicit parameter or playing station
        val effectiveNetwork = (currentNetwork ?: playingStation?.network)?.takeIf { it.isNotBlank() }

        fun RadioStation.isInCoverage(): Boolean {
            val notZero = coverageKm == null || coverageKm > 0.0
            val distanceOk = distance == null || coverageKm == null || distance <= coverageKm
            return notZero && distanceOk
        }

        // If the current station is present and still in coverage
        if (playingStation != null && playingStation.isInCoverage()) {
            val targetUrl = playingStation.getStreamUrl(useHq)
            if (targetUrl != null && targetUrl.trimEnd('/') != normalizedCurrent) {
                return CoverageResult.SwitchTo(
                    name = playingStation.name,
                    url = targetUrl,
                    logo = playingStation.logo,
                    network = playingStation.network,
                    forceReload = true,
                )
            }
            return CoverageResult.KeepCurrent
        }

        // The station is missing or out of coverage
        if (isPlayerActive) {
            // 1. Prefer station in the SAME network that is in coverage
            val nextNetworkStation = if (effectiveNetwork != null) {
                stations.asSequence()
                    .filter { it.network == effectiveNetwork && it.isInCoverage() }
                    .minByOrNull { it.distance ?: Double.MAX_VALUE }
            } else null

            // 2. Prefer favorite station that is in coverage
            val nextFavoriteStation = if (nextNetworkStation == null && favorites.isNotEmpty()) {
                stations.asSequence()
                    .filter { it.name in favorites && it.isInCoverage() }
                    .minByOrNull { it.distance ?: Double.MAX_VALUE }
            } else null

            // 3. Fallback to closest station in coverage
            val fallbackStation = nextNetworkStation
                ?: nextFavoriteStation
                ?: stations.asSequence()
                    .filter { it.isInCoverage() }
                    .minByOrNull { it.distance ?: Double.MAX_VALUE }

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

        return CoverageResult.KeepCurrent
    }
}
