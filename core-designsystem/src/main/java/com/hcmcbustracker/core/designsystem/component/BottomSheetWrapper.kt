package com.hcmcbustracker.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hcmcbustracker.core.designsystem.theme.BottomSheetShape
import com.hcmcbustracker.core.designsystem.theme.HcmcBusTrackerTheme
import com.hcmcbustracker.core.designsystem.theme.TransitDimens

/**
 * Standard iOS-style grab handle / drag handle for bottom sheets.
 */
@Composable
fun BottomSheetDragHandle(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = TransitDimens.SpacingSm),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = TransitDimens.SheetHandleWidth,
                    height = TransitDimens.SheetHandleHeight
                )
                .clip(RoundedCornerShape(TransitDimens.RadiusPill))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
        )
    }
}

/**
 * Standard Bottom Sheet header bar with title, optional subtitle, and close button.
 */
@Composable
fun BottomSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onCloseClick: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = TransitDimens.SpacingBase,
                vertical = TransitDimens.SpacingSm
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(modifier = Modifier.width(TransitDimens.SpacingMd))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(TransitDimens.Spacing2xs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        if (onCloseClick != null) {
            IconButton(
                onClick = onCloseClick,
                modifier = Modifier.size(TransitDimens.MinTouchTargetSize)
            ) {
                Surface(
                    shape = RoundedCornerShape(TransitDimens.RadiusPill),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Surface wrapper for standard modal or persistent bottom sheets.
 * Provides top-rounded corners, subtle elevation, and integrated drag handle.
 */
@Composable
fun TransitBottomSheetContainer(
    modifier: Modifier = Modifier,
    showDragHandle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = BottomSheetShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = TransitDimens.ElevationHigh
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (showDragHandle) {
                BottomSheetDragHandle()
            }
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BottomSheetWrapperPreviewLight() {
    HcmcBusTrackerTheme(darkTheme = false) {
        TransitBottomSheetContainer {
            BottomSheetHeader(
                title = "Thông tin trạm dừng",
                subtitle = "Chợ Bến Thành, Q.1",
                onCloseClick = {}
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(TransitDimens.SpacingBase)
            ) {
                Text("Nội dung chi tiết...")
            }
        }
    }
}
