/**
 * Helper object responsible for grouping, coverage filtering, and sorting radio stations by distance and favorites.
 */
package com.barteqcz.onqa.data.util

import com.barteqcz.onqa.data.model.RadioStation
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

object StationProcessor {

    private val comparator = compareBy<RadioStation> { it.distance ?: Double.MAX_VALUE }
        .thenBy { it.transmitterId ?: Int.MAX_VALUE }
        .thenBy { it.displayOrder ?: Int.MAX_VALUE }

    private val resultComparator = compareByDescending<RadioStation> { it.isFavorite }
        .thenBy { it.distance ?: Double.MAX_VALUE }
        .thenBy { it.transmitterId ?: Int.MAX_VALUE }
        .thenBy { it.displayOrder ?: Int.MAX_VALUE }

    fun groupAndSortStations(
        allStations: List<RadioStation>,
        favorites: Set<String>
    ): ImmutableList<RadioStation> {
        if (allStations.isEmpty()) return kotlinx.collections.immutable.persistentListOf()
        
        return allStations.groupBy { it.name to it.network }
            .asSequence()
            .mapNotNull { (_, networkStations) ->
                var bestRepresentative: RadioStation? = null

                for (station in networkStations) {
                    val isZeroCoverage = station.coverageKm != null && station.coverageKm <= 0.0
                    val isExplicitlyOutOfCoverage = station.distance != null && 
                            station.coverageKm != null && station.distance > station.coverageKm
                    
                    if (!isZeroCoverage && !isExplicitlyOutOfCoverage) {
                        if (bestRepresentative == null || comparator.compare(station, bestRepresentative) < 0) {
                            bestRepresentative = station
                        }
                    }
                }

                bestRepresentative?.copy(isFavorite = bestRepresentative.name in favorites)
            }
            .sortedWith(resultComparator)
            .toImmutableList()
    }
}
