package com.hcmcbustracker.core.database.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bus_stops",
    indices = [
        Index(value = ["code"]),
        Index(value = ["lat", "lng"]),
        Index(value = ["normalizedSearch"])
    ]
)
data class BusStopEntity(
    @PrimaryKey val id: Int,
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
    val routesRaw: String,
    val normalizedSearch: String
)

@Entity(tableName = "bus_stops_fts")
@Fts4(contentEntity = BusStopEntity::class)
data class BusStopFtsEntity(
    val name: String,
    val code: String,
    val street: String,
    val zone: String,
    val normalizedSearch: String
)
