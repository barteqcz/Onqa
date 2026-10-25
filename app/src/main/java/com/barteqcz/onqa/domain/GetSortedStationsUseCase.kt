package com.barteqcz.onqa.domain

import com.barteqcz.onqa.data.model.RadioStation
import com.barteqcz.onqa.data.util.StationProcessor
import kotlinx.collections.immutable.ImmutableList
import javax.inject.Inject

class GetSortedStationsUseCase @Inject constructor() {
    operator fun invoke(
        allStations: List<RadioStation>,
        activeUrl: String?,
        favorites: Set<String>
    ): ImmutableList<RadioStation> {
        return StationProcessor.groupAndSortStations(allStations, activeUrl, favorites)
    }
}
