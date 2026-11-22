package com.hcmcbustracker.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTabTest {

    @Test
    fun `navigation tabs have exact 4 tabs with expected titles according to Phase 2 architecture`() {
        val tabs = NavigationTab.entries
        assertEquals(4, tabs.size)
        assertEquals("Trang chủ", tabs[0].label)
        assertEquals("Bản đồ", tabs[1].label)
        assertEquals("Tuyến xe", tabs[2].label)
        assertEquals("Cá nhân", tabs[3].label)
    }

    @Test
    fun `navigation tab indices match ordinal`() {
        assertEquals(0, NavigationTab.HOME.ordinal)
        assertEquals(1, NavigationTab.MAP.ordinal)
        assertEquals(2, NavigationTab.ROUTES.ordinal)
        assertEquals(3, NavigationTab.PERSONAL.ordinal)
    }
}
