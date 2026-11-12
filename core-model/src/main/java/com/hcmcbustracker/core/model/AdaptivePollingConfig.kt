package com.hcmcbustracker.core.model

/**
 * Centralized configuration for adaptive bounded concurrent polling, request coalescing,
 * and rate-limit backoff/jitter controls.
 *
 * Note: [maxConcurrency] = 4 is strictly a client-side technical ceiling to protect stability,
 * NOT an assertion that the remote EBMS host endorses 4 simultaneous requests.
 */
data class AdaptivePollingConfig(
    val initialConcurrency: Int = 1,
    val minConcurrency: Int = 1,
    val maxConcurrency: Int = 4,
    val increaseSuccessWindow: Int = 6,
    val rttSpikeThresholdMs: Long = 1_500L,
    val errorRateWindowSize: Int = 10,
    val errorRateDowngradeThreshold: Double = 0.25,
    val default429BackoffMs: Long = 30_000L,
    val min429BackoffMs: Long = 5_000L,
    val max429BackoffMs: Long = 300_000L,
    val baseRetryBackoffMs: Long = 10_000L,
    val maxRetryBackoffMs: Long = 120_000L,
    val backoffJitterFraction: Double = 0.20,
    val shortLivedCacheTtlMs: Long = 5_000L,
    val requestTimeoutMs: Long = 8_000L
) {
    init {
        require(minConcurrency >= 1) { "minConcurrency must be >= 1" }
        require(maxConcurrency >= minConcurrency) { "maxConcurrency must be >= minConcurrency" }
        require(initialConcurrency in minConcurrency..maxConcurrency) {
            "initialConcurrency must be within [minConcurrency..maxConcurrency]"
        }
        require(increaseSuccessWindow >= 1) { "increaseSuccessWindow must be >= 1" }
        require(backoffJitterFraction in 0.0..0.5) { "backoffJitterFraction must be in [0.0..0.5]" }
    }

    companion object {
        val DEFAULT = AdaptivePollingConfig()
    }
}

/**
 * Distinguishes data origin for telemetry merge precedence and UI provenance transparency.
 */
enum class GpsSourceType {
    EBMS_PREDICTION_REST,
    PARTNER_DIRECT_GPS,
    UNKNOWN
}

/**
 * Explicit capability and trust metadata descriptor for a registered GPS/prediction endpoint.
 */
data class EndpointCapability(
    val sourceType: GpsSourceType,
    val endpointId: String,
    val supportsDirectLatLng: Boolean,
    val supportsDirectHeading: Boolean,
    val minSafePollIntervalMs: Long,
    val baseTrustRank: Int
)

/**
 * Registry of known endpoint capabilities. Only verified public endpoints or explicitly
 * configured partner feeds should be registered here.
 */
object EndpointCapabilityRegistry {

    const val ENDPOINT_PREDICT_BY_STOP_ID = "ebms_predict_by_stop_id"
    const val ENDPOINT_PREDICT_BY_STOP_CODE = "ebms_predict_by_stop_code"
    const val ENDPOINT_PREDICT_NEXT_STOPS = "ebms_predict_next_stops"

    private val entries: Map<String, EndpointCapability> = mapOf(
        ENDPOINT_PREDICT_BY_STOP_ID to EndpointCapability(
            sourceType = GpsSourceType.EBMS_PREDICTION_REST,
            endpointId = ENDPOINT_PREDICT_BY_STOP_ID,
            supportsDirectLatLng = false,
            supportsDirectHeading = false,
            minSafePollIntervalMs = 10_000L,
            baseTrustRank = 1
        ),
        ENDPOINT_PREDICT_BY_STOP_CODE to EndpointCapability(
            sourceType = GpsSourceType.EBMS_PREDICTION_REST,
            endpointId = ENDPOINT_PREDICT_BY_STOP_CODE,
            supportsDirectLatLng = false,
            supportsDirectHeading = false,
            minSafePollIntervalMs = 10_000L,
            baseTrustRank = 1
        ),
        ENDPOINT_PREDICT_NEXT_STOPS to EndpointCapability(
            sourceType = GpsSourceType.EBMS_PREDICTION_REST,
            endpointId = ENDPOINT_PREDICT_NEXT_STOPS,
            supportsDirectLatLng = false,
            supportsDirectHeading = false,
            minSafePollIntervalMs = 10_000L,
            baseTrustRank = 1
        )
    )

    fun resolve(endpointId: String): EndpointCapability {
        return entries[endpointId] ?: EndpointCapability(
            sourceType = GpsSourceType.UNKNOWN,
            endpointId = endpointId,
            supportsDirectLatLng = false,
            supportsDirectHeading = false,
            minSafePollIntervalMs = 15_000L,
            baseTrustRank = 0
        )
    }

    fun allCapabilities(): Collection<EndpointCapability> = entries.values
}

/**
 * Lightweight adapter contract allowing future verified GPS feeds to integrate alongside EBMS REST
 * without modifying [PositionCompensationEngine] or [VehicleInterpolationEngine].
 */
interface GpsSourceAdapter {
    val capability: EndpointCapability
    suspend fun fetchStopObservations(stopId: Int): NetworkResult<List<Prediction>>
}

/**
 * Operational non-secret telemetry metrics from the polling coordinator for dev diagnostics.
 * Explicitly separates HTTP latency, polling cycle duration, processing delay, and GPS data age.
 */
data class PollingDiagnosticsSnapshot(
    val currentConcurrency: Int = 1,
    val maxConcurrency: Int = 4,
    val requestsInFlight: Int = 0,
    val requestQueueLength: Int = 0,
    val lastHttpRttMs: Long = 0L,
    val avgHttpRttMs: Long = 0L,
    val http429Count: Long = 0L,
    val http5xxCount: Long = 0L,
    val timeoutCount: Long = 0L,
    val retryCount: Long = 0L,
    val deduplicatedRequestCount: Long = 0L,
    val cacheHitCount: Long = 0L,
    val totalRequestsExecuted: Long = 0L,
    val lastCycleDurationMs: Long = 0L,
    val lastProcessingDelayMs: Long = 0L,
    val minGpsAgeSeconds: Long? = null,
    val medianGpsAgeSeconds: Long? = null,
    val maxGpsAgeSeconds: Long? = null,
    val newDtsPercentage: Double = 0.0,
    val isInCooldown: Boolean = false
)

/**
 * Target route-variant descriptor for coordinated multi-route polling sweeps.
 */
data class RouteVariantPollingTarget(
    val routeId: Int,
    val varId: Int,
    val routeStops: List<BusStop> = emptyList()
)
