package com.hcmcbustracker.feature.map

import com.hcmcbustracker.feature.map.tile.TileProvider
import com.hcmcbustracker.feature.map.tile.TileProviderManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TileProviderTest {

    @Test
    fun `tile providers generate valid MapLibre v8 style JSON`() {
        TileProvider.entries.forEach { provider ->
            val styleJson = provider.toStyleJson()
            assertNotNull(styleJson)
            assertTrue(styleJson.contains("\"version\": 8"))
            assertTrue(styleJson.contains("\"type\": \"raster\""))
            assertTrue(styleJson.contains(provider.tileUrls.first()))
            assertTrue(styleJson.contains("attribution"))
        }
    }

    @Test
    fun `tile provider manager switches provider and style`() {
        val manager = TileProviderManager()
        assertEquals(TileProvider.FPT_STIS, manager.currentProvider.value)

        manager.setTileProvider(TileProvider.OSM_STANDARD)
        assertEquals(TileProvider.OSM_STANDARD, manager.currentProvider.value)
        assertTrue(manager.getActiveStyleJson().contains("tile.openstreetmap.org"))

        manager.setTileProvider(TileProvider.CARTO_VOYAGER)
        assertEquals(TileProvider.CARTO_VOYAGER, manager.currentProvider.value)
        assertTrue(manager.getActiveStyleJson().contains("cartocdn.com"))
    }

    @Test
    fun `osm standard tile provider contains proper attribution`() {
        val osm = TileProvider.OSM_STANDARD
        assertTrue(osm.attributionText.contains("OpenStreetMap contributors"))
        assertTrue(osm.attributionHtml.contains("https://www.openstreetmap.org/copyright"))
    }
}
