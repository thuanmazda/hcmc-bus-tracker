package com.hcmcbustracker.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcmcbustracker.core.database.repository.BusRepository
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.CompensationStatus
import com.hcmcbustracker.core.model.GpsFreshnessState
import com.hcmcbustracker.core.model.GpsLatencyCategory
import com.hcmcbustracker.core.model.LatLngPoint
import com.hcmcbustracker.core.model.NetworkResult
import com.hcmcbustracker.core.model.PollingDiagnosticsSnapshot
import com.hcmcbustracker.core.model.PositionCompensationEngine
import com.hcmcbustracker.core.model.PositionSource
import com.hcmcbustracker.core.model.PredictionFeedStatus
import com.hcmcbustracker.core.model.Route
import com.hcmcbustracker.core.model.RoutePath
import com.hcmcbustracker.core.model.RouteVariant
import com.hcmcbustracker.core.model.RouteVariantPollingTarget
import com.hcmcbustracker.core.model.RouteVehicleTelemetry
import com.hcmcbustracker.core.model.VehicleArrivalStatus
import com.hcmcbustracker.feature.map.engine.InterpolatedVehiclePosition
import com.hcmcbustracker.feature.map.engine.VehicleInterpolationEngine
import com.hcmcbustracker.feature.map.tile.TileProvider
import com.hcmcbustracker.feature.map.tile.TileProviderManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class NearbyBusStop(
    val stop: BusStop,
    val distanceMeters: Double
)

data class RouteActiveVehicleUiModel(
    val telemetry: RouteVehicleTelemetry,
    val position: InterpolatedVehiclePosition
)

data class TrackedVehicleState(
    val routeId: Int,
    val varId: Int,
    val stopId: Int,
    val stopName: String = "",
    val plateNumber: String,
    val speedKmH: Double,
    val rawDistanceMeters: Double,
    val effectiveDistanceMeters: Double,
    val projectedDistanceMeters: Double,
    val etaSeconds: Int? = null,
    val timestampIso: String? = null,
    val gpsAgeSeconds: Long = 0L,
    val isGpsTimestampUnchanged: Boolean = false,
    val compensationStatus: CompensationStatus = CompensationStatus.LIVE_PROJECTED,
    val lat: Double,
    val lng: Double,
    val bearing: Float,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val routeNo: String = "",
    val variantShortName: String = "",
    val selectedStopId: Int? = null,
    val selectedStopName: String? = null,
    val distanceToSelectedStopMeters: Double? = null,
    val nextStopId: Int? = null,
    val nextStopName: String? = null,
    val distanceToNextStopMeters: Double? = null,
    val etaToNextStopSeconds: Int? = null,
    val checkpointStopId: Int = stopId,
    val predictionTargetStopId: Int = stopId,
    val gpsTimestampMillis: Long? = null,
    val requestStartedAtMillis: Long = updatedAtMillis,
    val responseReceivedAtMillis: Long = updatedAtMillis,
    val predictionUpdatedAtMillis: Long = updatedAtMillis,
    val renderedAtMillis: Long = updatedAtMillis,
    val latencyCategory: GpsLatencyCategory = GpsLatencyCategory.UNKNOWN,
    val arrivalStatus: VehicleArrivalStatus = VehicleArrivalStatus.UNKNOWN,
    val positionSource: PositionSource = PositionSource.INTERPOLATED_ON_ROUTE,
    val hasValidHeading: Boolean = true,
    val isAgedAdaptiveProjection: Boolean = false
) {
    val distanceMeters: Double get() = effectiveDistanceMeters

    val networkLatencyMillis: Long
        get() = maxOf(0L, responseReceivedAtMillis - requestStartedAtMillis)

    val dataProcessingDelayMillis: Long
        get() = maxOf(0L, renderedAtMillis - responseReceivedAtMillis)

    val formattedGpsTime: String
        get() {
            val iso = timestampIso ?: return "--"
            return if (iso.contains("T")) iso.substringAfter("T").take(8) else iso.takeLast(8)
        }
}

enum class VehicleCameraMode {
    IDLE,
    FOCUS_VEHICLE,
    FOLLOW_VEHICLE,
    USER_CONTROLLED
}

data class MapUiState(
    val tileProvider: TileProvider = TileProvider.FPT_STIS,
    val stopsInViewport: List<BusStop> = emptyList(),
    val isZoomTooFar: Boolean = false,
    val selectedStop: BusStop? = null,
    val activeRoute: Route? = null,
    val activeVariant: RouteVariant? = null,
    val routeVariants: List<RouteVariant> = emptyList(),
    val activeRoutePath: RoutePath? = null,
    val activeRouteStops: List<BusStop> = emptyList(),
    val routeVehicles: List<RouteActiveVehicleUiModel> = emptyList(),
    val selectedVehiclePlate: String? = null,
    val routeVehicleFeedStatus: PredictionFeedStatus = PredictionFeedStatus.EMPTY,
    val routeLastUpdatedTime: String? = null,
    val userLocation: LatLng? = null,
    val userBearing: Float? = null,
    val userAccuracyMeters: Float? = null,
    val isLocationPermissionGranted: Boolean = false,
    val isUserLocationUnavailable: Boolean = false,
    val showStopsLayer: Boolean = true,
    val nearbyStops: List<NearbyBusStop> = emptyList(),
    val trackedVehicle: TrackedVehicleState? = null,
    val cameraMode: VehicleCameraMode = VehicleCameraMode.IDLE,
    val cameraFocusRequestId: Long = 0L,
    val cameraFollowRequestId: Long = 0L,
    val cameraWarningMessage: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: BusRepository,
    private val tileProviderManager: TileProviderManager
) : ViewModel() {

    companion object {
        private const val MIN_POLL_INTERVAL_MS = 10_000L
        private const val DEFAULT_POLL_INTERVAL_MS = 15_000L
        private const val MAX_BACKOFF_INTERVAL_MS = 120_000L
        internal const val FOLLOW_SIGNIFICANT_MOVE_METERS = 12.0
    }

    private val _stopsInViewport = MutableStateFlow<List<BusStop>>(emptyList())
    private val _isZoomTooFar = MutableStateFlow(false)
    private val _selectedStop = MutableStateFlow<BusStop?>(null)
    private val _activeRoute = MutableStateFlow<Route?>(null)
    private val _activeVariant = MutableStateFlow<RouteVariant?>(null)
    private val _routeVariants = MutableStateFlow<List<RouteVariant>>(emptyList())
    private val _activeRoutePath = MutableStateFlow<RoutePath?>(null)
    private val _activeRouteStops = MutableStateFlow<List<BusStop>>(emptyList())
    private val _routeVehicles = MutableStateFlow<List<RouteActiveVehicleUiModel>>(emptyList())
    private val _selectedVehiclePlate = MutableStateFlow<String?>(null)
    private val _routeVehicleFeedStatus = MutableStateFlow(PredictionFeedStatus.EMPTY)
    private val _routeLastUpdatedTime = MutableStateFlow<String?>(null)
    private val _userLocation = MutableStateFlow<LatLng?>(null)
    private val _userBearing = MutableStateFlow<Float?>(null)
    private val _userAccuracyMeters = MutableStateFlow<Float?>(null)
    private val _isLocationPermissionGranted = MutableStateFlow(false)
    private val _isUserLocationUnavailable = MutableStateFlow(false)
    private val _showStopsLayer = MutableStateFlow(true)
    private val _nearbyStops = MutableStateFlow<List<NearbyBusStop>>(emptyList())
    private val _trackedVehicle = MutableStateFlow<TrackedVehicleState?>(null)
    private val _cameraMode = MutableStateFlow(VehicleCameraMode.IDLE)
    private val _cameraFocusRequestId = MutableStateFlow(0L)
    private val _cameraFollowRequestId = MutableStateFlow(0L)
    private val _cameraWarningMessage = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    var savedCameraPosition: CameraPosition? = null
    private var lastFollowTargetLatLng: LatLng? = null
    private var stopsJob: Job? = null
    private var cameraDebounceJob: Job? = null
    private var currentCachedBounds: LatLngBounds? = null
    private var routeJob: Job? = null
    private var routePollingJob: Job? = null
    private var activePollingRouteVar: Pair<Int, Int>? = null
    private var isMapInForeground = false

    val isPollingActive: Boolean
        get() = routePollingJob?.isActive == true

    val isForegroundActive: Boolean
        get() = isMapInForeground
    private var basePollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS
    internal var computeDispatcher: CoroutineDispatcher = Dispatchers.Default
    internal var backoffJitterProvider: () -> Double = { kotlin.random.Random.Default.nextDouble() }

    val pollingDiagnostics: StateFlow<PollingDiagnosticsSnapshot> = repository.observePollingDiagnostics()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PollingDiagnosticsSnapshot()
        )

    // Preserved user-selected stop when arriving from StopPredictionScreen so route polling never overwrites it
    private var focusedSelectedStopId: Int? = null
    private var focusedSelectedStopName: String? = null

    // Cached monotonic history per normalized plate for active route variant
    private val previousTelemetryByPlate = LinkedHashMap<String, RouteVehicleTelemetry>()
    private val previousInterpolatedByPlate = LinkedHashMap<String, InterpolatedVehiclePosition>()

    init {
        viewModelScope.launch {
            repository.getUserPreferences().collect { prefs ->
                basePollIntervalMs = (prefs.pollingIntervalSeconds * 1000L).coerceIn(MIN_POLL_INTERVAL_MS, MAX_BACKOFF_INTERVAL_MS)
            }
        }
    }

    private data class DisplayStateParams(
        val tileProvider: TileProvider,
        val stopsInViewport: List<BusStop>,
        val isZoomTooFar: Boolean,
        val selectedStop: BusStop?
    )

    private val displayStateFlow = combine(
        tileProviderManager.currentProvider,
        _stopsInViewport,
        _isZoomTooFar,
        _selectedStop
    ) { provider, stops, zoomTooFar, selectedStop ->
        DisplayStateParams(provider, stops, zoomTooFar, selectedStop)
    }

    private data class RouteStateParams(
        val activeRoute: Route?,
        val activeVariant: RouteVariant?,
        val routeVariants: List<RouteVariant>,
        val activeRoutePath: RoutePath?,
        val activeRouteStops: List<BusStop>
    )

    private val routeStateFlow = combine(
        _activeRoute,
        _activeVariant,
        _routeVariants,
        _activeRoutePath,
        _activeRouteStops
    ) { route, variant, variants, path, stops ->
        RouteStateParams(route, variant, variants, path, stops)
    }

    private data class RouteFleetParams(
        val routeVehicles: List<RouteActiveVehicleUiModel>,
        val selectedVehiclePlate: String?,
        val feedStatus: PredictionFeedStatus,
        val lastUpdatedTime: String?
    )

    private val routeFleetFlow = combine(
        _routeVehicles,
        _selectedVehiclePlate,
        _routeVehicleFeedStatus,
        _routeLastUpdatedTime
    ) { vehicles, selectedPlate, status, time ->
        RouteFleetParams(vehicles, selectedPlate, status, time)
    }

    private data class StatusParams(
        val userLocation: LatLng?,
        val userBearing: Float?,
        val userAccuracyMeters: Float?,
        val isPermissionGranted: Boolean,
        val isUserLocationUnavailable: Boolean,
        val showStopsLayer: Boolean,
        val nearbyStops: List<NearbyBusStop>,
        val trackedVehicle: TrackedVehicleState?,
        val cameraMode: VehicleCameraMode,
        val cameraFocusRequestId: Long,
        val cameraFollowRequestId: Long,
        val cameraWarningMessage: String?,
        val isLoading: Boolean,
        val errorMessage: String?
    )

    private data class CameraControlParams(
        val mode: VehicleCameraMode,
        val focusRequestId: Long,
        val followRequestId: Long,
        val warningMessage: String?
    )

    private val cameraControlFlow = combine(
        _cameraMode,
        _cameraFocusRequestId,
        _cameraFollowRequestId,
        _cameraWarningMessage
    ) { mode, focusReqId, followReqId, warning ->
        CameraControlParams(mode, focusReqId, followReqId, warning)
    }

    private val statusFlow = combine(
        combine(
            _userLocation,
            _userBearing,
            _userAccuracyMeters,
            _isLocationPermissionGranted,
            _isUserLocationUnavailable
        ) { loc, bearing, acc, perm, unavail ->
            Tuple5(loc, bearing, acc, perm, unavail)
        },
        _showStopsLayer,
        _nearbyStops,
        combine(_trackedVehicle, _isLoading, _errorMessage) { vehicle, loading, error ->
            Triple(vehicle, loading, error)
        },
        cameraControlFlow
    ) { locTuple, showStops, nearby, (vehicle, loading, error), cam ->
        StatusParams(
            userLocation = locTuple.first,
            userBearing = locTuple.second,
            userAccuracyMeters = locTuple.third,
            isPermissionGranted = locTuple.fourth,
            isUserLocationUnavailable = locTuple.fifth,
            showStopsLayer = showStops,
            nearbyStops = nearby,
            trackedVehicle = vehicle,
            cameraMode = cam.mode,
            cameraFocusRequestId = cam.focusRequestId,
            cameraFollowRequestId = cam.followRequestId,
            cameraWarningMessage = cam.warningMessage,
            isLoading = loading,
            errorMessage = error
        )
    }

    private data class Tuple5<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

    val uiState: StateFlow<MapUiState> = combine(
        displayStateFlow,
        routeStateFlow,
        routeFleetFlow,
        statusFlow
    ) { display, routeParams, fleet, status ->
        MapUiState(
            tileProvider = display.tileProvider,
            stopsInViewport = display.stopsInViewport,
            isZoomTooFar = display.isZoomTooFar,
            selectedStop = display.selectedStop,
            activeRoute = routeParams.activeRoute,
            activeVariant = routeParams.activeVariant,
            routeVariants = routeParams.routeVariants,
            activeRoutePath = routeParams.activeRoutePath,
            activeRouteStops = routeParams.activeRouteStops,
            routeVehicles = fleet.routeVehicles,
            selectedVehiclePlate = fleet.selectedVehiclePlate,
            routeVehicleFeedStatus = fleet.feedStatus,
            routeLastUpdatedTime = fleet.lastUpdatedTime,
            userLocation = status.userLocation,
            userBearing = status.userBearing,
            userAccuracyMeters = status.userAccuracyMeters,
            isLocationPermissionGranted = status.isPermissionGranted,
            isUserLocationUnavailable = status.isUserLocationUnavailable,
            showStopsLayer = status.showStopsLayer,
            nearbyStops = status.nearbyStops,
            trackedVehicle = status.trackedVehicle,
            cameraMode = status.cameraMode,
            cameraFocusRequestId = status.cameraFocusRequestId,
            cameraFollowRequestId = status.cameraFollowRequestId,
            cameraWarningMessage = status.cameraWarningMessage,
            isLoading = status.isLoading,
            errorMessage = status.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MapUiState()
    )

    fun setTileProvider(provider: TileProvider) {
        tileProviderManager.setTileProvider(provider)
    }

    fun onMapResumed() {
        isMapInForeground = true
        val route = _activeRoute.value
        val variant = _activeVariant.value
        if (route != null && variant != null && (routePollingJob == null || routePollingJob?.isActive != true)) {
            startRouteWideVehiclePolling(route.id, variant.id)
        }
    }

    fun onMapPaused() {
        isMapInForeground = false
        routePollingJob?.cancel()
        routePollingJob = null
        activePollingRouteVar = null
    }

    fun onCameraIdle(bounds: LatLngBounds, zoom: Double) {
        cameraDebounceJob?.cancel()
        stopsJob?.cancel()

        if (zoom < 13.5) {
            _isZoomTooFar.value = true
            _stopsInViewport.value = emptyList()
            currentCachedBounds = null
            return
        }
        _isZoomTooFar.value = false

        if (_activeRouteStops.value.isNotEmpty()) {
            return
        }

        val cached = currentCachedBounds
        if (cached != null &&
            bounds.latitudeNorth <= cached.latitudeNorth &&
            bounds.latitudeSouth >= cached.latitudeSouth &&
            bounds.longitudeEast <= cached.longitudeEast &&
            bounds.longitudeWest >= cached.longitudeWest
        ) {
            return
        }

        cameraDebounceJob = viewModelScope.launch {
            delay(250L)
            val latSpan = bounds.latitudeNorth - bounds.latitudeSouth
            val lngSpan = bounds.longitudeEast - bounds.longitudeWest
            val margin = 0.25
            val expandedBounds = LatLngBounds.from(
                bounds.latitudeNorth + margin * latSpan,
                bounds.longitudeEast + margin * lngSpan,
                bounds.latitudeSouth - margin * latSpan,
                bounds.longitudeWest - margin * lngSpan
            )
            currentCachedBounds = expandedBounds

            stopsJob = viewModelScope.launch {
                repository.getStopsInBounds(
                    minLat = expandedBounds.latitudeSouth,
                    maxLat = expandedBounds.latitudeNorth,
                    minLng = expandedBounds.longitudeWest,
                    maxLng = expandedBounds.longitudeEast
                ).collect { stops ->
                    _stopsInViewport.value = stops
                }
            }
        }
    }

    fun selectStop(stop: BusStop) {
        _selectedStop.value = stop
    }

    fun selectStopById(stopId: Int) {
        viewModelScope.launch {
            val stop = repository.getStopById(stopId).firstOrNull()
            _selectedStop.value = stop
        }
    }

    fun clearSelectedStop() {
        _selectedStop.value = null
    }

    fun loadRoute(routeId: Int, preferredVarId: Int? = null) {
        routeJob?.cancel()
        stopRouteWidePollingAndClearFleet()
        focusedSelectedStopId = null
        focusedSelectedStopName = null
        _trackedVehicle.value = null
        _selectedVehiclePlate.value = null
        lastFollowTargetLatLng = null
        _cameraMode.value = VehicleCameraMode.IDLE
        _cameraWarningMessage.value = null

        routeJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            repository.ensureInitialStaticDatasetLoaded()

            var route = repository.getRouteById(routeId).firstOrNull()
            var variants = repository.getVariantsForRoute(routeId).firstOrNull() ?: emptyList()

            if (variants.isEmpty()) {
                repository.syncRouteDetailAndVariants(routeId)
                route = repository.getRouteById(routeId).firstOrNull()
                variants = repository.getVariantsForRoute(routeId).firstOrNull() ?: emptyList()
            }

            _activeRoute.value = route
            _routeVariants.value = variants

            val targetVariant = if (preferredVarId != null) {
                variants.firstOrNull { it.id == preferredVarId }
            } else null
                ?: variants.firstOrNull { it.isOutbound }
                ?: variants.firstOrNull()

            _activeVariant.value = targetVariant

            if (targetVariant != null) {
                loadVariantDetails(routeId, targetVariant.id)
                _isLoading.value = false
                if (isMapInForeground) {
                    startRouteWideVehiclePolling(routeId, targetVariant.id)
                }
            } else {
                _isLoading.value = false
            }
        }
    }

    fun switchVariant(variantId: Int) {
        val variant = _routeVariants.value.firstOrNull { it.id == variantId } ?: return
        val routeId = _activeRoute.value?.id ?: return
        _activeVariant.value = variant

        // Reset old variant's vehicles strictly so directions are never mixed
        stopRouteWidePollingAndClearFleet()
        focusedSelectedStopId = null
        focusedSelectedStopName = null
        _trackedVehicle.value = null
        _selectedVehiclePlate.value = null
        lastFollowTargetLatLng = null
        _cameraMode.value = VehicleCameraMode.IDLE
        _cameraWarningMessage.value = null

        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            loadVariantDetails(routeId, variant.id)
            if (isMapInForeground) {
                startRouteWideVehiclePolling(routeId, variant.id)
            }
        }
    }

    fun toggleRouteDirection() {
        val currentVar = _activeVariant.value ?: return
        val otherVar = _routeVariants.value.firstOrNull { it.id != currentVar.id } ?: return
        switchVariant(otherVar.id)
    }

    fun clearRoute() {
        routeJob?.cancel()
        stopRouteWidePollingAndClearFleet()
        focusedSelectedStopId = null
        focusedSelectedStopName = null
        _activeRoute.value = null
        _activeVariant.value = null
        _routeVariants.value = emptyList()
        _activeRoutePath.value = null
        _activeRouteStops.value = emptyList()
        _trackedVehicle.value = null
        _selectedVehiclePlate.value = null
        lastFollowTargetLatLng = null
        _cameraMode.value = VehicleCameraMode.IDLE
        _cameraWarningMessage.value = null
    }

    private fun stopRouteWidePollingAndClearFleet() {
        routePollingJob?.cancel()
        routePollingJob = null
        activePollingRouteVar = null
        _routeVehicles.value = emptyList()
        previousTelemetryByPlate.clear()
        previousInterpolatedByPlate.clear()
        _routeVehicleFeedStatus.value = PredictionFeedStatus.EMPTY
    }

    private suspend fun loadVariantDetails(routeId: Int, varId: Int) {
        _isLoading.value = true
        val localStops = repository.getStopsForRouteVar(routeId, varId).firstOrNull() ?: emptyList()
        val stops = if (localStops.isNotEmpty()) {
            localStops
        } else {
            repository.syncStopsForRouteVar(routeId, varId)
            repository.getStopsForRouteVar(routeId, varId).firstOrNull() ?: emptyList()
        }

        val localPath = repository.getRoutePath(routeId, varId).firstOrNull()
        val path = if (localPath != null && localPath.points.size >= 2) {
            localPath
        } else {
            repository.ensureRoutePath(routeId, varId)
            repository.getRoutePath(routeId, varId).firstOrNull()
        }

        _activeRoutePath.value = path
        _activeRouteStops.value = stops
        _isLoading.value = false
    }

    /**
     * Periodically discovers and positions all active buses operating along the active route variant.
     * Enforces bounded concurrency, monotonic GPS timestamps, HTTP 429 Retry-After, and lifecycle bounds.
     */
    fun startRouteWideVehiclePolling(routeId: Int, varId: Int) {
        val requestedKey = routeId to varId
        if (activePollingRouteVar == requestedKey && routePollingJob?.isActive == true) {
            return
        }
        routePollingJob?.cancel()
        activePollingRouteVar = requestedKey
        routePollingJob = viewModelScope.launch {
            var consecutiveFailures = 0
            while (isActive) {
                if (_routeVehicles.value.isEmpty()) {
                    _routeVehicleFeedStatus.value = PredictionFeedStatus.LOADING
                }

                val path = _activeRoutePath.value
                val stops = _activeRouteStops.value
                var nextDelayMs = basePollIntervalMs

                if (path != null && path.points.size >= 2 && stops.isNotEmpty()) {
                    val result = repository.fetchRouteVariantVehicles(
                        routeId = routeId,
                        varId = varId,
                        routeStops = stops,
                        previousVehicles = previousTelemetryByPlate
                    )

                    when (result) {
                        is NetworkResult.Success -> {
                            consecutiveFailures = 0
                            val vehiclesTelemetry = result.data.vehicles
                            val updatedModels = withContext(computeDispatcher) {
                                interpolateRouteFleet(
                                    vehiclesTelemetry = vehiclesTelemetry,
                                    path = path,
                                    stops = stops
                                )
                            }

                            _routeVehicles.value = updatedModels
                            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            _routeLastUpdatedTime.value = sdf.format(Date())

                            // Register active fleet into in-memory ActiveFleetRegistry for license plate search
                            val curRoute = _activeRoute.value
                            val curVar = _activeVariant.value
                            if (curRoute != null && curVar != null) {
                                com.hcmcbustracker.core.model.ActiveFleetRegistry.recordFleet(
                                    telemetries = vehiclesTelemetry,
                                    routeNo = curRoute.routeNo,
                                    routeName = curRoute.name,
                                    directionName = curVar.shortName
                                )
                            }

                            _routeVehicleFeedStatus.value = when {
                                updatedModels.isEmpty() -> PredictionFeedStatus.EMPTY
                                updatedModels.all { it.telemetry.freshnessState == GpsFreshnessState.STALE } -> PredictionFeedStatus.STALE
                                else -> PredictionFeedStatus.LIVE
                            }

                            // Synchronize selected vehicle bottom card and stable follow-camera triggers
                            val selectedPlate = _selectedVehiclePlate.value
                            if (selectedPlate != null) {
                                updatedModels.firstOrNull {
                                    it.telemetry.plateNumber.equals(selectedPlate, ignoreCase = true) ||
                                        it.telemetry.formattedPlate.equals(selectedPlate, ignoreCase = true)
                                }?.let { activeModel ->
                                    val updatedTracked = activeModel.toTrackedVehicleState()
                                    _trackedVehicle.value = updatedTracked
                                    if (_cameraMode.value == VehicleCameraMode.FOLLOW_VEHICLE) {
                                        if (isValidCoordinate(updatedTracked.lat, updatedTracked.lng)) {
                                            val prevTarget = lastFollowTargetLatLng
                                            val movedMeters = if (prevTarget != null) {
                                                VehicleInterpolationEngine.computeDistanceMeters(
                                                    LatLngPoint(prevTarget.latitude, prevTarget.longitude),
                                                    LatLngPoint(updatedTracked.lat, updatedTracked.lng)
                                                )
                                            } else {
                                                Double.MAX_VALUE
                                            }
                                            if (movedMeters >= FOLLOW_SIGNIFICANT_MOVE_METERS) {
                                                lastFollowTargetLatLng = LatLng(updatedTracked.lat, updatedTracked.lng)
                                                _cameraFollowRequestId.value += 1L
                                            }
                                        } else {
                                            _cameraWarningMessage.value =
                                                "Tọa độ xe ${updatedTracked.plateNumber} không hợp lệ"
                                        }
                                    }
                                }
                            }
                        }
                        is NetworkResult.Error -> {
                            consecutiveFailures++
                            _routeVehicleFeedStatus.value = if (_routeVehicles.value.isNotEmpty()) {
                                PredictionFeedStatus.STALE
                            } else {
                                PredictionFeedStatus.API_ERROR
                            }
                            val rand = backoffJitterProvider().coerceIn(0.0, 1.0)
                            nextDelayMs = if (result.retryAfterSeconds != null) {
                                val retryMs = (result.retryAfterSeconds!! * 1000L)
                                    .coerceIn(MIN_POLL_INTERVAL_MS, MAX_BACKOFF_INTERVAL_MS)
                                (retryMs + (rand * 950.0).toLong()).coerceAtMost(MAX_BACKOFF_INTERVAL_MS)
                            } else {
                                val factor = 1L shl (consecutiveFailures - 1).coerceAtMost(3)
                                val rawBackoff = (basePollIntervalMs * factor).coerceAtMost(MAX_BACKOFF_INTERVAL_MS)
                                val jitter = ((rand * 2.0 - 1.0) * 0.15 * rawBackoff).toLong()
                                (rawBackoff + jitter).coerceIn(MIN_POLL_INTERVAL_MS, MAX_BACKOFF_INTERVAL_MS)
                            }
                        }
                        is NetworkResult.Loading -> {}
                    }
                }

                delay(nextDelayMs)
            }
        }
    }

    /**
     * Interpolates every discovered bus along the route using the unmodified Phase 5/9 engine.
     * Avoids artificial animations if a bus's GPS timestamp `dts` remained unchanged.
     */
    private fun interpolateRouteFleet(
        vehiclesTelemetry: List<RouteVehicleTelemetry>,
        path: RoutePath,
        stops: List<BusStop>
    ): List<RouteActiveVehicleUiModel> {
        val stopById = stops.associateBy { it.id }
        val resultList = ArrayList<RouteActiveVehicleUiModel>(vehiclesTelemetry.size)
        val nowMillis = System.currentTimeMillis()

        for (rawTelemetry in vehiclesTelemetry) {
            val telemetry = rawTelemetry.copy(
                selectedStopId = focusedSelectedStopId,
                renderedAtMillis = nowMillis
            )
            val plateKey = telemetry.plateNumber.trim().uppercase()
            previousTelemetryByPlate[plateKey] = telemetry

            val stop = stopById[telemetry.predictionTargetStopId] ?: continue
            val prevPos = previousInterpolatedByPlate[plateKey]
            val isFrozen = telemetry.consecutiveUnchangedPolls >= 2

            // Hold coordinates steady when EBMS dts tick did not change, while transparently
            // updating gpsAgeSeconds, latencyCategory, arrivalStatus, and render timestamp.
            val interpolated = if (telemetry.isGpsTimestampUnchanged &&
                prevPos != null &&
                prevPos.gpsTimestampMillis == telemetry.gpsTimestampMillis &&
                prevPos.targetStopId == telemetry.predictionTargetStopId &&
                prevPos.selectedStopId == focusedSelectedStopId
            ) {
                val updatedAge = telemetry.computeGpsAgeSeconds(nowMillis)
                val updatedCategory = PositionCompensationEngine.classifyGpsLatency(updatedAge)
                val targetDistForStatus = prevPos.distanceToSelectedStopMeters
                    ?: prevPos.distanceToNextStopMeters
                    ?: prevPos.effectiveDistanceMeters
                val updatedArrivalStatus = PositionCompensationEngine.evaluateArrivalStatus(
                    distanceToTargetStopMeters = targetDistForStatus,
                    speedKmH = telemetry.speedKmH,
                    etaSeconds = prevPos.etaToNextStopSeconds,
                    gpsAgeSeconds = updatedAge,
                    isDirectionValid = true,
                    hasValidGeometry = true
                )
                prevPos.copy(
                    gpsAgeSeconds = updatedAge,
                    latencyCategory = updatedCategory,
                    arrivalStatus = updatedArrivalStatus,
                    updatedAtMillis = nowMillis
                )
            } else {
                VehicleInterpolationEngine.interpolatePosition(
                    polylinePoints = path.points,
                    targetStopLocation = LatLngPoint(stop.lat, stop.lng),
                    targetStopId = telemetry.predictionTargetStopId,
                    distanceToStopMeters = telemetry.rawDistanceMeters,
                    plateNumber = telemetry.plateNumber,
                    speedKmH = telemetry.speedKmH,
                    timestampIso = telemetry.gpsTimestampIso,
                    responseReceivedAtMillis = telemetry.responseReceivedAtMillis,
                    currentTimeMillis = nowMillis,
                    routeStops = stops,
                    previousPosition = if (telemetry.isGpsTimestampUnchanged) null else prevPos,
                    selectedStopId = focusedSelectedStopId,
                    rawEtaSeconds = telemetry.etaSeconds,
                    isDirectionValid = true,
                    allowAdaptiveExtendedProjection = true,
                    isGpsTimestampFrozen = isFrozen
                )
            }

            if (interpolated != null) {
                val renderedTelemetry = telemetry.copy(
                    renderedAtMillis = System.currentTimeMillis(),
                    latencyCategory = interpolated.latencyCategory
                )
                previousTelemetryByPlate[plateKey] = renderedTelemetry
                previousInterpolatedByPlate[plateKey] = interpolated
                resultList.add(RouteActiveVehicleUiModel(telemetry = renderedTelemetry, position = interpolated))
            }
        }
        return resultList
    }

    fun selectRouteVehicle(plate: String) {
        val match = _routeVehicles.value.firstOrNull {
            it.telemetry.plateNumber.equals(plate, ignoreCase = true) ||
                it.telemetry.formattedPlate.equals(plate, ignoreCase = true)
        } ?: return

        focusedSelectedStopId = null
        focusedSelectedStopName = null
        _selectedVehiclePlate.value = match.telemetry.plateNumber
        _selectedStop.value = null
        val tracked = match.toTrackedVehicleState()
        _trackedVehicle.value = tracked

        if (isValidCoordinate(tracked.lat, tracked.lng)) {
            _cameraWarningMessage.value = null
            lastFollowTargetLatLng = LatLng(tracked.lat, tracked.lng)
            _cameraMode.value = VehicleCameraMode.FOCUS_VEHICLE
            _cameraFocusRequestId.value += 1L
        } else {
            lastFollowTargetLatLng = null
            _cameraMode.value = VehicleCameraMode.IDLE
            _cameraWarningMessage.value = "Chưa có tọa độ hợp lệ cho xe ${tracked.plateNumber}"
        }
    }

    fun enableVehicleAutoFollow() {
        val tracked = _trackedVehicle.value ?: return
        if (isValidCoordinate(tracked.lat, tracked.lng)) {
            _cameraWarningMessage.value = null
            lastFollowTargetLatLng = LatLng(tracked.lat, tracked.lng)
            _cameraMode.value = VehicleCameraMode.FOLLOW_VEHICLE
            _cameraFocusRequestId.value += 1L
        } else {
            _cameraWarningMessage.value = "Chưa có tọa độ hợp lệ để theo dõi xe ${tracked.plateNumber}"
        }
    }

    fun toggleVehicleAutoFollow() {
        when (_cameraMode.value) {
            VehicleCameraMode.FOLLOW_VEHICLE -> _cameraMode.value = VehicleCameraMode.USER_CONTROLLED
            else -> enableVehicleAutoFollow()
        }
    }

    fun onUserMapGesture() {
        if (_cameraMode.value == VehicleCameraMode.FOLLOW_VEHICLE ||
            _cameraMode.value == VehicleCameraMode.FOCUS_VEHICLE
        ) {
            _cameraMode.value = VehicleCameraMode.USER_CONTROLLED
        }
    }

    fun clearCameraWarning() {
        _cameraWarningMessage.value = null
    }

    private fun isValidWgs84Coordinate(lat: Double, lng: Double): Boolean {
        return lat.isFinite() && lng.isFinite() &&
            lat in -90.0..90.0 && lng in -180.0..180.0 &&
            !(lat == 0.0 && lng == 0.0)
    }

    private fun isValidCoordinate(lat: Double, lng: Double): Boolean {
        return isValidWgs84Coordinate(lat, lng) &&
            lat in 8.0..13.0 && lng in 104.0..109.0
    }

    private fun RouteActiveVehicleUiModel.toTrackedVehicleState(): TrackedVehicleState {
        val activeSelectedId = focusedSelectedStopId ?: position.selectedStopId
        val activeSelectedName = focusedSelectedStopName ?: position.selectedStopName
        val displayStopId = activeSelectedId ?: position.nextStopId ?: telemetry.checkpointStopId
        val displayStopName = when {
            activeSelectedId != null -> activeSelectedName ?: position.nextStopName ?: telemetry.checkpointStopName
            else -> position.nextStopName ?: telemetry.checkpointStopName
        }
        val displayDistance = when {
            activeSelectedId != null && activeSelectedId == telemetry.predictionTargetStopId -> position.effectiveDistanceMeters
            activeSelectedId != null && position.distanceToSelectedStopMeters != null -> kotlin.math.abs(position.distanceToSelectedStopMeters)
            position.distanceToNextStopMeters != null -> position.distanceToNextStopMeters
            else -> position.effectiveDistanceMeters
        }
        val displayEta = if (activeSelectedId != null && activeSelectedId == telemetry.predictionTargetStopId) {
            position.etaToNextStopSeconds ?: telemetry.etaSeconds
        } else {
            position.etaToNextStopSeconds
        }
        val renderedAt = System.currentTimeMillis()
        return TrackedVehicleState(
            routeId = telemetry.routeId,
            varId = telemetry.varId,
            stopId = displayStopId,
            stopName = displayStopName,
            plateNumber = telemetry.formattedPlate,
            speedKmH = telemetry.speedKmH,
            rawDistanceMeters = telemetry.rawDistanceMeters,
            effectiveDistanceMeters = displayDistance,
            projectedDistanceMeters = position.projectedDistanceMeters,
            etaSeconds = displayEta,
            timestampIso = telemetry.gpsTimestampIso,
            gpsAgeSeconds = position.gpsAgeSeconds,
            isGpsTimestampUnchanged = telemetry.isGpsTimestampUnchanged,
            compensationStatus = position.compensationStatus,
            lat = position.lat,
            lng = position.lng,
            bearing = position.bearing,
            updatedAtMillis = renderedAt,
            routeNo = _activeRoute.value?.routeNo.orEmpty(),
            variantShortName = _activeVariant.value?.shortName.orEmpty(),
            selectedStopId = activeSelectedId,
            selectedStopName = activeSelectedName,
            distanceToSelectedStopMeters = position.distanceToSelectedStopMeters,
            nextStopId = position.nextStopId,
            nextStopName = position.nextStopName,
            distanceToNextStopMeters = position.distanceToNextStopMeters,
            etaToNextStopSeconds = position.etaToNextStopSeconds,
            checkpointStopId = telemetry.checkpointStopId,
            predictionTargetStopId = telemetry.predictionTargetStopId,
            gpsTimestampMillis = position.gpsTimestampMillis ?: telemetry.gpsTimestampMillis,
            requestStartedAtMillis = telemetry.requestStartedAtMillis,
            responseReceivedAtMillis = telemetry.responseReceivedAtMillis,
            predictionUpdatedAtMillis = telemetry.predictionUpdatedAtMillis,
            renderedAtMillis = renderedAt,
            latencyCategory = position.latencyCategory,
            arrivalStatus = position.arrivalStatus,
            positionSource = position.positionSource,
            hasValidHeading = position.hasValidHeading,
            isAgedAdaptiveProjection = position.isAgedAdaptiveProjection
        )
    }

    fun updateUserLocation(lat: Double, lng: Double, bearing: Float? = null, accuracyMeters: Float? = null) {
        if (!isValidWgs84Coordinate(lat, lng)) {
            _userLocation.value = null
            _userBearing.value = null
            _userAccuracyMeters.value = null
            _isUserLocationUnavailable.value = true
            return
        }
        _userLocation.value = LatLng(lat, lng)
        _userBearing.value = bearing
        _userAccuracyMeters.value = accuracyMeters
        _isUserLocationUnavailable.value = false
    }

    fun setLocationPermissionGranted(granted: Boolean) {
        _isLocationPermissionGranted.value = granted
        if (!granted) {
            _userLocation.value = null
            _userBearing.value = null
            _userAccuracyMeters.value = null
            _isUserLocationUnavailable.value = true
        }
    }

    fun markUserLocationUnavailable() {
        _userLocation.value = null
        _userBearing.value = null
        _userAccuracyMeters.value = null
        _isUserLocationUnavailable.value = true
    }

    fun toggleStopsLayer() {
        _showStopsLayer.value = !_showStopsLayer.value
    }

    fun findNearbyStops(radiusMeters: Double = 1500.0) {
        val loc = _userLocation.value ?: return
        viewModelScope.launch {
            val latDelta = (radiusMeters / 111320.0) * 1.2
            val lngDelta = (radiusMeters / (111320.0 * kotlin.math.cos(Math.toRadians(loc.latitude)))) * 1.2

            repository.getStopsInBounds(
                minLat = loc.latitude - latDelta,
                maxLat = loc.latitude + latDelta,
                minLng = loc.longitude - lngDelta,
                maxLng = loc.longitude + lngDelta
            ).firstOrNull()?.let { stops ->
                val list = stops.mapNotNull { stop ->
                    if (stop.lat == 0.0 && stop.lng == 0.0) return@mapNotNull null
                    val dist = VehicleInterpolationEngine.computeDistanceMeters(
                        LatLngPoint(loc.latitude, loc.longitude),
                        LatLngPoint(stop.lat, stop.lng)
                    )
                    if (dist <= radiusMeters) NearbyBusStop(stop, dist) else null
                }.sortedBy { it.distanceMeters }.take(10)

                _nearbyStops.value = list
            }
        }
    }

    fun clearNearbyStops() {
        _nearbyStops.value = emptyList()
    }

    fun trackVehicle(
        routeId: Int,
        varId: Int,
        stopId: Int,
        plate: String,
        distanceMeters: Double,
        speedKmH: Double,
        timestampIso: String? = null,
        etaSeconds: Int? = null
    ) {
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            val initiatedAt = System.currentTimeMillis()
            _isLoading.value = true
            focusedSelectedStopId = stopId
            repository.ensureInitialStaticDatasetLoaded()

            var route = repository.getRouteById(routeId).firstOrNull()
            var variants = repository.getVariantsForRoute(routeId).firstOrNull() ?: emptyList()
            if (variants.isEmpty()) {
                repository.syncRouteDetailAndVariants(routeId)
                route = repository.getRouteById(routeId).firstOrNull()
                variants = repository.getVariantsForRoute(routeId).firstOrNull() ?: emptyList()
            }

            _activeRoute.value = route
            _routeVariants.value = variants
            val targetVariant = variants.firstOrNull { it.id == varId }
                ?: variants.firstOrNull { it.isOutbound }
                ?: variants.firstOrNull()
            _activeVariant.value = targetVariant

            val activeVarId = targetVariant?.id ?: varId
            loadVariantDetails(routeId, activeVarId)

            val path = _activeRoutePath.value
            val stops = _activeRouteStops.value
            val stop = repository.getStopById(stopId).firstOrNull() ?: stops.firstOrNull { it.id == stopId }
            focusedSelectedStopName = stop?.name

            val hasValidPathGeometry = path != null &&
                path.points.size >= 2 &&
                path.points.all { isValidCoordinate(it.lat, it.lng) }
            val hasValidStopGeometry = stop != null && isValidCoordinate(stop.lat, stop.lng)

            if (hasValidPathGeometry && hasValidStopGeometry) {
                val validPath = path!!
                val validStop = stop!!
                val stopPoint = LatLngPoint(validStop.lat, validStop.lng)
                val prevVehicle = _trackedVehicle.value
                val previousPos = if (prevVehicle != null && prevVehicle.plateNumber == plate) {
                    InterpolatedVehiclePosition(
                        lat = prevVehicle.lat,
                        lng = prevVehicle.lng,
                        bearing = prevVehicle.bearing,
                        plateNumber = prevVehicle.plateNumber,
                        speedKmH = prevVehicle.speedKmH,
                        targetStopId = prevVehicle.predictionTargetStopId,
                        cumulativeDistanceMeters = 0.0,
                        updatedAtMillis = prevVehicle.updatedAtMillis,
                        hasValidHeading = prevVehicle.hasValidHeading
                    )
                } else null

                val nowMs = System.currentTimeMillis()
                val interpolated = VehicleInterpolationEngine.interpolatePosition(
                    polylinePoints = validPath.points,
                    targetStopLocation = stopPoint,
                    targetStopId = stopId,
                    distanceToStopMeters = distanceMeters,
                    plateNumber = plate,
                    speedKmH = speedKmH,
                    timestampIso = timestampIso,
                    responseReceivedAtMillis = nowMs,
                    currentTimeMillis = nowMs,
                    routeStops = stops,
                    previousPosition = previousPos,
                    selectedStopId = stopId,
                    rawEtaSeconds = etaSeconds,
                    isDirectionValid = true,
                    allowAdaptiveExtendedProjection = true
                )

                if (interpolated != null && isValidCoordinate(interpolated.lat, interpolated.lng)) {
                    val renderedAt = System.currentTimeMillis()
                    _selectedVehiclePlate.value = plate
                    _trackedVehicle.value = TrackedVehicleState(
                        routeId = routeId,
                        varId = activeVarId,
                        stopId = stopId,
                        stopName = validStop.name,
                        plateNumber = plate,
                        speedKmH = speedKmH,
                        rawDistanceMeters = distanceMeters,
                        effectiveDistanceMeters = interpolated.effectiveDistanceMeters,
                        projectedDistanceMeters = interpolated.projectedDistanceMeters,
                        etaSeconds = etaSeconds ?: interpolated.etaToNextStopSeconds,
                        timestampIso = timestampIso,
                        gpsAgeSeconds = interpolated.gpsAgeSeconds,
                        compensationStatus = interpolated.compensationStatus,
                        lat = interpolated.lat,
                        lng = interpolated.lng,
                        bearing = interpolated.bearing,
                        updatedAtMillis = renderedAt,
                        routeNo = route?.routeNo.orEmpty(),
                        variantShortName = targetVariant?.shortName.orEmpty(),
                        selectedStopId = stopId,
                        selectedStopName = validStop.name,
                        distanceToSelectedStopMeters = interpolated.distanceToSelectedStopMeters ?: interpolated.effectiveDistanceMeters,
                        nextStopId = interpolated.nextStopId,
                        nextStopName = interpolated.nextStopName,
                        distanceToNextStopMeters = interpolated.distanceToNextStopMeters,
                        etaToNextStopSeconds = interpolated.etaToNextStopSeconds,
                        checkpointStopId = stopId,
                        predictionTargetStopId = stopId,
                        gpsTimestampMillis = interpolated.gpsTimestampMillis,
                        requestStartedAtMillis = initiatedAt,
                        responseReceivedAtMillis = nowMs,
                        predictionUpdatedAtMillis = nowMs,
                        renderedAtMillis = renderedAt,
                        latencyCategory = interpolated.latencyCategory,
                        arrivalStatus = interpolated.arrivalStatus,
                        positionSource = interpolated.positionSource,
                        hasValidHeading = interpolated.hasValidHeading,
                        isAgedAdaptiveProjection = interpolated.isAgedAdaptiveProjection
                    )
                    _selectedStop.value = validStop
                    _cameraWarningMessage.value = null
                    lastFollowTargetLatLng = LatLng(interpolated.lat, interpolated.lng)
                    _cameraMode.value = VehicleCameraMode.FOCUS_VEHICLE
                    _cameraFocusRequestId.value += 1L
                } else {
                    _selectedVehiclePlate.value = null
                    _trackedVehicle.value = null
                    lastFollowTargetLatLng = null
                    _cameraMode.value = VehicleCameraMode.IDLE
                    _cameraWarningMessage.value = "Không thể xác định tọa độ hợp lệ trên tuyến cho xe $plate"
                }
            } else {
                _selectedVehiclePlate.value = null
                _trackedVehicle.value = null
                lastFollowTargetLatLng = null
                _cameraMode.value = VehicleCameraMode.IDLE
                _cameraWarningMessage.value = "Thiếu dữ liệu lộ trình hoặc tọa độ trạm hợp lệ cho xe $plate"
            }
            _isLoading.value = false
            if (isMapInForeground) {
                startRouteWideVehiclePolling(routeId, activeVarId)
            }
        }
    }

    fun clearTrackedVehicle() {
        focusedSelectedStopId = null
        focusedSelectedStopName = null
        _trackedVehicle.value = null
        _selectedVehiclePlate.value = null
        lastFollowTargetLatLng = null
        _cameraMode.value = VehicleCameraMode.IDLE
        _cameraWarningMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        routePollingJob?.cancel()
        routeJob?.cancel()
        stopsJob?.cancel()
        cameraDebounceJob?.cancel()
    }
}
