package com.hcmcbustracker.core.model

import kotlinx.serialization.Serializable

/**
 * Domain model representing a physical bus stop in HCMC.
 */
@Serializable
data class BusStop(
    val id: Int,
    val code: String,
    val name: String,
    val stopType: String,
    val zone: String,
    val ward: String? = null,
    val addressNo: String,
    val street: String,
    val supportDisability: Boolean,
    val status: String,
    val lat: Double,
    val lng: Double,
    val searchKeywords: String,
    val routes: List<String>
) {
    /**
     * Formats full readable address (e.g. "4 Hàm Nghi, Phường Bến Thành, Quận 1").
     * Normalizes trailing punctuation, delimiters, and incomplete trailing administrative tokens.
     */
    val fullAddress: String
        get() = formatStopAddress(addressNo, street, ward, zone)

    companion object {
        private val STANDALONE_ADMIN_REGEX = Regex("""^(Phường|Quận|P\.|Q\.)\s*$""", RegexOption.IGNORE_CASE)
        private val DANGLING_ADMIN_REGEX = Regex("""(^|[,;\s\-]+)(Phường|Quận|P\.|Q\.)\s*$""", RegexOption.IGNORE_CASE)
        private val MULTIPLE_COMMAS_REGEX = Regex(""",\s*(,\s*)+""")

        /**
         * Formats and cleans address components into a standardized string without trailing delimiters.
         */
        fun formatStopAddress(
            addressNo: String,
            street: String,
            ward: String?,
            zone: String
        ): String {
            fun cleanPart(text: String?): String {
                if (text.isNullOrBlank()) return ""
                var s = text.trim().trim(',', '.', ';', '-', ' ')
                if (STANDALONE_ADMIN_REGEX.matches(s)) return ""
                // Strip dangling incomplete administrative qualifiers at the end (e.g. "..., Phường")
                s = DANGLING_ADMIN_REGEX.replace(s, "").trim().trimEnd(',', '.', ';', '-', ' ')
                return s
            }

            val cleanAddressNo = cleanPart(addressNo)
            val cleanStreet = cleanPart(street)
            val cleanWard = cleanPart(ward)
            val cleanZone = cleanPart(zone)

            val parts = mutableListOf<String>()
            if (cleanAddressNo.isNotBlank()) {
                parts.add(cleanAddressNo)
            }
            if (cleanStreet.isNotBlank() && !cleanAddressNo.contains(cleanStreet, ignoreCase = true)) {
                parts.add(cleanStreet)
            }
            if (cleanWard.isNotBlank() && !cleanAddressNo.contains(cleanWard, ignoreCase = true) && !cleanStreet.contains(cleanWard, ignoreCase = true)) {
                parts.add(cleanWard)
            }
            if (cleanZone.isNotBlank() && !cleanAddressNo.contains(cleanZone, ignoreCase = true) && !cleanWard.contains(cleanZone, ignoreCase = true)) {
                parts.add(cleanZone)
            }

            var joined = parts.joinToString(", ")
            joined = MULTIPLE_COMMAS_REGEX.replace(joined, ", ")
            joined = DANGLING_ADMIN_REGEX.replace(joined, "").trim().trimEnd(',', '.', ';', '-', ' ')
            return joined
        }
    }
}
