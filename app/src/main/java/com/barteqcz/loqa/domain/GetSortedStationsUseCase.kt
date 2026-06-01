package com.barteqcz.loqa.domain

import com.barteqcz.loqa.data.model.RadioStation
import com.barteqcz.loqa.data.util.StationProcessor
import javax.inject.Inject

class GetSortedStationsUseCase @Inject constructor() {
    operator fun invoke(
        allStations: List<RadioStation>,
        activeUrl: String?,
        favorites: Set<String>
    ): List<RadioStation> {
        return StationProcessor.groupAndSortStations(allStations, activeUrl, favorites)
    }
}
