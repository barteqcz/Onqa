package com.barteqcz.loqa.util

import android.location.Address

object AddressRefiner {

    private val CITY_PART_SPLIT_REGEX = Regex("[-–/](?=\\p{Ll})")
    private val DASH_CLEAN_REGEX = Regex("[-–/](?=\\p{Ll})")
    private val FORBIDDEN_WORDS = setOf(
        "district", "okres", "kraj", "mesto", "město", "powiat", "wojewodztwo"
    )

    fun findBestCityCandidate(addresses: List<Address>): String? {
        val firstAddress = addresses.firstOrNull() ?: return null
        val province = firstAddress.adminArea ?: ""
        val district = firstAddress.subAdminArea ?: ""

        val cityFields = addresses.flatMap { addr -> listOfNotNull(addr.locality, addr.subLocality) }
        val cityParts = cityFields.flatMap { it.split(CITY_PART_SPLIT_REGEX) }
        val districtParts = district.split(CITY_PART_SPLIT_REGEX)

        val allCandidates = (cityFields + cityParts + districtParts)
            .filter { part ->
                val p = part.lowercase()
                p.length > 2 &&
                !p.equals(province, ignoreCase = true) &&
                !p.all { it.isDigit() } &&
                p !in FORBIDDEN_WORDS
            }
            .distinct()

        return allCandidates.maxByOrNull { candidate ->
            var score = 0.0
            val appearedInCityFields = cityFields.any { it.contains(candidate, ignoreCase = true) }

            addresses.forEach { addr ->
                val loc = addr.locality ?: ""
                val sub = addr.subLocality ?: ""
                val adm = addr.subAdminArea ?: ""

                if (loc.equals(candidate, ignoreCase = true)) score += 30.0
                else if (loc.contains(candidate, ignoreCase = true)) score += 15.0

                if (sub.equals(candidate, ignoreCase = true)) score += 10.0

                if (adm.contains(candidate, ignoreCase = true) && !adm.equals(candidate, ignoreCase = true)) {
                    score += if (appearedInCityFields) 100.0 else 5.0
                }
            }

            if (candidate.equals(district, ignoreCase = true)) {
                val isPrimaryLocality = firstAddress.locality?.equals(candidate, ignoreCase = true) == true
                val hasSpecificTownNames = cityFields.any { !it.equals(district, ignoreCase = true) }
                if (hasSpecificTownNames && !isPrimaryLocality) score -= 500.0
            }

            score
        }
    }

    fun cleanCityName(name: String?): String? {
        if (name == null) return null
        var cleaned = name.trim()

        val commaIndex = cleaned.indexOf(',')
        if (commaIndex != -1) {
            cleaned = cleaned.substring(0, commaIndex)
        }

        val spaceIndex = cleaned.indexOf(' ')
        if (spaceIndex != -1 && spaceIndex < cleaned.length - 1) {
            if (cleaned[spaceIndex + 1].isDigit()) {
                cleaned = cleaned.substring(0, spaceIndex)
            }
        }
        
        val dashMatch = DASH_CLEAN_REGEX.find(cleaned)
        if (dashMatch != null) {
            cleaned = cleaned.substring(0, dashMatch.range.first).trim()
        }
        return cleaned
    }
}
