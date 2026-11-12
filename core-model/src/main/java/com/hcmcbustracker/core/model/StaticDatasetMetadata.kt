package com.hcmcbustracker.core.model

/**
 * Integrity & availability state of the offline static transit dataset.
 */
enum class DatasetSyncStatus {
    UNINITIALIZED,
    SEEDING,
    SYNCING,
    VALID,
    FAILED
}

/**
 * Metadata summarizing the validated offline static transit dataset stored in Room.
 */
data class StaticDatasetMetadata(
    val datasetVersion: String,
    val datasetCreatedAt: Long,
    val datasetUpdatedAt: Long,
    val source: String,
    val totalRoutes: Int,
    val totalVariants: Int,
    val totalStops: Int,
    val totalPolylines: Int,
    val checksum: String,
    val lastSyncStatus: DatasetSyncStatus,
    val lastErrorMessage: String? = null
)

/**
 * Progress indicator during initial asset import or Settings manual dataset update.
 */
data class DatasetSyncProgress(
    val phase: String,
    val completedSteps: Int,
    val totalSteps: Int,
    val isCancellable: Boolean = true
)

/**
 * Result returned after checking/updating the static dataset from EBMS.
 */
sealed class DatasetUpdateOutcome {
    data class Updated(val metadata: StaticDatasetMetadata) : DatasetUpdateOutcome()
    data class AlreadyUpToDate(val metadata: StaticDatasetMetadata) : DatasetUpdateOutcome()
    data class Failed(val message: String, val preservedMetadata: StaticDatasetMetadata?) : DatasetUpdateOutcome()
}
