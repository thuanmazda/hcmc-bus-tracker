package com.hcmcbustracker.feature.prediction.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hcmcbustracker.core.model.ArrivalVehicle
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager handling bus arrival notifications in compliance with Android platform rules:
 *
 * 1. Background Execution Rules:
 *    Continuous 15-second polling is strictly forbidden in the background by Android Doze Mode
 *    and Background Execution Limits (Android 8.0+). Background WorkManager jobs have a mandatory
 *    minimum periodic interval of 15 minutes (PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS).
 *    Real-time high-frequency polling is foreground-only.
 *
 * 2. Notification Permissions:
 *    Requires runtime POST_NOTIFICATIONS permission on Android 13+ (API 33).
 *
 * 3. Anti-Spam & De-duplication:
 *    Prevents duplicate notifications for the same vehicle at the same stop within a 5-minute cooldown.
 *
 * 4. Stale GPS Rejection:
 *    Suppresses notification if vehicle GPS timestamp is stale (> 180s) or invalid.
 *
 * 5. Disclaimer Requirement:
 *    Explicitly states arrival times are estimates based on EBMS API and subject to traffic.
 */
@Singleton
class BusArrivalNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_ID = "bus_arrival_alerts"
        const val CHANNEL_NAME = "Thông báo xe buýt đến trạm"
        const val CHANNEL_DESCRIPTION = "Thông báo dự báo ước lượng khi xe buýt sắp tiếp cận điểm dừng"
        const val COOLDOWN_DURATION_MS = 5 * 60 * 1000L // 5 minutes anti-spam cooldown
        const val DISCLAIMER = "Dự báo ước lượng dựa trên dữ liệu EBMS, có thể thay đổi tùy điều kiện giao thông."
    }

    // Thread-safe memory cache tracking last notified timestamp: key = "${stopId}_${plateNumber}_${routeNo}"
    private val notifiedVehiclesHistory = ConcurrentHashMap<String, Long>()

    init {
        createNotificationChannel()
    }

    /**
     * Creates the Android Notification Channel on Android 8.0 (API 26) and above.
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Checks if the app has permission to post notifications.
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    /**
     * Validates whether an alert should be dispatched for a given vehicle:
     * - Must have notification permission.
     * - Vehicle GPS must be fresh (not stale).
     * - Vehicle must be approaching (ETA <= 300s or distance <= 1000m).
     * - Not within anti-spam cooldown period for the same vehicle plate and stop.
     */
    fun canSendAlert(
        stopId: Int,
        routeNo: String,
        vehicle: ArrivalVehicle,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!hasNotificationPermission()) return false

        // Rejection rule 1: Reject corrupt, missing or stale GPS (> 180s)
        if (vehicle.isGpsStale()) return false

        // Rejection rule 2: Vehicle must be within approaching threshold (eta <= 5 min or distance <= 1000m)
        val isApproaching = vehicle.isApproaching || (vehicle.etaSeconds in 1..300) || (vehicle.distanceMeters in 1.0..1000.0)
        if (!isApproaching) return false

        // Rejection rule 3: Anti-duplication check within cooldown window
        val key = buildVehicleKey(stopId, routeNo, vehicle)
        val lastNotified = notifiedVehiclesHistory[key]
        if (lastNotified != null && (currentTimeMillis - lastNotified) < COOLDOWN_DURATION_MS) {
            return false // Duplicate alert within cooldown period suppressed
        }

        return true
    }

    /**
     * Constructs a notification builder with structured arrival information and disclaimer.
     */
    fun buildNotification(
        stopName: String,
        stopId: Int,
        routeNo: String,
        vehicle: ArrivalVehicle
    ): NotificationCompat.Builder {
        val title = "Xe tuyến $routeNo sắp đến $stopName"
        val message = "Biển số ${vehicle.formattedPlate} cách trạm ${vehicle.formattedDistance} (~${vehicle.formattedEta})."
        val bigText = "$message\n\n⚠️ $DISCLAIMER"

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
    }

    /**
     * Dispatches notification safely if validation succeeds.
     * Returns true if notification was posted, false if suppressed.
     */
    fun sendArrivalAlert(
        stopName: String,
        stopId: Int,
        routeNo: String,
        vehicle: ArrivalVehicle,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!canSendAlert(stopId, routeNo, vehicle, currentTimeMillis)) {
            return false
        }

        return try {
            val builder = buildNotification(stopName, stopId, routeNo, vehicle)
            val notificationId = (stopId * 31) + vehicle.plateNumber.hashCode()
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())

            // Record cooldown timestamp
            val key = buildVehicleKey(stopId, routeNo, vehicle)
            notifiedVehiclesHistory[key] = currentTimeMillis
            true
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clears anti-spam memory history (useful for reset and testing).
     */
    fun clearHistory() {
        notifiedVehiclesHistory.clear()
    }

    private fun buildVehicleKey(stopId: Int, routeNo: String, vehicle: ArrivalVehicle): String {
        val plate = vehicle.plateNumber.ifBlank { vehicle.formattedPlate }
        return "${stopId}_${plate}_$routeNo"
    }
}
