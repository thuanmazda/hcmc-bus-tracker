package com.hcmcbustracker.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hcmcbustracker.core.designsystem.theme.BadgeShape
import com.hcmcbustracker.core.designsystem.theme.GreenPrimary
import com.hcmcbustracker.core.designsystem.theme.PillShape
import com.hcmcbustracker.core.designsystem.theme.RedApproaching
import com.hcmcbustracker.core.designsystem.theme.TransitDimens

/**
 * Standard bus route identifier badge.
 * Provides high-contrast styling and consistent sizing across cards, sheets, and maps.
 */
@Composable
fun RouteBadge(
    routeNo: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = GreenPrimary,
    contentColor: Color = Color.White
) {
    Box(
        modifier = modifier
            .clip(BadgeShape)
            .background(backgroundColor)
            .padding(
                horizontal = TransitDimens.SpacingSm + 1.dp,
                vertical = TransitDimens.SpacingXs
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = routeNo,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold
            )
        )
    }
}

/**
 * Arrival ETA chip displaying approaching urgency with Apple HIG color coding.
 */
@Composable
fun ArrivalTimeChip(
    etaMinutes: Int,
    isApproaching: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isApproaching) RedApproaching else GreenPrimary
    val text = if (isApproaching) "< 1 phút" else "$etaMinutes phút"

    Surface(
        modifier = modifier,
        shape = PillShape,
        color = bgColor.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = TransitDimens.SpacingMd,
                vertical = TransitDimens.SpacingXs
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsBus,
                contentDescription = null,
                tint = bgColor,
                modifier = Modifier.size(TransitDimens.IconSm)
            )
            Spacer(modifier = Modifier.width(TransitDimens.SpacingXs))
            Text(
                text = text,
                color = bgColor,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

/**
 * Universal loading state indicator with transit-style progress indicator.
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String = "Đang tải dữ liệu..."
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(TransitDimens.Spacing2xl),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(TransitDimens.SpacingMd))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
