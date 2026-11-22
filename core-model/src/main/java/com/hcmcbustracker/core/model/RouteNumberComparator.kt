package com.hcmcbustracker.core.model

/**
 * Natural route number comparator designed for Ho Chi Minh City transit routes.
 *
 * Requirements:
 * 1. Purely numeric routes (e.g. "01", "1", "02", "03", "10", "150", "152") sort in natural numeric order:
 *    1, 2, 3... 10... 150...
 * 2. Purely numeric routes strictly precede letter-prefixed or alphanumeric routes:
 *    "01", "02", "03"... "150" < "D4", "DL01", "72-1"
 * 3. Hyphenated / suffixed numeric routes (e.g. "60-3", "70-2", "72-1", "72-2C") sort by base number,
 *    then by suffix.
 * 4. Letter-prefixed routes (e.g. "D1", "D2", "D4", "DL01", "DL06") sort by letter prefix, then trailing number:
 *    "D1" < "D2" < "D4" < "DL01" < "DL06".
 * 5. Case-insensitive and resilient to leading/trailing whitespace.
 */
object RouteNumberComparator : Comparator<String> {

    @JvmField
    val ROUTE_COMPARATOR: Comparator<Route> = Comparator { r1, r2 -> compare(r1.routeNo, r2.routeNo) }

    private val PURE_NUMBER_REGEX = Regex("""^0*(\d+)$""")
    private val NUMBER_WITH_SUFFIX_REGEX = Regex("""^0*(\d+)[-_/](.+)$""")
    private val PREFIX_THEN_NUMBER_REGEX = Regex("""^([a-zA-Z]+)0*(\d*)(.*)$""")

    override fun compare(routeA: String?, routeB: String?): Int {
        val a = routeA?.trim().orEmpty()
        val b = routeB?.trim().orEmpty()

        if (a == b) return 0
        if (a.isEmpty()) return 1
        if (b.isEmpty()) return -1

        val parsedA = parseRouteKey(a)
        val parsedB = parseRouteKey(b)

        return parsedA.compareTo(parsedB)
    }

    private fun parseRouteKey(routeNo: String): RouteSortKey {
        // Case 1: Pure number (e.g., "01", "2", "150")
        PURE_NUMBER_REGEX.matchEntire(routeNo)?.let { match ->
            val num = match.groupValues[1].toLongOrNull() ?: Long.MAX_VALUE
            return RouteSortKey(
                tier = 0,
                primaryNumber = num,
                secondaryNumber = 0L,
                prefix = "",
                suffix = routeNo
            )
        }

        // Case 2: Number with suffix / sub-route (e.g., "72-1", "60-3", "72-2C")
        NUMBER_WITH_SUFFIX_REGEX.matchEntire(routeNo)?.let { match ->
            val num = match.groupValues[1].toLongOrNull() ?: Long.MAX_VALUE
            val suffix = match.groupValues[2]
            val subNum = suffix.filter { it.isDigit() }.toLongOrNull() ?: 0L
            return RouteSortKey(
                tier = 1,
                primaryNumber = num,
                secondaryNumber = subNum,
                prefix = "",
                suffix = suffix.lowercase()
            )
        }

        // Case 3: Letter prefix then number (e.g., "D4", "D1", "DL01", "DL06")
        PREFIX_THEN_NUMBER_REGEX.matchEntire(routeNo)?.let { match ->
            val prefix = match.groupValues[1].uppercase()
            val num = match.groupValues[2].toLongOrNull() ?: 0L
            val remainder = match.groupValues[3].lowercase()
            return RouteSortKey(
                tier = 2,
                primaryNumber = num,
                secondaryNumber = 0L,
                prefix = prefix,
                suffix = remainder
            )
        }

        // Case 4: Any other miscellaneous code
        return RouteSortKey(
            tier = 3,
            primaryNumber = Long.MAX_VALUE,
            secondaryNumber = 0L,
            prefix = routeNo.lowercase(),
            suffix = ""
        )
    }

    private data class RouteSortKey(
        val tier: Int,
        val primaryNumber: Long,
        val secondaryNumber: Long,
        val prefix: String,
        val suffix: String
    ) : Comparable<RouteSortKey> {
        override fun compareTo(other: RouteSortKey): Int {
            if (this.tier != other.tier) {
                return this.tier.compareTo(other.tier)
            }
            return when (this.tier) {
                0 -> {
                    val numComp = this.primaryNumber.compareTo(other.primaryNumber)
                    if (numComp != 0) numComp else this.suffix.compareTo(other.suffix)
                }
                1 -> {
                    val numComp = this.primaryNumber.compareTo(other.primaryNumber)
                    if (numComp != 0) return numComp
                    val secComp = this.secondaryNumber.compareTo(other.secondaryNumber)
                    if (secComp != 0) secComp else this.suffix.compareTo(other.suffix)
                }
                2 -> {
                    val prefixComp = this.prefix.compareTo(other.prefix)
                    if (prefixComp != 0) return prefixComp
                    val numComp = this.primaryNumber.compareTo(other.primaryNumber)
                    if (numComp != 0) numComp else this.suffix.compareTo(other.suffix)
                }
                else -> this.prefix.compareTo(other.prefix)
            }
        }
    }
}

/**
 * Extension functions for natural route number sorting.
 */
fun List<Route>.sortedByRouteNumber(): List<Route> =
    sortedWith(RouteNumberComparator.ROUTE_COMPARATOR)

fun List<RouteCombo>.sortedCombosByRouteNumber(): List<RouteCombo> =
    sortedWith { r1, r2 -> RouteNumberComparator.compare(r1.routeNo, r2.routeNo) }
