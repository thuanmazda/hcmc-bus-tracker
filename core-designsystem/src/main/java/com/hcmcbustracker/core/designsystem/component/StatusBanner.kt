package com.hcmcbustracker.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hcmcbustracker.core.designsystem.theme.HcmcBusTrackerTheme
import com.hcmcbustracker.core.designsystem.theme.OrangeAccent
import com.hcmcbustracker.core.designsystem.theme.StatusSuccess
import com.hcmcbustracker.core.designsystem.theme.TransitDimens

/**
 * Animated feedback banner to communicate instant user action results or alerts.
 */
@Composable
fun StatusBanner(
    message: String,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    icon: ImageVector = Icons.Default.CheckCircle,
    color: Color = StatusSuccess
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = TransitDimens.SpacingBase, vertical = TransitDimens.SpacingXs)
                .clip(RoundedCornerShape(TransitDimens.RadiusMd))
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = TransitDimens.SpacingMd, vertical = TransitDimens.SpacingSm),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(TransitDimens.IconSm)
                )

                Spacer(modifier = Modifier.width(TransitDimens.SpacingSm))

                Text(
                    text = message,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = color
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusBannerPreview() {
    HcmcBusTrackerTheme {
        StatusBanner(
            message = "Đã lưu tuyến vào danh sách yêu thích",
            visible = true
        )
    }
}
