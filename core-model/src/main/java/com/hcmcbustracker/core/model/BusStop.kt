package com.hcmcbustracker.core.model

import kotlinx.serialization.Serializable

/**
 * Domain model representing a physical bus stop in HCMC.
 */
@Serializable
data class BusStop(
    val id: Int,
    val code: String,
    val name: String,
    val stopType: String,
    val zone: String,
    val ward: String? = null,
    val addressNo: String,
    val street: String,
    val supportDisability: Boolean,
    val status: String,
    val lat: Double,
    val lng: Double,
    val searchKeywords: String,
    val routes: List<String>
) {
    /**
     * Formats full readable address (e.g. "4 Hàm Nghi, Phường Bến Thành, Quận 1").
     */
    val fullAddress: String
        get() {
            val parts = mutableListOf<String>()
            if (addressNo.isNotBlank()) parts.add(addressNo)
            if (street.isNotBlank() && !addressNo.contains(street, ignoreCase = true)) parts.add(street)
            if (!ward.isNullOrBlank()) parts.add(ward)
            if (zone.isNotBlank()) parts.add(zone)
            return parts.joinToString(", ")
        }
}
