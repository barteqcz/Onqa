package com.barteqcz.onqa.data.util

import com.barteqcz.onqa.data.model.RadioStation
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

object StationProcessor {

    fun groupAndSortStations(
        allStations: List<RadioStation>,
        activeUrl: String?,
        favorites: Set<String>
    ): ImmutableList<RadioStation> {
        val normalizedActive = activeUrl?.trimEnd('/')
        return allStations.groupBy { it.name to it.network }
            .asSequence()
            .mapNotNull { (_, networkStations) ->
                val stationsInCoverage = networkStations.filter {
                    val isZeroCoverage = it.coverageKm != null && it.coverageKm <= 0.0
                    val isExplicitlyOutOfCoverage = it.distance != null && it.coverageKm != null && it.distance > it.coverageKm
                    !isZeroCoverage && !isExplicitlyOutOfCoverage
                }

                val currentInGroup = if (normalizedActive != null) {
                    networkStations.find { 
                        it.normalizedStreamUrl == normalizedActive || 
                        it.normalizedStreamUrlHq == normalizedActive
                    }
                } else null

                val representative = currentInGroup ?: stationsInCoverage
                    .minWithOrNull(
                        compareBy<RadioStation> { it.distance ?: Double.MAX_VALUE }
                            .thenBy { it.transmitterId ?: Int.MAX_VALUE }
                            .thenBy { it.displayOrder ?: Int.MAX_VALUE }
                    )

                representative?.copy(isFavorite = representative.name in favorites)
            }
            .sortedWith(
                compareByDescending<RadioStation> { it.isFavorite }
                    .thenBy { it.distance ?: Double.MAX_VALUE }
                    .thenBy { it.transmitterId ?: Int.MAX_VALUE }
                    .thenBy { it.displayOrder ?: Int.MAX_VALUE }
            )
            .toImmutableList()
    }
}
