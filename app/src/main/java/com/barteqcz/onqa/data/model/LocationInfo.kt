package com.barteqcz.onqa.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class LocationInfo(
    val city: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
)
