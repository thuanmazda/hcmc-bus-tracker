package com.hcmcbustracker.feature.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.LocationServices
import com.hcmcbustracker.core.designsystem.component.RouteBadge
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.GpsLatencyCategory
import com.hcmcbustracker.core.model.PositionSource
import com.hcmcbustracker.core.model.PredictionFeedStatus
import com.hcmcbustracker.core.model.VehicleArrivalStatus
import com.hcmcbustracker.feature.map.render.BearingSmoother
import com.hcmcbustracker.feature.map.render.MapLayerManager
import com.hcmcbustracker.feature.map.render.MapLibreStyleOperations
import com.hcmcbustracker.feature.map.render.ScreenCoord
import com.hcmcbustracker.feature.map.render.VehicleMarkerBitmapFactory
import com.hcmcbustracker.feature.map.render.VehicleOverlapPickerSheet
import com.hcmcbustracker.feature.map.render.VehicleOverlapResolver
import com.hcmcbustracker.feature.map.render.VehicleRenderDiffer
import com.hcmcbustracker.feature.map.render.VehicleRenderSnapshot
import com.hcmcbustracker.feature.map.render.VehicleTapCandidate
import com.hcmcbustracker.feature.map.tile.TileProvider
import com.hcmcbustracker.feature.map.util.RoutePathGeoJsonConverter
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onStopClicked: (Int) -> Unit,
    modifier: Modifier = Modifier,
    initialRouteId: Int? = null,
    initialVarId: Int? = null,
    initialStopId: Int? = null,
    initialVehiclePlate: String? = null,
    initialVehicleDistance: Double? = null,
    initialVehicleSpeed: Double? = null,
    initialVehicleDts: String? = null,
    initialVehicleEta: Int? = null
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    var maplibreMapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentStyleInstance by remember { mutableStateOf<Style?>(null) }
    var isTileMenuOpen by remember { mutableStateOf(false) }

    val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
    val densityDpi = context.resources.displayMetrics.densityDpi
    val bitmapFactory = remember(densityDpi) { VehicleMarkerBitmapFactory(densityDpi) }

    var overlapCandidates by remember { mutableStateOf<List<RouteActiveVehicleUiModel>>(emptyList()) }
    var lastVehicleSnapshots by remember { mutableStateOf<Map<String, VehicleRenderSnapshot>>(emptyMap()) }
    var lastRenderedStyleRef by remember { mutableStateOf<Style?>(null) }
    var animatedBearings by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val locationCallback = remember {
        object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                val loc = result.lastLocation ?: return
                val bearing = if (loc.hasBearing() && loc.speed > 0.3f) loc.bearing else null
                val accuracy = if (loc.hasAccuracy()) loc.accuracy else null
                viewModel.updateUserLocation(loc.latitude, loc.longitude, bearing, accuracy)
            }
        }
    }

    // Permission launcher for Location
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.setLocationPermissionGranted(granted)
        if (granted) {
            fetchDeviceLocation(
                context = context,
                client = fusedLocationClient,
                onLocation = { lat, lng, bearing, accuracy ->
                    viewModel.updateUserLocation(lat, lng, bearing, accuracy)
                    maplibreMapInstance?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15.5),
                        650
                    )
                },
                onUnavailable = {
                    viewModel.markUserLocationUnavailable()
                }
            )
        }
    }

    // Check initial permission status
    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            viewModel.setLocationPermissionGranted(true)
            fetchDeviceLocation(
                context = context,
                client = fusedLocationClient,
                onLocation = { lat, lng, bearing, accuracy ->
                    viewModel.updateUserLocation(lat, lng, bearing, accuracy)
                },
                onUnavailable = {
                    viewModel.markUserLocationUnavailable()
                }
            )
        } else {
            viewModel.setLocationPermissionGranted(false)
        }
    }

    // Continuous location observer when permission is active
    DisposableEffect(state.isLocationPermissionGranted) {
        if (state.isLocationPermissionGranted) {
            val request = com.google.android.gms.location.LocationRequest.Builder(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                2000L
            ).setMinUpdateDistanceMeters(2f).build()
            try {
                fusedLocationClient.requestLocationUpdates(
                    request,
                    locationCallback,
                    android.os.Looper.getMainLooper()
                )
            } catch (_: SecurityException) {}
        }
        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    // Lifecycle-aware polling management (pause network calls when backgrounded)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onMapResumed()
                Lifecycle.Event.ON_PAUSE -> viewModel.onMapPaused()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Initialize MapLibre
    remember {
        MapLibre.getInstance(context)
    }

    // Load initial route or track vehicle if provided
    LaunchedEffect(initialRouteId, initialVarId, initialStopId, initialVehiclePlate, initialVehicleDistance, initialVehicleSpeed, initialVehicleDts, initialVehicleEta) {
        if (initialRouteId != null && initialRouteId > 0) {
            if (!initialVehiclePlate.isNullOrBlank() && initialStopId != null) {
                viewModel.trackVehicle(
                    routeId = initialRouteId,
                    varId = initialVarId ?: 1,
                    stopId = initialStopId,
                    plate = initialVehiclePlate,
                    distanceMeters = initialVehicleDistance ?: 0.0,
                    speedKmH = initialVehicleSpeed ?: 0.0,
                    timestampIso = initialVehicleDts,
                    etaSeconds = initialVehicleEta
                )
            } else {
                viewModel.loadRoute(initialRouteId, initialVarId)
            }
        }
    }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                maplibreMapInstance = map

                // Load initial style
                val initialStyleJson = state.tileProvider.toStyleJson()
                map.setStyle(Style.Builder().fromJson(initialStyleJson)) { style ->
                    currentStyleInstance = style
                    lastRenderedStyleRef = null
                    lastVehicleSnapshots = emptyMap()

                    // Restore or set default camera at District 1, HCMC
                    val initialCamera = viewModel.savedCameraPosition ?: CameraPosition.Builder()
                        .target(LatLng(10.77079, 106.700152))
                        .zoom(14.5)
                        .build()
                    map.cameraPosition = initialCamera

                    // Detect user drag/pinch gestures to pause vehicle auto-follow cleanly
                    map.addOnCameraMoveStartedListener { reason ->
                        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                            viewModel.onUserMapGesture()
                        }
                    }

                    // Register camera idle listener
                    map.addOnCameraIdleListener {
                        val pos = map.cameraPosition
                        viewModel.savedCameraPosition = pos
                        val bounds = map.projection.visibleRegion.latLngBounds
                        viewModel.onCameraIdle(bounds, pos.zoom)
                    }

                    // Screen-space hit-testing: resolves single vs overlapping cluster (44dp) cleanly
                    map.addOnMapClickListener { latLng ->
                        val pointF = map.projection.toScreenLocation(latLng)
                        val currentState = viewModel.uiState.value

                        // 1. Check active vehicles within 44dp screen radius (without mutating GPS coordinates)
                        val tapCandidates = currentState.routeVehicles
                            .filter { RoutePathGeoJsonConverter.isValidCoordinate(it.position.lat, it.position.lng) }
                            .map { v ->
                                val pt = map.projection.toScreenLocation(LatLng(v.position.lat, v.position.lng))
                                VehicleTapCandidate(v, ScreenCoord(pt.x, pt.y))
                            }
                        when (
                            val tapOutcome = VehicleOverlapResolver.resolveTap(
                                tapPoint = ScreenCoord(pointF.x, pointF.y),
                                candidates = tapCandidates,
                                density = density,
                                selectedPlate = currentState.selectedVehiclePlate
                            )
                        ) {
                            is com.hcmcbustracker.feature.map.render.OverlapTapOutcome.Single -> {
                                viewModel.selectRouteVehicle(tapOutcome.vehicle.telemetry.plateNumber)
                                return@addOnMapClickListener true
                            }
                            is com.hcmcbustracker.feature.map.render.OverlapTapOutcome.Multiple -> {
                                overlapCandidates = tapOutcome.vehicles
                                return@addOnMapClickListener true
                            }
                            com.hcmcbustracker.feature.map.render.OverlapTapOutcome.None -> {
                                // Fallback symbol hit-test in case tracked fallback vehicle is rendered
                                val symbolHits = map.queryRenderedFeatures(
                                    pointF,
                                    MapLayerManager.VEHICLE_SELECTED_LAYER_ID,
                                    MapLayerManager.VEHICLE_NORMAL_LAYER_ID,
                                    MapLayerManager.VEHICLE_LABEL_LAYER_ID
                                )
                                if (symbolHits.isNotEmpty()) {
                                    val clickedPlate = symbolHits.first().getStringProperty("raw_plate")
                                        ?: symbolHits.first().getStringProperty("plate")
                                    if (!clickedPlate.isNullOrBlank()) {
                                        viewModel.selectRouteVehicle(clickedPlate)
                                        return@addOnMapClickListener true
                                    }
                                }
                            }
                        }

                        // 2. Check Stop Cluster tap -> smoothly zoom into cluster
                        val clusterFeatures = map.queryRenderedFeatures(pointF, MapLayerManager.STOP_CLUSTER_LAYER_ID)
                        if (clusterFeatures.isNotEmpty()) {
                            val targetZoom = (map.cameraPosition.zoom + 2.2).coerceAtMost(15.5)
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, targetZoom), 400)
                            return@addOnMapClickListener true
                        }

                        // 3. Check Stop markers
                        val renderedFeatures = map.queryRenderedFeatures(
                            pointF,
                            MapLayerManager.SELECTED_STOP_LAYER_ID,
                            MapLayerManager.TERMINAL_STOPS_LAYER_ID,
                            MapLayerManager.STOPS_LAYER_ID
                        )
                        val allStops = if (currentState.activeRouteStops.isNotEmpty()) {
                            currentState.activeRouteStops
                        } else {
                            currentState.stopsInViewport
                        }

                        if (renderedFeatures.isNotEmpty()) {
                            val stopId = renderedFeatures.first().getNumberProperty("stop_id")?.toInt()
                            val clicked = allStops.find { it.id == stopId }
                            if (clicked != null) {
                                viewModel.selectStop(clicked)
                                return@addOnMapClickListener true
                            }
                        }

                        // Fallback stop distance check (within 70 meters)
                        val clickedStop = allStops.find { stop ->
                            val stopLoc = LatLng(stop.lat, stop.lng)
                            latLng.distanceTo(stopLoc) < 70.0
                        }
                        if (clickedStop != null) {
                            viewModel.selectStop(clickedStop)
                        } else {
                            viewModel.clearSelectedStop()
                        }
                        true
                    }
                }
            }
        }
    }

    // Lifecycle & LowMemory coordination
    DisposableEffect(mapView, context) {
        mapView.onStart()
        mapView.onResume()
        val memCallbacks = object : android.content.ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                    bitmapFactory.trimCachesOnLowMemory()
                }
            }

            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {}

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() {
                bitmapFactory.trimCachesOnLowMemory()
                mapView.onLowMemory()
            }
        }
        context.registerComponentCallbacks(memCallbacks)
        onDispose {
            context.unregisterComponentCallbacks(memCallbacks)
            viewModel.savedCameraPosition = maplibreMapInstance?.cameraPosition
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Update Tile Provider Style when changed (re-applies all layers in canonical Z-order)
    LaunchedEffect(state.tileProvider) {
        val map = maplibreMapInstance ?: return@LaunchedEffect
        val newStyleJson = state.tileProvider.toStyleJson()
        map.setStyle(Style.Builder().fromJson(newStyleJson)) { style ->
            currentStyleInstance = style
            lastRenderedStyleRef = null
            lastVehicleSnapshots = emptyMap()
            val ops = MapLibreStyleOperations(style)
            val currentState = viewModel.uiState.value

            MapLayerManager.applyRoutePolyline(
                ops = ops,
                feature = currentState.activeRoutePath?.let { RoutePathGeoJsonConverter.toLineStringFeature(it) },
                isOutbound = currentState.activeVariant?.isOutbound ?: true
            )

            val stopsToRender = if (currentState.activeRouteStops.isNotEmpty()) {
                currentState.activeRouteStops
            } else {
                currentState.stopsInViewport
            }
            MapLayerManager.applyStops(
                ops = ops,
                featureCollection = RoutePathGeoJsonConverter.toStopFeatureCollection(stopsToRender, currentState.selectedStop?.id),
                hasStops = stopsToRender.isNotEmpty(),
                showStops = currentState.showStopsLayer,
                bitmapFactory = bitmapFactory
            )

            val userFeature = currentState.userLocation?.takeIf {
                RoutePathGeoJsonConverter.isValidCoordinate(it.latitude, it.longitude)
            }?.let { loc ->
                RoutePathGeoJsonConverter.toUserLocationFeature(
                    lat = loc.latitude,
                    lng = loc.longitude,
                    bearing = currentState.userBearing,
                    accuracyMeters = currentState.userAccuracyMeters
                )
            }
            MapLayerManager.applyUserLocation(ops, userFeature, bitmapFactory)

            val snapshots = VehicleRenderDiffer.buildSnapshots(
                vehicles = currentState.routeVehicles,
                selectedPlate = currentState.selectedVehiclePlate,
                fallbackTracked = currentState.trackedVehicle
            )
            MapLayerManager.applyVehicles(
                ops = ops,
                featureCollection = RoutePathGeoJsonConverter.toRouteVehiclesFeatureCollection(
                    vehicles = currentState.routeVehicles,
                    selectedPlate = currentState.selectedVehiclePlate,
                    fallbackTracked = currentState.trackedVehicle
                ),
                snapshots = snapshots.values,
                bitmapFactory = bitmapFactory
            )
            lastVehicleSnapshots = snapshots
            lastRenderedStyleRef = style
        }
    }

    // Render / Update Route Casing + Polyline via canonical MapLayerManager
    LaunchedEffect(state.activeRoutePath, state.activeVariant, currentStyleInstance) {
        val style = currentStyleInstance ?: return@LaunchedEffect
        val path = state.activeRoutePath
        val map = maplibreMapInstance
        val ops = MapLibreStyleOperations(style)

        val lineFeature = path?.let {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                RoutePathGeoJsonConverter.toLineStringFeature(it)
            }
        }
        MapLayerManager.applyRoutePolyline(
            ops = ops,
            feature = lineFeature,
            isOutbound = state.activeVariant?.isOutbound ?: true
        )

        if (path != null && state.selectedVehiclePlate == null && state.cameraFocusRequestId == 0L) {
            val bounds = RoutePathGeoJsonConverter.calculateBounds(path)
            if (bounds != null && map != null) {
                map.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, 80),
                    900
                )
            }
        }
    }

    // Render / Update User Location Google Maps Blue Dot + Accuracy Circle
    LaunchedEffect(state.userLocation, state.userBearing, state.userAccuracyMeters, currentStyleInstance) {
        val style = currentStyleInstance ?: return@LaunchedEffect
        val loc = state.userLocation
        val feature = if (loc != null && RoutePathGeoJsonConverter.isValidCoordinate(loc.latitude, loc.longitude)) {
            RoutePathGeoJsonConverter.toUserLocationFeature(
                lat = loc.latitude,
                lng = loc.longitude,
                bearing = state.userBearing,
                accuracyMeters = state.userAccuracyMeters
            )
        } else null
        MapLayerManager.applyUserLocation(MapLibreStyleOperations(style), feature, bitmapFactory)
    }

    // Dismiss stale overlap sheet whenever active route or variant changes
    LaunchedEffect(state.activeRoute?.id, state.activeVariant?.id) {
        overlapCandidates = emptyList()
    }

    // Smooth shortest-arc 0/360° bearing animation ONLY for moving vehicles with valid heading
    LaunchedEffect(state.routeVehicles, state.trackedVehicle) {
        val targetMap = VehicleRenderDiffer.buildSnapshots(
            vehicles = state.routeVehicles,
            selectedPlate = state.selectedVehiclePlate,
            fallbackTracked = state.trackedVehicle
        )
        val stationaryByPlate = state.routeVehicles.associate { v ->
            val key = v.telemetry.plateNumber.trim().uppercase().replace("-", "").replace(".", "").replace(" ", "")
            key to (v.telemetry.speedKmH <= 1.5 || v.telemetry.isGpsTimestampUnchanged)
        }
        val animPairs = LinkedHashMap<String, Pair<Float, Float>>()
        for ((key, next) in targetMap) {
            val prev = lastVehicleSnapshots[key] ?: continue
            val isStat = stationaryByPlate[key] ?: true
            if (BearingSmoother.shouldAnimateRotation(
                    previousHasHeading = prev.hasValidHeading,
                    nextHasHeading = next.hasValidHeading,
                    isStationary = isStat,
                    fromDeg = prev.bearing,
                    toDeg = next.bearing
                )
            ) {
                animPairs[key] = prev.bearing to next.bearing
            }
        }

        if (animPairs.isEmpty()) {
            animatedBearings = emptyMap()
        } else {
            val steps = 6
            for (step in 1 until steps) {
                val frac = step.toFloat() / steps.toFloat()
                animatedBearings = animPairs.mapValues { (_, pair) ->
                    BearingSmoother.interpolateBearingDegrees(pair.first, pair.second, frac)
                }
                delay(50L)
            }
            animatedBearings = emptyMap()
        }
    }

    // Incremental Vehicle Marker Rendering via VehicleRenderDiffer + MapLayerManager
    LaunchedEffect(state.routeVehicles, state.trackedVehicle, state.selectedVehiclePlate, animatedBearings, currentStyleInstance) {
        val style = currentStyleInstance ?: return@LaunchedEffect
        val targetSnapshots = VehicleRenderDiffer.buildSnapshots(
            vehicles = state.routeVehicles,
            selectedPlate = state.selectedVehiclePlate,
            fallbackTracked = state.trackedVehicle,
            bearingOverrides = animatedBearings
        )
        val styleSwitched = lastRenderedStyleRef !== style
        val diff = VehicleRenderDiffer.diff(lastVehicleSnapshots, targetSnapshots)
        if (!styleSwitched && !diff.hasAnyChange && targetSnapshots.isNotEmpty()) {
            return@LaunchedEffect
        }

        val featureCollection = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            RoutePathGeoJsonConverter.toRouteVehiclesFeatureCollection(
                vehicles = state.routeVehicles,
                selectedPlate = state.selectedVehiclePlate,
                fallbackTracked = state.trackedVehicle,
                bearingOverrides = animatedBearings
            )
        }
        MapLayerManager.applyVehicles(
            ops = MapLibreStyleOperations(style),
            featureCollection = featureCollection,
            snapshots = targetSnapshots.values,
            bitmapFactory = bitmapFactory
        )
        lastVehicleSnapshots = targetSnapshots
        lastRenderedStyleRef = style
    }

    var lastHandledFocusRequestId by remember { mutableStateOf(0L) }
    var lastHandledFollowRequestId by remember { mutableStateOf(0L) }
    var lastFollowedLatLng by remember { mutableStateOf<LatLng?>(null) }

    // 1. Reliable Auto-Center Camera when selecting / re-selecting a vehicle OR once map/vehicle becomes ready
    LaunchedEffect(state.cameraFocusRequestId, state.trackedVehicle, maplibreMapInstance) {
        val reqId = state.cameraFocusRequestId
        if (reqId <= 0L || reqId == lastHandledFocusRequestId) return@LaunchedEffect
        val map = maplibreMapInstance ?: return@LaunchedEffect
        val vehicle = state.trackedVehicle ?: return@LaunchedEffect
        if (!RoutePathGeoJsonConverter.isValidCoordinate(vehicle.lat, vehicle.lng)) return@LaunchedEffect

        val currentZoom = map.cameraPosition.zoom
        val targetZoom = if (currentZoom in 15.2..17.2) currentZoom else 16.0
        val targetLatLng = LatLng(vehicle.lat, vehicle.lng)

        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(targetLatLng, targetZoom),
            550
        )
        lastHandledFocusRequestId = reqId
        lastHandledFollowRequestId = state.cameraFollowRequestId
        lastFollowedLatLng = targetLatLng
    }

    // 2. Smooth Auto-Follow Camera driven by ViewModel cameraFollowRequestId (>= 12m move guard, valid coords only)
    LaunchedEffect(state.cameraFollowRequestId, state.cameraMode, state.trackedVehicle, maplibreMapInstance) {
        if (state.cameraMode != VehicleCameraMode.FOLLOW_VEHICLE) return@LaunchedEffect
        val reqId = state.cameraFollowRequestId
        if (reqId <= 0L || reqId == lastHandledFollowRequestId) return@LaunchedEffect
        val map = maplibreMapInstance ?: return@LaunchedEffect
        val vehicle = state.trackedVehicle ?: return@LaunchedEffect
        if (!RoutePathGeoJsonConverter.isValidCoordinate(vehicle.lat, vehicle.lng)) return@LaunchedEffect

        val newLatLng = LatLng(vehicle.lat, vehicle.lng)
        val prevLatLng = lastFollowedLatLng
        val movedEnough = prevLatLng == null || prevLatLng.distanceTo(newLatLng) >= 12.0
        lastHandledFollowRequestId = reqId
        if (movedEnough) {
            map.animateCamera(
                CameraUpdateFactory.newLatLng(newLatLng),
                500
            )
            lastFollowedLatLng = newLatLng
        }
    }

    // Render / Update Stops on Map (with background GeoJSON processing)
    LaunchedEffect(
        state.stopsInViewport,
        state.activeRouteStops,
        state.selectedStop,
        state.showStopsLayer,
        currentStyleInstance
    ) {
        val style = currentStyleInstance ?: return@LaunchedEffect
        val stopsToRender = if (state.activeRouteStops.isNotEmpty()) {
            state.activeRouteStops
        } else {
            state.stopsInViewport
        }
        val featureCollection = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            RoutePathGeoJsonConverter.toStopFeatureCollection(stopsToRender, state.selectedStop?.id)
        }
        MapLayerManager.applyStops(
            ops = MapLibreStyleOperations(style),
            featureCollection = featureCollection,
            hasStops = stopsToRender.isNotEmpty(),
            showStops = state.showStopsLayer,
            bitmapFactory = bitmapFactory
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        // MapLibre Surface
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Top Control Overlay: Active Route Indicator & Tile Provider Switcher
        TopControlsOverlay(
            activeRoute = state.activeRoute,
            activeVariant = state.activeVariant,
            routeVehicleCount = state.routeVehicles.size,
            routeVehicleFeedStatus = state.routeVehicleFeedStatus,
            routeLastUpdatedTime = state.routeLastUpdatedTime,
            tileProvider = state.tileProvider,
            isTileMenuOpen = isTileMenuOpen,
            onTileMenuToggle = { isTileMenuOpen = !isTileMenuOpen },
            onTileProviderSelected = {
                viewModel.setTileProvider(it)
                isTileMenuOpen = false
            },
            onToggleDirection = viewModel::toggleRouteDirection,
            onClearRoute = viewModel::clearRoute,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp, start = 12.dp, end = 12.dp)
        )

        // Zoom Too Far Hint Toast
        AnimatedVisibility(
            visible = state.isZoomTooFar && state.activeRoute == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Phóng to bản đồ để xem các trạm dừng",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Floating Action Buttons (GPS, Zoom In/Out, Direction Switch)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // My Location Button
            FloatingActionButton(
                onClick = {
                    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

                    if (hasFine || hasCoarse) {
                        viewModel.setLocationPermissionGranted(true)
                        fetchDeviceLocation(
                            context = context,
                            client = fusedLocationClient,
                            onLocation = { lat, lng, bearing, accuracy ->
                                viewModel.updateUserLocation(lat, lng, bearing, accuracy)
                                maplibreMapInstance?.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15.5),
                                    600
                                )
                            },
                            onUnavailable = {
                                viewModel.markUserLocationUnavailable()
                            }
                        )
                    } else {
                        viewModel.setLocationPermissionGranted(false)
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Vị trí của tôi",
                    modifier = Modifier.size(22.dp)
                )
            }

            // Toggle Stops Layer FAB
            SmallFloatingActionButton(
                onClick = viewModel::toggleStopsLayer,
                shape = CircleShape,
                containerColor = if (state.showStopsLayer) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                contentColor = if (state.showStopsLayer) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = if (state.showStopsLayer) "Ẩn điểm dừng" else "Hiện điểm dừng",
                    modifier = Modifier.size(18.dp)
                )
            }

            // Nearby Stops FAB
            SmallFloatingActionButton(
                onClick = {
                    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (hasFine || hasCoarse) {
                        viewModel.setLocationPermissionGranted(true)
                        fetchDeviceLocation(
                            context = context,
                            client = fusedLocationClient,
                            onLocation = { lat, lng, bearing, accuracy ->
                                viewModel.updateUserLocation(lat, lng, bearing, accuracy)
                                viewModel.findNearbyStops()
                            },
                            onUnavailable = {
                                viewModel.markUserLocationUnavailable()
                            }
                        )
                    } else {
                        viewModel.setLocationPermissionGranted(false)
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "Tìm trạm gần đây",
                    modifier = Modifier.size(18.dp)
                )
            }

            // Direction Switch FAB (Visible only when Route is active)
            if (state.activeRoute != null && state.routeVariants.size > 1) {
                SmallFloatingActionButton(
                    onClick = viewModel::toggleRouteDirection,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Đổi chiều đi/về",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Zoom In FAB
            SmallFloatingActionButton(
                onClick = {
                    maplibreMapInstance?.animateCamera(CameraUpdateFactory.zoomIn(), 300)
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Phóng to", modifier = Modifier.size(18.dp))
            }

            // Zoom Out FAB
            SmallFloatingActionButton(
                onClick = {
                    maplibreMapInstance?.animateCamera(CameraUpdateFactory.zoomOut(), 300)
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Thu nhỏ", modifier = Modifier.size(18.dp))
            }
        }

        // Bottom Attribution Chip
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color.Black.copy(alpha = 0.55f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 12.dp)
        ) {
            Text(
                text = state.tileProvider.attributionText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = Color.White,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // Bottom Stop Information Popup Card
        AnimatedVisibility(
            visible = state.selectedStop != null && state.trackedVehicle == null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            state.selectedStop?.let { stop ->
                StopInfoPopupCard(
                    stop = stop,
                    onViewPredictions = { onStopClicked(stop.id) },
                    onClose = viewModel::clearSelectedStop
                )
            }
        }

        // GPS / Camera Validation Status Toast
        AnimatedVisibility(
            visible = state.cameraWarningMessage != null || (state.isUserLocationUnavailable && state.userLocation == null && !state.isZoomTooFar),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 84.dp, start = 16.dp, end = 16.dp)
        ) {
            val warningText = state.cameraWarningMessage ?: if (!state.isLocationPermissionGranted) {
                "Chưa cấp quyền vị trí để hiển thị chấm GPS điện thoại"
            } else {
                "GPS điện thoại hiện chưa khả dụng"
            }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f),
                tonalElevation = 4.dp,
                modifier = Modifier.clickable {
                    if (state.cameraWarningMessage != null) viewModel.clearCameraWarning()
                }
            ) {
                Text(
                    text = warningText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Bottom Tracked Vehicle Card
        AnimatedVisibility(
            visible = state.trackedVehicle != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            state.trackedVehicle?.let { vehicle ->
                VehicleTrackingInfoCard(
                    vehicle = vehicle,
                    cameraMode = state.cameraMode,
                    onToggleAutoFollow = viewModel::toggleVehicleAutoFollow,
                    onCenterVehicle = viewModel::enableVehicleAutoFollow,
                    onClose = viewModel::clearTrackedVehicle
                )
            }
        }

        if (overlapCandidates.size >= 2) {
            VehicleOverlapPickerSheet(
                routeNo = state.activeRoute?.routeNo.orEmpty(),
                vehicles = overlapCandidates,
                onSelectVehicle = { picked ->
                    overlapCandidates = emptyList()
                    viewModel.selectRouteVehicle(picked.telemetry.plateNumber)
                },
                onDismiss = { overlapCandidates = emptyList() }
            )
        }

        // Bottom Nearby Stops Card
        AnimatedVisibility(
            visible = state.nearbyStops.isNotEmpty() && state.selectedStop == null && state.trackedVehicle == null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Trạm dừng gần bạn (${state.nearbyStops.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        IconButton(onClick = viewModel::clearNearbyStops) {
                            Icon(Icons.Default.Close, contentDescription = "Đóng")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    androidx.compose.foundation.lazy.LazyColumn {
                        items(state.nearbyStops) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectStop(item.stop)
                                        maplibreMapInstance?.animateCamera(
                                            CameraUpdateFactory.newLatLngZoom(
                                                LatLng(item.stop.lat, item.stop.lng),
                                                16.0
                                            ),
                                            800
                                        )
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.stop.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    if (item.stop.fullAddress.isNotBlank()) {
                                        Text(
                                            text = item.stop.fullAddress,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${item.distanceMeters.toInt()}m",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        // Loading Indicator
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .size(32.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Top bar overlay containing active route indicator and Tile Provider dropdown selector.
 */
@Composable
private fun TopControlsOverlay(
    activeRoute: com.hcmcbustracker.core.model.Route?,
    activeVariant: com.hcmcbustracker.core.model.RouteVariant?,
    routeVehicleCount: Int,
    routeVehicleFeedStatus: PredictionFeedStatus,
    routeLastUpdatedTime: String?,
    tileProvider: TileProvider,
    isTileMenuOpen: Boolean,
    onTileMenuToggle: () -> Unit,
    onTileProviderSelected: (TileProvider) -> Unit,
    onToggleDirection: () -> Unit,
    onClearRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Active Route or App Label
        if (activeRoute != null) {
            Card(
                modifier = Modifier.weight(1f, fill = false),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RouteBadge(routeNo = activeRoute.routeNo)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = activeVariant?.shortName ?: activeRoute.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                        val dirLabel = if (activeVariant?.isOutbound == true) "Lượt đi" else "Lượt về"
                        val fleetLabel = when {
                            routeVehicleFeedStatus == PredictionFeedStatus.LOADING && routeVehicleCount == 0 -> "$dirLabel • Đang tìm xe..."
                            routeVehicleCount > 0 -> "$dirLabel • $routeVehicleCount xe${routeLastUpdatedTime?.let { " ($it)" } ?: ""}"
                            routeVehicleFeedStatus == PredictionFeedStatus.API_ERROR -> "$dirLabel • Ngoại tuyến/Lỗi mạng"
                            else -> "$dirLabel • Chưa có xe"
                        }
                        Text(
                            text = fleetLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = if (activeVariant?.isOutbound == true) MaterialTheme.colorScheme.primary else Color(0xFFE64A19),
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onToggleDirection,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Đổi hướng",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onClearRoute,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng tuyến",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsBus,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Bản đồ xe buýt TP.HCM",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // Right: Tile Provider Switcher Menu
        Box {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.clickable(onClick = onTileMenuToggle)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Chọn lớp bản đồ",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tileProvider.displayName.substringBefore(" ("),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            DropdownMenu(
                expanded = isTileMenuOpen,
                onDismissRequest = onTileMenuToggle
            ) {
                TileProvider.entries.forEach { provider ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = provider.displayName,
                                    fontWeight = if (provider == tileProvider) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = provider.attributionText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = { onTileProviderSelected(provider) }
                    )
                }
            }
        }
    }
}

/**
 * Bottom stop popup card shown when a marker is clicked on the map.
 */
@Composable
private fun StopInfoPopupCard(
    stop: BusStop,
    onViewPredictions: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Stop Name, Code & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (stop.code.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = stop.code,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = stop.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                maxLines = 1
                            )
                        }

                        if (stop.fullAddress.isNotBlank()) {
                            Text(
                                text = stop.fullAddress,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng thông tin trạm",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Passing Routes Badges
            if (stop.routes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tuyến đi qua:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    stop.routes.forEach { routeNo ->
                        RouteBadge(
                            routeNo = routeNo,
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action: View Predictions
            Button(
                onClick = onViewPredictions,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsBus,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Dự báo xe đến trạm thời gian thực",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun fetchDeviceLocation(
    context: Context,
    client: com.google.android.gms.location.FusedLocationProviderClient,
    onLocation: (Double, Double, Float?, Float?) -> Unit,
    onUnavailable: () -> Unit = {}
) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
    val isProviderEnabled = locationManager?.let { lm ->
        lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
    } ?: true

    if (!isProviderEnabled) {
        onUnavailable()
        return
    }

    client.lastLocation
        .addOnSuccessListener { loc ->
            val nowMs = System.currentTimeMillis()
            val isFreshCached = loc != null && (nowMs - loc.time) in 0L..180_000L
            if (isFreshCached) {
                val bearing = if (loc.hasBearing() && loc.speed > 0.3f) loc.bearing else null
                val accuracy = if (loc.hasAccuracy()) loc.accuracy else null
                onLocation(loc.latitude, loc.longitude, bearing, accuracy)
            } else {
                client.getCurrentLocation(
                    com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                    null
                ).addOnSuccessListener { freshLoc ->
                    if (freshLoc != null) {
                        val bearing = if (freshLoc.hasBearing() && freshLoc.speed > 0.3f) freshLoc.bearing else null
                        val accuracy = if (freshLoc.hasAccuracy()) freshLoc.accuracy else null
                        onLocation(freshLoc.latitude, freshLoc.longitude, bearing, accuracy)
                    } else {
                        onUnavailable()
                    }
                }.addOnFailureListener {
                    onUnavailable()
                }
            }
        }
        .addOnFailureListener {
            onUnavailable()
        }
}

/**
 * Compact, honest Bottom Card displaying tracked vehicle route/direction context,
 * next/selected stop distance, reliable ETA, arrival status, 5-timestamp freshness & follow toggle.
 */
@Composable
private fun VehicleTrackingInfoCard(
    vehicle: TrackedVehicleState,
    cameraMode: VehicleCameraMode,
    onToggleAutoFollow: () -> Unit,
    onCenterVehicle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUnknown = vehicle.arrivalStatus == VehicleArrivalStatus.UNKNOWN
    val isPassed = vehicle.arrivalStatus == VehicleArrivalStatus.PASSED_STOP
    val isFollowing = cameraMode == VehicleCameraMode.FOLLOW_VEHICLE

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 1. Header: Plate, Route/Variant badge, 6-state Arrival Status Chip, Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DirectionsBus,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Xe ${vehicle.plateNumber}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                            if (vehicle.routeNo.isNotBlank()) {
                                RouteBadge(routeNo = vehicle.routeNo)
                            }
                            VehicleArrivalStatusChip(status = vehicle.arrivalStatus)
                        }

                        if (vehicle.variantShortName.isNotBlank()) {
                            Text(
                                text = "Chiều: ${vehicle.variantShortName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng theo dõi",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Stop Progression Line (Selected stop vs Next stop along route)
            if (isPassed && !vehicle.selectedStopName.isNullOrBlank()) {
                val passedDist = kotlin.math.abs(vehicle.distanceToSelectedStopMeters ?: 0.0)
                Text(
                    text = "Đã đi qua trạm ${vehicle.selectedStopName} (${formatMetricDistance(passedDist)})",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFFE65100),
                    maxLines = 1
                )
            }
            val nextStopLabel = vehicle.nextStopName ?: vehicle.stopName.ifBlank { "Trạm #${vehicle.stopId}" }
            Text(
                text = "Trạm kế tiếp: $nextStopLabel",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. 3 Clean Metric Boxes: Route Distance | Reliable ETA | Speed
            val distanceText = when {
                isUnknown -> "--"
                isPassed -> formatMetricDistance(vehicle.distanceToNextStopMeters ?: vehicle.effectiveDistanceMeters)
                else -> formatMetricDistance(vehicle.effectiveDistanceMeters)
            }
            val distanceSublabel = when (vehicle.positionSource) {
                PositionSource.LATENCY_COMPENSATED ->
                    if (vehicle.projectedDistanceMeters > 0) "Dự đoán bù +${vehicle.projectedDistanceMeters.toInt()}m" else "Dự đoán theo tuyến"
                PositionSource.INTERPOLATED_ON_ROUTE -> "Ước tính theo tuyến"
                PositionSource.HELD_AT_LAST_VALID_GPS -> "Giữ vị trí cũ (GPS trễ)"
                PositionSource.UNKNOWN -> "Thiếu dữ liệu vị trí"
            }

            val etaText = if (!isUnknown && vehicle.etaSeconds != null && vehicle.etaSeconds >= 0) {
                formatMetricEta(vehicle.etaSeconds)
            } else {
                "--"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VehicleMetricBox(
                    label = if (isPassed && vehicle.nextStopName != null) "Tới trạm kế" else "Cự ly tuyến",
                    value = distanceText,
                    sublabel = distanceSublabel,
                    modifier = Modifier.weight(1f)
                )

                VehicleMetricBox(
                    label = "ETA ước tính",
                    value = etaText,
                    sublabel = if (vehicle.gpsAgeSeconds >= 0) "GPS ${vehicle.formattedGpsTime} (${vehicle.gpsAgeSeconds}s)" else "GPS ${vehicle.formattedGpsTime}",
                    modifier = Modifier.weight(1f)
                )

                VehicleMetricBox(
                    label = "Vận tốc",
                    value = if (vehicle.speedKmH >= 0.0) "${vehicle.speedKmH.toInt()} km/h" else "--",
                    sublabel = if (vehicle.speedKmH <= 1.5) "Đang dừng" else "Đang chạy",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Footer: Freshness Tier Badge, Update timestamp, and Auto-Follow / Center button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LatencyTierBadge(category = vehicle.latencyCategory)
                        if (vehicle.isGpsTimestampUnchanged) {
                            Text(
                                text = "• GPS giữ nguyên",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color(0xFFF57F17)
                            )
                        }
                    }
                    val updatedTimeText = remember(vehicle.renderedAtMillis) {
                        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                            .format(java.util.Date(vehicle.renderedAtMillis))
                    }
                    Text(
                        text = "Cập nhật app: $updatedTimeText",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilterChip(
                    selected = isFollowing,
                    onClick = {
                        if (isFollowing) onToggleAutoFollow() else onCenterVehicle()
                    },
                    label = {
                        Text(
                            text = if (isFollowing) "Đang theo dõi" else "Theo dõi xe",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isFollowing) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun VehicleMetricBox(
    label: String,
    value: String,
    sublabel: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = sublabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun VehicleArrivalStatusChip(status: VehicleArrivalStatus) {
    val (bgColor, textColor, label) = when (status) {
        VehicleArrivalStatus.MOVING -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), status.labelVi)
        VehicleArrivalStatus.APPROACHING_STOP -> Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), status.labelVi)
        VehicleArrivalStatus.ARRIVING_AT_STOP -> Triple(Color(0xFFFFF8E1), Color(0xFFE65100), status.labelVi)
        VehicleArrivalStatus.AT_STOP -> Triple(Color(0xFFEDE7F6), Color(0xFF512DA8), status.labelVi)
        VehicleArrivalStatus.PASSED_STOP -> Triple(Color(0xFFFFF3E0), Color(0xFFD84315), status.labelVi)
        VehicleArrivalStatus.UNKNOWN -> Triple(Color(0xFFF5F5F5), Color(0xFF616161), "Không đủ dữ liệu")
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun LatencyTierBadge(category: GpsLatencyCategory) {
    val (bgColor, textColor) = when (category) {
        GpsLatencyCategory.FRESH -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
        GpsLatencyCategory.SLIGHT_DELAY -> Pair(Color(0xFFE0F2F1), Color(0xFF00695C))
        GpsLatencyCategory.DELAYED -> Pair(Color(0xFFFFF8E1), Color(0xFFF57F17))
        GpsLatencyCategory.STALE -> Pair(Color(0xFFFFEBEE), Color(0xFFC62828))
        GpsLatencyCategory.UNKNOWN -> Pair(Color(0xFFF5F5F5), Color(0xFF616161))
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text = category.labelVi,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1
        )
    }
}

private fun formatMetricDistance(meters: Double): String {
    return if (meters < 1000.0) {
        "${meters.toInt()}m"
    } else {
        String.format(java.util.Locale.US, "%.2f km", meters / 1000.0)
    }
}

private fun formatMetricEta(etaSeconds: Int?): String {
    if (etaSeconds == null || etaSeconds < 0) return "--"
    if (etaSeconds < 60) return "< 1 phút"
    val mins = etaSeconds / 60
    val secs = etaSeconds % 60
    return if (secs == 0) "$mins phút" else "$mins p $secs s"
}
