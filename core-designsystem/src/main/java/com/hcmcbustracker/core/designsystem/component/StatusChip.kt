package com.hcmcbustracker.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hcmcbustracker.core.designsystem.theme.PillShape
import com.hcmcbustracker.core.designsystem.theme.StatusDelayed
import com.hcmcbustracker.core.designsystem.theme.StatusFresh
import com.hcmcbustracker.core.designsystem.theme.StatusSlightDelay
import com.hcmcbustracker.core.designsystem.theme.StatusStale
import com.hcmcbustracker.core.designsystem.theme.TransitDimens
import com.hcmcbustracker.core.model.GpsLatencyCategory
import com.hcmcbustracker.core.model.PositionCompensationEngine

/**
 * GPS latency classification for realtime vehicle updates.
 * Directly supports Phase 13 Legal-Safe & Truthful GPS telemetry display.
 */
enum class GpsFreshnessStatus(val label: String, val color: Color) {
    FRESH("Vừa cập nhật", StatusFresh),
    SLIGHT_DELAY("Trễ 1-2 phút", StatusSlightDelay),
    DELAYED("Trễ > 2 phút", StatusDelayed),
    STALE("Mất tín hiệu", StatusStale);

    companion object {
        fun fromLatencyCategory(category: GpsLatencyCategory): GpsFreshnessStatus = when (category) {
            GpsLatencyCategory.FRESH -> FRESH
            GpsLatencyCategory.SLIGHT_DELAY -> SLIGHT_DELAY
            GpsLatencyCategory.DELAYED -> DELAYED
            GpsLatencyCategory.STALE, GpsLatencyCategory.UNKNOWN -> STALE
        }

        fun fromTimestamp(timestampMillis: Long?, nowMillis: Long = System.currentTimeMillis()): GpsFreshnessStatus {
            if (timestampMillis == null || timestampMillis <= 0L) return STALE
            val ageSec = (nowMillis - timestampMillis).coerceAtLeast(0L) / 1000L
            return fromLatencyCategory(PositionCompensationEngine.classifyGpsLatency(ageSec))
        }
    }
}

/**
 * Visual pill displaying GPS telemetry status with an animated/colored indicator dot.
 */
@Composable
fun GpsFreshnessChip(
    status: GpsFreshnessStatus,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = PillShape,
        color = status.color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = TransitDimens.SpacingSm, vertical = TransitDimens.SpacingXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(TransitDimens.Icon2xs)
                    .clip(CircleShape)
                    .background(status.color)
            )
            Spacer(modifier = Modifier.width(TransitDimens.SpacingXs))
            Text(
                text = status.label,
                color = status.color,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

/**
 * Selectable filter chip styled after Apple HIG segmented filters.
 */
@Composable
fun TransitFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    badgeCount: Int? = null
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = PillShape,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = TransitDimens.SpacingMd,
                vertical = TransitDimens.SpacingSm
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(TransitDimens.IconSm)
                )
                Spacer(modifier = Modifier.width(TransitDimens.SpacingXs))
            }

            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            )

            if (badgeCount != null && badgeCount > 0) {
                Spacer(modifier = Modifier.width(TransitDimens.SpacingXs))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (selected) contentColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = badgeCount.toString(),
                        color = if (selected) contentColor else MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

/**
 * Inline colored tag for transit metadata (e.g., "2 chiều", "Xe điện", "Miễn phí").
 */
@Composable
fun StatusTag(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(TransitDimens.RadiusXs),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(
                horizontal = TransitDimens.SpacingSm,
                vertical = TransitDimens.Spacing2xs
            )
        )
    }
}
