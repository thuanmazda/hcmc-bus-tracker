package com.hcmcbustracker.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hcmcbustracker.core.designsystem.theme.BlueSecondary
import com.hcmcbustracker.core.designsystem.theme.GreenPrimary
import com.hcmcbustracker.core.designsystem.theme.OrangeAccent
import com.hcmcbustracker.core.designsystem.theme.RedApproaching

/**
 * Visual operating schedule chart for a transit route.
 * Strictly adheres to Phase 13 Legal-safe & Truthful data principles:
 * - Level 1: 24-hour linear span bar rendered directly via Compose Canvas from operationTime ("HH:mm - HH:mm").
 * - Level 2: Hourly frequency histogram rendered when actual trip departure times are available.
 * - Never fabricates fake frequencies when only bounding start/end times are available.
 */
@Composable
fun RouteOperatingHoursChart(
    operationTime: String?,
    modifier: Modifier = Modifier,
    headway: String? = null,
    totalTrips: String? = null,
    tripDurationMinutes: Int? = null,
    hourlyTripCounts: Map<Int, Int>? = null,
    selectedDayType: String = "Ngày thường",
    availableDayTypes: List<String> = listOf("Ngày thường", "Thứ 7", "Chủ nhật"),
    onDayTypeSelected: ((String) -> Unit)? = null
) {
    val parsedTimes = remember(operationTime) {
        parseOperationTimeHours(operationTime)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Icon + Title + Day selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Lịch trình & Giờ hoạt động",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (parsedTimes != null) "Khung giờ vận hành trong ngày" else "Dữ liệu vận hành",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Optional Day Type Selector Chips (if multiple available)
            if (availableDayTypes.size > 1 && onDayTypeSelected != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableDayTypes.forEach { dayType ->
                        FilterChip(
                            selected = dayType == selectedDayType,
                            onClick = { onDayTypeSelected(dayType) },
                            label = { Text(dayType, fontSize = 12.sp) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Canvas Timeline: 24-hour linear span bar
            val startHour = parsedTimes?.first ?: 5.0f
            val endHour = parsedTimes?.second ?: 21.0f

            TimelineCanvas(
                startHour = startHour,
                endHour = endHour,
                hasValidTimes = parsedTimes != null,
                primaryColor = GreenPrimary,
                accentColor = BlueSecondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
            )

            // Hourly departure histogram (when explicit trip schedule is present)
            if (hourlyTripCounts != null && hourlyTripCounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Tần suất xuất bến theo khung giờ (chuyến/giờ):",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                HourlyHistogramCanvas(
                    hourlyCounts = hourlyTripCounts,
                    startHour = startHour.toInt().coerceAtLeast(0),
                    endHour = (endHour + 0.99f).toInt().coerceAtMost(24),
                    barColor = GreenPrimary,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Summary Stats Grid (First trip, Last trip, Headway, Trips/Duration)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatBadge(
                    label = "Chuyến đầu",
                    value = formatHourString(startHour),
                    iconColor = GreenPrimary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatBadge(
                    label = "Chuyến cuối",
                    value = formatHourString(endHour),
                    iconColor = RedApproaching,
                    modifier = Modifier.weight(1f)
                )
                if (!headway.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    StatBadge(
                        label = "Giãn cách",
                        value = headway,
                        iconColor = OrangeAccent,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!totalTrips.isNullOrBlank() || tripDurationMinutes != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    StatBadge(
                        label = if (tripDurationMinutes != null) "Thời gian" else "Tổng chuyến",
                        value = tripDurationMinutes?.let { "$it phút" } ?: totalTrips.orEmpty(),
                        iconColor = BlueSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineCanvas(
    startHour: Float,
    endHour: Float,
    hasValidTimes: Boolean,
    primaryColor: Color,
    accentColor: Color,
    trackColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val barHeight = 16.dp.toPx()
        val barTop = 8.dp.toPx()
        val barBottom = barTop + barHeight

        // 1. Draw 24h background track
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, barTop),
            size = Size(width, barHeight),
            cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)
        )

        // 2. Draw active operating range
        if (hasValidTimes) {
            val startRatio = (startHour / 24f).coerceIn(0f, 1f)
            val endRatio = (endHour / 24f).coerceIn(0f, 1f)
            val activeStart = startRatio * width
            val activeWidth = (endRatio - startRatio) * width

            val gradient = Brush.horizontalGradient(
                colors = listOf(primaryColor, accentColor),
                startX = activeStart,
                endX = activeStart + activeWidth
            )

            drawRoundRect(
                brush = gradient,
                topLeft = Offset(activeStart, barTop),
                size = Size(activeWidth.coerceAtLeast(barHeight), barHeight),
                cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)
            )

            // Draw start and end pin caps
            drawCircle(
                color = primaryColor,
                radius = 6.dp.toPx(),
                center = Offset(activeStart, barTop + barHeight / 2f)
            )
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = Offset(activeStart, barTop + barHeight / 2f)
            )

            drawCircle(
                color = accentColor,
                radius = 6.dp.toPx(),
                center = Offset(activeStart + activeWidth, barTop + barHeight / 2f)
            )
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = Offset(activeStart + activeWidth, barTop + barHeight / 2f)
            )
        }

        // 3. Draw tick marks at 00:00, 06:00, 12:00, 18:00, 24:00
        val ticks = listOf(0, 6, 12, 18, 24)
        val tickY = barBottom + 6.dp.toPx()
        val tickHeight = 4.dp.toPx()

        ticks.forEach { hour ->
            val x = (hour / 24f) * width
            drawLine(
                color = labelColor.copy(alpha = 0.5f),
                start = Offset(x, tickY),
                end = Offset(x, tickY + tickHeight),
                strokeWidth = 1.5.dp.toPx()
            )
        }
    }
}

@Composable
private fun HourlyHistogramCanvas(
    hourlyCounts: Map<Int, Int>,
    startHour: Int,
    endHour: Int,
    barColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    val maxCount = remember(hourlyCounts) {
        hourlyCounts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val totalHours = 24
        val colWidth = width / totalHours.toFloat()
        val barWidth = (colWidth * 0.7f).coerceAtLeast(2f)

        for (hour in 0..23) {
            val count = hourlyCounts[hour] ?: 0
            val x = hour * colWidth + (colWidth - barWidth) / 2f
            if (count > 0) {
                val barH = (count.toFloat() / maxCount.toFloat()) * (height - 16.dp.toPx())
                val y = height - 16.dp.toPx() - barH

                drawRoundRect(
                    color = barColor.copy(alpha = if (hour in startHour..endHour) 0.85f else 0.35f),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barH),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun StatBadge(
    label: String,
    value: String,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(iconColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

/**
 * Parses operationTime string (e.g. "05:00 - 20:30", "05h00 - 21h00", "4:45-21:15") into decimal start and end hours.
 */
internal fun parseOperationTimeHours(operationTime: String?): Pair<Float, Float>? {
    if (operationTime.isNullOrBlank()) return null
    val parts = operationTime.split("-", "–", "đến", "to").map { it.trim() }
    if (parts.size < 2) return null

    val startH = parseSingleHour(parts[0]) ?: return null
    val endH = parseSingleHour(parts[1]) ?: return null
    return if (endH > startH) startH to endH else null
}

private fun parseSingleHour(timeStr: String): Float? {
    val clean = timeStr.replace("h", ":").replace("H", ":").trim()
    val tokens = clean.split(":").map { it.trim() }
    if (tokens.isEmpty()) return null
    val hour = tokens[0].toFloatOrNull() ?: return null
    val minute = if (tokens.size >= 2) tokens[1].toFloatOrNull() ?: 0f else 0f
    return hour + (minute / 60f)
}

private fun formatHourString(hourDec: Float): String {
    val h = hourDec.toInt()
    val m = ((hourDec - h) * 60f).toInt()
    return String.format("%02d:%02d", h, m)
}
