package com.hcmcbustracker.feature.map.render

import android.graphics.Color as AndroidColor
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection

/**
 * Target Z-order placement computed by [MapLayerManager] before instantiating MapLibre native layers.
 */
sealed class LayerAnchor {
    data object Top : LayerAnchor()
    data class Below(val anchorLayerId: String) : LayerAnchor()
    data class Above(val anchorLayerId: String) : LayerAnchor()
}

/**
 * Abstracted style/layer operations allowing [MapLayerManager] Z-order logic
 * to be strictly unit-tested without requiring native OpenGL/JNI initialization.
 */
interface StyleLayerOperations {
    fun hasLayer(layerId: String): Boolean
    fun hasSource(sourceId: String): Boolean
    fun addGeoJsonSource(
        sourceId: String,
        featureCollection: FeatureCollection,
        clusterMaxZoom: Int? = null,
        clusterRadius: Int? = null
    )
    fun addGeoJsonFeatureSource(sourceId: String, feature: Feature)
    fun updateSourceGeoJson(sourceId: String, featureCollection: FeatureCollection)
    fun updateSourceFeature(sourceId: String, feature: Feature)
    fun addAnchoredLayer(layerId: String, anchor: LayerAnchor, createLayer: () -> Layer)
    fun removeLayer(layerId: String)
    fun removeSource(sourceId: String)
    fun hasImage(imageId: String): Boolean
    fun addImages(images: HashMap<String, android.graphics.Bitmap>)
}

/**
 * Production MapLibre [Style] adapter for [StyleLayerOperations].
 */
class MapLibreStyleOperations(private val style: Style) : StyleLayerOperations {
    override fun hasLayer(layerId: String): Boolean = style.getLayer(layerId) != null
    override fun hasSource(sourceId: String): Boolean = style.getSource(sourceId) != null

    override fun addGeoJsonSource(
        sourceId: String,
        featureCollection: FeatureCollection,
        clusterMaxZoom: Int?,
        clusterRadius: Int?
    ) {
        val source = if (clusterMaxZoom != null && clusterRadius != null) {
            GeoJsonSource(
                sourceId,
                featureCollection,
                GeoJsonOptions()
                    .withCluster(true)
                    .withClusterRadius(clusterRadius)
                    .withClusterMaxZoom(clusterMaxZoom)
            )
        } else {
            GeoJsonSource(sourceId, featureCollection)
        }
        style.addSource(source)
    }

    override fun addGeoJsonFeatureSource(sourceId: String, feature: Feature) {
        style.addSource(GeoJsonSource(sourceId, feature))
    }

    override fun updateSourceGeoJson(sourceId: String, featureCollection: FeatureCollection) {
        style.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(featureCollection)
    }

    override fun updateSourceFeature(sourceId: String, feature: Feature) {
        style.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(feature)
    }

    override fun addAnchoredLayer(layerId: String, anchor: LayerAnchor, createLayer: () -> Layer) {
        if (style.getLayer(layerId) != null) return
        val layer = createLayer()
        when (anchor) {
            is LayerAnchor.Below -> style.addLayerBelow(layer, anchor.anchorLayerId)
            is LayerAnchor.Above -> style.addLayerAbove(layer, anchor.anchorLayerId)
            LayerAnchor.Top -> style.addLayer(layer)
        }
    }

    override fun removeLayer(layerId: String) {
        if (style.getLayer(layerId) != null) style.removeLayer(layerId)
    }

    override fun removeSource(sourceId: String) {
        if (style.getSource(sourceId) != null) style.removeSource(sourceId)
    }

    override fun hasImage(imageId: String): Boolean = style.getImage(imageId) != null
    override fun addImages(images: HashMap<String, android.graphics.Bitmap>) {
        if (images.isNotEmpty()) style.addImages(images)
    }
}

/**
 * Centralized MapLibre Layer & Source Z-Order Manager.
 *
 * Guarantees invariant bottom-to-top hierarchy regardless of when Compose LaunchedEffects trigger:
 * 1. Basemap
 * 2. Route Casing (`hcmc-route-casing-layer`)
 * 3. Route Line (`hcmc-route-layer`)
 * 4. Stop Cluster (`hcmc-stop-cluster-layer`)
 * 5. Stop Markers (`hcmc-stops-layer`, `hcmc-terminal-stops-layer`, `hcmc-selected-stop-layer`)
 * 6. User Location Accuracy Circle (`hcmc-user-location-accuracy-layer`)
 * 7. Normal Bus Markers (`hcmc-vehicle-normal-layer`)
 * 8. Selected Bus Marker (`hcmc-vehicle-selected-layer`)
 * 9. Bus License Plate Labels (`hcmc-vehicle-label-layer`)
 * 10. User Blue Dot Marker (`hcmc-user-location-layer`)
 */
object MapLayerManager {

    const val ROUTE_SOURCE_ID = "hcmc-route-source"
    const val ROUTE_CASING_LAYER_ID = "hcmc-route-casing-layer"
    const val ROUTE_LAYER_ID = "hcmc-route-layer"

    const val STOPS_SOURCE_ID = "hcmc-stops-source"
    const val STOP_CLUSTER_LAYER_ID = "hcmc-stop-cluster-layer"
    const val STOPS_LAYER_ID = "hcmc-stops-layer"
    const val TERMINAL_STOPS_LAYER_ID = "hcmc-terminal-stops-layer"
    const val SELECTED_STOP_LAYER_ID = "hcmc-selected-stop-layer"

    const val USER_LOCATION_SOURCE_ID = "hcmc-user-location-source"
    const val USER_LOCATION_ACCURACY_LAYER_ID = "hcmc-user-location-accuracy-layer"
    const val USER_LOCATION_LAYER_ID = "hcmc-user-location-layer"
    const val USER_BLUE_DOT_ICON_ID = "hcmc-user-blue-dot"

    const val VEHICLE_SOURCE_ID = "hcmc-vehicle-source"
    const val VEHICLE_NORMAL_LAYER_ID = "hcmc-vehicle-normal-layer"
    const val VEHICLE_SELECTED_LAYER_ID = "hcmc-vehicle-selected-layer"
    const val VEHICLE_LABEL_LAYER_ID = "hcmc-vehicle-label-layer"

    val CANONICAL_LAYER_ORDER: List<String> = listOf(
        ROUTE_CASING_LAYER_ID,
        ROUTE_LAYER_ID,
        STOP_CLUSTER_LAYER_ID,
        STOPS_LAYER_ID,
        TERMINAL_STOPS_LAYER_ID,
        SELECTED_STOP_LAYER_ID,
        USER_LOCATION_ACCURACY_LAYER_ID,
        VEHICLE_NORMAL_LAYER_ID,
        VEHICLE_SELECTED_LAYER_ID,
        VEHICLE_LABEL_LAYER_ID,
        USER_LOCATION_LAYER_ID
    )

    /**
     * Resolves the canonical Z-anchor relative to layers currently registered in [ops].
     */
    fun resolveAnchor(ops: StyleLayerOperations, layerId: String): LayerAnchor {
        val targetIdx = CANONICAL_LAYER_ORDER.indexOf(layerId)
        if (targetIdx < 0) return LayerAnchor.Top

        // Nearest existing layer strictly ABOVE targetIdx -> insert immediately below it
        for (upperIdx in (targetIdx + 1) until CANONICAL_LAYER_ORDER.size) {
            val upperId = CANONICAL_LAYER_ORDER[upperIdx]
            if (ops.hasLayer(upperId)) {
                return LayerAnchor.Below(upperId)
            }
        }
        // Otherwise nearest existing layer strictly BELOW targetIdx -> insert immediately above it
        for (lowerIdx in (targetIdx - 1) downTo 0) {
            val lowerId = CANONICAL_LAYER_ORDER[lowerIdx]
            if (ops.hasLayer(lowerId)) {
                return LayerAnchor.Above(lowerId)
            }
        }
        return LayerAnchor.Top
    }

    private fun addLayerAnchored(ops: StyleLayerOperations, layerId: String, createLayer: () -> Layer) {
        if (ops.hasLayer(layerId)) return
        ops.addAnchoredLayer(layerId, resolveAnchor(ops, layerId), createLayer)
    }

    fun applyRoutePolyline(
        ops: StyleLayerOperations,
        feature: Feature?,
        isOutbound: Boolean
    ) {
        if (feature == null) {
            ops.removeLayer(ROUTE_LAYER_ID)
            ops.removeLayer(ROUTE_CASING_LAYER_ID)
            ops.removeSource(ROUTE_SOURCE_ID)
            return
        }

        val innerHex = if (isOutbound) "#00897B" else "#E64A19"
        val casingHex = if (isOutbound) "#004D40" else "#BF360C"
        val fc = FeatureCollection.fromFeature(feature)

        if (ops.hasSource(ROUTE_SOURCE_ID)) {
            ops.updateSourceGeoJson(ROUTE_SOURCE_ID, fc)
        } else {
            ops.addGeoJsonSource(ROUTE_SOURCE_ID, fc)
        }

        if (ops.hasLayer(ROUTE_CASING_LAYER_ID)) {
            // Re-tint casing & inner line cleanly when flipping outbound/inbound
            ops.removeLayer(ROUTE_LAYER_ID)
            ops.removeLayer(ROUTE_CASING_LAYER_ID)
        }

        addLayerAnchored(ops, ROUTE_CASING_LAYER_ID) {
            LineLayer(ROUTE_CASING_LAYER_ID, ROUTE_SOURCE_ID).apply {
                setProperties(
                    PropertyFactory.lineColor(AndroidColor.parseColor(casingHex)),
                    PropertyFactory.lineWidth(8.5f),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineOpacity(0.55f)
                )
            }
        }

        addLayerAnchored(ops, ROUTE_LAYER_ID) {
            LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).apply {
                setProperties(
                    PropertyFactory.lineColor(AndroidColor.parseColor(innerHex)),
                    PropertyFactory.lineWidth(5.5f),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineOpacity(0.92f)
                )
            }
        }
    }

    fun applyStops(
        ops: StyleLayerOperations,
        featureCollection: FeatureCollection,
        hasStops: Boolean,
        showStops: Boolean,
        bitmapFactory: VehicleMarkerBitmapFactory
    ) {
        if (!showStops || !hasStops) {
            ops.removeLayer(SELECTED_STOP_LAYER_ID)
            ops.removeLayer(TERMINAL_STOPS_LAYER_ID)
            ops.removeLayer(STOPS_LAYER_ID)
            ops.removeLayer(STOP_CLUSTER_LAYER_ID)
            ops.removeSource(STOPS_SOURCE_ID)
            return
        }

        // Pre-upload common cluster count badges (2..15, 20+, 50+, 99+) offline-safe
        val clusterImages = HashMap<String, android.graphics.Bitmap>()
        val clusterSizes = listOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20, 30, 50, 99, 100)
        for (c in clusterSizes) {
            val label = if (c > 99) "99+" else c.toString()
            val imgId = "hcmc-stop-cluster-$label"
            if (!ops.hasImage(imgId)) {
                clusterImages[imgId] = bitmapFactory.getOrCreateStopClusterBitmap(c)
            }
        }
        if (clusterImages.isNotEmpty()) {
            ops.addImages(clusterImages)
        }

        if (ops.hasSource(STOPS_SOURCE_ID)) {
            ops.updateSourceGeoJson(STOPS_SOURCE_ID, featureCollection)
        } else {
            ops.addGeoJsonSource(
                sourceId = STOPS_SOURCE_ID,
                featureCollection = featureCollection,
                clusterMaxZoom = VehicleMapRenderConfig.STOP_CLUSTER_MAX_ZOOM,
                clusterRadius = 42
            )
        }

        addLayerAnchored(ops, STOP_CLUSTER_LAYER_ID) {
            SymbolLayer(STOP_CLUSTER_LAYER_ID, STOPS_SOURCE_ID).apply {
                setFilter(Expression.has("point_count"))
                setProperties(
                    PropertyFactory.iconImage(
                        Expression.step(
                            Expression.get("point_count"),
                            Expression.literal("hcmc-stop-cluster-2"),
                            Expression.stop(3, Expression.literal("hcmc-stop-cluster-3")),
                            Expression.stop(4, Expression.literal("hcmc-stop-cluster-4")),
                            Expression.stop(5, Expression.literal("hcmc-stop-cluster-5")),
                            Expression.stop(6, Expression.literal("hcmc-stop-cluster-6")),
                            Expression.stop(8, Expression.literal("hcmc-stop-cluster-8")),
                            Expression.stop(10, Expression.literal("hcmc-stop-cluster-10")),
                            Expression.stop(15, Expression.literal("hcmc-stop-cluster-15")),
                            Expression.stop(20, Expression.literal("hcmc-stop-cluster-20")),
                            Expression.stop(30, Expression.literal("hcmc-stop-cluster-30")),
                            Expression.stop(50, Expression.literal("hcmc-stop-cluster-50")),
                            Expression.stop(100, Expression.literal("hcmc-stop-cluster-99+"))
                        )
                    ),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconSize(1.0f)
                )
            }
        }

        addLayerAnchored(ops, STOPS_LAYER_ID) {
            CircleLayer(STOPS_LAYER_ID, STOPS_SOURCE_ID).apply {
                setFilter(Expression.not(Expression.has("point_count")))
                setProperties(
                    PropertyFactory.circleColor(AndroidColor.parseColor("#1976D2")),
                    PropertyFactory.circleRadius(4.5f),
                    PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
                    PropertyFactory.circleStrokeWidth(1.8f),
                    PropertyFactory.circleOpacity(0.92f)
                )
            }
        }

        addLayerAnchored(ops, TERMINAL_STOPS_LAYER_ID) {
            CircleLayer(TERMINAL_STOPS_LAYER_ID, STOPS_SOURCE_ID).apply {
                setFilter(
                    Expression.all(
                        Expression.not(Expression.has("point_count")),
                        Expression.any(
                            Expression.eq(Expression.get("is_start"), Expression.literal(true)),
                            Expression.eq(Expression.get("is_end"), Expression.literal(true))
                        )
                    )
                )
                setProperties(
                    PropertyFactory.circleColor(AndroidColor.parseColor("#2E7D32")),
                    PropertyFactory.circleRadius(6.5f),
                    PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
                    PropertyFactory.circleStrokeWidth(2.2f)
                )
            }
        }

        addLayerAnchored(ops, SELECTED_STOP_LAYER_ID) {
            CircleLayer(SELECTED_STOP_LAYER_ID, STOPS_SOURCE_ID).apply {
                setFilter(
                    Expression.all(
                        Expression.not(Expression.has("point_count")),
                        Expression.eq(Expression.get("is_selected"), Expression.literal(true))
                    )
                )
                setProperties(
                    PropertyFactory.circleColor(AndroidColor.parseColor("#FF8F00")),
                    PropertyFactory.circleRadius(8.5f),
                    PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
                    PropertyFactory.circleStrokeWidth(2.8f)
                )
            }
        }
    }

    fun applyUserLocation(
        ops: StyleLayerOperations,
        feature: Feature?,
        bitmapFactory: VehicleMarkerBitmapFactory
    ) {
        if (feature == null) {
            ops.removeLayer(USER_LOCATION_LAYER_ID)
            ops.removeLayer(USER_LOCATION_ACCURACY_LAYER_ID)
            ops.removeSource(USER_LOCATION_SOURCE_ID)
            return
        }

        if (!ops.hasImage(USER_BLUE_DOT_ICON_ID)) {
            ops.addImages(hashMapOf(USER_BLUE_DOT_ICON_ID to bitmapFactory.getOrCreateBlueDotBitmap()))
        }

        if (ops.hasSource(USER_LOCATION_SOURCE_ID)) {
            ops.updateSourceFeature(USER_LOCATION_SOURCE_ID, feature)
        } else {
            ops.addGeoJsonFeatureSource(USER_LOCATION_SOURCE_ID, feature)
        }

        addLayerAnchored(ops, USER_LOCATION_ACCURACY_LAYER_ID) {
            CircleLayer(USER_LOCATION_ACCURACY_LAYER_ID, USER_LOCATION_SOURCE_ID).apply {
                setProperties(
                    PropertyFactory.circleColor(AndroidColor.parseColor("#4285F4")),
                    PropertyFactory.circleOpacity(
                        Expression.switchCase(
                            Expression.get("has_accuracy"),
                            Expression.literal(0.15f),
                            Expression.literal(0.0f)
                        )
                    ),
                    PropertyFactory.circleStrokeColor(AndroidColor.parseColor("#4285F4")),
                    PropertyFactory.circleStrokeWidth(
                        Expression.switchCase(
                            Expression.get("has_accuracy"),
                            Expression.literal(1.2f),
                            Expression.literal(0.0f)
                        )
                    ),
                    PropertyFactory.circleStrokeOpacity(0.35f),
                    PropertyFactory.circleRadius(
                        Expression.switchCase(
                            Expression.get("has_accuracy"),
                            Expression.min(
                                Expression.literal(72f),
                                Expression.max(
                                    Expression.literal(16f),
                                    Expression.product(Expression.get("accuracy"), Expression.literal(0.5f))
                                )
                            ),
                            Expression.literal(0f)
                        )
                    )
                )
            }
        }

        addLayerAnchored(ops, USER_LOCATION_LAYER_ID) {
            SymbolLayer(USER_LOCATION_LAYER_ID, USER_LOCATION_SOURCE_ID).apply {
                setProperties(
                    PropertyFactory.iconImage(USER_BLUE_DOT_ICON_ID),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconSize(1.0f)
                )
            }
        }
    }

    fun applyVehicles(
        ops: StyleLayerOperations,
        featureCollection: FeatureCollection,
        snapshots: Collection<VehicleRenderSnapshot>,
        bitmapFactory: VehicleMarkerBitmapFactory
    ) {
        val features = featureCollection.features()
        if (features.isNullOrEmpty()) {
            ops.removeLayer(VEHICLE_LABEL_LAYER_ID)
            ops.removeLayer(VEHICLE_SELECTED_LAYER_ID)
            ops.removeLayer(VEHICLE_NORMAL_LAYER_ID)
            ops.removeSource(VEHICLE_SOURCE_ID)
            return
        }

        // Batch-upload only unseen coach or label bitmaps from pooled factory
        val pendingImages = HashMap<String, android.graphics.Bitmap>()
        for (snap in snapshots) {
            if (!ops.hasImage(snap.coachIconId)) {
                val freshTier = VehicleMarkerBitmapFactory.mapFreshTier(
                    snap.freshnessState,
                    snap.latencyCategory,
                    snap.positionSource
                )
                val badge = VehicleMarkerBitmapFactory.mapStatusBadge(
                    snap.arrivalStatus,
                    freshTier,
                    snap.hasValidHeading
                )
                val isPred = snap.positionSource != com.hcmcbustracker.core.model.PositionSource.HELD_AT_LAST_VALID_GPS
                pendingImages[snap.coachIconId] = bitmapFactory.getOrCreateCoachBitmap(
                    CoachIconSpec(snap.isSelected, freshTier, badge, snap.hasValidHeading, isPred)
                )
            }
            val isStale = VehicleMarkerBitmapFactory.mapFreshTier(
                snap.freshnessState,
                snap.latencyCategory,
                snap.positionSource
            ) == CoachFreshTier.STALE_OR_HELD
            if (!ops.hasImage(snap.shortLabelIconId)) {
                pendingImages[snap.shortLabelIconId] = bitmapFactory.getOrCreateLabelBitmap(
                    LabelIconSpec(
                        text = VehicleMarkerBitmapFactory.formatShortPlate(snap.formattedPlate),
                        tier = LabelZoomTier.SHORT,
                        isSelected = snap.isSelected,
                        isStale = isStale
                    )
                )
            }
            if (!ops.hasImage(snap.fullLabelIconId)) {
                pendingImages[snap.fullLabelIconId] = bitmapFactory.getOrCreateLabelBitmap(
                    LabelIconSpec(
                        text = snap.formattedPlate,
                        tier = LabelZoomTier.FULL,
                        isSelected = snap.isSelected,
                        isStale = isStale
                    )
                )
            }
        }
        if (pendingImages.isNotEmpty()) {
            ops.addImages(pendingImages)
        }

        if (ops.hasSource(VEHICLE_SOURCE_ID)) {
            ops.updateSourceGeoJson(VEHICLE_SOURCE_ID, featureCollection)
        } else {
            ops.addGeoJsonSource(VEHICLE_SOURCE_ID, featureCollection)
        }

        val zoomIconSizeExpr = Expression.interpolate(
            Expression.linear(),
            Expression.zoom(),
            Expression.stop(11, VehicleMapRenderConfig.LOW_ZOOM_ICON_SIZE),
            Expression.stop(13, VehicleMapRenderConfig.DEFAULT_ICON_SIZE),
            Expression.stop(17, VehicleMapRenderConfig.HIGH_ZOOM_ICON_SIZE)
        )
        val rotationExpr = Expression.switchCase(
            Expression.get("has_heading"),
            Expression.get("bearing"),
            Expression.literal(0f)
        )

        addLayerAnchored(ops, VEHICLE_NORMAL_LAYER_ID) {
            SymbolLayer(VEHICLE_NORMAL_LAYER_ID, VEHICLE_SOURCE_ID).apply {
                setFilter(Expression.neq(Expression.get("is_selected"), Expression.literal(true)))
                setProperties(
                    PropertyFactory.iconImage(Expression.get("coach_icon_id")),
                    PropertyFactory.iconRotate(rotationExpr),
                    PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconPadding(2f),
                    PropertyFactory.symbolSortKey(Expression.get("sort_key")),
                    PropertyFactory.iconSize(zoomIconSizeExpr)
                )
            }
        }

        addLayerAnchored(ops, VEHICLE_SELECTED_LAYER_ID) {
            SymbolLayer(VEHICLE_SELECTED_LAYER_ID, VEHICLE_SOURCE_ID).apply {
                setFilter(Expression.eq(Expression.get("is_selected"), Expression.literal(true)))
                setProperties(
                    PropertyFactory.iconImage(Expression.get("coach_icon_id")),
                    PropertyFactory.iconRotate(rotationExpr),
                    PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconPadding(2f),
                    PropertyFactory.iconSize(zoomIconSizeExpr)
                )
            }
        }

        addLayerAnchored(ops, VEHICLE_LABEL_LAYER_ID) {
            SymbolLayer(VEHICLE_LABEL_LAYER_ID, VEHICLE_SOURCE_ID).apply {
                setFilter(
                    Expression.any(
                        Expression.eq(Expression.get("is_selected"), Expression.literal(true)),
                        Expression.gte(Expression.zoom(), Expression.literal(VehicleMapRenderConfig.MEDIUM_ZOOM_LABEL_MIN))
                    )
                )
                setProperties(
                    PropertyFactory.iconImage(buildLabelIconImageExpression()),
                    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_TOP),
                    PropertyFactory.iconOffset(
                        Expression.switchCase(
                            Expression.eq(Expression.get("is_selected"), Expression.literal(true)),
                            Expression.literal(arrayOf(0f, VehicleMapRenderConfig.LABEL_SELECTED_OFFSET_Y_DP)),
                            Expression.literal(arrayOf(0f, VehicleMapRenderConfig.LABEL_NORMAL_OFFSET_Y_DP))
                        )
                    ),
                    PropertyFactory.iconPadding(4f),
                    PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                    PropertyFactory.symbolSortKey(Expression.get("sort_key")),
                    PropertyFactory.iconAllowOverlap(false),
                    PropertyFactory.iconIgnorePlacement(false),
                    PropertyFactory.iconSize(1.0f)
                )
            }
        }
    }

    /**
     * Top-level MapLibre `step(zoom, ...)` expression required by gl-native:
     * camera `zoom` must stay at the expression root and never be nested inside `case`.
     */
    fun buildLabelIconImageExpression(): Expression {
        return Expression.step(
            Expression.zoom(),
            Expression.switchCase(
                Expression.eq(Expression.get("is_selected"), Expression.literal(true)),
                Expression.get("full_label_icon_id"),
                Expression.get("short_label_icon_id")
            ),
            Expression.stop(
                VehicleMapRenderConfig.HIGH_ZOOM_LABEL_MIN,
                Expression.get("full_label_icon_id")
            )
        )
    }

    /**
     * Verifies whether [presentLayerIds] strictly respect [CANONICAL_LAYER_ORDER].
     */
    fun isCanonicalOrderPreserved(presentLayerIds: List<String>): Boolean {
        var lastCanonicalIdx = -1
        for (id in presentLayerIds) {
            val idx = CANONICAL_LAYER_ORDER.indexOf(id)
            if (idx < 0) continue
            if (idx < lastCanonicalIdx) return false
            lastCanonicalIdx = idx
        }
        return true
    }
}
