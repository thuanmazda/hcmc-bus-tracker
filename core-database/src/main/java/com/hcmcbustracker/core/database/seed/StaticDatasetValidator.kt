package com.hcmcbustracker.core.database.seed

import com.hcmcbustracker.core.database.entity.BusStopEntity
import com.hcmcbustracker.core.database.entity.RouteEntity
import com.hcmcbustracker.core.database.entity.RoutePathEntity
import com.hcmcbustracker.core.database.entity.RouteStopCrossReference
import com.hcmcbustracker.core.database.entity.RouteVariantEntity
import java.security.MessageDigest
import java.util.Locale

/**
 * Staging dataset ready for atomic database insertion after full validation.
 */
data class ValidatedStagingDataset(
    val datasetVersion: String,
    val datasetCreatedAt: Long,
    val source: String,
    val checksum: String,
    val routes: List<RouteEntity>,
    val variants: List<RouteVariantEntity>,
    val stops: List<BusStopEntity>,
    val crossRefs: List<RouteStopCrossReference>,
    val paths: List<RoutePathEntity>
)

sealed class DatasetValidationResult {
    data class Valid(val dataset: ValidatedStagingDataset) : DatasetValidationResult()
    data class Invalid(val reason: String) : DatasetValidationResult()
}

/**
 * Validates structural completeness, relational integrity, HCMC geographic bounds,
 * and computes a canonical SHA-256 checksum before static database updates are committed.
 */
object StaticDatasetValidator {

    private const val REGION_MIN_LAT = 8.0
    private const val REGION_MAX_LAT = 13.0
    private const val REGION_MIN_LNG = 104.0
    private const val REGION_MAX_LNG = 109.0

    fun validateAndPackage(
        datasetVersion: String,
        datasetCreatedAt: Long,
        source: String,
        routes: List<RouteEntity>,
        variants: List<RouteVariantEntity>,
        stops: List<BusStopEntity>,
        crossRefs: List<RouteStopCrossReference>,
        paths: List<RoutePathEntity>
    ): DatasetValidationResult {
        if (routes.isEmpty()) {
            return DatasetValidationResult.Invalid("Dataset không có dữ liệu tuyến (routes = 0).")
        }
        if (variants.isEmpty()) {
            return DatasetValidationResult.Invalid("Dataset không có dữ liệu chiều tuyến (variants = 0).")
        }
        if (stops.isEmpty()) {
            return DatasetValidationResult.Invalid("Dataset không có dữ liệu trạm dừng (stops = 0).")
        }
        if (paths.isEmpty()) {
            return DatasetValidationResult.Invalid("Dataset không có dữ liệu đa tuyến bản đồ (polylines = 0).")
        }

        // Validate route integrity
        val routeIds = HashSet<Int>(routes.size)
        for (r in routes) {
            if (r.id <= 0 || r.name.isBlank()) {
                return DatasetValidationResult.Invalid("Tuyến không hợp lệ: id=${r.id}, name='${r.name}'.")
            }
            routeIds.add(r.id)
        }

        // Validate variant relational mapping to routes
        val variantKeys = HashSet<Pair<Int, Int>>(variants.size)
        for (v in variants) {
            if (v.id <= 0 || !routeIds.contains(v.routeId)) {
                return DatasetValidationResult.Invalid("Chiều tuyến ${v.id} không gắn với tuyến hợp lệ (routeId=${v.routeId}).")
            }
            variantKeys.add(v.routeId to v.id)
        }

        // Validate stops and HCMC coordinate integrity
        val stopIds = HashSet<Int>(stops.size)
        var geoValidStopCount = 0
        for (s in stops) {
            if (s.id <= 0 || s.name.isBlank()) {
                return DatasetValidationResult.Invalid("Trạm dừng không hợp lệ: id=${s.id}.")
            }
            if (s.lat != 0.0 || s.lng != 0.0) {
                if (s.lat !in REGION_MIN_LAT..REGION_MAX_LAT || s.lng !in REGION_MIN_LNG..REGION_MAX_LNG) {
                    return DatasetValidationResult.Invalid("Tọa độ trạm ${s.id} nằm ngoài vùng dữ liệu (${s.lat}, ${s.lng}).")
                }
                geoValidStopCount++
            }
            stopIds.add(s.id)
        }

        if (geoValidStopCount == 0) {
            return DatasetValidationResult.Invalid("Không có trạm dừng nào sở hữu tọa độ GPS hợp lệ.")
        }

        // Filter/verify cross references point to valid variants & stops
        val cleanRefs = crossRefs.filter { ref ->
            variantKeys.contains(ref.routeId to ref.varId) && stopIds.contains(ref.stopId)
        }
        if (cleanRefs.isEmpty()) {
            return DatasetValidationResult.Invalid("Không có liên kết hợp lệ giữa chiều tuyến và trạm dừng.")
        }

        // Validate polylines
        for (p in paths) {
            if (!variantKeys.contains(p.routeId to p.varId)) {
                return DatasetValidationResult.Invalid("Polyline ${p.compositeKey} không thuộc biến thể hợp lệ.")
            }
            if (p.pointCount < 2 || p.latsJson.isBlank() || p.lngsJson.isBlank()) {
                return DatasetValidationResult.Invalid("Polyline ${p.compositeKey} có ít hơn 2 điểm tọa độ.")
            }
        }

        val checksum = computeCanonicalSha256(routes, variants, stops, cleanRefs, paths)

        return DatasetValidationResult.Valid(
            ValidatedStagingDataset(
                datasetVersion = datasetVersion,
                datasetCreatedAt = datasetCreatedAt,
                source = source,
                checksum = checksum,
                routes = routes,
                variants = variants,
                stops = stops,
                crossRefs = cleanRefs,
                paths = paths
            )
        )
    }

    /**
     * Computes deterministic SHA-256 fingerprint across all sorted static transit records.
     */
    fun computeCanonicalSha256(
        routes: List<RouteEntity>,
        variants: List<RouteVariantEntity>,
        stops: List<BusStopEntity>,
        crossRefs: List<RouteStopCrossReference>,
        paths: List<RoutePathEntity>
    ): String {
        val md = MessageDigest.getInstance("SHA-256")
        val sb = StringBuilder(4096)

        routes.sortedBy { it.id }.forEach {
            sb.append("R:").append(it.id).append('|').append(it.routeNo).append('|').append(it.name).append('\n')
        }
        variants.sortedWith(compareBy({ it.routeId }, { it.id })).forEach {
            sb.append("V:").append(it.routeId).append(':').append(it.id).append('|').append(it.distanceMeters).append('\n')
        }
        stops.sortedBy { it.id }.forEach {
            val latStr = String.format(Locale.US, "%.5f", it.lat)
            val lngStr = String.format(Locale.US, "%.5f", it.lng)
            sb.append("S:").append(it.id).append('|').append(latStr).append(',').append(lngStr).append('\n')
        }
        crossRefs.sortedWith(compareBy({ it.routeId }, { it.varId }, { it.orderIndex })).forEach {
            sb.append("X:").append(it.routeId).append(':').append(it.varId).append(':').append(it.stopId).append('\n')
        }
        paths.sortedBy { it.compositeKey }.forEach {
            sb.append("P:").append(it.compositeKey).append('|').append(it.pointCount).append('\n')
        }

        val digest = md.digest(sb.toString().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
