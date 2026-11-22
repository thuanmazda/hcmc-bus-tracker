package com.hcmcbustracker.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hcmcbustracker.core.designsystem.theme.CardShape
import com.hcmcbustracker.core.designsystem.theme.HcmcBusTrackerTheme
import com.hcmcbustracker.core.designsystem.theme.TransitDimens
import com.hcmcbustracker.core.designsystem.theme.borderSubtle
import com.hcmcbustracker.core.model.ArrivalVehicle
import kotlin.math.roundToInt

/**
 * Realtime vehicle arrival prediction card.
 * Modernized with Apple HIG subtle hairline borders, crisp typography, and GPS freshness dot.
 */
@Composable
fun ArrivalCard(
    routeNo: String,
    destination: String,
    vehicle: ArrivalVehicle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val freshnessStatus = GpsFreshnessStatus.fromTimestamp(vehicle.gpsTimestampMillis)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = TransitDimens.CardBorderWidth,
            color = MaterialTheme.colorScheme.borderSubtle
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = TransitDimens.ElevationLow,
            pressedElevation = TransitDimens.ElevationMedium
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(TransitDimens.SpacingBase),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: RouteBadge
            RouteBadge(
                routeNo = routeNo,
                modifier = Modifier.padding(end = TransitDimens.SpacingMd)
            )

            // Middle: Destination, Plate Number, Distance & Speed
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = destination,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(TransitDimens.SpacingXs))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TransitDimens.SpacingSm)
                ) {
                    // License Plate Chip with GPS freshness indicator dot
                    Surface(
                        shape = RoundedCornerShape(TransitDimens.RadiusXs),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = TransitDimens.SpacingSm, vertical = TransitDimens.Spacing2xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(freshnessStatus.color)
                            )
                            Spacer(modifier = Modifier.width(TransitDimens.SpacingXs))
                            Text(
                                text = vehicle.plateNumber.ifBlank { "Xe buýt" },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Distance
                    Text(
                        text = "Cách ${vehicle.formattedDistance}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Speed
                    if (vehicle.speedKmH > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(TransitDimens.Icon2xs)
                            )
                            Spacer(modifier = Modifier.width(TransitDimens.Spacing2xs))
                            Text(
                                text = "${vehicle.speedKmH.roundToInt()} km/h",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(TransitDimens.SpacingSm))

            // Right: ETA Chip
            ArrivalTimeChip(
                etaMinutes = vehicle.etaMinutes,
                isApproaching = vehicle.isApproaching
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ArrivalCardPreviewLight() {
    HcmcBusTrackerTheme(darkTheme = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            ArrivalCard(
                routeNo = "01",
                destination = "Bến xe Chợ Lớn",
                vehicle = DesignPreviewData.sampleApproachingVehicle,
                onClick = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ArrivalCardPreviewDark() {
    HcmcBusTrackerTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
            ArrivalCard(
                routeNo = "150",
                destination = "Ngã 3 Tân Vạn",
                vehicle = DesignPreviewData.sampleIncomingVehicle,
                onClick = {}
            )
        }
    }
}
