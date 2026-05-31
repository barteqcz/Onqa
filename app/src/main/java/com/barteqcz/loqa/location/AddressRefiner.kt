package com.barteqcz.loqa.location

import android.location.Address

object AddressRefiner {

    fun findBestCityCandidate(addresses: List<Address>): String? {
        if (addresses.isEmpty()) return null
        
        // To get the "Main Name" (e.g., "Ljubljana" instead of a district like "Bežigrad"),
        // we look for the 'locality' starting from the least specific results.
        // Geocoders usually return broad area matches at the end of the list.
        val mainCity = addresses.asReversed().firstNotNullOfOrNull { it.locality }
        
        if (mainCity != null) return mainCity

        // Fallback to administrative areas if locality is entirely missing
        val first = addresses.first()
        return first.subAdminArea ?: first.adminArea
    }

    fun cleanCityName(name: String?): String? {
        if (name.isNullOrBlank()) return null

        return name.replace(Regex("\\d+"), "")
            .replace(Regex("-(?=[a-z])\\w+"), "")
            .trim()
            .takeIf { it.length > 2 }
    }
}
