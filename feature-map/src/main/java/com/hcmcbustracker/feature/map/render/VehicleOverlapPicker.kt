package com.hcmcbustracker.feature.map.render

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hcmcbustracker.core.designsystem.component.RouteBadge
import com.hcmcbustracker.core.model.GpsLatencyCategory
import com.hcmcbustracker.core.model.PositionSource
import com.hcmcbustracker.core.model.VehicleArrivalStatus
import com.hcmcbustracker.feature.map.RouteActiveVehicleUiModel
import kotlin.math.hypot

data class ScreenCoord(val x: Float, val y: Float)

data class VehicleTapCandidate(
    val vehicle: RouteActiveVehicleUiModel,
    val screenCoord: ScreenCoord
)

sealed class OverlapTapOutcome {
    data object None : OverlapTapOutcome()
    data class Single(val vehicle: RouteActiveVehicleUiModel) : OverlapTapOutcome()
    data class Multiple(val vehicles: List<RouteActiveVehicleUiModel>) : OverlapTapOutcome()
}

/**
 * Pure screen-space proximity resolver for selecting exact buses when multiple markers overlap
 * at terminals or major junctions, WITHOUT shifting or altering true GPS coordinates.
 */
object VehicleOverlapResolver {

    fun resolveTap(
        tapPoint: ScreenCoord,
        candidates: List<VehicleTapCandidate>,
        density: Float,
        hitRadiusDp: Float = VehicleMapRenderConfig.OVERLAP_TAP_RADIUS_DP,
        selectedPlate: String? = null
    ): OverlapTapOutcome {
        if (candidates.isEmpty()) return OverlapTapOutcome.None
        val safeDensity = density.coerceAtLeast(1.0f)
        val radiusPx = hitRadiusDp * safeDensity

        val hits = candidates.mapNotNull { cand ->
            val distPx = hypot(
                (cand.screenCoord.x - tapPoint.x).toDouble(),
                (cand.screenCoord.y - tapPoint.y).toDouble()
            ).toFloat()
            if (distPx <= radiusPx) cand.vehicle to distPx else null
        }.sortedWith(
            compareBy<Pair<RouteActiveVehicleUiModel, Float>>(
                { it.second },
                { pair ->
                    val p = pair.first.telemetry
                    val isSel = selectedPlate != null && (
                        p.plateNumber.equals(selectedPlate, ignoreCase = true) ||
                            p.formattedPlate.equals(selectedPlate, ignoreCase = true)
                        )
                    if (isSel) 0 else 1
                },
                { it.first.telemetry.plateNumber }
            )
        ).map { it.first }

        return when (hits.size) {
            0 -> OverlapTapOutcome.None
            1 -> OverlapTapOutcome.Single(hits.first())
            else -> OverlapTapOutcome.Multiple(hits)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleOverlapPickerSheet(
    routeNo: String,
    vehicles: List<RouteActiveVehicleUiModel>,
    onSelectVehicle: (RouteActiveVehicleUiModel) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Chọn xe đang gần nhau (${vehicles.size} xe)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Chạm đúng biển số để theo dõi mà không làm lệch vị trí xe trên bản đồ.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = vehicles,
                    key = { it.telemetry.plateNumber }
                ) { item ->
                    val t = item.telemetry
                    val pos = item.position
                    val sourceLabel = when (pos.positionSource) {
                        PositionSource.LATENCY_COMPENSATED -> "Dự đoán bù trễ"
                        PositionSource.INTERPOLATED_ON_ROUTE -> "Ước tính tuyến"
                        PositionSource.HELD_AT_LAST_VALID_GPS -> "Giữ vị trí cũ"
                        PositionSource.UNKNOWN -> "Thiếu dữ liệu"
                    }
                    val statusColor = when (pos.latencyCategory) {
                        GpsLatencyCategory.FRESH -> Color(0xFF2E7D32)
                        GpsLatencyCategory.SLIGHT_DELAY -> Color(0xFF00695C)
                        GpsLatencyCategory.DELAYED -> Color(0xFFF57F17)
                        GpsLatencyCategory.STALE -> Color(0xFFC62828)
                        GpsLatencyCategory.UNKNOWN -> Color(0xFF616161)
                    }
                    val distText = pos.distanceToNextStopMeters?.let { m ->
                        if (m < 1000.0) "${m.toInt()}m" else String.format(java.util.Locale.US, "%.1f km", m / 1000.0)
                    } ?: "--"

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectVehicle(item) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (routeNo.isNotBlank()) {
                                    RouteBadge(
                                        routeNo = routeNo,
                                        backgroundColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Column {
                                    Text(
                                        text = t.formattedPlate,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val arrivalLabel = if (pos.arrivalStatus == VehicleArrivalStatus.UNKNOWN) {
                                        "Không đủ dữ liệu"
                                    } else {
                                        pos.arrivalStatus.labelVi
                                    }
                                    Text(
                                        text = "$arrivalLabel • $sourceLabel",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${pos.latencyCategory.labelVi} (${pos.gpsAgeSeconds}s)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = statusColor
                                )
                                Text(
                                    text = "${t.speedKmH.toInt()} km/h • $distText",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
