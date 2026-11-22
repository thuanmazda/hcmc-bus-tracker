package com.hcmcbustracker.core.designsystem.component

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hcmcbustracker.core.designsystem.theme.CardShape
import com.hcmcbustracker.core.designsystem.theme.HcmcBusTrackerTheme
import com.hcmcbustracker.core.designsystem.theme.TransitDimens
import com.hcmcbustracker.core.designsystem.theme.borderSubtle
import com.hcmcbustracker.core.model.Route

/**
 * Standard transit route card.
 * Modernized with Apple HIG subtle hairline borders, crisp typography, and touch target accessibility.
 */
@Composable
fun RouteCard(
    route: Route,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onFavoriteToggle: (() -> Unit)? = null
) {
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(TransitDimens.SpacingBase)
        ) {
            // Header: Badge + Route Name + Type
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RouteBadge(
                    routeNo = route.routeNo,
                    backgroundColor = parseRouteColor(route.color)
                )

                Spacer(modifier = Modifier.width(TransitDimens.SpacingMd))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = route.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!route.type.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(TransitDimens.Spacing2xs))
                        Text(
                            text = route.type.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (onFavoriteToggle != null) {
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier.size(TransitDimens.MinTouchTargetSize)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFavorite) "Bỏ yêu thích" else "Yêu thích",
                            tint = if (isFavorite) Color(0xFFFF3B30) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(TransitDimens.IconMd)
                        )
                    }
                }
            }

            // Terminals: Outbound / Inbound Descriptions
            if (!route.outBoundDesc.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(TransitDimens.SpacingSm))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Lộ trình",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(TransitDimens.IconXs)
                    )
                    Spacer(modifier = Modifier.width(TransitDimens.SpacingSm))
                    Text(
                        text = route.outBoundDesc.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(TransitDimens.SpacingSm))

            // Footer: Operating Time & Direction Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!route.operationTime.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(TransitDimens.IconXs)
                        )
                        Spacer(modifier = Modifier.width(TransitDimens.SpacingXs))
                        Text(
                            text = route.operationTime.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(TransitDimens.SpacingXs))
                }

                // 2-Way Direction Chip
                Surface(
                    shape = RoundedCornerShape(TransitDimens.RadiusXs),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = TransitDimens.SpacingSm, vertical = TransitDimens.Spacing2xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(TransitDimens.IconXs)
                        )
                        Spacer(modifier = Modifier.width(TransitDimens.SpacingXs))
                        Text(
                            text = "2 chiều",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Robust route hex color parser.
 * Falls back to default emerald green (0xFF00A86B) if null or unparseable.
 */
fun parseRouteColor(hexColor: String?): Color {
    if (hexColor.isNullOrBlank() || !hexColor.startsWith("#")) {
        return Color(0xFF00A86B)
    }
    return try {
        val clean = hexColor.removePrefix("#")
        val hexValue = clean.toLong(16)
        when (clean.length) {
            6 -> Color(0xFF000000 or hexValue)
            8 -> Color(hexValue)
            else -> Color(0xFF00A86B)
        }
    } catch (_: Exception) {
        Color(0xFF00A86B)
    }
}

@Preview(showBackground = true)
@Composable
private fun RouteCardPreviewLight() {
    HcmcBusTrackerTheme(darkTheme = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            RouteCard(route = DesignPreviewData.sampleRoute, onClick = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RouteCardPreviewDark() {
    HcmcBusTrackerTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
            RouteCard(route = DesignPreviewData.sampleRoute150, onClick = {})
        }
    }
}
