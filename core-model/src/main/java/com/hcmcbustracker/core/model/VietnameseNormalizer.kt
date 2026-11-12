package com.hcmcbustracker.core.model

import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

/**
 * Utility for normalizing Vietnamese text:
 * - Strips diacritical marks (dấu tiếng Việt)
 * - Converts "đ" and "Đ" to "d" and "D"
 * - Normalizes whitespaces (trims and collapses repeated spaces)
 * - Converts to lowercase for case-insensitive matching
 */
object VietnameseNormalizer {

    private val DIACRITICAL_MARKS: Pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
    private val MULTIPLE_SPACES: Pattern = Pattern.compile("\\s+")

    /**
     * Strips accents and converts text to lowercase unaccented string.
     * E.g. "Bến xe buýt Chợ Lớn" -> "ben xe buyt cho lon"
     * "Nguyễn Thị Nghĩa, Quận 1" -> "nguyen thi nghia, quan 1"
     * "Đinh Tiên Hoàng" -> "dinh tien hoang"
     */
    fun removeAccents(input: String?): String {
        if (input.isNullOrBlank()) return ""

        var normalized = input.trim()
        // Replace special Vietnamese characters that are not fully decomposed by NFD
        normalized = normalized
            .replace('đ', 'd')
            .replace('Đ', 'd')
            .replace('ð', 'd')

        // Decompose unicode characters (e.g. ế -> e + acute + circumflex)
        val nfd = Normalizer.normalize(normalized, Normalizer.Form.NFD)
        // Remove all diacritical mark characters
        val stripped = DIACRITICAL_MARKS.matcher(nfd).replaceAll("")

        // Normalize multiple spaces and convert to lowercase
        return MULTIPLE_SPACES.matcher(stripped).replaceAll(" ").lowercase(Locale.ROOT).trim()
    }

    /**
     * Checks if [source] contains [query] ignoring case, accents, and extra whitespaces.
     */
    fun containsIgnoreCaseAndAccents(source: String?, query: String?): Boolean {
        if (query.isNullOrBlank()) return true
        if (source.isNullOrBlank()) return false

        val cleanSource = removeAccents(source)
        val cleanQuery = removeAccents(query)
        return cleanSource.contains(cleanQuery)
    }

    /**
     * Token-based search: checks if all words in [query] appear in [source].
     * E.g. query "cho lon 01" matches "[01] Bến xe buýt Chợ Lớn".
     */
    fun matchesAllTokens(source: String?, query: String?): Boolean {
        if (query.isNullOrBlank()) return true
        if (source.isNullOrBlank()) return false

        val cleanSource = removeAccents(source)
        val tokens = removeAccents(query).split(" ").filter { it.isNotBlank() }

        return tokens.all { token -> cleanSource.contains(token) }
    }
}
