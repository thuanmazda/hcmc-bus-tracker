package com.hcmcbustracker.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hcmcbustracker.core.designsystem.theme.CardShape
import com.hcmcbustracker.core.designsystem.theme.HcmcBusTrackerTheme
import com.hcmcbustracker.core.designsystem.theme.TransitDimens
import com.hcmcbustracker.core.designsystem.theme.borderSubtle

/**
 * Modern search input field styled after Apple Maps search bars.
 * Features crisp hairline borders, high touch target accessibility, and trailing action slots.
 */
@Composable
fun BusSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Tìm số tuyến, trạm dừng, biển số xe...",
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onClearClick: (() -> Unit)? = { onQueryChange("") },
    onSearch: ((String) -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(TransitDimens.SearchBarHeight)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = TransitDimens.CardBorderWidth,
            color = MaterialTheme.colorScheme.borderSubtle
        ),
        tonalElevation = TransitDimens.ElevationLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TransitDimens.SpacingBase),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Tìm kiếm",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(TransitDimens.IconMd)
            )

            Spacer(modifier = Modifier.width(TransitDimens.SpacingMd))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = TransitDimens.SpacingXs),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        ),
                        maxLines = 1
                    )
                }

                if (onClick == null) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        enabled = enabled,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke(query) }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (query.isNotEmpty() && onClearClick != null) {
                IconButton(
                    onClick = onClearClick,
                    modifier = Modifier.size(TransitDimens.IconXl)
                ) {
                    Surface(
                        shape = RoundedCornerShape(TransitDimens.RadiusPill),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Xóa nội dung",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            } else if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BusSearchBarPreviewLight() {
    HcmcBusTrackerTheme(darkTheme = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            BusSearchBar(query = "", onQueryChange = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BusSearchBarPreviewDark() {
    HcmcBusTrackerTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
            BusSearchBar(query = "01 Bến Thành", onQueryChange = {})
        }
    }
}
