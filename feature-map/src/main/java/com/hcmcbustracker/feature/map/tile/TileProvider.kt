package com.hcmcbustracker.feature.map.tile

/**
 * Supported OpenStreetMap-compatible Tile Providers for HCMC Bus Tracker.
 *
 * Implements a pluggable abstraction allowing seamless provider switching
 * while adhering to respective Tile Usage Policies, caching headers, and attributions.
 */
enum class TileProvider(
    val id: String,
    val displayName: String,
    val tileUrls: List<String>,
    val attributionHtml: String,
    val attributionText: String,
    val maxZoom: Float = 19f,
    val tileSize: Int = 256
) {
    /**
     * FPT STIS Transit Tile Server.
     * High performance, optimized for public transit in HCMC.
     */
    FPT_STIS(
        id = "fpt_stis",
        displayName = "FPT STIS (Khuyến nghị)",
        tileUrls = listOf("https://map.stis.vn/bright/{z}/{x}/{y}.png"),
        attributionHtml = "© <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a> contributors, FPT STIS",
        attributionText = "© OpenStreetMap contributors, FPT STIS",
        maxZoom = 19f,
        tileSize = 256
    ),

    /**
     * Official OpenStreetMap Standard Tile Server.
     * Complies with OSM Foundation Tile Usage Policy:
     * - Uses custom User-Agent (HcmcBusTracker/1.0).
     * - Respects zoom limits and avoids bulk scraping.
     */
    OSM_STANDARD(
        id = "osm_standard",
        displayName = "OpenStreetMap Standard",
        tileUrls = listOf(
            "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        ),
        attributionHtml = "© <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a> contributors",
        attributionText = "© OpenStreetMap contributors",
        maxZoom = 19f,
        tileSize = 256
    ),

    /**
     * CartoDB Voyager Raster Tiles (OSM-based).
     * High-contrast, clean typography suitable for transit map visualization.
     */
    CARTO_VOYAGER(
        id = "carto_voyager",
        displayName = "Carto Voyager",
        tileUrls = listOf(
            "https://basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png"
        ),
        attributionHtml = "© <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a> contributors, © <a href='https://carto.com/attributions'>CARTO</a>",
        attributionText = "© OpenStreetMap contributors, © CARTO",
        maxZoom = 19f,
        tileSize = 256
    );

    /**
     * Generates a valid MapLibre Style Specification v8 JSON string
     * for this tile provider.
     */
    fun toStyleJson(): String {
        val urlsArray = tileUrls.joinToString(separator = "\", \"", prefix = "[\"", postfix = "\"]")
        return """
        {
          "version": 8,
          "name": "MapLibre-OSM-${id}",
          "sources": {
            "raster-tiles": {
              "type": "raster",
              "tiles": $urlsArray,
              "tileSize": $tileSize,
              "attribution": "$attributionHtml"
            }
          },
          "layers": [
            {
              "id": "raster-tiles-layer",
              "type": "raster",
              "source": "raster-tiles",
              "minzoom": 0,
              "maxzoom": ${maxZoom.toInt()}
            }
          ]
        }
        """.trimIndent()
    }
}
