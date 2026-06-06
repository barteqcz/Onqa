package com.barteqcz.onqa.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
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
) {
    val normalizedStreamUrl: String? by lazy { streamUrl?.trimEnd('/') }
    val normalizedStreamUrlHq: String? by lazy { streamUrlHq?.trimEnd('/') }

    fun getStreamUrl(useHq: Boolean): String? {
        return if (useHq && !streamUrlHq.isNullOrBlank()) streamUrlHq else streamUrl
    }
}
