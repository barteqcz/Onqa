package com.barteqcz.loqa.location

import android.location.Address
import com.barteqcz.loqa.data.model.LocationInfo

object AddressRefiner {

    fun refineLocation(addresses: List<Address>?): LocationInfo {
        if (addresses.isNullOrEmpty()) return LocationInfo()

        val bestCandidate = findBestCityCandidate(addresses)
        val city = cleanCityName(bestCandidate)
        val firstAddress = addresses.first()

        return LocationInfo(
            city = city,
            country = firstAddress.countryName,
            countryCode = firstAddress.countryCode
        )
    }

    private fun findBestCityCandidate(addresses: List<Address>): String? {
        if (addresses.isEmpty()) return null

        val mainCity = addresses.asReversed().firstNotNullOfOrNull { it.locality }

        if (mainCity != null) return mainCity

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
