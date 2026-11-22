package com.hcmcbustracker.feature.prediction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcmcbustracker.core.database.repository.BusRepository
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.PositionCompensationConfig
import com.hcmcbustracker.core.model.Prediction
import com.hcmcbustracker.core.model.PredictionFeedStatus
import com.hcmcbustracker.core.network.monitor.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class PredictionUiState(
    val stop: BusStop? = null,
    val predictions: List<Prediction> = emptyList(),
    val isFavorite: Boolean = false,
    val status: PredictionFeedStatus = PredictionFeedStatus.LOADING,
    val secondsAgo: Int = 0,
    val lastResponseTime: String? = null,
    val expandedNextStops: Map<String, List<Prediction>> = emptyMap(),
    val errorMessage: String? = null,
    val isRefreshing: Boolean = false,
    val consecutiveFailures: Int = 0,
    val isGpsTimestampUnchanged: Boolean = false
) {
    val isLoading: Boolean get() = status == PredictionFeedStatus.LOADING || isRefreshing
    val isLive: Boolean get() = status == PredictionFeedStatus.LIVE
    val isStale: Boolean get() = status == PredictionFeedStatus.STALE
    val isOffline: Boolean get() = status == PredictionFeedStatus.OFFLINE
    val isEmpty: Boolean get() = status == PredictionFeedStatus.EMPTY
    val isApiError: Boolean get() = status == PredictionFeedStatus.API_ERROR
}

@HiltViewModel
class PredictionViewModel @Inject constructor(
    private val repository: BusRepository,
    private val networkMonitor: NetworkMonitor,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        const val MIN_POLL_INTERVAL_MS = 10_000L
        const val DEFAULT_POLL_INTERVAL_MS = 15_000L
        const val MAX_BACKOFF_INTERVAL_MS = 120_000L
        const val STALE_THRESHOLD_SECONDS = 45
    }

    val stopId: Int = checkNotNull(savedStateHandle["stopId"])

    private val _predictions = MutableStateFlow<List<Prediction>>(emptyList())
    private val _status = MutableStateFlow(PredictionFeedStatus.LOADING)
    private val _secondsAgo = MutableStateFlow(0)
    private val _lastResponseTime = MutableStateFlow<String?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    private val _consecutiveFailures = MutableStateFlow(0)
    private val _expandedNextStops = MutableStateFlow<Map<String, List<Prediction>>>(emptyMap())
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isGpsTimestampUnchanged = MutableStateFlow(false)

    private var basePollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS
    private var lastRetryAfterMs: Long? = null
    internal var jitterProvider: () -> Double = { kotlin.random.Random.Default.nextDouble() }

    private val stopFlow = repository.getStopById(stopId)
    private val isFavoriteFlow = repository.isStopFavorite(stopId)

    private var pollingJob: Job? = null
    private var timerJob: Job? = null
    private var refreshJob: Job? = null
    private val queryMutex = Mutex()

    private data class PollingStatus(
        val status: PredictionFeedStatus,
        val secondsAgo: Int,
        val lastResponseTime: String?,
        val expandedNextStops: Map<String, List<Prediction>>,
        val errorMessage: String?,
        val isRefreshing: Boolean,
        val consecutiveFailures: Int
    )

    private val pollingStatusFlow = combine(
        _status,
        _secondsAgo,
        _lastResponseTime,
        _expandedNextStops,
        _errorMessage
    ) { status, secondsAgo, lastTime, nextStops, error ->
        Triple(status, secondsAgo, Triple(lastTime, nextStops, error))
    }

    val uiState: StateFlow<PredictionUiState> = combine(
        stopFlow,
        _predictions,
        isFavoriteFlow,
        pollingStatusFlow,
        _isRefreshing,
        _consecutiveFailures,
        _isGpsTimestampUnchanged
    ) { args: Array<Any?> ->
        val stop = args[0] as? BusStop
        @Suppress("UNCHECKED_CAST")
        val predictions = args[1] as? List<Prediction> ?: emptyList()
        val isFav = args[2] as? Boolean ?: false
        @Suppress("UNCHECKED_CAST")
        val triple = args[3] as Triple<PredictionFeedStatus, Int, Triple<String?, Map<String, List<Prediction>>, String?>>
        val refreshing = args[4] as? Boolean ?: false
        val failures = args[5] as? Int ?: 0
        val unchanged = args[6] as? Boolean ?: false

        val status = triple.first
        val secondsAgo = triple.second
        val innerTriple = triple.third
        val lastResponseTime = innerTriple.first
        val nextStops = innerTriple.second
        val error = innerTriple.third

        PredictionUiState(
            stop = stop,
            predictions = predictions,
            isFavorite = isFav,
            status = status,
            secondsAgo = secondsAgo,
            lastResponseTime = lastResponseTime,
            expandedNextStops = nextStops,
            errorMessage = error,
            isRefreshing = refreshing,
            consecutiveFailures = failures,
            isGpsTimestampUnchanged = unchanged
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PredictionUiState(status = PredictionFeedStatus.LOADING)
    )

    init {
        // Observe network state: auto-refresh when connectivity is restored
        viewModelScope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                if (isOnline && _status.value == PredictionFeedStatus.OFFLINE) {
                    refresh()
                }
            }
        }
        // Observe user polling interval safely clamped to [10s..120s]
        viewModelScope.launch {
            repository.getUserPreferences().collect { prefs ->
                basePollIntervalMs = (prefs.pollingIntervalSeconds * 1000L).coerceIn(MIN_POLL_INTERVAL_MS, MAX_BACKOFF_INTERVAL_MS)
            }
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            executePredictionQuery()
        }
    }

    fun startRealtimePolling() {
        stopRealtimePolling()

        // 1. Polling Coroutine with Exponential Backoff & HTTP 429 Retry-After
        pollingJob = viewModelScope.launch {
            while (isActive) {
                if (!networkMonitor.isCurrentlyOnline) {
                    _status.value = PredictionFeedStatus.OFFLINE
                    _errorMessage.value = "Thiết bị mất kết nối mạng internet"
                    _isRefreshing.value = false
                    delay(5000L)
                    continue
                }

                executePredictionQuery()

                val nextDelay = calculateNextPollDelay(_consecutiveFailures.value)
                delay(nextDelay)
            }
        }

        // 2. Continuous time-ago ticker (every 1 second)
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val newSeconds = _secondsAgo.value + 1
                _secondsAgo.value = newSeconds

                // Automatically transition LIVE to STALE if no new data received within 45s
                if (newSeconds >= STALE_THRESHOLD_SECONDS && _status.value == PredictionFeedStatus.LIVE) {
                    _status.value = PredictionFeedStatus.STALE
                }
            }
        }
    }

    private suspend fun executePredictionQuery() = queryMutex.withLock {
        _isRefreshing.value = true
        val result = repository.fetchRealtimePrediction(stopId)
        _isRefreshing.value = false

        if (result.isSuccess) {
            val incomingList = result.getOrDefault(emptyList())
            val mergedResult = mergePredictionsMonotonically(_predictions.value, incomingList)
            _predictions.value = mergedResult.first
            _isGpsTimestampUnchanged.value = mergedResult.second

            _secondsAgo.value = 0
            _consecutiveFailures.value = 0
            _errorMessage.value = null
            lastRetryAfterMs = null

            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            _lastResponseTime.value = sdf.format(Date())

            val hasVehicles = mergedResult.first.any { it.vehicles.isNotEmpty() }
            if (hasVehicles) {
                // Register observed vehicles into ActiveFleetRegistry for license plate search
                for (pred in mergedResult.first) {
                    for (veh in pred.vehicles) {
                        val parsedGps = com.hcmcbustracker.core.model.PositionCompensationEngine.parseGpsTimestampMillis(veh.timestampIso)
                        val telemetry = com.hcmcbustracker.core.model.RouteVehicleTelemetry(
                            routeId = pred.routeId,
                            varId = pred.varId ?: 1,
                            plateNumber = veh.plateNumber,
                            formattedPlate = veh.formattedPlate,
                            checkpointStopId = stopId,
                            checkpointStopName = pred.stopName.orEmpty(),
                            rawDistanceMeters = veh.distanceMeters,
                            speedKmH = veh.speedKmH,
                            etaSeconds = veh.etaSeconds,
                            gpsTimestampIso = veh.timestampIso.orEmpty(),
                            gpsTimestampMillis = parsedGps,
                            requestStartedAtMillis = System.currentTimeMillis() - 100,
                            responseReceivedAtMillis = System.currentTimeMillis(),
                            predictionReceivedAtMillis = System.currentTimeMillis(),
                            statusCode = veh.status ?: 0
                        )
                        com.hcmcbustracker.core.model.ActiveFleetRegistry.recordVehicle(
                            telemetry = telemetry,
                            routeNo = pred.routeNo,
                            routeName = pred.routeName,
                            directionName = pred.destinationName ?: ""
                        )
                    }
                }

                val allVehicles = mergedResult.first.flatMap { it.vehicles }
                val allStaleGps = allVehicles.all { it.isGpsStale() }
                _status.value = if (allStaleGps) {
                    PredictionFeedStatus.STALE
                } else {
                    PredictionFeedStatus.LIVE
                }
            } else {
                _status.value = PredictionFeedStatus.EMPTY
            }
        } else {
            val failures = _consecutiveFailures.value + 1
            _consecutiveFailures.value = failures
            val errObj = result as? com.hcmcbustracker.core.model.NetworkResult.Error
            _errorMessage.value = errObj?.message ?: "Lỗi kết nối máy chủ"
            lastRetryAfterMs = errObj?.retryAfterSeconds?.let { (it * 1000L).coerceAtLeast(MIN_POLL_INTERVAL_MS) }

            if (_predictions.value.isEmpty()) {
                _status.value = PredictionFeedStatus.API_ERROR
            } else {
                _status.value = PredictionFeedStatus.STALE
            }
        }
    }

    /**
     * Prevents out-of-order delayed responses with older `dts` timestamps from overwriting newer records,
     * and detects when EBMS returned the exact same GPS timestamps across consecutive polls.
     */
    private fun mergePredictionsMonotonically(
        current: List<Prediction>,
        incoming: List<Prediction>
    ): Pair<List<Prediction>, Boolean> {
        if (current.isEmpty() || incoming.isEmpty()) {
            return incoming to false
        }

        val currentVehicleMap = HashMap<String, com.hcmcbustracker.core.model.ArrivalVehicle>()
        for (pred in current) {
            for (v in pred.vehicles) {
                currentVehicleMap["${pred.routeId}_${pred.varId}_${v.plateNumber.trim().uppercase()}"] = v
            }
        }

        var unchangedCount = 0
        var totalCompared = 0

        val mergedPredictions = incoming.map { pred ->
            val updatedVehicles = pred.vehicles.map { inc ->
                val key = "${pred.routeId}_${pred.varId}_${inc.plateNumber.trim().uppercase()}"
                val prev = currentVehicleMap[key]
                val prevTs = prev?.gpsTimestampMillis
                val rawIncTs = inc.gpsTimestampMillis
                val isFuture = rawIncTs != null &&
                    rawIncTs > System.currentTimeMillis() + PositionCompensationConfig.DEFAULT.futureTimestampToleranceMs
                val incTs = if (isFuture) null else rawIncTs

                if (prev != null && prevTs != null) {
                    if (incTs == null) {
                        // Do not overwrite a valid GPS record with an unparseable or anomalous future timestamp
                        prev
                    } else {
                        totalCompared++
                        when {
                            incTs < prevTs -> {
                                // Reject stale rollback from delayed network packet
                                prev
                            }
                            incTs == prevTs -> {
                                unchangedCount++
                                inc
                            }
                            else -> inc
                        }
                    }
                } else {
                    inc
                }
            }
            pred.copy(vehicles = updatedVehicles)
        }

        val allUnchanged = totalCompared > 0 && unchangedCount == totalCompared
        return mergedPredictions to allUnchanged
    }

    private fun calculateNextPollDelay(consecutiveFailures: Int): Long {
        val rand = jitterProvider().coerceIn(0.0, 1.0)
        lastRetryAfterMs?.let { retryAfter ->
            lastRetryAfterMs = null
            // Preserve exact Retry-After floor + small sub-second jitter so retries desynchronize safely
            val subSecondJitter = (rand * 950.0).toLong()
            return (retryAfter + subSecondJitter).coerceAtMost(MAX_BACKOFF_INTERVAL_MS)
        }
        if (consecutiveFailures <= 1) return basePollIntervalMs
        val factor = 1L shl (consecutiveFailures - 1).coerceAtMost(3)
        val rawBackoff = (basePollIntervalMs * factor).coerceAtMost(MAX_BACKOFF_INTERVAL_MS)
        val jitterOffset = ((rand * 2.0 - 1.0) * 0.15 * rawBackoff).toLong()
        return (rawBackoff + jitterOffset).coerceIn(MIN_POLL_INTERVAL_MS, MAX_BACKOFF_INTERVAL_MS)
    }

    fun stopRealtimePolling() {
        pollingJob?.cancel()
        timerJob?.cancel()
        _isRefreshing.value = false
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            repository.toggleFavoriteStop(stopId)
        }
    }

    fun toggleNextStops(prediction: Prediction) {
        val key = "${prediction.routeId}_${prediction.varId}"
        if (_expandedNextStops.value.containsKey(key)) {
            _expandedNextStops.value = _expandedNextStops.value - key
            return
        }

        viewModelScope.launch {
            val result = repository.fetchPredictNextStops(
                routeId = prediction.routeId,
                varId = prediction.varId,
                stopId = stopId,
                maxNextStops = 3
            )
            if (result.isSuccess) {
                val list = result.getOrDefault(emptyList())
                _expandedNextStops.value = _expandedNextStops.value + (key to list)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopRealtimePolling()
    }
}
