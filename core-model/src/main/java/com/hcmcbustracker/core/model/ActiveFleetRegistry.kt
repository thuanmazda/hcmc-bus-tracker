package com.hcmcbustracker.core.model

import java.util.concurrent.ConcurrentHashMap

/**
 * Summary record of an active bus vehicle currently operating in the HCMC transit network.
 * Derived strictly from legitimate EBMS arrival telematics without any unverified APIs or scraping.
 */
data class ActiveVehicleSummary(
    val plateNumber: String,
    val formattedPlate: String,
    val normalizedPlate: String,
    val routeId: Int,
    val routeNo: String,
    val routeName: String,
    val varId: Int,
    val directionName: String,
    val checkpointStopId: Int,
    val checkpointStopName: String,
    val distanceMeters: Double,
    val speedKmH: Double,
    val etaSeconds: Int,
    val gpsTimestampIso: String?,
    val gpsTimestampMillis: Long?,
    val lastSeenMillis: Long = System.currentTimeMillis(),
    val latencyCategory: GpsLatencyCategory = GpsLatencyCategory.FRESH
) {
    val isStale: Boolean
        get() = (System.currentTimeMillis() - lastSeenMillis) > 180_000L // > 3 minutes without update
}

/**
 * Thread-safe In-Memory registry tracking active vehicles observed across bus routes.
 * Enables legal-safe license plate lookup without inventing unverified private APIs.
 */
object ActiveFleetRegistry {

    private val fleetMap = ConcurrentHashMap<String, ActiveVehicleSummary>()

    /**
     * Registers or updates an observed vehicle from route polling telematics.
     * Merges monotonically: only overwrites if new telemetry has equal or newer timestamp.
     */
    fun recordVehicle(
        telemetry: RouteVehicleTelemetry,
        routeNo: String,
        routeName: String = "",
        directionName: String = ""
    ) {
        val normPlate = LicensePlateNormalizer.normalize(telemetry.plateNumber)
        if (normPlate.isBlank()) return

        val now = System.currentTimeMillis()
        val existing = fleetMap[normPlate]

        if (existing == null ||
            (telemetry.gpsTimestampMillis ?: now) >= (existing.gpsTimestampMillis ?: 0L)
        ) {
            fleetMap[normPlate] = ActiveVehicleSummary(
                plateNumber = telemetry.plateNumber,
                formattedPlate = LicensePlateNormalizer.formatDisplay(telemetry.plateNumber),
                normalizedPlate = normPlate,
                routeId = telemetry.routeId,
                routeNo = routeNo,
                routeName = routeName,
                varId = telemetry.varId,
                directionName = directionName,
                checkpointStopId = telemetry.checkpointStopId,
                checkpointStopName = telemetry.checkpointStopName,
                distanceMeters = telemetry.rawDistanceMeters,
                speedKmH = telemetry.speedKmH,
                etaSeconds = telemetry.etaSeconds,
                gpsTimestampIso = telemetry.gpsTimestampIso,
                gpsTimestampMillis = telemetry.gpsTimestampMillis,
                lastSeenMillis = now,
                latencyCategory = telemetry.latencyCategory
            )
        }
    }

    /**
     * Batch registers vehicles observed on a route variant.
     */
    fun recordFleet(
        telemetries: List<RouteVehicleTelemetry>,
        routeNo: String,
        routeName: String = "",
        directionName: String = ""
    ) {
        telemetries.forEach { recordVehicle(it, routeNo, routeName, directionName) }
    }

    /**
     * Searches for active buses matching [query] by license plate or route number.
     */
    fun searchByPlate(query: String): List<ActiveVehicleSummary> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        return fleetMap.values
            .filter { summary ->
                LicensePlateNormalizer.matches(summary.plateNumber, trimmed) ||
                    summary.routeNo.equals(trimmed, ignoreCase = true)
            }
            .sortedWith(
                compareBy<ActiveVehicleSummary> { !it.normalizedPlate.startsWith(LicensePlateNormalizer.normalize(trimmed)) }
                    .thenByDescending { it.lastSeenMillis }
            )
    }

    fun searchVehicles(query: String): List<ActiveVehicleSummary> = searchByPlate(query)

    /**
     * Retrieves an exact active vehicle by normalized plate number.
     */
    fun getVehicle(plateNumber: String): ActiveVehicleSummary? {
        val norm = LicensePlateNormalizer.normalize(plateNumber)
        return fleetMap[norm]
    }

    fun findVehicleByPlate(plateNumber: String): ActiveVehicleSummary? = getVehicle(plateNumber)

    /**
     * Returns all currently known active vehicles.
     */
    fun getAllActiveVehicles(): List<ActiveVehicleSummary> {
        return fleetMap.values.toList().sortedBy { it.routeNo }
    }

    /**
     * Clears vehicles older than [maxAgeMillis] (default: 20 minutes) to prevent memory leak.
     */
    fun pruneStale(maxAgeMillis: Long = 20 * 60 * 1000L) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        fleetMap.entries.removeIf {
            val ts = it.value.gpsTimestampMillis ?: it.value.lastSeenMillis
            ts < cutoff
        }
    }

    fun evictStaleVehicles(nowMillis: Long = System.currentTimeMillis(), maxAgeMillis: Long = 20 * 60 * 1000L) {
        val cutoff = nowMillis - maxAgeMillis
        fleetMap.entries.removeIf {
            val ts = it.value.gpsTimestampMillis ?: it.value.lastSeenMillis
            ts < cutoff
        }
    }

    /**
     * Resets the registry (primarily for testing).
     */
    fun clear() {
        fleetMap.clear()
    }
}
