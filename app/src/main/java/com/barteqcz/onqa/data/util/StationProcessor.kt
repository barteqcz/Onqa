package com.barteqcz.onqa.data.util

import com.barteqcz.onqa.data.model.RadioStation

object StationProcessor {

    fun groupAndSortStations(
        allStations: List<RadioStation>,
        activeUrl: String?,
        favorites: Set<String>
    ): List<RadioStation> {
        val normalizedActive = activeUrl?.trimEnd('/')
        return allStations.groupBy { "${it.name}|${it.network}" }
            .asSequence()
            .mapNotNull { (_, networkStations) ->
                val stationsInCoverage = networkStations.filter {
                    val isZeroCoverage = it.coverageKm != null && it.coverageKm <= 0.0
                    val isExplicitlyOutOfCoverage = it.distance != null && it.coverageKm != null && it.distance > it.coverageKm
                    !isZeroCoverage && !isExplicitlyOutOfCoverage
                }

                val currentInGroup = if (normalizedActive != null) {
                    networkStations.find { 
                        it.streamUrl?.trimEnd('/') == normalizedActive || 
                        it.streamUrlHq?.trimEnd('/') == normalizedActive
                    }
                } else null

                val representative = currentInGroup ?: stationsInCoverage
                    .sortedWith(
                        compareBy<RadioStation> { it.distance ?: Double.MAX_VALUE }
                            .thenBy { it.transmitterId ?: Int.MAX_VALUE }
                            .thenBy { it.displayOrder ?: Int.MAX_VALUE }
                    ).firstOrNull()

                representative?.copy(isFavorite = representative.name in favorites)
            }
            .sortedWith(
                compareByDescending<RadioStation> { it.isFavorite }
                    .thenBy { it.distance ?: Double.MAX_VALUE }
                    .thenBy { it.transmitterId ?: Int.MAX_VALUE }
                    .thenBy { it.displayOrder ?: Int.MAX_VALUE }
            )
            .toList()
    }
}
