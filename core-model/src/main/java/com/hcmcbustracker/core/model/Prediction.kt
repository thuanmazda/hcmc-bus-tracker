package com.hcmcbustracker.core.model

import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.roundToInt
import java.util.Locale

/**
 * Domain model representing real-time arrival predictions for a specific route passing a stop.
 */
@Serializable
data class Prediction(
    val routeId: Int,
    val routeNo: String,
    val routeName: String,
    val stopId: Int,
    val stopName: String,
    val varId: Int,
    val destinationName: String,
    val vehicles: List<ArrivalVehicle>
)

/**
 * Status categorization of the prediction feed for the current stop.
 */
enum class PredictionFeedStatus {
    LOADING,
    LIVE,
    STALE,
    OFFLINE,
    EMPTY,
    API_ERROR
}

/**
 * Domain model representing an approaching vehicle.
 */
@Serializable
data class ArrivalVehicle(
    val plateNumber: String,
    val etaSeconds: Int,
    val distanceMeters: Double,
    val speedKmH: Double,
    val timestampIso: String,
    val status: Int,
    val responseReceivedTimeMillis: Long = System.currentTimeMillis(),
    val requestStartedAtMillis: Long = responseReceivedTimeMillis,
    val predictionReceivedAtMillis: Long = responseReceivedTimeMillis
) {
    /**
     * Parsed GPS timestamp epoch milliseconds from EBMS `dts`.
     */
    val gpsTimestampMillis: Long?
        get() = PositionCompensationEngine.parseGpsTimestampMillis(timestampIso)
    /**
     * Estimated arrival time in minutes (minimum 1, e.g. "1 phút" if under 60s).
     */
    val etaMinutes: Int
        get() = ceil(etaSeconds / 60.0).toInt().coerceAtLeast(1)

    /**
     * Precise and user-friendly ETA display:
     * - Under 60s: "Sắp đến (< 1 phút)"
     * - Non-zero seconds: "4 phút 15 giây"
     * - Even minutes: "5 phút"
     */
    val formattedEta: String
        get() {
            if (etaSeconds <= 0) return "--"
            if (etaSeconds < 60) return "Sắp đến (< 1 phút)"
            val minutes = etaSeconds / 60
            val seconds = etaSeconds % 60
            return if (seconds == 0) {
                "$minutes phút"
            } else {
                "$minutes phút $seconds giây"
            }
        }

    val isApproaching: Boolean
        get() = etaSeconds in 1..60 || distanceMeters < 300.0

    /**
     * Human-readable distance: "520m" or "1.4 km"
     */
    val formattedDistance: String
        get() = if (distanceMeters < 1000) {
            "${distanceMeters.roundToInt()}m"
        } else {
            String.format(Locale.US, "%.1f km", distanceMeters / 1000.0)
        }

    val formattedSpeed: String
        get() = "${speedKmH.roundToInt()} km/h"

    /**
     * Formatted license plate (e.g. "50E20651" -> "50E-206.51")
     */
    val formattedPlate: String
        get() {
            val p = plateNumber.trim().uppercase()
            return if (p.length >= 8) {
                "${p.substring(0, 3)}-${p.substring(3, 6)}.${p.substring(6)}"
            } else {
                p
            }
        }

    /**
     * Extracts time part from GPS timestamp ISO (e.g. "2026-11-09T21:13:29+07:00" -> "21:13:29")
     */
    val gpsTimeFormatted: String
        get() {
            if (timestampIso.isBlank()) return "--"
            return try {
                if (timestampIso.contains("T")) {
                    timestampIso.substringAfter("T").take(8)
                } else {
                    timestampIso.takeLast(8)
                }
            } catch (e: Exception) {
                timestampIso
            }
        }

    /**
     * Formatted time when response was received by the application (e.g. "09:30:15")
     */
    val responseTimeFormatted: String
        get() {
            return try {
                val date = java.util.Date(responseReceivedTimeMillis)
                val sdf = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                sdf.format(date)
            } catch (e: Exception) {
                "--"
            }
        }

    /**
     * Calculates age of GPS packet in seconds compared to [currentMillis].
     * Reuses canonical parser so ISO offsets, UTC, and epoch seconds/millis remain uniform.
     */
    fun getGpsAgeSeconds(currentMillis: Long = System.currentTimeMillis()): Long {
        val gpsMillis = PositionCompensationEngine.parseGpsTimestampMillis(timestampIso) ?: return -1L
        if (gpsMillis > currentMillis + PositionCompensationConfig.DEFAULT.futureTimestampToleranceMs) {
            return -1L
        }
        return maxOf(0L, (currentMillis - gpsMillis) / 1000L)
    }

    /**
     * Classifies GPS age into Phase 11 freshness tiers.
     */
    fun getGpsLatencyCategory(currentMillis: Long = System.currentTimeMillis()): GpsLatencyCategory {
        return PositionCompensationEngine.classifyGpsLatency(getGpsAgeSeconds(currentMillis))
    }

    /**
     * Returns true if the GPS data packet is stale (> 3 minutes / 180 seconds old, or unknown).
     */
    fun isGpsStale(currentMillis: Long = System.currentTimeMillis()): Boolean {
        val age = getGpsAgeSeconds(currentMillis)
        return age < 0L || age > PositionCompensationEngine.STALE_THRESHOLD_SECONDS
    }
}
