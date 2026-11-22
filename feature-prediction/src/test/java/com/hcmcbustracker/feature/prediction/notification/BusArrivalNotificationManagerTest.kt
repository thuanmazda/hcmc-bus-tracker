package com.hcmcbustracker.feature.prediction.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hcmcbustracker.core.model.ArrivalVehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BusArrivalNotificationManagerTest {

    private lateinit var context: Context
    private lateinit var notificationManager: BusArrivalNotificationManager

    private fun getCurrentIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
        return sdf.format(Date())
    }

    private fun getOldIsoTimestamp(minutesAgo: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
        return sdf.format(Date(System.currentTimeMillis() - (minutesAgo * 60 * 1000L)))
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = BusArrivalNotificationManager(context)
        notificationManager.clearHistory()
    }

    @Test
    fun `canSendAlert rejects vehicle when GPS timestamp is stale`() {
        val staleVehicle = ArrivalVehicle(
            plateNumber = "51B-12345",
            distanceMeters = 300.0,
            etaSeconds = 60,
            speedKmH = 25.0,
            timestampIso = getOldIsoTimestamp(10), // 10 minutes old
            status = 1,
            responseReceivedTimeMillis = System.currentTimeMillis()
        )

        val canSend = notificationManager.canSendAlert(stopId = 100, routeNo = "01", vehicle = staleVehicle)
        assertFalse("Stale vehicle GPS should be rejected from notification", canSend)
    }

    @Test
    fun `canSendAlert rejects vehicle when not approaching and far away`() {
        val farVehicle = ArrivalVehicle(
            plateNumber = "51B-12345",
            distanceMeters = 5000.0,
            etaSeconds = 900, // 15 minutes away
            speedKmH = 30.0,
            timestampIso = getCurrentIsoTimestamp(),
            status = 1,
            responseReceivedTimeMillis = System.currentTimeMillis()
        )

        val canSend = notificationManager.canSendAlert(stopId = 100, routeNo = "01", vehicle = farVehicle)
        assertFalse("Vehicles farther than approaching threshold should not trigger notification", canSend)
    }

    @Test
    fun `canSendAlert accepts fresh approaching vehicle`() {
        val approachingVehicle = ArrivalVehicle(
            plateNumber = "51B-12345",
            distanceMeters = 450.0,
            etaSeconds = 90,
            speedKmH = 22.0,
            timestampIso = getCurrentIsoTimestamp(),
            status = 1,
            responseReceivedTimeMillis = System.currentTimeMillis()
        )

        val canSend = notificationManager.canSendAlert(stopId = 100, routeNo = "01", vehicle = approachingVehicle)
        assertTrue("Approaching vehicle with fresh GPS should be allowed to notify", canSend)
    }

    @Test
    fun `canSendAlert rejects vehicle when stop is muted`() {
        val vehicle = ArrivalVehicle(
            plateNumber = "51B-12345",
            distanceMeters = 300.0,
            etaSeconds = 60,
            speedKmH = 20.0,
            timestampIso = getCurrentIsoTimestamp(),
            status = 1,
            responseReceivedTimeMillis = System.currentTimeMillis()
        )

        val canSend = notificationManager.canSendAlert(
            stopId = 100,
            routeNo = "01",
            vehicle = vehicle,
            isStopMuted = true
        )
        assertFalse("Alert should be suppressed when stop is muted by user", canSend)
    }

    @Test
    fun `anti-spam cooldown suppresses duplicate alert for same vehicle within 5 minutes`() {
        val now = 10_000_000L
        val vehicle = ArrivalVehicle(
            plateNumber = "51B-99999",
            distanceMeters = 300.0,
            etaSeconds = 60,
            speedKmH = 20.0,
            timestampIso = getCurrentIsoTimestamp(),
            status = 1,
            responseReceivedTimeMillis = System.currentTimeMillis()
        )

        // First alert succeeds
        val firstSent = notificationManager.sendArrivalAlert(
            stopName = "Bến Thành",
            stopId = 100,
            routeNo = "01",
            vehicle = vehicle,
            currentTimeMillis = now
        )
        assertTrue("First alert should be successfully sent", firstSent)

        // Second alert 2 minutes later must be rejected by anti-spam cooldown
        val duplicateWithinCooldown = notificationManager.canSendAlert(
            stopId = 100,
            routeNo = "01",
            vehicle = vehicle,
            currentTimeMillis = now + (2 * 60 * 1000L)
        )
        assertFalse("Duplicate alert within cooldown period must be suppressed", duplicateWithinCooldown)

        // Third alert 6 minutes later should be allowed (after cooldown expires)
        val afterCooldown = notificationManager.canSendAlert(
            stopId = 100,
            routeNo = "01",
            vehicle = vehicle,
            currentTimeMillis = now + (6 * 60 * 1000L)
        )
        assertTrue("Alert should be permitted once 5-minute cooldown duration expires", afterCooldown)
    }

    @Test
    fun `buildNotification includes formatted data and mandatory disclaimer`() {
        val vehicle = ArrivalVehicle(
            plateNumber = "51B-88888",
            distanceMeters = 500.0,
            etaSeconds = 120,
            speedKmH = 28.0,
            timestampIso = getCurrentIsoTimestamp(),
            status = 1,
            responseReceivedTimeMillis = System.currentTimeMillis()
        )

        val builder = notificationManager.buildNotification(
            stopName = "Trạm Đại học Bách Khoa",
            stopId = 200,
            routeNo = "150",
            vehicle = vehicle
        )

        val notification = builder.build()
        val extras = notification.extras
        val title = extras.getCharSequence("android.title")?.toString()
        val text = extras.getCharSequence("android.text")?.toString()
        val bigText = extras.getCharSequence("android.bigText")?.toString()

        val fullContent = "$text $bigText"

        assertEquals("Xe tuyến 150 sắp đến Trạm Đại học Bách Khoa", title)
        assertTrue("Notification should contain vehicle plate number", fullContent.contains(vehicle.formattedPlate))
        assertTrue("Notification should contain distance", fullContent.contains(vehicle.formattedDistance))
        assertTrue("Notification must contain EBMS disclaimer", fullContent.contains("EBMS"))
        assertTrue("Notification must state prediction is an estimate", fullContent.contains("ước lượng"))
    }

    @Test
    fun `canSendAlert respects custom user-configured thresholds`() {
        val vehicle = ArrivalVehicle(
            plateNumber = "50E-123.45",
            distanceMeters = 800.0,
            etaSeconds = 200, // ~3.3 minutes
            speedKmH = 20.0,
            timestampIso = getCurrentIsoTimestamp(),
            status = 1,
            responseReceivedTimeMillis = System.currentTimeMillis()
        )

        // Threshold of 3 minutes: 200 seconds > 180 seconds, so time threshold fails
        val strictTimeThreshold = ArrivalAlertThresholds(
            enableTimeThreshold = true,
            timeThresholdMinutes = 3,
            enableDistanceThreshold = false
        )
        assertFalse(notificationManager.canSendAlert(100, "01", vehicle, thresholds = strictTimeThreshold))

        // Threshold of 5 minutes: 200 seconds <= 300 seconds, so time threshold succeeds
        val lenientTimeThreshold = ArrivalAlertThresholds(
            enableTimeThreshold = true,
            timeThresholdMinutes = 5,
            enableDistanceThreshold = false
        )
        assertTrue(notificationManager.canSendAlert(100, "01", vehicle, thresholds = lenientTimeThreshold))

        // Distance threshold of 500m: 800m > 500m, fails
        val strictDistThreshold = ArrivalAlertThresholds(
            enableTimeThreshold = false,
            enableDistanceThreshold = true,
            distanceThresholdMeters = 500.0
        )
        assertFalse(notificationManager.canSendAlert(100, "01", vehicle, thresholds = strictDistThreshold))

        // Distance threshold of 1000m: 800m <= 1000m, succeeds
        val lenientDistThreshold = ArrivalAlertThresholds(
            enableTimeThreshold = false,
            enableDistanceThreshold = true,
            distanceThresholdMeters = 1000.0
        )
        assertTrue(notificationManager.canSendAlert(100, "01", vehicle, thresholds = lenientDistThreshold))
    }

    @Test
    fun `buildTrackingNotification creates sticky ongoing notification with correct metadata`() {
        val builder = notificationManager.buildTrackingNotification(
            stopName = "Bến xe buýt Sài Gòn",
            routeNo = "01",
            plateNumber = "50E-784.27",
            statusText = "Còn 500m (~2 phút)"
        )

        val notification = builder.build()
        val extras = notification.extras
        val title = extras.getCharSequence("android.title")?.toString()
        val text = extras.getCharSequence("android.text")?.toString()

        assertEquals("Đang theo dõi xe tuyến 01 (50E-784.27)", title)
        assertTrue(text?.contains("Bến xe buýt Sài Gòn") == true)
        assertTrue(text?.contains("Còn 500m") == true)
        assertTrue((notification.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0)
    }
}
