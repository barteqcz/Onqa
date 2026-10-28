/**
 * Use case responsible for processing and sorting the radio station list.
 */
package com.barteqcz.onqa.domain

import com.barteqcz.onqa.data.model.RadioStation
import com.barteqcz.onqa.data.util.StationProcessor
import kotlinx.collections.immutable.ImmutableList
import javax.inject.Inject

class GetSortedStationsUseCase @Inject constructor() {
    operator fun invoke(
        allStations: List<RadioStation>,
        favorites: Set<String>
    ): ImmutableList<RadioStation> {
        return StationProcessor.groupAndSortStations(allStations, favorites)
    }
}
