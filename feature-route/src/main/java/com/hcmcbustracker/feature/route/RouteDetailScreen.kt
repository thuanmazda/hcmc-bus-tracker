package com.hcmcbustracker.feature.route

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hcmcbustracker.core.designsystem.component.EmptyState
import com.hcmcbustracker.core.designsystem.component.ErrorState
import com.hcmcbustracker.core.designsystem.component.LoadingState
import com.hcmcbustracker.core.designsystem.component.RouteBadge
import com.hcmcbustracker.core.designsystem.component.RouteOperatingHoursChart
import com.hcmcbustracker.core.designsystem.theme.CardShape
import com.hcmcbustracker.core.designsystem.theme.TransitDimens
import com.hcmcbustracker.core.designsystem.theme.borderSubtle
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.Trip

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
    var selectedContentTab by remember { mutableIntStateOf(0) } // 0: Trạm dừng, 1: Lịch trình & Giờ xuất bến

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
                            Text(
                                text = it.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 16.sp
                            )
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
            // Direction Selection (Lượt đi / Lượt về)
            if (state.variants.isNotEmpty()) {
                val selectedIndex = state.variants.indexOfFirst { it.id == state.selectedVariant?.id }.coerceAtLeast(0)
                TabRow(
                    selectedTabIndex = selectedIndex,
                    modifier = Modifier.fillMaxWidth(),
                    indicator = { tabPositions ->
                        if (selectedIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                ) {
                    state.variants.forEachIndexed { index, variant ->
                        val isSelected = index == selectedIndex
                        Tab(
                            selected = isSelected,
                            onClick = { viewModel.selectVariant(variant.id) },
                            text = {
                                Text(
                                    text = variant.shortName.ifBlank { variant.name },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
                }
            }

            // Quick Map Banner: View Corridor & Active Vehicles
            if (state.route != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
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
                                text = "Xem bản đồ lộ trình & vị trí xe trực tiếp",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            val stopCountLabel = if (state.stops.isNotEmpty()) "${state.stops.size} trạm dừng • Chạm để mở bản đồ Tab 2" else "Đang chuẩn bị lộ trình..."
                            Text(
                                text = stopCountLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Content Sub-tabs: Trạm dừng vs Lịch trình & Giờ xuất bến
            TabRow(
                selectedTabIndex = selectedContentTab,
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                indicator = { tabPositions ->
                    if (selectedContentTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedContentTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            ) {
                Tab(
                    selected = selectedContentTab == 0,
                    onClick = { selectedContentTab = 0 },
                    text = {
                        Text(
                            text = "Trạm dừng (${state.stops.size})",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selectedContentTab == 0) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                )
                Tab(
                    selected = selectedContentTab == 1,
                    onClick = { selectedContentTab = 1 },
                    text = {
                        val tripCountText = if (state.trips.isNotEmpty()) " (${state.trips.size})" else ""
                        Text(
                            text = "Giờ xuất bến$tripCountText",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selectedContentTab == 1) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                )
            }

            // Main Content Area based on selected sub-tab
            when (selectedContentTab) {
                0 -> {
                    // TAB 0: STOPS SEQUENCE
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
                                item {
                                    // Terminal Summary Bar
                                    state.selectedVariant?.let { variant ->
                                        RouteVariantTerminalBar(variant = variant)
                                    }
                                }

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
                1 -> {
                    // TAB 1: TIMETABLE & DEPARTURE SCHEDULE
                    RouteTimetableContent(
                        state = state,
                        onRetry = viewModel::loadTimetableSchedule
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteVariantTerminalBar(variant: com.hcmcbustracker.core.model.RouteVariant) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.borderSubtle),
        shape = RoundedCornerShape(TransitDimens.RadiusMd),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2E7D32))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Bến đầu: ${variant.startStop.ifBlank { "--" }}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFC62828))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Bến cuối: ${variant.endStop.ifBlank { "--" }}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (variant.distanceMeters > 0.0 || variant.runningTimeMinutes > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Cự ly: ${String.format(java.util.Locale.US, "%.1f km", variant.distanceMeters / 1000.0)} • Thời gian hành trình: ~${variant.runningTimeMinutes} phút",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RouteTimetableContent(
    state: RouteDetailUiState,
    onRetry: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            // Visual 24-hour Operating Hours Chart
            RouteOperatingHoursChart(
                operationTime = state.route?.operationTime,
                headway = state.route?.headway,
                totalTrips = state.route?.totalTrips,
                tripDurationMinutes = state.selectedVariant?.runningTimeMinutes,
                hourlyTripCounts = state.hourlyTripCounts,
                modifier = Modifier.padding(16.dp)
            )
        }

        item {
            // Truthful Legal & Operational Notice
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = CardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(TransitDimens.CardBorderWidth, MaterialTheme.colorScheme.borderSubtle)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Lịch xuất bến tại đầu bến (Giờ cố định)",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Giờ xuất phát cố định theo biểu đồ chạy xe của đơn vị vận tải. Đây không phải vị trí GPS trực tiếp hay thời gian dự báo đến (ETA) của xe đang chạy trên đường.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (state.trips.isNotEmpty()) "Bảng giờ xuất bến từng chuyến (${state.trips.size} chuyến)" else "Lịch trình xuất bến",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        when {
            state.isTimetableLoading -> {
                item {
                    LoadingState(message = "Đang tải dữ liệu giờ xuất bến từ máy chủ EBMS...")
                }
            }
            state.trips.isNotEmpty() -> {
                // Table / Grid of Departure times: 4 columns
                val chunkedTrips = state.trips.chunked(4)
                itemsIndexed(chunkedTrips) { _, rowTrips ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (i in 0 until 4) {
                            if (i < rowTrips.size) {
                                val trip = rowTrips[i]
                                TripDepartureChip(
                                    trip = trip,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            state.timetableErrorMessage != null -> {
                item {
                    ErrorState(
                        message = state.timetableErrorMessage,
                        onRetry = onRetry
                    )
                }
            }
            !state.route?.operationTime.isNullOrBlank() -> {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = CardShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(TransitDimens.CardBorderWidth, MaterialTheme.colorScheme.borderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Thông tin vận hành",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Thời gian hoạt động: ${state.route?.operationTime ?: "--"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Giãn cách chuyến: ${state.route?.headway ?: "--"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!state.route?.totalTrips.isNullOrBlank()) {
                                Text(
                                    text = "Tổng số chuyến: ${state.route?.totalTrips}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Ghi chú: Máy chủ EBMS hiện cung cấp khung giờ vận hành chung cho tuyến này, chưa hỗ trợ chi tiết giờ xuất bến từng chuyến.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            else -> {
                item {
                    EmptyState(
                        title = "Chưa có thông tin giờ xuất bến",
                        message = "Dữ liệu lịch trình của tuyến này chưa được cập nhật từ hệ thống EBMS.",
                        actionLabel = "Tải lại",
                        onActionClick = onRetry
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TripDepartureChip(
    trip: Trip,
    modifier: Modifier = Modifier
) {
    val formattedTime = formatDepartureTime(trip.startTime)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(TransitDimens.RadiusSm),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.borderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

fun formatDepartureTime(raw: String): String {
    val clean = raw.trim()
    return if (clean.contains(":")) {
        val parts = clean.split(":")
        if (parts.size >= 2) "${parts[0].padStart(2, '0')}:${parts[1].padStart(2, '0')}" else clean
    } else clean
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
                        shape = RoundedCornerShape(TransitDimens.RadiusXs),
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}
