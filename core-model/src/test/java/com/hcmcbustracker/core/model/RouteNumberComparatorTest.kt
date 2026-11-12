package com.hcmcbustracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteNumberComparatorTest {

    @Test
    fun `test letters first before numbers`() {
        val input = listOf("01", "D4", "150", "DL01", "03", "MRT1", "SWB1", "A01")
        val sorted = input.sortedWith(RouteNumberComparator)

        val expected = listOf("A01", "D4", "DL01", "MRT1", "SWB1", "01", "03", "150")
        assertEquals(expected, sorted)
    }

    @Test
    fun `test natural numeric order for numbers`() {
        val input = listOf("150", "01", "10", "02", "1", "19", "56", "100", "08")
        val sorted = input.sortedWith(RouteNumberComparator)

        // 01 vs 1: 01 has length 2, 1 has length 1. 01 < 02 < 08 < 10 < 19 < 56 < 100 < 150
        val numericValues = sorted.map { it.toInt() }
        assertEquals(listOf(1, 1, 2, 8, 10, 19, 56, 100, 150), numericValues)
    }

    @Test
    fun `test hyphenated and mixed chunk routes`() {
        val input = listOf("62-10", "62-1", "62-2", "70-1", "156V", "156D")
        val sorted = input.sortedWith(RouteNumberComparator)

        // 62-1 < 62-2 < 62-10 < 70-1 < 156D < 156V
        val expected = listOf("62-1", "62-2", "62-10", "70-1", "156D", "156V")
        assertEquals(expected, sorted)
    }

    @Test
    fun `test route comparator with Route objects`() {
        val r1 = Route(id = 1, routeNo = "150", name = "Tuyến 150")
        val r2 = Route(id = 2, routeNo = "D4", name = "Tuyến D4")
        val r3 = Route(id = 3, routeNo = "01", name = "Tuyến 01")

        val sorted = listOf(r1, r2, r3).sortedWith(RouteNumberComparator.ROUTE_COMPARATOR)
        assertEquals(listOf("D4", "01", "150"), sorted.map { it.routeNo })
    }

    @Test
    fun `test route combo comparator with RouteCombo objects`() {
        val rc1 = RouteCombo(id = 1, routeNo = "10", name = "Tuyến 10")
        val rc2 = RouteCombo(id = 2, routeNo = "02", name = "Tuyến 02")
        val rc3 = RouteCombo(id = 3, routeNo = "MRT1", name = "Metro 1")

        val sorted = listOf(rc1, rc2, rc3).sortedWith(RouteNumberComparator.ROUTE_COMBO_COMPARATOR)
        assertEquals(listOf("MRT1", "02", "10"), sorted.map { it.routeNo })
    }

    @Test
    fun `test null and empty strings`() {
        val list: List<String?> = listOf("01", null, "", "D4")
        val sorted = list.sortedWith(RouteNumberComparator)
        assertEquals(listOf(null, "", "D4", "01"), sorted)
    }
}
