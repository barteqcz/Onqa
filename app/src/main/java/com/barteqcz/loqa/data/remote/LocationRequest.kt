package com.barteqcz.loqa.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class LocationRequest(
    val lat: Double,
    val lon: Double
)
