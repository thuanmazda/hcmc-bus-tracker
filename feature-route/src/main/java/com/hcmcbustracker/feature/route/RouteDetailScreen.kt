package com.hcmcbustracker.feature.route

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hcmcbustracker.core.designsystem.component.EmptyState
import com.hcmcbustracker.core.designsystem.component.LoadingState
import com.hcmcbustracker.core.designsystem.component.RouteBadge
import com.hcmcbustracker.core.model.BusStop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    viewModel: RouteDetailViewModel,
    onBackClick: () -> Unit,
    onStopClick: (Int) -> Unit,
    onMapClick: (Int) -> Unit = {},
    onTrackRouteOnMap: ((routeId: Int, varId: Int?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    val openMapAction: () -> Unit = {
        state.route?.let { route ->
            if (onTrackRouteOnMap != null) {
                onTrackRouteOnMap(route.id, state.selectedVariant?.id)
            } else {
                onMapClick(route.id)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        state.route?.let {
                            RouteBadge(routeNo = it.routeNo)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = it.name, maxLines = 1, fontSize = 16.sp)
                        } ?: Text("Chi tiết tuyến")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            imageVector = if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (state.isFavorite) "Bỏ lưu tuyến" else "Lưu tuyến yêu thích",
                            tint = if (state.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    state.route?.let {
                        IconButton(onClick = openMapAction) {
                            Icon(Icons.Default.Map, contentDescription = "Xem lộ trình và xe trên bản đồ")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Variant Selection Tabs (Lượt đi / Lượt về)
            if (state.variants.isNotEmpty()) {
                val selectedIndex = state.variants.indexOfFirst { it.id == state.selectedVariant?.id }.coerceAtLeast(0)
                TabRow(selectedTabIndex = selectedIndex, modifier = Modifier.fillMaxWidth()) {
                    state.variants.forEachIndexed { index, variant ->
                        Tab(
                            selected = index == selectedIndex,
                            onClick = { viewModel.selectVariant(variant.id) },
                            text = { Text(variant.shortName, maxLines = 1) }
                        )
                    }
                }
            }

            // Quick Action Bar to view full polyline & all active vehicles on the map
            if (state.route != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = openMapAction)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Xem bản đồ & vị trí xe trên toàn tuyến",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            val stopCountLabel = if (state.stops.isNotEmpty()) "${state.stops.size} trạm • Lộ trình offline sẵn sàng" else "Đang tải lộ trình..."
                            Text(
                                text = stopCountLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            when {
                state.isLoading && state.stops.isEmpty() -> {
                    LoadingState(message = "Đang tải danh sách trạm dừng của tuyến...")
                }
                state.stops.isEmpty() -> {
                    EmptyState(
                        title = "Chưa có danh sách trạm dừng",
                        message = "Chưa có dữ liệu trạm dừng cho hướng tuyến này. Bấm để thử tải lại.",
                        actionLabel = "Thử lại",
                        onActionClick = {
                            state.selectedVariant?.let { viewModel.selectVariant(it.id) }
                                ?: viewModel.refreshRoute()
                        }
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(state.stops, key = { _, stop -> stop.id }) { index, stop ->
                            RouteStopRow(
                                order = index + 1,
                                isFirst = index == 0,
                                isLast = index == state.stops.lastIndex,
                                stop = stop,
                                onClick = { onStopClick(stop.id) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                        }
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RouteStopRow(
    order: Int,
    isFirst: Boolean,
    isLast: Boolean,
    stop: BusStop,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val circleColor = when {
            isFirst -> Color(0xFF2E7D32) // Green for start
            isLast -> Color(0xFFC62828)  // Red for end
            else -> MaterialTheme.colorScheme.primary
        }

        val typeLabel = when {
            isFirst -> "Bến đầu"
            isLast -> "Bến cuối"
            else -> "Trạm #$order"
        }

        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(circleColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = order.toString(),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (stop.code.isNotBlank()) {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = stop.code,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = stop.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = circleColor,
                    fontWeight = FontWeight.Medium
                )
                if (stop.fullAddress.isNotBlank()) {
                    Text(
                        text = " • ${stop.fullAddress}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
