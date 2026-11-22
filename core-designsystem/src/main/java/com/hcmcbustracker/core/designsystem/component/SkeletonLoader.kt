package com.hcmcbustracker.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hcmcbustracker.core.designsystem.theme.CardShape
import com.hcmcbustracker.core.designsystem.theme.TransitDimens
import com.hcmcbustracker.core.designsystem.theme.shimmerBase
import com.hcmcbustracker.core.designsystem.theme.shimmerHighlight

/**
 * Shimmer animation modifier that creates an Apple-style smooth loading gradient wave.
 */
fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "ShimmerTransition")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerTranslate"
    )

    val baseColor = MaterialTheme.colorScheme.shimmerBase
    val highlightColor = MaterialTheme.colorScheme.shimmerHighlight

    val brush = Brush.linearGradient(
        colors = listOf(
            baseColor,
            highlightColor,
            baseColor
        ),
        start = Offset(translateAnim.value - 300f, translateAnim.value - 300f),
        end = Offset(translateAnim.value, translateAnim.value)
    )

    this.background(brush)
}

/**
 * Basic rectangular or rounded skeleton block.
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(TransitDimens.RadiusSm)
) {
    Box(
        modifier = modifier
            .clip(shape)
            .shimmerEffect()
    )
}

/**
 * Skeleton placeholder for a single line of text.
 */
@Composable
fun SkeletonText(
    modifier: Modifier = Modifier,
    widthFraction: Float = 0.7f,
    height: Dp = 14.dp,
    shape: Shape = RoundedCornerShape(TransitDimens.RadiusXs)
) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(shape)
            .shimmerEffect()
    )
}

/**
 * Skeleton placeholder for RouteCard.
 */
@Composable
fun SkeletonRouteCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = TransitDimens.ElevationLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(TransitDimens.SpacingBase)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Route Badge skeleton
                SkeletonBox(
                    modifier = Modifier.size(width = 44.dp, height = 28.dp),
                    shape = RoundedCornerShape(TransitDimens.RadiusSm)
                )

                Spacer(modifier = Modifier.width(TransitDimens.SpacingMd))

                Column(modifier = Modifier.weight(1f)) {
                    SkeletonText(widthFraction = 0.6f, height = 16.dp)
                    Spacer(modifier = Modifier.height(TransitDimens.SpacingXs))
                    SkeletonText(widthFraction = 0.35f, height = 12.dp)
                }
            }

            Spacer(modifier = Modifier.height(TransitDimens.SpacingMd))
            SkeletonText(widthFraction = 0.9f, height = 12.dp)
            Spacer(modifier = Modifier.height(TransitDimens.SpacingSm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonText(widthFraction = 0.3f, height = 12.dp)
                SkeletonBox(
                    modifier = Modifier.size(width = 60.dp, height = 20.dp),
                    shape = RoundedCornerShape(TransitDimens.RadiusXs)
                )
            }
        }
    }
}

/**
 * Skeleton placeholder for StopCard.
 */
@Composable
fun SkeletonStopCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = TransitDimens.ElevationLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(TransitDimens.SpacingBase)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                SkeletonBox(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(TransitDimens.RadiusMd)
                )

                Spacer(modifier = Modifier.width(TransitDimens.SpacingMd))

                Column(modifier = Modifier.weight(1f)) {
                    SkeletonText(widthFraction = 0.7f, height = 16.dp)
                    Spacer(modifier = Modifier.height(TransitDimens.SpacingXs))
                    SkeletonText(widthFraction = 0.9f, height = 12.dp)
                }
            }

            Spacer(modifier = Modifier.height(TransitDimens.SpacingMd))

            Row(horizontalArrangement = Arrangement.spacedBy(TransitDimens.SpacingSm)) {
                repeat(4) {
                    SkeletonBox(
                        modifier = Modifier.size(width = 36.dp, height = 22.dp),
                        shape = RoundedCornerShape(TransitDimens.RadiusXs)
                    )
                }
            }
        }
    }
}

/**
 * Skeleton placeholder for ArrivalCard.
 */
@Composable
fun SkeletonArrivalCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = TransitDimens.ElevationMedium)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(TransitDimens.SpacingBase),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkeletonBox(
                modifier = Modifier.size(width = 44.dp, height = 28.dp),
                shape = RoundedCornerShape(TransitDimens.RadiusSm)
            )

            Spacer(modifier = Modifier.width(TransitDimens.SpacingMd))

            Column(modifier = Modifier.weight(1f)) {
                SkeletonText(widthFraction = 0.6f, height = 16.dp)
                Spacer(modifier = Modifier.height(TransitDimens.SpacingXs))
                SkeletonText(widthFraction = 0.4f, height = 12.dp)
            }

            Spacer(modifier = Modifier.width(TransitDimens.SpacingMd))

            SkeletonBox(
                modifier = Modifier.size(width = 72.dp, height = 32.dp),
                shape = RoundedCornerShape(TransitDimens.RadiusPill)
            )
        }
    }
}
