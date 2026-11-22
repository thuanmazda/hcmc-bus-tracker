package com.hcmcbustracker.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hcmcbustracker.core.designsystem.component.parseRouteColor
import com.hcmcbustracker.core.designsystem.theme.GreenPrimary
import com.hcmcbustracker.core.designsystem.theme.TransitDimens
import org.junit.Assert.assertEquals
import org.junit.Test

class DesignTokensTest {

    @Test
    fun parseRouteColor_handlesNullAndBlank() {
        assertEquals(GreenPrimary, parseRouteColor(null))
        assertEquals(GreenPrimary, parseRouteColor(""))
        assertEquals(GreenPrimary, parseRouteColor("   "))
    }

    @Test
    fun parseRouteColor_handlesInvalidHex() {
        assertEquals(GreenPrimary, parseRouteColor("invalid"))
        assertEquals(GreenPrimary, parseRouteColor("#XYZ"))
        assertEquals(GreenPrimary, parseRouteColor("00FF00")) // missing leading #
    }

    @Test
    fun parseRouteColor_parsesStandard6DigitHex() {
        val color = parseRouteColor("#FF0000")
        assertEquals(Color(0xFFFF0000), color)

        val blueColor = parseRouteColor("#007AFF")
        assertEquals(Color(0xFF007AFF), blueColor)
    }

    @Test
    fun parseRouteColor_parses8DigitHexWithAlpha() {
        val transparentRed = parseRouteColor("#80FF0000")
        assertEquals(Color(0x80FF0000), transparentRed)
    }

    @Test
    fun transitDimens_verifyAppleHigAndGridStandards() {
        assertEquals(48.dp, TransitDimens.ButtonHeightDefault)
        assertEquals(48.dp, TransitDimens.MinTouchTargetSize)
        assertEquals(52.dp, TransitDimens.SearchBarHeight)
        assertEquals(16.dp, TransitDimens.RadiusCard)
        assertEquals(24.dp, TransitDimens.RadiusSheet)
        assertEquals(999.dp, TransitDimens.RadiusPill)
    }
}
