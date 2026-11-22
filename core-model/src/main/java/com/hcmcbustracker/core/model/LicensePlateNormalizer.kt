package com.hcmcbustracker.core.model

/**
 * Normalizer and utility for Vietnamese transit bus license plates (Biển số xe buýt TP.HCM).
 *
 * Examples of valid plates in HCMC transit:
 * - "50E-784.27" / "50E78427" (VinBus electric / CNG / diesel)
 * - "51B-123.45" / "51B12345"
 * - "53N-4567"   / "53N4567"
 * - "60B-012.34" (inter-provincial Dong Nai - HCMC)
 */
object LicensePlateNormalizer {

    private val STRIP_REGEX = Regex("[^A-Za-z0-9]")
    private val PLATE_PATTERN = Regex("^[0-9]{2}[A-Za-z]{1,2}[0-9]{4,5}$")

    /**
     * Normalizes license plate by removing spaces, hyphens, periods, and uppercase.
     * E.g.: "50E - 784.27" -> "50E78427", "51b.12345" -> "51B12345".
     */
    fun normalize(rawPlate: String?): String {
        if (rawPlate.isNullOrBlank()) return ""
        return rawPlate.replace(STRIP_REGEX, "").uppercase()
    }

    /**
     * Extracts digits-only suffix for flexible tail matching (e.g. "78427" or "12345").
     */
    fun extractNumericSuffix(normalizedPlate: String): String {
        return normalizedPlate.filter { it.isDigit() }
    }

    /**
     * Extracts numeric integer value of license plate digits if present (trailing numeric tail).
     */
    fun extractNumericTail(rawPlate: String?): Int? {
        val norm = normalize(rawPlate)
        if (norm.isBlank()) return null
        val trailingDigits = norm.takeLastWhile { it.isDigit() }
        return trailingDigits.toIntOrNull()
    }

    /**
     * Formats normalized or raw plate string into standard Vietnamese display notation.
     * E.g. "50E78427" -> "50E-784.27", "51B12345" -> "51B-123.45", "53N4567" -> "53N-4567".
     */
    fun formatDisplay(rawPlate: String?): String {
        val norm = normalize(rawPlate)
        if (norm.length !in 7..8) return rawPlate?.trim() ?: ""

        val prefixLength = if (norm.length >= 3 && norm[2].isLetter() && (norm.length >= 4 && norm[3].isLetter())) 4 else 3
        if (norm.length < prefixLength + 4) return norm

        val prefix = norm.substring(0, prefixLength) // e.g. "50E" or "51LD"
        val numbers = norm.substring(prefixLength)   // e.g. "78427" or "4567"

        return if (numbers.length == 5) {
            "$prefix-${numbers.substring(0, 3)}.${numbers.substring(3)}"
        } else if (numbers.length == 4) {
            "$prefix-$numbers"
        } else {
            norm
        }
    }

    fun formatPlate(rawPlate: String?): String = formatDisplay(rawPlate)

    /**
     * Checks if search query matches a vehicle plate:
     * - Case-insensitive, punctuation-insensitive.
     * - Supports partial matching (contains) or matching by numerical tail.
     */
    fun matches(candidatePlate: String, query: String): Boolean {
        val normCandidate = normalize(candidatePlate)
        val normQuery = normalize(query)
        if (normCandidate.isEmpty() || normQuery.isEmpty()) return false

        if (normCandidate.contains(normQuery)) return true

        // Check if query is numeric tail (e.g. user typed "78427" for "50E78427")
        if (normQuery.all { it.isDigit() } && normQuery.length >= 3) {
            val candidateNumeric = extractNumericSuffix(normCandidate)
            if (candidateNumeric.endsWith(normQuery) || candidateNumeric.contains(normQuery)) {
                return true
            }
        }

        return false
    }

    fun matchesQuery(candidatePlate: String, query: String): Boolean = matches(candidatePlate, query)

    /**
     * Checks whether a string resembles a valid Vietnamese vehicle license plate structure.
     */
    fun isValidPlateSyntax(candidate: String): Boolean {
        val norm = normalize(candidate)
        return norm.matches(PLATE_PATTERN)
    }

    fun isValidPlateFormat(candidate: String?): Boolean = candidate != null && isValidPlateSyntax(candidate)
}
