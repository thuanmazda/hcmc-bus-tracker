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
 * User-configurable thresholds for arrival notifications.
 */
data class ArrivalAlertThresholds(
    val enableTimeThreshold: Boolean = true,
    val timeThresholdMinutes: Int = 5,
    val enableDistanceThreshold: Boolean = true,
    val distanceThresholdMeters: Double = 1000.0
)

/**
 * Manager handling bus arrival notifications in compliance with Android platform rules:
 *
 * 1. Background Execution Rules:
 *    Background execution complies with Android Doze Mode and platform constraints.
 *    Active short-term tracking runs through a bounded Foreground Service with sticky notification.
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
        const val TRACKING_CHANNEL_ID = "bus_arrival_tracking"
        const val TRACKING_CHANNEL_NAME = "Dịch vụ theo dõi xe buýt trực tiếp"
        const val COOLDOWN_DURATION_MS = 5 * 60 * 1000L // 5 minutes anti-spam cooldown
        const val DISCLAIMER = "Dự báo ước lượng dựa trên dữ liệu EBMS, có thể thay đổi tùy điều kiện giao thông."
    }

    // Thread-safe memory cache tracking last notified timestamp: key = "${stopId}_${plateNumber}_${routeNo}"
    private val notifiedVehiclesHistory = ConcurrentHashMap<String, Long>()

    init {
        createNotificationChannel()
    }

    /**
     * Creates the Android Notification Channels on Android 8.0 (API 26) and above.
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

            val arrivalChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
            }
            notificationManager?.createNotificationChannel(arrivalChannel)

            val trackingChannel = NotificationChannel(
                TRACKING_CHANNEL_ID,
                TRACKING_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Trạng thái theo dõi xe buýt khi ứng dụng đang chạy ở nền"
                enableVibration(false)
            }
            notificationManager?.createNotificationChannel(trackingChannel)
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
     * - Vehicle GPS must be fresh (not stale <= 180s).
     * - Vehicle must satisfy time threshold and/or distance threshold.
     * - Not within anti-spam cooldown period for the same vehicle plate and stop.
     */
    fun canSendAlert(
        stopId: Int,
        routeNo: String,
        vehicle: ArrivalVehicle,
        currentTimeMillis: Long = System.currentTimeMillis(),
        thresholds: ArrivalAlertThresholds = ArrivalAlertThresholds(),
        isStopMuted: Boolean = false
    ): Boolean {
        if (isStopMuted) return false
        if (!hasNotificationPermission()) return false

        // Rejection rule 1: Reject corrupt, missing or stale GPS (> 180s)
        if (vehicle.isGpsStale()) return false

        // Rejection rule 2: Check configurable thresholds
        val satisfiesTime = thresholds.enableTimeThreshold && vehicle.etaSeconds in 1..(thresholds.timeThresholdMinutes * 60)
        val satisfiesDistance = thresholds.enableDistanceThreshold && vehicle.distanceMeters in 1.0..thresholds.distanceThresholdMeters
        val fallbackApproaching = !thresholds.enableTimeThreshold && !thresholds.enableDistanceThreshold &&
            (vehicle.isApproaching || vehicle.etaSeconds in 1..300 || vehicle.distanceMeters in 1.0..1000.0)

        val isApproaching = satisfiesTime || satisfiesDistance || fallbackApproaching
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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
    }

    /**
     * Builds ongoing notification for background tracking service.
     */
    fun buildTrackingNotification(
        stopName: String,
        routeNo: String,
        plateNumber: String,
        statusText: String,
        cancelIntent: android.app.PendingIntent? = null
    ): NotificationCompat.Builder {
        val title = "Đang theo dõi xe tuyến $routeNo ($plateNumber)"
        val message = "Điểm đến: $stopName • $statusText"

        val builder = NotificationCompat.Builder(context, TRACKING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (cancelIntent != null) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Hủy theo dõi",
                cancelIntent
            )
        }

        return builder
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
        currentTimeMillis: Long = System.currentTimeMillis(),
        thresholds: ArrivalAlertThresholds = ArrivalAlertThresholds(),
        isStopMuted: Boolean = false
    ): Boolean {
        if (!canSendAlert(stopId, routeNo, vehicle, currentTimeMillis, thresholds, isStopMuted)) {
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
