package com.hcmcbustracker.feature.prediction.notification

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import com.hcmcbustracker.core.database.repository.BusRepository
import com.hcmcbustracker.core.model.ArrivalVehicle
import com.hcmcbustracker.core.model.LicensePlateNormalizer
import com.hcmcbustracker.core.model.NetworkResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Android Foreground Service handling short-lived background vehicle tracking.
 *
 * Rules:
 * 1. Requires FOREGROUND_SERVICE & FOREGROUND_SERVICE_DATA_SYNC permissions.
 * 2. Uses sticky notification in `bus_arrival_tracking` channel with user cancel action.
 * 3. Enforces strict auto-shutdown after 30 minutes max to protect battery.
 * 4. Polling loop: queries official prediction endpoint every 20 seconds.
 * 5. When vehicle reaches configured threshold (time or distance), dispatches high-priority alert.
 * 6. Shuts down immediately upon arrival or when target vehicle completes trip.
 */
@AndroidEntryPoint
class BusTrackingForegroundService : Service() {

    companion object {
        const val ACTION_START_TRACKING = "com.hcmcbustracker.action.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.hcmcbustracker.action.STOP_TRACKING"

        const val EXTRA_STOP_ID = "extra_stop_id"
        const val EXTRA_STOP_NAME = "extra_stop_name"
        const val EXTRA_ROUTE_NO = "extra_route_no"
        const val EXTRA_PLATE_NUMBER = "extra_plate_number"
        const val EXTRA_TIME_THRESHOLD_MINUTES = "extra_time_threshold_minutes"
        const val EXTRA_DISTANCE_THRESHOLD_METERS = "extra_distance_threshold_meters"

        private const val NOTIFICATION_ID = 9901
        private const val POLL_INTERVAL_MS = 20_000L
        private const val MAX_TRACKING_DURATION_MS = 30 * 60 * 1000L // 30 minutes auto-stop

        fun startTracking(
            context: Context,
            stopId: Int,
            stopName: String,
            routeNo: String,
            plateNumber: String,
            timeThresholdMinutes: Int = 5,
            distanceThresholdMeters: Double = 1000.0
        ) {
            val intent = Intent(context, BusTrackingForegroundService::class.java).apply {
                action = ACTION_START_TRACKING
                putExtra(EXTRA_STOP_ID, stopId)
                putExtra(EXTRA_STOP_NAME, stopName)
                putExtra(EXTRA_ROUTE_NO, routeNo)
                putExtra(EXTRA_PLATE_NUMBER, plateNumber)
                putExtra(EXTRA_TIME_THRESHOLD_MINUTES, timeThresholdMinutes)
                putExtra(EXTRA_DISTANCE_THRESHOLD_METERS, distanceThresholdMeters)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopTracking(context: Context) {
            val intent = Intent(context, BusTrackingForegroundService::class.java).apply {
                action = ACTION_STOP_TRACKING
            }
            context.startService(intent)
        }
    }

    @Inject
    lateinit var repository: BusRepository

    @Inject
    lateinit var notificationManager: BusArrivalNotificationManager

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var trackingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                val stopId = intent.getIntExtra(EXTRA_STOP_ID, -1)
                val stopName = intent.getStringExtra(EXTRA_STOP_NAME).orEmpty()
                val routeNo = intent.getStringExtra(EXTRA_ROUTE_NO).orEmpty()
                val plateNumber = intent.getStringExtra(EXTRA_PLATE_NUMBER).orEmpty()
                val timeThreshold = intent.getIntExtra(EXTRA_TIME_THRESHOLD_MINUTES, 5)
                val distThreshold = intent.getDoubleExtra(EXTRA_DISTANCE_THRESHOLD_METERS, 1000.0)

                if (stopId > 0 && routeNo.isNotBlank()) {
                    startTrackingSession(stopId, stopName, routeNo, plateNumber, timeThreshold, distThreshold)
                } else {
                    stopSelf()
                }
            }
            ACTION_STOP_TRACKING -> {
                stopSelf()
            }
            else -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startTrackingSession(
        stopId: Int,
        stopName: String,
        routeNo: String,
        plateNumber: String,
        timeThresholdMinutes: Int,
        distanceThresholdMeters: Double
    ) {
        trackingJob?.cancel()

        val cancelIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, BusTrackingForegroundService::class.java).apply { action = ACTION_STOP_TRACKING },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val initialNotif = notificationManager.buildTrackingNotification(
            stopName = stopName,
            routeNo = routeNo,
            plateNumber = plateNumber.ifBlank { "Tất cả xe" },
            statusText = "Đang kết nối hệ thống dự báo...",
            cancelIntent = cancelIntent
        ).build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, initialNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, initialNotif)
        }

        trackingJob = serviceScope.launch {
            val startTime = System.currentTimeMillis()
            val thresholds = ArrivalAlertThresholds(
                enableTimeThreshold = timeThresholdMinutes > 0,
                timeThresholdMinutes = timeThresholdMinutes,
                enableDistanceThreshold = distanceThresholdMeters > 0,
                distanceThresholdMeters = distanceThresholdMeters
            )

            var alertSent = false

            while (isActive) {
                // Check auto-stop safety limit (30 minutes)
                if (System.currentTimeMillis() - startTime >= MAX_TRACKING_DURATION_MS) {
                    break
                }

                try {
                    val result = repository.fetchRealtimePrediction(stopId)
                    if (result is NetworkResult.Success<List<com.hcmcbustracker.core.model.Prediction>>) {
                        val predictions = result.data
                        val matchingPred = predictions.firstOrNull { it.routeNo.equals(routeNo, ignoreCase = true) }
                        val matchingVehicle = matchingPred?.vehicles?.firstOrNull { veh: ArrivalVehicle ->
                            if (plateNumber.isNotBlank()) {
                                LicensePlateNormalizer.normalize(veh.plateNumber) == LicensePlateNormalizer.normalize(plateNumber)
                            } else {
                                true
                            }
                        }

                        if (matchingVehicle != null) {
                            val updatedNotif = notificationManager.buildTrackingNotification(
                                stopName = stopName,
                                routeNo = routeNo,
                                plateNumber = matchingVehicle.formattedPlate,
                                statusText = "Còn ${matchingVehicle.formattedDistance} (~${matchingVehicle.formattedEta})",
                                cancelIntent = cancelIntent
                            ).build()
                            NotificationManagerCompat.from(this@BusTrackingForegroundService).notify(NOTIFICATION_ID, updatedNotif)

                            // Check arrival alert condition
                            if (!alertSent && notificationManager.canSendAlert(stopId, routeNo, matchingVehicle, thresholds = thresholds)) {
                                val sent = notificationManager.sendArrivalAlert(
                                    stopName = stopName,
                                    stopId = stopId,
                                    routeNo = routeNo,
                                    vehicle = matchingVehicle,
                                    thresholds = thresholds
                                )
                                if (sent) {
                                    alertSent = true
                                }
                            }

                            // If arrived (distance <= 30m or eta == 0), terminate tracking
                            if (matchingVehicle.distanceMeters in 0.1..30.0 || matchingVehicle.etaSeconds == 0) {
                                delay(5000L) // Allow user to see final arrival
                                break
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Suppress network fluctuations, retry on next interval
                }

                delay(POLL_INTERVAL_MS)
            }

            stopSelf()
        }
    }

    override fun onDestroy() {
        trackingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
