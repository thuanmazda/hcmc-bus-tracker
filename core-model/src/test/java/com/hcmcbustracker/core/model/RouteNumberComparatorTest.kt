package com.hcmcbustracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteNumberComparatorTest {

    @Test
    fun `D4 does NOT precede routes 01, 02, 03, 150`() {
        val routes = listOf("D4", "01", "02", "03", "150")
        val sorted = routes.sortedWith(RouteNumberComparator)

        // D4 must be strictly placed after pure numeric routes
        assertEquals(listOf("01", "02", "03", "150", "D4"), sorted)
    }

    @Test
    fun `pure numeric routes sort in natural numerical order`() {
        val routes = listOf("150", "03", "10", "01", "02", "20", "1")
        val sorted = routes.sortedWith(RouteNumberComparator)

        // Numerical order: 1, 01, 02, 03, 10, 20, 150
        assertEquals("01", sorted[0])
        assertEquals("1", sorted[1])
        assertEquals("02", sorted[2])
        assertEquals("03", sorted[3])
        assertEquals("10", sorted[4])
        assertEquals("20", sorted[5])
        assertEquals("150", sorted[6])
    }

    @Test
    fun `letter prefix routes sort by prefix then trailing number`() {
        val routes = listOf("DL06", "D4", "DL01", "D1", "D2", "B1")
        val sorted = routes.sortedWith(RouteNumberComparator)

        // B1 < D1 < D2 < D4 < DL01 < DL06
        assertEquals(listOf("B1", "D1", "D2", "D4", "DL01", "DL06"), sorted)
    }

    @Test
    fun `hyphenated sub-routes sort by base number then sub-number`() {
        val routes = listOf("72-2C", "60-3", "72-1", "70-2")
        val sorted = routes.sortedWith(RouteNumberComparator)

        assertEquals(listOf("60-3", "70-2", "72-1", "72-2C"), sorted)
    }

    @Test
    fun `mixed complex routes sort in truthful predictable tiers`() {
        val routes = listOf("DL02", "D4", "150", "01", "72-1", "03", "60-3", "D1", "02")
        val sorted = routes.sortedWith(RouteNumberComparator)

        val expected = listOf(
            "01", "02", "03", "150",   // Tier 0: Pure numbers
            "60-3", "72-1",             // Tier 1: Hyphenated sub-routes
            "D1", "D4", "DL02"          // Tier 2: Letter prefix routes
        )
        assertEquals(expected, sorted)
    }

    @Test
    fun `resilient to whitespace and case differences`() {
        assertEquals(0, RouteNumberComparator.compare(" 01 ", "01"))
        assertEquals(0, RouteNumberComparator.compare("d4", "D4"))
        assertTrue(RouteNumberComparator.compare("01", "d4") < 0)
        assertTrue(RouteNumberComparator.compare("D4", "01") > 0)
    }

    @Test
    fun `extension sortedByRouteNumber sorts domain Route objects`() {
        val r1 = Route(id = 1, routeNo = "D4", name = "Vinhomes Grand Park")
        val r2 = Route(id = 2, routeNo = "01", name = "Ga Ba Son")
        val r3 = Route(id = 3, routeNo = "150", name = "Chợ Lớn")

        val sorted = listOf(r1, r2, r3).sortedByRouteNumber()
        assertEquals("01", sorted[0].routeNo)
        assertEquals("150", sorted[1].routeNo)
        assertEquals("D4", sorted[2].routeNo)
    }
}
