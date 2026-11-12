package com.hcmcbustracker.core.model

import java.util.Locale

/**
 * Shared Natural Alphanumeric Comparator for Bus Route Numbers in HCMC Bus Tracker.
 *
 * Rules:
 * 1. Group 1 (Alphabetic prefix): Routes starting with letters (e.g. A01, D4, DL01, MRT1, SWB1)
 *    are prioritized and displayed BEFORE numeric routes, sorted alphabetically (case-insensitive).
 * 2. Group 2 (Numeric prefix): Routes starting with digits (e.g. 01, 02, 08, 10, 19, 56, 122, 150)
 *    are sorted by their natural numeric value rather than lexical order (so 10 < 150, 2 < 10).
 * 3. Group 3 (Mixed chunks): Routes with hyphenated or alphanumeric suffix (e.g. 62-1, 62-2, 70-1, 156D, 156V)
 *    are chunked into alternating text and number parts, comparing numeric parts by value.
 *
 * Preserves original EBMS route codes and leading zeros.
 */
object RouteNumberComparator : Comparator<String?> {

    private val TOKEN_REGEX = Regex("(\\d+|[a-zA-Z]+|[^a-zA-Z0-9]+)")

    override fun compare(r1: String?, r2: String?): Int {
        if (r1 == null && r2 == null) return 0
        if (r1 == null) return -1
        if (r2 == null) return 1

        val s1 = r1.trim()
        val s2 = r2.trim()
        if (s1.isEmpty() && s2.isEmpty()) return 0
        if (s1.isEmpty()) return -1
        if (s2.isEmpty()) return 1

        // Category 1: Letter prefix (A01, D4, DL01...)
        // Category 2: Digit prefix (01, 150...)
        // Category 3: Other
        val cat1 = getCategory(s1)
        val cat2 = getCategory(s2)

        if (cat1 != cat2) {
            return cat1.compareTo(cat2)
        }

        // Chunked natural comparison
        val tokens1 = tokenize(s1)
        val tokens2 = tokenize(s2)
        val minSize = minOf(tokens1.size, tokens2.size)

        for (i in 0 until minSize) {
            val t1 = tokens1[i]
            val t2 = tokens2[i]

            val isNum1 = t1.all { it.isDigit() }
            val isNum2 = t2.all { it.isDigit() }

            if (isNum1 && isNum2) {
                val n1 = t1.toLongOrNull() ?: Long.MAX_VALUE
                val n2 = t2.toLongOrNull() ?: Long.MAX_VALUE
                val numCmp = n1.compareTo(n2)
                if (numCmp != 0) return numCmp

                // If values are equal (e.g. "01" vs "1"), deterministic secondary check
                val lenCmp = t1.length.compareTo(t2.length)
                if (lenCmp != 0) return lenCmp
            } else if (isNum1) {
                return -1 // numbers before letters in secondary chunks
            } else if (isNum2) {
                return 1
            } else {
                val strCmp = t1.compareTo(t2, ignoreCase = true)
                if (strCmp != 0) return strCmp
            }
        }

        return tokens1.size.compareTo(tokens2.size).let {
            if (it != 0) it else s1.compareTo(s2)
        }
    }

    private fun getCategory(s: String): Int {
        val first = s.first()
        return when {
            first.isLetter() -> 1 // Alphabetic prefix first
            first.isDigit() -> 2  // Numeric prefix second
            else -> 3
        }
    }

    private fun tokenize(s: String): List<String> {
        return TOKEN_REGEX.findAll(s).map { it.value }.toList()
    }

    /**
     * Comparator for Route objects based on their routeNo.
     */
    val ROUTE_COMPARATOR = Comparator<Route> { r1, r2 ->
        compare(r1.routeNo, r2.routeNo)
    }

    /**
     * Comparator for RouteCombo objects based on their routeNo.
     */
    val ROUTE_COMBO_COMPARATOR = Comparator<RouteCombo> { r1, r2 ->
        compare(r1.routeNo, r2.routeNo)
    }
}
