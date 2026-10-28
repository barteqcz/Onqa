/**
 * Immutable data model storing smoothed geographic coordinates (latitude and longitude).
 */
package com.barteqcz.onqa.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class StableLocation(
    val latitude: Double,
    val longitude: Double,
)
