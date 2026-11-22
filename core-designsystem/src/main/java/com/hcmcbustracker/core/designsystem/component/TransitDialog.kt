package com.hcmcbustracker.core.designsystem.component

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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.hcmcbustracker.core.designsystem.theme.HcmcBusTrackerTheme
import com.hcmcbustracker.core.designsystem.theme.RedApproaching
import com.hcmcbustracker.core.designsystem.theme.TransitDimens

/**
 * Standard Alert and Confirmation Dialog with Apple HIG rounded dialog styling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitAlertDialog(
    title: String,
    message: String,
    onDismissRequest: () -> Unit,
    confirmButtonText: String = "Xác nhận",
    onConfirm: () -> Unit,
    dismissButtonText: String? = "Hủy",
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    isDestructive: Boolean = false,
    properties: DialogProperties = DialogProperties()
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        Surface(
            shape = RoundedCornerShape(TransitDimens.RadiusSheet),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = TransitDimens.ElevationHigh,
            modifier = Modifier.fillMaxWidth().padding(horizontal = TransitDimens.SpacingLg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(TransitDimens.SpacingXl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (icon != null) {
                    val iconColor = if (isDestructive) RedApproaching else iconTint
                    Surface(
                        shape = CircleShape,
                        color = iconColor.copy(alpha = 0.12f),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(TransitDimens.IconLg)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(TransitDimens.SpacingBase))
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(TransitDimens.SpacingSm))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(TransitDimens.SpacingXl))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TransitDimens.SpacingMd)
                ) {
                    if (dismissButtonText != null) {
                        TransitSecondaryButton(
                            text = dismissButtonText,
                            onClick = onDismissRequest,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    TransitPrimaryButton(
                        text = confirmButtonText,
                        onClick = onConfirm,
                        containerColor = if (isDestructive) RedApproaching else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TransitDialogPreviewLight() {
    HcmcBusTrackerTheme(darkTheme = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            TransitAlertDialog(
                title = "Xóa lịch sử tìm kiếm?",
                message = "Tất cả các tìm kiếm tuyến xe và trạm dừng gần đây sẽ bị xóa.",
                onDismissRequest = {},
                confirmButtonText = "Xóa",
                onConfirm = {},
                isDestructive = true,
                icon = Icons.Default.Info
            )
        }
    }
}
