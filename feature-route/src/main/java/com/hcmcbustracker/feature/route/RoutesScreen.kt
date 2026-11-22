package com.hcmcbustracker.feature.route

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hcmcbustracker.core.designsystem.component.BusSearchBar
import com.hcmcbustracker.core.designsystem.component.EmptyState
import com.hcmcbustracker.core.designsystem.component.ErrorState
import com.hcmcbustracker.core.designsystem.component.LoadingState
import com.hcmcbustracker.core.designsystem.component.OfflineBanner
import com.hcmcbustracker.core.designsystem.component.RouteCard
import com.hcmcbustracker.core.designsystem.theme.TransitDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesScreen(
    viewModel: RoutesViewModel,
    onRouteSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DirectionsBus,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tuyến xe buýt TP.HCM",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::syncRoutes) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Đồng bộ danh sách tuyến"
                        )
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
            // Offline or Syncing Banner
            OfflineBanner(
                isSyncing = state.isSyncing,
                isOffline = false
            )

            // Search Bar
            Box(modifier = Modifier.padding(horizontal = TransitDimens.SpacingBase, vertical = TransitDimens.SpacingSm)) {
                BusSearchBar(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChanged,
                    placeholder = "Tìm số tuyến (vd: 01, 150) hoặc tên tuyến..."
                )
            }

            // Route Group Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = TransitDimens.SpacingBase, vertical = TransitDimens.Spacing2xs),
                horizontalArrangement = Arrangement.spacedBy(TransitDimens.SpacingSm)
            ) {
                RouteFilterType.entries.forEach { filter ->
                    val isSelected = filter == state.selectedFilter
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onFilterSelected(filter) },
                        label = {
                            Text(
                                text = filter.labelVi,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            // Results Counter & Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TransitDimens.SpacingBase, vertical = TransitDimens.SpacingXs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val headerText = when {
                    state.query.isNotBlank() -> "Kết quả tìm kiếm (${state.routes.size})"
                    state.selectedFilter != RouteFilterType.ALL -> "${state.selectedFilter.labelVi} (${state.routes.size} / ${state.totalRouteCount})"
                    else -> "Tất cả tuyến (${state.routes.size})"
                }

                Text(
                    text = headerText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(TransitDimens.Spacing2xs))

            // Main Content Area
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading || (state.routes.isEmpty() && state.isSyncing) -> {
                        LoadingState(message = "Đang tải danh sách tuyến xe...")
                    }
                    state.errorMessage != null && state.routes.isEmpty() -> {
                        ErrorState(
                            message = state.errorMessage ?: "Đã xảy ra lỗi kết nối.",
                            onRetry = viewModel::syncRoutes
                        )
                    }
                    state.query.isNotBlank() && state.routes.isEmpty() -> {
                        EmptyState(
                            title = "Không tìm thấy tuyến xe nào",
                            message = "Không có tuyến buýt nào khớp với \"${state.query}\". Hãy kiểm tra lại số hiệu hoặc tên tuyến.",
                            actionLabel = "Xóa tìm kiếm",
                            onActionClick = { viewModel.onQueryChanged("") }
                        )
                    }
                    state.selectedFilter != RouteFilterType.ALL && state.routes.isEmpty() -> {
                        EmptyState(
                            title = "Không có tuyến ${state.selectedFilter.labelVi.lowercase()}",
                            message = "Hiện chưa có tuyến xe buýt nào thuộc nhóm này trong hệ thống.",
                            actionLabel = "Xem tất cả tuyến",
                            onActionClick = { viewModel.onFilterSelected(RouteFilterType.ALL) }
                        )
                    }
                    state.query.isBlank() && state.routes.isEmpty() -> {
                        EmptyState(
                            title = "Chưa có dữ liệu tuyến xe",
                            message = "Chưa có dữ liệu tuyến xe buýt lưu trong máy. Bấm nút bên dưới để tải về từ hệ thống EBMS.",
                            actionLabel = "Tải dữ liệu ngay",
                            onActionClick = viewModel::syncRoutes
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = TransitDimens.SpacingBase)
                        ) {
                            items(
                                items = state.routes,
                                key = { it.id }
                            ) { route ->
                                RouteCard(
                                    route = route,
                                    isFavorite = route.id in state.favoriteRouteIds,
                                    onFavoriteToggle = { viewModel.toggleFavorite(route.id) },
                                    onClick = { onRouteSelected(route.id) },
                                    modifier = Modifier.padding(vertical = TransitDimens.SpacingXs)
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(TransitDimens.SpacingBase))
                            }
                        }
                    }
                }
            }
        }
    }
}
