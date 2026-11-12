package com.hcmcbustracker.feature.map.render

import android.graphics.Bitmap
import android.graphics.Color
import android.util.DisplayMetrics
import com.hcmcbustracker.core.model.GpsFreshnessState
import com.hcmcbustracker.core.model.GpsLatencyCategory
import com.hcmcbustracker.core.model.PositionSource
import com.hcmcbustracker.core.model.VehicleArrivalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VehicleMarkerBitmapFactoryTest {

    @Test
    fun `coach bitmaps maintain exact logical dp across mdpi to xxxhdpi densities`() {
        val densities = listOf(
            DisplayMetrics.DENSITY_MEDIUM to 1.0f,  // 160 mdpi
            DisplayMetrics.DENSITY_HIGH to 1.5f,    // 240 hdpi
            DisplayMetrics.DENSITY_XHIGH to 2.0f,   // 320 xhdpi
            DisplayMetrics.DENSITY_XXHIGH to 3.0f,  // 480 xxhdpi
            DisplayMetrics.DENSITY_XXXHIGH to 4.0f  // 640 xxxhdpi
        )

        assertTrue(VehicleMapRenderConfig.NORMAL_COACH_DP in 36f..40f)
        assertTrue(VehicleMapRenderConfig.SELECTED_COACH_DP in 48f..54f)
        assertTrue(VehicleMapRenderConfig.SELECTED_HALO_DP in 56f..62f)

        val normalSpec = CoachIconSpec(
            isSelected = false,
            freshTier = CoachFreshTier.ACTIVE,
            statusBadge = CoachStatusBadge.NONE,
            hasHeading = true,
            isPredictedOrInterpolated = true
        )
        val selectedSpec = normalSpec.copy(isSelected = true)

        val expectedNormalCanvasDp = VehicleMapRenderConfig.NORMAL_COACH_DP + 6f
        val expectedSelectedCanvasDp = VehicleMapRenderConfig.SELECTED_HALO_DP

        for ((dpi, scale) in densities) {
            val factory = VehicleMarkerBitmapFactory(dpi)
            val normalBmp = factory.getOrCreateCoachBitmap(normalSpec)
            val selectedBmp = factory.getOrCreateCoachBitmap(selectedSpec)

            assertEquals(dpi, normalBmp.density)
            assertEquals(dpi, selectedBmp.density)
            assertEquals(Bitmap.Config.ARGB_8888, normalBmp.config)

            val expectedNormalPx = (expectedNormalCanvasDp * scale).toInt()
            val expectedSelectedPx = (expectedSelectedCanvasDp * scale).toInt()
            assertEquals(expectedNormalPx, normalBmp.width)
            assertEquals(expectedNormalPx, normalBmp.height)
            assertEquals(expectedSelectedPx, selectedBmp.width)
            assertEquals(expectedSelectedPx, selectedBmp.height)

            val renderedNormalDp = VehicleMarkerBitmapFactory.computeRenderedDp(
                bitmapWidthPx = normalBmp.width,
                bitmapDensityDpi = normalBmp.density,
                iconSize = VehicleMapRenderConfig.DEFAULT_ICON_SIZE
            )
            val renderedSelectedDp = VehicleMarkerBitmapFactory.computeRenderedDp(
                bitmapWidthPx = selectedBmp.width,
                bitmapDensityDpi = selectedBmp.density,
                iconSize = VehicleMapRenderConfig.DEFAULT_ICON_SIZE
            )

            assertEquals(expectedNormalCanvasDp, renderedNormalDp, 0.25f)
            assertEquals(expectedSelectedCanvasDp, renderedSelectedDp, 0.25f)
            assertTrue(selectedBmp.width > normalBmp.width)
        }
    }

    @Test
    fun `sub-mdpi density clamps safely to DENSITY_MEDIUM`() {
        val factory = VehicleMarkerBitmapFactory(DisplayMetrics.DENSITY_LOW)
        assertEquals(DisplayMetrics.DENSITY_MEDIUM, factory.safeDensityDpi)
        assertEquals(1.0f, factory.scaleFactor, 0.001f)
    }

    @Test
    fun `normal selected and no-heading specs generate distinct cached icons without premature recycle`() {
        val factory = VehicleMarkerBitmapFactory(DisplayMetrics.DENSITY_XXHIGH)
        val activeHeading = CoachIconSpec(
            isSelected = false,
            freshTier = CoachFreshTier.ACTIVE,
            statusBadge = CoachStatusBadge.NONE,
            hasHeading = true,
            isPredictedOrInterpolated = true
        )
        val activeNoHeading = activeHeading.copy(hasHeading = false, statusBadge = CoachStatusBadge.AT_STOP)
        val delayedHeading = activeHeading.copy(freshTier = CoachFreshTier.DELAYED)
        val staleHeld = activeHeading.copy(
            freshTier = CoachFreshTier.STALE_OR_HELD,
            statusBadge = CoachStatusBadge.STALE_CLOCK,
            isPredictedOrInterpolated = false
        )
        val selected = activeHeading.copy(isSelected = true)

        assertNotEquals(activeHeading.toIconId(), activeNoHeading.toIconId())
        assertNotEquals(activeHeading.toIconId(), delayedHeading.toIconId())
        assertNotEquals(activeHeading.toIconId(), staleHeld.toIconId())
        assertNotEquals(activeHeading.toIconId(), selected.toIconId())

        val bmp1 = factory.getOrCreateCoachBitmap(activeHeading)
        val bmp1Cached = factory.getOrCreateCoachBitmap(activeHeading)
        val bmpNoHead = factory.getOrCreateCoachBitmap(activeNoHeading)
        val bmpSelected = factory.getOrCreateCoachBitmap(selected)

        assertSame(bmp1, bmp1Cached)
        assertNotEquals(bmp1, bmpNoHead)
        assertNotEquals(bmp1, bmpSelected)
        assertTrue(factory.coachPoolSize() >= 3)

        val shortLbl = factory.getOrCreateLabelBitmap(
            LabelIconSpec("615.77", LabelZoomTier.SHORT, isSelected = false, isStale = false)
        )
        val fullLbl = factory.getOrCreateLabelBitmap(
            LabelIconSpec("50E-615.77", LabelZoomTier.FULL, isSelected = true, isStale = false)
        )
        assertTrue(fullLbl.width > shortLbl.width)
        assertEquals(2, factory.labelPoolSize())

        factory.trimCachesOnLowMemory()
        assertEquals(0, factory.labelPoolSize())
        assertFalse(shortLbl.isRecycled)
        assertFalse(bmp1.isRecycled)
    }

    @Test
    fun `freshness status mapping and short plate formatting stay deterministic`() {
        assertEquals(
            CoachFreshTier.STALE_OR_HELD,
            VehicleMarkerBitmapFactory.mapFreshTier(
                GpsFreshnessState.FRESH,
                GpsLatencyCategory.FRESH,
                PositionSource.HELD_AT_LAST_VALID_GPS
            )
        )
        assertEquals(
            CoachFreshTier.DELAYED,
            VehicleMarkerBitmapFactory.mapFreshTier(
                GpsFreshnessState.AGING,
                GpsLatencyCategory.DELAYED,
                PositionSource.INTERPOLATED_ON_ROUTE
            )
        )
        assertEquals(
            CoachFreshTier.ACTIVE,
            VehicleMarkerBitmapFactory.mapFreshTier(
                GpsFreshnessState.FRESH,
                GpsLatencyCategory.FRESH,
                PositionSource.INTERPOLATED_ON_ROUTE
            )
        )

        assertEquals(
            CoachStatusBadge.ARRIVING,
            VehicleMarkerBitmapFactory.mapStatusBadge(
                VehicleArrivalStatus.ARRIVING_AT_STOP,
                CoachFreshTier.ACTIVE,
                hasHeading = true
            )
        )
        assertEquals(
            CoachStatusBadge.STALE_CLOCK,
            VehicleMarkerBitmapFactory.mapStatusBadge(
                VehicleArrivalStatus.ARRIVING_AT_STOP,
                CoachFreshTier.STALE_OR_HELD,
                hasHeading = true
            )
        )

        assertEquals("615.77", VehicleMarkerBitmapFactory.formatShortPlate("50E-615.77"))
        assertEquals("BUS", VehicleMarkerBitmapFactory.formatShortPlate("   "))

        val factory = VehicleMarkerBitmapFactory(DisplayMetrics.DENSITY_XHIGH)
        val cluster = factory.getOrCreateStopClusterBitmap(150)
        val blueDot = factory.getOrCreateBlueDotBitmap()
        assertNotNull(cluster)
        assertNotNull(blueDot)
        assertSame(blueDot, factory.getOrCreateBlueDotBitmap())
    }

    @Test
    fun `round 4e normal and selected coaches never clip across 160 to 640 dpi in native skia raster`() {
        val densities = listOf(
            DisplayMetrics.DENSITY_MEDIUM to 1.0f,
            DisplayMetrics.DENSITY_HIGH to 1.5f,
            DisplayMetrics.DENSITY_XHIGH to 2.0f,
            DisplayMetrics.DENSITY_XXHIGH to 3.0f,
            DisplayMetrics.DENSITY_XXXHIGH to 4.0f
        )

        val specs = listOf(
            CoachIconSpec(false, CoachFreshTier.ACTIVE, CoachStatusBadge.NONE, hasHeading = true, isPredictedOrInterpolated = true),
            CoachIconSpec(false, CoachFreshTier.DELAYED, CoachStatusBadge.ARRIVING, hasHeading = true, isPredictedOrInterpolated = true),
            CoachIconSpec(false, CoachFreshTier.STALE_OR_HELD, CoachStatusBadge.STALE_CLOCK, hasHeading = true, isPredictedOrInterpolated = false),
            CoachIconSpec(false, CoachFreshTier.ACTIVE, CoachStatusBadge.AT_STOP, hasHeading = false, isPredictedOrInterpolated = true),
            CoachIconSpec(true, CoachFreshTier.ACTIVE, CoachStatusBadge.NONE, hasHeading = true, isPredictedOrInterpolated = true),
            CoachIconSpec(true, CoachFreshTier.DELAYED, CoachStatusBadge.ARRIVING, hasHeading = true, isPredictedOrInterpolated = true)
        )

        // Verify pure mathematical geometry bounds first
        for (spec in specs) {
            val geom = VehicleMarkerBitmapFactory.computeCoachGeometry(spec)
            assertTrue("Geometry clipped canvas for $spec: $geom", geom.isStrictlyInsideCanvas)
            if (!spec.isSelected && spec.hasHeading) {
                assertTrue("Normal visual height should stay in 36..40 dp, got ${geom.opaqueHeightDp}", geom.opaqueHeightDp in 36.0f..40.0f)
            }
            if (spec.isSelected) {
                val haloDiameter = (geom.haloRadiusDp + geom.haloStrokeDp / 2f) * 2f
                assertTrue("Selected halo diameter should stay in 56..62 dp, got $haloDiameter", haloDiameter in 56.0f..62.0f)
            }
        }

        // Verify real Skia rasterized pixel perimeter & opaque bounding box on every density
        for ((dpi, scale) in densities) {
            val factory = VehicleMarkerBitmapFactory(dpi)
            for (spec in specs) {
                val bmp = factory.getOrCreateCoachBitmap(spec)
                val w = bmp.width
                val h = bmp.height

                // Normal coaches keep >= 1.2 dp edge clearance so even Skia anti-aliasing never touches border pixels (alpha == 0).
                // Selected halo (58.8 dp kept intact inside 60 dp canvas) stays strictly free of opaque border clipping (< 128).
                var maxBorderAlpha = 0
                for (x in 0 until w) {
                    maxBorderAlpha = maxOf(maxBorderAlpha, Color.alpha(bmp.getPixel(x, 0)), Color.alpha(bmp.getPixel(x, h - 1)))
                }
                for (y in 0 until h) {
                    maxBorderAlpha = maxOf(maxBorderAlpha, Color.alpha(bmp.getPixel(0, y)), Color.alpha(bmp.getPixel(w - 1, y)))
                }
                if (!spec.isSelected) {
                    assertEquals("Clipped normal border pixel detected (alpha=$maxBorderAlpha) at dpi=$dpi for $spec", 0, maxBorderAlpha)
                } else {
                    assertTrue("Clipped opaque selected border pixel detected (alpha=$maxBorderAlpha) at dpi=$dpi for $spec", maxBorderAlpha < 128)
                }

                // Measure real rasterized opaque bounding box (alpha >= 128)
                var minX = w
                var minY = h
                var maxX = -1
                var maxY = -1
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        if (Color.alpha(bmp.getPixel(x, y)) >= 128) {
                            if (x < minX) minX = x
                            if (y < minY) minY = y
                            if (x > maxX) maxX = x
                            if (y > maxY) maxY = y
                        }
                    }
                }
                assertTrue("Native Skia failed to draw ink for $spec", maxY > minY && maxX > minX)
                assertTrue(minX >= 1 && minY >= 1 && maxX <= w - 2 && maxY <= h - 2)

                val measuredHeightDp = (maxY - minY + 1) / scale
                if (!spec.isSelected && spec.hasHeading && spec.statusBadge == CoachStatusBadge.NONE) {
                    println("RASTER_NORMAL dpi=$dpi: px=${w}x${h}, box=(${minX},${minY})..(${maxX},${maxY}), opaqueH=${"%.2f".format(measuredHeightDp)}dp")
                    assertTrue("Expected raster normal height in 35.5..40.0 dp at dpi=$dpi, got $measuredHeightDp", measuredHeightDp in 35.5f..40.0f)
                }
                if (spec.isSelected && spec.statusBadge == CoachStatusBadge.NONE) {
                    println("RASTER_SELECTED dpi=$dpi: px=${w}x${h}, box=(${minX},${minY})..(${maxX},${maxY}), haloOuterH=${"%.2f".format(measuredHeightDp)}dp")
                    assertTrue("Expected raster selected halo diameter in 56.0..61.5 dp at dpi=$dpi, got $measuredHeightDp", measuredHeightDp in 56.0f..61.5f)
                }
            }
        }
    }
}
