package com.hcmcbustracker.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hcmcbustracker.core.designsystem.theme.HcmcBusTrackerTheme
import com.hcmcbustracker.core.designsystem.theme.OrangeAccent
import com.hcmcbustracker.core.designsystem.theme.TransitDimens

@Composable
fun OfflineBanner(
    isSyncing: Boolean,
    isOffline: Boolean,
    modifier: Modifier = Modifier,
    customMessage: String? = null
) {
    AnimatedVisibility(
        visible = isSyncing || isOffline,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        val backgroundColor = if (isOffline) {
            OrangeAccent.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        }

        val contentColor = if (isOffline) {
            OrangeAccent
        } else {
            MaterialTheme.colorScheme.primary
        }

        val text = customMessage ?: when {
            isSyncing -> "Đang cập nhật lộ trình xe buýt..."
            isOffline -> "Đang dùng dữ liệu ngoại tuyến (offline)"
            else -> ""
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(horizontal = TransitDimens.SpacingBase, vertical = TransitDimens.SpacingSm),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(TransitDimens.IconSm),
                        strokeWidth = 2.dp,
                        color = contentColor
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(TransitDimens.IconSm)
                    )
                }

                Spacer(modifier = Modifier.width(TransitDimens.SpacingMd))

                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = contentColor
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OfflineBannerPreviewSyncing() {
    HcmcBusTrackerTheme {
        OfflineBanner(isSyncing = true, isOffline = false)
    }
}

@Preview(showBackground = true)
@Composable
private fun OfflineBannerPreviewOffline() {
    HcmcBusTrackerTheme {
        OfflineBanner(isSyncing = false, isOffline = true)
    }
}
