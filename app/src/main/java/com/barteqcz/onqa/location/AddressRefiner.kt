package com.barteqcz.onqa.location

import android.location.Address
import com.barteqcz.onqa.data.model.LocationInfo

object AddressRefiner {

    fun refineLocation(addresses: List<Address>?, lat: Double? = null, lon: Double? = null): LocationInfo {
        val baseInfo = if (addresses.isNullOrEmpty()) {
            LocationInfo()
        } else {
            val bestCandidate = findBestCityCandidate(addresses)
            val city = cleanCityName(bestCandidate)
            val firstAddress = addresses.first()
            LocationInfo(
                city = city,
                country = firstAddress.countryName,
                countryCode = firstAddress.countryCode
            )
        }

        return applySpecialRegionOverrides(baseInfo, lat, lon, addresses)
    }

    private fun applySpecialRegionOverrides(
        info: LocationInfo,
        lat: Double?,
        lon: Double?,
        addresses: List<Address>?
    ): LocationInfo {
        val area = addresses?.firstNotNullOfOrNull { it.adminArea } ?: ""
        val country = addresses?.firstNotNullOfOrNull { it.countryName } ?: ""
        val locality = addresses?.firstNotNullOfOrNull { it.locality } ?: ""
        val currentCode = info.countryCode

        val mentionsCrimea = listOf(area, country, locality).any {
            it.contains("Crimea", ignoreCase = true)
        }

        if (mentionsCrimea) {
            return info.copy(countryCode = "UA")
        }

        if (lat != null && lon != null && lat in 44.38..46.1 && lon in 32.4..36.6) {
            if (currentCode == null || currentCode == "RU") {
                if (info.city != null || info.country != null || currentCode == "RU") {
                    return info.copy(countryCode = "UA")
                }
            }
        }

        val mentionsKosovo = listOf(area, country, locality).any {
            it.contains("Kosovo", ignoreCase = true)
        }

        if (mentionsKosovo || currentCode == "XK") {
            return info.copy(countryCode = "XK")
        }

        if (currentCode == null && lat != null && lon != null && lat in 41.85..43.25 && lon in 20.0..21.7) {
            if (info.city != null || info.country != null) {
                return info.copy(countryCode = "XK")
            }
        }

        return info
    }

    private fun findBestCityCandidate(addresses: List<Address>): String? {
        if (addresses.isEmpty()) return null

        val mainCity = addresses.asReversed().firstNotNullOfOrNull { it.locality }

        if (mainCity != null) return mainCity

        val first = addresses.first()

        return first.subAdminArea ?: first.adminArea ?: first.countryName
    }

    fun cleanCityName(name: String?): String? {
        if (name.isNullOrBlank()) return null

        return name.replace(Regex("\\d+"), "")
            .replace(Regex("-(?=[a-z])\\w+"), "")
            .trim()
            .takeIf { it.length > 2 }
    }
}
