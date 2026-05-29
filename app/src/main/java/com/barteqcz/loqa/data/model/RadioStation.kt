package com.barteqcz.loqa.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RadioStation(
    val name: String,
    val streamUrl: String? = null,
    val streamUrlHq: String? = null,
    val logo: String? = null,
    val transmitterName: String? = null,
    val transmitterId: Int? = null,
    val network: String? = null,
    val distance: Double? = null,
    val displayOrder: Int? = 0,
    @SerialName("coverage_km")
    val coverageKm: Double? = null,
    val isFavorite: Boolean = false
)
