package com.hcmcbustracker.core.model

import kotlinx.serialization.Serializable

/**
 * Categorization of search history entries.
 */
@Serializable
enum class SearchHistoryType(val label: String) {
    ROUTE("Tuyến xe"),
    STOP("Trạm dừng"),
    PLATE("Biển số xe"),
    GENERAL("Tìm kiếm")
}

/**
 * Individual search history record.
 * Only stores minimal user query text, category, and timestamp.
 * Does not store any personal, sensitive, or full fleet data.
 */
@Serializable
data class SearchHistoryItem(
    val query: String,
    val type: SearchHistoryType = SearchHistoryType.GENERAL,
    val timestamp: Long = System.currentTimeMillis()
)
