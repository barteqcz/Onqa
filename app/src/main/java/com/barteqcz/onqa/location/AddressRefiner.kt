package com.barteqcz.onqa.location

import android.location.Address
import com.barteqcz.onqa.data.model.LocationInfo

object AddressRefiner {

    fun refineLocation(addresses: List<Address>?, lat: Double? = null, lon: Double? = null): LocationInfo {
        val firstAddress = addresses?.firstOrNull() ?: return LocationInfo()

        val countryNames = addresses.mapNotNull { it.countryName?.lowercase() }.toSet()
        val roadNames = addresses.mapNotNull { it.thoroughfare?.lowercase() }.toSet()

        // 1. Look for a "Major City" match across all results.
        // A major city match occurs when a locality or subLocality in the results list 
        // matches any administrative area (district, province, or region) found in the results.
        val allAdmins = addresses.flatMap { listOfNotNull(it.subAdminArea, it.adminArea) }.toSet()
        
        if (allAdmins.isNotEmpty()) {
            // Priority 1: Strong Matches (e.g., "Ostrava" in "Okres Ostrava-město")
            val strongCity = addresses.firstNotNullOfOrNull { addr ->
                val candidates = listOfNotNull(addr.locality, addr.subLocality)
                candidates.firstOrNull { candidate ->
                    allAdmins.any { admin ->
                        isStrongMajorCityMatch(candidate, admin)
                    } && candidate.lowercase() !in countryNames && candidate.lowercase() !in roadNames
                }
            }
            
            // Priority 2: Weak Matches (significant word overlap)
            val weakCity = if (strongCity == null) {
                addresses.firstNotNullOfOrNull { addr ->
                    val candidates = listOfNotNull(addr.locality, addr.subLocality)
                    candidates.firstOrNull { candidate ->
                        allAdmins.any { admin ->
                            isMajorCityMatch(candidate, admin)
                        } && candidate.lowercase() !in countryNames && candidate.lowercase() !in roadNames
                    }
                }
            } else null

            val majorCity = strongCity ?: weakCity
            
            if (majorCity != null) {
                val baseInfo = LocationInfo(
                    city = majorCity.substringBefore(",").trim(),
                    country = firstAddress.countryName,
                    countryCode = firstAddress.countryCode
                )
                return applySpecialRegionOverrides(baseInfo, lat, lon, addresses)
            }
        }

        // 2. Fallback to most specific city name detection
        val baseInfo = run {
            var city = addresses.firstNotNullOfOrNull { addr ->
                val loc = addr.locality ?: return@firstNotNullOfOrNull null
                
                // Avoid using country names or road names as the city name
                if (loc.lowercase() in countryNames || loc.lowercase() in roadNames) return@firstNotNullOfOrNull null
                
                loc
            }

            if (city == null) {
                // Hierarchical fallback: Prefer locality, then subLocality (neighborhood/town), 
                // then subAdminArea (district), then adminArea (region).
                city = firstAddress.locality ?: firstAddress.subLocality ?: firstAddress.subAdminArea ?: firstAddress.adminArea
            }

            val finalCity = city?.substringBefore(",")?.trim()
            
            LocationInfo(
                city = finalCity,
                country = firstAddress.countryName,
                countryCode = firstAddress.countryCode
            )
        }

        return applySpecialRegionOverrides(baseInfo, lat, lon, addresses)
    }

    private fun isStrongMajorCityMatch(name: String, adminArea: String): Boolean {
        val n = name.lowercase()
        val a = adminArea.lowercase()
        if (n == a) return true

        val nameWords = n.split(Regex("[\\s\\-\\u00A0]+")).filter { it.length > 3 }
        if (nameWords.isEmpty()) return false
        
        val adminWords = a.split(Regex("[\\s\\-\\u00A0]+")).filter { it.length > 3 }.toSet()

        return nameWords.all { it in adminWords }
    }

    private fun isMajorCityMatch(name: String, adminArea: String): Boolean {
        val n = name.lowercase()
        val a = adminArea.lowercase()
        if (n == a) return true

        // Split by space, hyphen, or non-breaking space
        val nameWords = n.split(Regex("[\\s\\-\\u00A0]+")).filter { it.length > 3 }
        val adminWords = a.split(Regex("[\\s\\-\\u00A0]+")).filter { it.length > 3 }

        return nameWords.any { nw -> adminWords.any { aw -> nw == aw } }
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
}
