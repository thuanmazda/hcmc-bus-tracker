package com.hcmcbustracker.feature.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hcmcbustracker.core.designsystem.component.BusSearchBar
import com.hcmcbustracker.core.designsystem.component.EmptyState
import com.hcmcbustracker.core.designsystem.component.ErrorState
import com.hcmcbustracker.core.designsystem.component.LoadingState
import com.hcmcbustracker.core.designsystem.component.RouteBadge
import com.hcmcbustracker.core.designsystem.component.RouteCard
import com.hcmcbustracker.core.designsystem.component.StopCard
import com.hcmcbustracker.core.designsystem.theme.CardShape
import com.hcmcbustracker.core.designsystem.theme.GreenPrimary
import com.hcmcbustracker.core.designsystem.theme.OrangeAccent
import com.hcmcbustracker.core.designsystem.theme.RedApproaching
import com.hcmcbustracker.core.designsystem.theme.StatusDelayed
import com.hcmcbustracker.core.designsystem.theme.StatusFresh
import com.hcmcbustracker.core.designsystem.theme.StatusSlightDelay
import com.hcmcbustracker.core.designsystem.theme.StatusStale
import com.hcmcbustracker.core.designsystem.theme.TransitDimens
import com.hcmcbustracker.core.designsystem.theme.borderSubtle
import com.hcmcbustracker.core.model.ActiveVehicleSummary
import com.hcmcbustracker.core.model.GpsLatencyCategory
import com.hcmcbustracker.core.model.LicensePlateNormalizer
import com.hcmcbustracker.core.model.SearchHistoryItem
import com.hcmcbustracker.core.model.SearchHistoryType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onRouteSelected: (Int) -> Unit,
    onStopSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onVehicleSelected: ((ActiveVehicleSummary) -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(TransitDimens.SpacingBase)
    ) {
        // Unified Search Input Bar (Standardized BusSearchBar from Design System)
        BusSearchBar(
            query = state.query,
            onQueryChange = viewModel::onQueryChanged,
            placeholder = "Tìm số tuyến (01), trạm dừng, biển số xe...",
            onSearch = { q ->
                if (q.isNotBlank()) {
                    viewModel.recordSearchSelection(q, SearchHistoryType.GENERAL)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(TransitDimens.SpacingSm))

        // Categorized Group Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(TransitDimens.SpacingSm)
        ) {
            SearchTab.entries.forEach { tab ->
                val isSelected = state.selectedTab == tab
                val countText = when (tab) {
                    SearchTab.ALL -> ""
                    SearchTab.ROUTES -> " (${state.routes.size})"
                    SearchTab.STOPS -> " (${state.stops.size})"
                    SearchTab.VEHICLES -> " (${state.vehicles.size})"
                }
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.onTabSelected(tab) },
                    label = {
                        Text(
                            text = "${tab.label}$countText",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(TransitDimens.SpacingSm))

        if (state.isSyncing) {
            LoadingState(message = "Đang đồng bộ dữ liệu giao thông công cộng TP.HCM...")
        }

        if (state.errorMessage != null) {
            ErrorState(
                message = state.errorMessage.orEmpty(),
                onRetry = viewModel::triggerSync
            )
        }

        // Result Content Area
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                // When query is blank: show search guidance and tips
                state.query.isBlank() -> {
                    SearchGuidanceView(
                        history = state.searchHistory,
                        onHistoryItemClick = viewModel::onHistoryItemClicked,
                        onDeleteHistoryItem = viewModel::removeHistoryItem,
                        onClearAllHistory = viewModel::clearAllHistory,
                        onQuickRoute = { viewModel.onQueryChanged(it) },
                        onQuickPlate = { viewModel.onQueryChanged(it) }
                    )
                }

                // TAB: ALL (Unified Categorized View)
                state.selectedTab == SearchTab.ALL -> {
                    val hasResults = state.vehicles.isNotEmpty() || state.routes.isNotEmpty() || state.stops.isNotEmpty()
                    if (!hasResults && !state.isLoading && !state.isSyncing) {
                        if (LicensePlateNormalizer.isValidPlateSyntax(state.query) || state.query.startsWith("50") || state.query.startsWith("51")) {
                            PlateNotFoundCard(query = state.query, onClear = { viewModel.onQueryChanged("") })
                        } else {
                            EmptyState(
                                title = "Không tìm thấy kết quả",
                                message = "Không có tuyến buýt, trạm dừng hoặc biển số xe nào khớp với \"${state.query}\".",
                                actionLabel = "Xóa tìm kiếm",
                                onActionClick = { viewModel.onQueryChanged("") }
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(TransitDimens.SpacingSm)
                        ) {
                            // Section 1: Active Vehicles (if any matching)
                            if (state.vehicles.isNotEmpty()) {
                                item {
                                    SearchSectionHeader(
                                        title = "Xe buýt đang hoạt động",
                                        count = state.vehicles.size,
                                        icon = Icons.Default.DirectionsBus
                                    )
                                }
                                items(state.vehicles, key = { it.normalizedPlate }) { vehicle ->
                                    ActiveVehicleCard(
                                        vehicle = vehicle,
                                        onClick = {
                                            viewModel.recordSearchSelection(vehicle.formattedPlate, SearchHistoryType.PLATE)
                                            onVehicleSelected?.invoke(vehicle)
                                        }
                                    )
                                }
                            }

                            // Section 2: Routes
                            if (state.routes.isNotEmpty()) {
                                item {
                                    SearchSectionHeader(
                                        title = "Tuyến xe buýt",
                                        count = state.routes.size,
                                        icon = Icons.Default.Navigation
                                    )
                                }
                                items(state.routes, key = { it.id }) { route ->
                                    RouteCard(
                                        route = route,
                                        isFavorite = state.favoriteRouteIds.contains(route.id),
                                        onFavoriteToggle = { viewModel.toggleFavoriteRoute(route.id) },
                                        onClick = {
                                            viewModel.recordSearchSelection(route.routeNo, SearchHistoryType.ROUTE)
                                            onRouteSelected(route.id)
                                        }
                                    )
                                }
                            }

                            // Section 3: Bus Stops
                            if (state.stops.isNotEmpty()) {
                                item {
                                    SearchSectionHeader(
                                        title = "Trạm dừng",
                                        count = state.stops.size,
                                        icon = Icons.Default.Place
                                    )
                                }
                                items(state.stops, key = { it.id }) { stop ->
                                    StopCard(
                                        stop = stop,
                                        isFavorite = state.favoriteStopIds.contains(stop.id),
                                        onFavoriteToggle = { viewModel.toggleFavoriteStop(stop.id) },
                                        onClick = {
                                            viewModel.recordSearchSelection(stop.name, SearchHistoryType.STOP)
                                            onStopSelected(stop.id)
                                        }
                                    )
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(TransitDimens.SpacingBase))
                            }
                        }
                    }
                }

                // TAB: ROUTES
                state.selectedTab == SearchTab.ROUTES -> {
                    if (state.routes.isEmpty() && !state.isLoading && !state.isSyncing) {
                        EmptyState(
                            title = "Không tìm thấy tuyến xe buýt",
                            message = "Không có tuyến nào khớp với '${state.query}'.",
                            actionLabel = "Xóa tìm kiếm",
                            onActionClick = { viewModel.onQueryChanged("") }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(TransitDimens.SpacingSm)
                        ) {
                            items(state.routes, key = { it.id }) { route ->
                                RouteCard(
                                    route = route,
                                    isFavorite = state.favoriteRouteIds.contains(route.id),
                                    onFavoriteToggle = { viewModel.toggleFavoriteRoute(route.id) },
                                    onClick = {
                                        viewModel.recordSearchSelection(route.routeNo, SearchHistoryType.ROUTE)
                                        onRouteSelected(route.id)
                                    }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(TransitDimens.SpacingBase)) }
                        }
                    }
                }

                // TAB: STOPS
                state.selectedTab == SearchTab.STOPS -> {
                    if (state.stops.isEmpty() && !state.isLoading && !state.isSyncing) {
                        EmptyState(
                            title = "Không tìm thấy trạm dừng",
                            message = "Không có trạm dừng nào khớp với từ khóa '${state.query}'.",
                            actionLabel = "Xóa tìm kiếm",
                            onActionClick = { viewModel.onQueryChanged("") }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(TransitDimens.SpacingSm)
                        ) {
                            items(state.stops, key = { it.id }) { stop ->
                                StopCard(
                                    stop = stop,
                                    isFavorite = state.favoriteStopIds.contains(stop.id),
                                    onFavoriteToggle = { viewModel.toggleFavoriteStop(stop.id) },
                                    onClick = {
                                        viewModel.recordSearchSelection(stop.name, SearchHistoryType.STOP)
                                        onStopSelected(stop.id)
                                    }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(TransitDimens.SpacingBase)) }
                        }
                    }
                }

                // TAB: VEHICLES (PLATE LOOKUP)
                state.selectedTab == SearchTab.VEHICLES -> {
                    if (state.vehicles.isEmpty()) {
                        PlateNotFoundCard(query = state.query, onClear = { viewModel.onQueryChanged("") })
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(TransitDimens.SpacingSm)
                        ) {
                            item {
                                Text(
                                    text = "Phương tiện đang phát tín hiệu định vị (${state.vehicles.size}):",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(state.vehicles, key = { it.normalizedPlate }) { vehicle ->
                                ActiveVehicleCard(
                                    vehicle = vehicle,
                                    onClick = {
                                        viewModel.recordSearchSelection(vehicle.formattedPlate, SearchHistoryType.PLATE)
                                        onVehicleSelected?.invoke(vehicle)
                                    }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(TransitDimens.SpacingBase)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ActiveVehicleCard(
    vehicle: ActiveVehicleSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(TransitDimens.CardBorderWidth, MaterialTheme.colorScheme.borderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = TransitDimens.ElevationLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(TransitDimens.SpacingBase)
        ) {
            // Top Row: Plate + Route Badge + Freshness Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RouteBadge(routeNo = vehicle.routeNo)
                    Spacer(modifier = Modifier.width(TransitDimens.SpacingSm))
                    Text(
                        text = vehicle.formattedPlate,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val (badgeBg, badgeColor, badgeLabel) = when (vehicle.latencyCategory) {
                    GpsLatencyCategory.FRESH -> Triple(StatusFresh.copy(alpha = 0.12f), StatusFresh, "Trực tiếp")
                    GpsLatencyCategory.SLIGHT_DELAY -> Triple(StatusSlightDelay.copy(alpha = 0.14f), StatusSlightDelay, "Trễ nhẹ")
                    GpsLatencyCategory.DELAYED -> Triple(StatusDelayed.copy(alpha = 0.14f), StatusDelayed, "Trễ > 2p")
                    GpsLatencyCategory.STALE -> Triple(StatusStale.copy(alpha = 0.12f), StatusStale, "Dữ liệu cũ")
                    GpsLatencyCategory.UNKNOWN -> Triple(Color(0xFF8E8E93).copy(alpha = 0.12f), Color(0xFF8E8E93), "Không rõ")
                }

                Surface(
                    shape = RoundedCornerShape(TransitDimens.RadiusXs),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = badgeColor
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(TransitDimens.SpacingSm))

            // Route Name & Direction
            Text(
                text = vehicle.routeName.ifBlank { "Tuyến ${vehicle.routeNo}" },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (vehicle.directionName.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = vehicle.directionName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(TransitDimens.SpacingSm))

            // Metrics row: Speed, Checkpoint, CTA to view on map
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${vehicle.speedKmH.toInt()} km/h",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (vehicle.checkpointStopName.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${vehicle.checkpointStopName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(TransitDimens.SpacingSm))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Xem trên bản đồ",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlateNotFoundCard(
    query: String,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = TransitDimens.SpacingSm),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(TransitDimens.CardBorderWidth, MaterialTheme.colorScheme.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(TransitDimens.SpacingBase)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (query.isNotBlank()) "Chưa có định vị cho biển số \"$query\"" else "Tra cứu biển số xe buýt",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Phạm vi tra cứu biển số dựa trên các tuyến và điểm dừng đã nạp dữ liệu định vị trực tiếp trong phiên làm việc hiện tại.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Lưu ý: Không tìm thấy biển số trong danh sách không có nghĩa là xe đã ngừng hoạt động hay đang nghỉ bến. Xe có thể đang chạy trên tuyến chưa được mở hoặc tạm thời chưa gửi gói tin GPS về máy chủ EBMS.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Gợi ý: Bạn có thể chọn xem tuyến tương ứng để kích hoạt theo dõi vị trí xe trực tiếp trên bản đồ Live Map.",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchGuidanceView(
    history: List<SearchHistoryItem>,
    onHistoryItemClick: (SearchHistoryItem) -> Unit,
    onDeleteHistoryItem: (String) -> Unit,
    onClearAllHistory: () -> Unit,
    onQuickRoute: (String) -> Unit,
    onQuickPlate: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(TransitDimens.SpacingSm)
    ) {
        if (history.isNotEmpty()) {
            item {
                SearchHistorySection(
                    history = history,
                    onItemClick = onHistoryItemClick,
                    onDeleteItem = onDeleteHistoryItem,
                    onClearAll = onClearAllHistory
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = BorderStroke(TransitDimens.CardBorderWidth, MaterialTheme.colorScheme.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(TransitDimens.SpacingBase)) {
                    Text(
                        text = "Mẹo tìm kiếm thông minh",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Tuyến xe: Nhập số hiệu (vd: 01, 150, D4) hoặc tên bến.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Điểm dừng: Nhập tên đường, số nhà hoặc mã trạm (vd: Q1 031, Bạch Đằng).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Biển số xe: Nhập đầy đủ (50E-616.63) hoặc 5 số cuối (61663).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchHistorySection(
    history: List<SearchHistoryItem>,
    onItemClick: (SearchHistoryItem) -> Unit,
    onDeleteItem: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(TransitDimens.CardBorderWidth, MaterialTheme.colorScheme.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(TransitDimens.SpacingBase)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(TransitDimens.IconSm)
                    )
                    Spacer(modifier = Modifier.width(TransitDimens.SpacingSm))
                    Text(
                        text = "Tìm kiếm gần đây",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                TextButton(onClick = onClearAll) {
                    Text(
                        text = "Xóa tất cả",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(TransitDimens.SpacingXs))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TransitDimens.SpacingSm),
                verticalArrangement = Arrangement.spacedBy(TransitDimens.SpacingSm)
            ) {
                history.forEach { item ->
                    val typeIcon = when (item.type) {
                        SearchHistoryType.ROUTE -> Icons.Default.Navigation
                        SearchHistoryType.STOP -> Icons.Default.Place
                        SearchHistoryType.PLATE -> Icons.Default.DirectionsBus
                        SearchHistoryType.GENERAL -> Icons.Default.Search
                    }

                    InputChip(
                        selected = false,
                        onClick = { onItemClick(item) },
                        label = { Text(item.query) },
                        leadingIcon = {
                            Icon(
                                imageVector = typeIcon,
                                contentDescription = item.type.label,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Xóa",
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { onDeleteItem(item.query) }
                            )
                        }
                    )
                }
            }
        }
    }
}
