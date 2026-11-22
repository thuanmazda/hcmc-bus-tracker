package com.hcmcbustracker.feature.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hcmcbustracker.core.designsystem.component.ArrivalTimeChip
import com.hcmcbustracker.core.designsystem.component.BottomSheetDragHandle
import com.hcmcbustracker.core.designsystem.component.RouteBadge
import com.hcmcbustracker.core.designsystem.theme.BottomSheetShape
import com.hcmcbustracker.core.designsystem.theme.CardShape
import com.hcmcbustracker.core.designsystem.theme.OrangeAccent
import com.hcmcbustracker.core.designsystem.theme.StatusDelayed
import com.hcmcbustracker.core.designsystem.theme.StatusFresh
import com.hcmcbustracker.core.designsystem.theme.StatusSlightDelay
import com.hcmcbustracker.core.designsystem.theme.StatusStale
import com.hcmcbustracker.core.designsystem.theme.TransitDimens
import com.hcmcbustracker.core.designsystem.theme.borderSubtle
import com.hcmcbustracker.core.model.GpsLatencyCategory
import com.hcmcbustracker.core.model.PositionSource
import com.hcmcbustracker.core.model.VehicleArrivalStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modern, collapsible Vehicle Tracking Bottom Sheet for Live Map (Tab 2).
 *
 * Adheres strictly to Phase 3 design priorities:
 * 1. Route number badge
 * 2. License plate
 * 3. Truthful GPS freshness tier & latency
 * 4. Distance to checkpoint/stop
 * 5. Velocity
 * 6. Estimated arrival time (ETA)
 *
 * Supports:
 * - Collapsed (Peek mode): Leaves 85% of map visible.
 * - Expanded (Detail mode): Shows stop progression, detailed metric boxes, telemetry diagnostics.
 * - Drag & Tap gestures: Smoothly toggle expansion or dismiss.
 * - Auto-Follow mode: Re-centers on vehicle without fighting user manual panning.
 */
@Composable
fun VehicleTrackingSheet(
    vehicle: TrackedVehicleState,
    cameraMode: VehicleCameraMode,
    onToggleAutoFollow: () -> Unit,
    onCenterVehicle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable(vehicle.plateNumber) { mutableStateOf(false) }

    val isUnknown = vehicle.arrivalStatus == VehicleArrivalStatus.UNKNOWN
    val isPassed = vehicle.arrivalStatus == VehicleArrivalStatus.PASSED_STOP
    val isFollowing = cameraMode == VehicleCameraMode.FOLLOW_VEHICLE

    // Vertical drag gesture detector to expand or collapse
    val dragModifier = Modifier.draggable(
        orientation = Orientation.Vertical,
        state = rememberDraggableState { delta ->
            if (delta < -15f && !isExpanded) {
                isExpanded = true
            } else if (delta > 25f) {
                if (isExpanded) {
                    isExpanded = false
                } else {
                    onClose()
                }
            }
        }
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(dragModifier)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = TransitDimens.CardBorderWidth,
            color = MaterialTheme.colorScheme.borderSubtle
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = TransitDimens.ElevationHigh)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TransitDimens.SpacingBase, vertical = TransitDimens.SpacingSm)
        ) {
            // Drag handle area (tappable to expand/collapse)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                contentAlignment = Alignment.Center
            ) {
                BottomSheetDragHandle()
            }

            // ==========================================
            // PRIORITY 1, 2, 3: Header Row (Always visible)
            // 1. Route No  2. License Plate  3. GPS Freshness
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // 1. Route Badge
                    if (vehicle.routeNo.isNotBlank()) {
                        RouteBadge(
                            routeNo = vehicle.routeNo,
                            modifier = Modifier.padding(end = TransitDimens.SpacingSm)
                        )
                    }

                    // 2. License Plate
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(TransitDimens.SpacingXs)
                        ) {
                            Text(
                                text = "Xe ${vehicle.plateNumber}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )

                            // 3. Truthful GPS Freshness Tier
                            VehicleGpsFreshnessBadge(
                                category = vehicle.latencyCategory,
                                isTimestampUnchanged = vehicle.isGpsTimestampUnchanged
                            )
                        }

                        if (vehicle.variantShortName.isNotBlank() && !isExpanded) {
                            Text(
                                text = "Chiều: ${vehicle.variantShortName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Close / Dismiss button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(TransitDimens.MinTouchTargetSize)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Đóng theo dõi xe",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(TransitDimens.SpacingSm))

            // ==========================================
            // PRIORITY 4, 5, 6: Key Metrics Row
            // 4. Distance  5. Speed  6. Estimated Arrival Time (ETA)
            // ==========================================
            val displayDistance = when {
                isUnknown -> "--"
                isPassed -> formatMetricDistance(vehicle.distanceToNextStopMeters ?: vehicle.effectiveDistanceMeters)
                else -> formatMetricDistance(vehicle.effectiveDistanceMeters)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Distance & Speed compact summary
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TransitDimens.SpacingMd),
                    modifier = Modifier.weight(1f)
                ) {
                    // 4. Distance
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(TransitDimens.IconXs)
                        )
                        Spacer(modifier = Modifier.width(TransitDimens.Spacing2xs))
                        Text(
                            text = if (isPassed && vehicle.nextStopName != null) "Tới trạm: $displayDistance" else "Cách: $displayDistance",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }

                    // 5. Speed
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(TransitDimens.IconXs)
                        )
                        Spacer(modifier = Modifier.width(TransitDimens.Spacing2xs))
                        Text(
                            text = if (vehicle.speedKmH >= 0.0) "${vehicle.speedKmH.toInt()} km/h" else "--",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                // 6. Reliable ETA Chip (if valid)
                if (vehicle.etaSeconds != null && vehicle.etaSeconds >= 0) {
                    val etaMins = (vehicle.etaSeconds + 59) / 60
                    ArrivalTimeChip(
                        etaMinutes = etaMins,
                        isApproaching = vehicle.arrivalStatus == VehicleArrivalStatus.APPROACHING_STOP || etaMins <= 1
                    )
                }
            }

            // ==========================================
            // AUTO-FOLLOW BUTTON (Always easily accessible)
            // ==========================================
            Spacer(modifier = Modifier.height(TransitDimens.SpacingSm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Operational Arrival Status Tag
                VehicleArrivalStatusTag(status = vehicle.arrivalStatus)

                // Auto-Follow / Re-Center Camera Toggle
                FilterChip(
                    selected = isFollowing,
                    onClick = {
                        if (isFollowing) onToggleAutoFollow() else onCenterVehicle()
                    },
                    label = {
                        Text(
                            text = if (isFollowing) "Đang bám theo xe" else "Bám theo xe",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isFollowing) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                            contentDescription = null,
                            tint = if (isFollowing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(TransitDimens.IconSm)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(TransitDimens.RadiusPill)
                )
            }

            // ==========================================
            // EXPANDED SECTION (Shown when user drags or taps)
            // ==========================================
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = TransitDimens.SpacingSm)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.borderSubtle,
                        thickness = TransitDimens.CardBorderWidth
                    )

                    Spacer(modifier = Modifier.height(TransitDimens.SpacingSm))

                    // Stop Progression Context
                    if (isPassed && !vehicle.selectedStopName.isNullOrBlank()) {
                        val passedDist = kotlin.math.abs(vehicle.distanceToSelectedStopMeters ?: 0.0)
                        Text(
                            text = "Đã đi qua trạm: ${vehicle.selectedStopName} (${formatMetricDistance(passedDist)})",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = OrangeAccent,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(TransitDimens.Spacing2xs))
                    }

                    val nextStopLabel = vehicle.nextStopName ?: vehicle.stopName.ifBlank { "Trạm #${vehicle.stopId}" }
                    Text(
                        text = "Trạm kế tiếp: $nextStopLabel",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )

                    if (vehicle.variantShortName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(TransitDimens.Spacing2xs))
                        Text(
                            text = "Lộ trình: ${vehicle.variantShortName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(TransitDimens.SpacingMd))

                    // 3 Detailed Metric Boxes
                    val distanceSublabel = when (vehicle.positionSource) {
                        com.hcmcbustracker.core.model.PositionSource.LATENCY_COMPENSATED ->
                            if (vehicle.projectedDistanceMeters > 0) "Bù trễ +${vehicle.projectedDistanceMeters.toInt()}m" else "Dự đoán theo tuyến"
                        com.hcmcbustracker.core.model.PositionSource.INTERPOLATED_ON_ROUTE -> "Nội suy theo tuyến"
                        com.hcmcbustracker.core.model.PositionSource.HELD_AT_LAST_VALID_GPS -> "Giữ vị trí cũ (GPS trễ)"
                        com.hcmcbustracker.core.model.PositionSource.UNKNOWN -> "Thiếu dữ liệu vị trí"
                    }

                    val etaText = if (!isUnknown && vehicle.etaSeconds != null && vehicle.etaSeconds >= 0) {
                        formatMetricEta(vehicle.etaSeconds)
                    } else {
                        "--"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(TransitDimens.SpacingSm)
                    ) {
                        VehicleDetailMetricBox(
                            label = if (isPassed && vehicle.nextStopName != null) "Tới trạm kế" else "Cự ly tuyến",
                            value = displayDistance,
                            sublabel = distanceSublabel,
                            modifier = Modifier.weight(1f)
                        )

                        VehicleDetailMetricBox(
                            label = "ETA dự báo",
                            value = etaText,
                            sublabel = if (vehicle.gpsAgeSeconds >= 0) "GPS ${vehicle.formattedGpsTime} (${vehicle.gpsAgeSeconds}s)" else "GPS ${vehicle.formattedGpsTime}",
                            modifier = Modifier.weight(1f)
                        )

                        VehicleDetailMetricBox(
                            label = "Vận tốc",
                            value = if (vehicle.speedKmH >= 0.0) "${vehicle.speedKmH.toInt()} km/h" else "--",
                            sublabel = if (vehicle.speedKmH <= 1.5) "Đang dừng" else "Đang chạy",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(TransitDimens.SpacingSm))

                    // Telemetry Timestamp Footer (Honest data verification)
                    val updatedTimeText = remember(vehicle.renderedAtMillis) {
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(vehicle.renderedAtMillis))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tín hiệu GPS: ${vehicle.formattedGpsTime} (${vehicle.latencyCategory.labelVi})",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Cập nhật app: $updatedTimeText",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(TransitDimens.SpacingXs))
                }
            }
        }
    }
}

/**
 * Truthful GPS Freshness Badge.
 * Categorizes latency strictly using real timestamp (dts) age:
 * - FRESH: Green
 * - SLIGHT_DELAY: Amber
 * - DELAYED: Orange
 * - STALE: Gray/Red
 * - UNKNOWN: Muted Gray
 */
@Composable
fun VehicleGpsFreshnessBadge(
    category: GpsLatencyCategory,
    isTimestampUnchanged: Boolean,
    modifier: Modifier = Modifier
) {
    val (dotColor, label) = when (category) {
        GpsLatencyCategory.FRESH -> Pair(StatusFresh, "Trực tiếp")
        GpsLatencyCategory.SLIGHT_DELAY -> Pair(StatusSlightDelay, "Trễ nhẹ")
        GpsLatencyCategory.DELAYED -> Pair(StatusDelayed, "Trễ > 2p")
        GpsLatencyCategory.STALE -> Pair(StatusStale, "Dữ liệu cũ")
        GpsLatencyCategory.UNKNOWN -> Pair(Color(0xFF8E8E93), "Không xác định")
    }

    Surface(
        shape = RoundedCornerShape(TransitDimens.RadiusXs),
        color = dotColor.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isTimestampUnchanged) "$label • Tọa độ giữ" else label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                color = dotColor
            )
        }
    }
}

@Composable
fun VehicleArrivalStatusTag(
    status: VehicleArrivalStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label) = when (status) {
        VehicleArrivalStatus.MOVING -> Triple(StatusFresh.copy(alpha = 0.12f), StatusFresh, status.labelVi)
        VehicleArrivalStatus.APPROACHING_STOP -> Triple(Color(0xFF007AFF).copy(alpha = 0.12f), Color(0xFF007AFF), status.labelVi)
        VehicleArrivalStatus.ARRIVING_AT_STOP -> Triple(StatusDelayed.copy(alpha = 0.14f), StatusDelayed, status.labelVi)
        VehicleArrivalStatus.AT_STOP -> Triple(Color(0xFF5856D6).copy(alpha = 0.12f), Color(0xFF5856D6), status.labelVi)
        VehicleArrivalStatus.PASSED_STOP -> Triple(OrangeAccent.copy(alpha = 0.12f), OrangeAccent, status.labelVi)
        VehicleArrivalStatus.UNKNOWN -> Triple(Color(0xFF8E8E93).copy(alpha = 0.12f), Color(0xFF8E8E93), "Không đủ dữ liệu")
    }

    Surface(
        shape = RoundedCornerShape(TransitDimens.RadiusXs),
        color = bgColor,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun VehicleDetailMetricBox(
    label: String,
    value: String,
    sublabel: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(TransitDimens.RadiusMd),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = sublabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
    }
}

fun formatMetricDistance(meters: Double): String {
    return if (meters < 1000.0) {
        "${meters.toInt()}m"
    } else {
        String.format(Locale.US, "%.2f km", meters / 1000.0)
    }
}

fun formatMetricEta(etaSeconds: Int?): String {
    if (etaSeconds == null || etaSeconds < 0) return "--"
    if (etaSeconds < 60) return "< 1 phút"
    val mins = etaSeconds / 60
    val secs = etaSeconds % 60
    return if (secs == 0) "$mins phút" else "$mins p $secs s"
}
