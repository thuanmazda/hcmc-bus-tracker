package com.hcmcbustracker.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Place
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
import com.hcmcbustracker.core.designsystem.theme.OrangeAccent
import com.hcmcbustracker.core.designsystem.theme.TransitDimens
import com.hcmcbustracker.core.designsystem.theme.borderSubtle
import com.hcmcbustracker.core.model.BusStop

/**
 * Standard bus stop card.
 * Modernized with Apple HIG subtle hairline borders, crisp typography, and touch target accessibility.
 */
@Composable
fun StopCard(
    stop: BusStop,
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
            // Top Row: Pin Icon, Stop Name, Code, and Favorite Toggle
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(TransitDimens.RadiusMd),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "Trạm dừng",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(TransitDimens.IconMd)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(TransitDimens.SpacingMd))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TransitDimens.SpacingXs)
                    ) {
                        if (stop.code.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(TransitDimens.RadiusXs),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = stop.code,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = TransitDimens.SpacingSm, vertical = TransitDimens.Spacing2xs)
                                )
                            }
                        }

                        Text(
                            text = stop.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }

                    Spacer(modifier = Modifier.height(TransitDimens.SpacingXs))

                    if (stop.fullAddress.isNotBlank()) {
                        Text(
                            text = stop.fullAddress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
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
                            contentDescription = if (isFavorite) "Bỏ lưu trạm" else "Lưu trạm yêu thích",
                            tint = if (isFavorite) OrangeAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(TransitDimens.IconMd)
                        )
                    }
                }
            }

            // Passing Routes Row
            if (stop.routes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(TransitDimens.SpacingMd))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(TransitDimens.SpacingXs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tuyến:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = TransitDimens.Spacing2xs)
                    )
                    stop.routes.forEach { routeNo ->
                        RouteBadge(
                            routeNo = routeNo,
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StopCardPreviewLight() {
    HcmcBusTrackerTheme(darkTheme = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            StopCard(
                stop = DesignPreviewData.sampleStop,
                isFavorite = true,
                onFavoriteToggle = {},
                onClick = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StopCardPreviewDark() {
    HcmcBusTrackerTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
            StopCard(
                stop = DesignPreviewData.sampleStopChoLon,
                isFavorite = false,
                onFavoriteToggle = {},
                onClick = {}
            )
        }
    }
}
