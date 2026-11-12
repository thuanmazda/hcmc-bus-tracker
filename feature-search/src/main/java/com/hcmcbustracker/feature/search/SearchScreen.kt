package com.hcmcbustracker.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hcmcbustracker.core.designsystem.component.EmptyState
import com.hcmcbustracker.core.designsystem.component.ErrorState
import com.hcmcbustracker.core.designsystem.component.LoadingState
import com.hcmcbustracker.core.designsystem.component.RouteCard
import com.hcmcbustracker.core.designsystem.component.StopCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onRouteSelected: (Int) -> Unit,
    onStopSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search Input Bar
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChanged,
            placeholder = { Text("Tìm số tuyến, tên tuyến, địa chỉ trạm...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Xóa nội dung tìm kiếm")
                    }
                } else {
                    IconButton(onClick = viewModel::triggerSync) {
                        Icon(Icons.Default.Refresh, contentDescription = "Đồng bộ dữ liệu")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Tabs: Tuyến xe / Điểm dừng
        TabRow(
            selectedTabIndex = state.selectedTab.ordinal,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = state.selectedTab == SearchTab.ROUTES,
                onClick = { viewModel.onTabSelected(SearchTab.ROUTES) },
                text = { Text("Tuyến xe buýt (${state.routes.size})") }
            )
            Tab(
                selected = state.selectedTab == SearchTab.STOPS,
                onClick = { viewModel.onTabSelected(SearchTab.STOPS) },
                text = { Text("Điểm dừng (${state.stops.size})") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.isSyncing) {
            LoadingState(message = "Đang đồng bộ dữ liệu giao thông công cộng TP.HCM...")
        }

        if (state.errorMessage != null) {
            ErrorState(
                message = state.errorMessage.orEmpty(),
                onRetry = viewModel::triggerSync
            )
        }

        // Result Lists
        Box(modifier = Modifier.fillMaxSize()) {
            when (state.selectedTab) {
                SearchTab.ROUTES -> {
                    if (state.routes.isEmpty() && !state.isLoading && !state.isSyncing) {
                        EmptyState(
                            title = "Không tìm thấy tuyến xe buýt",
                            message = if (state.query.isNotBlank()) "Không có tuyến nào khớp với '${state.query}'." else "Chưa có dữ liệu tuyến xe buýt.",
                            actionLabel = "Đồng bộ lại dữ liệu",
                            onActionClick = viewModel::triggerSync
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.routes, key = { it.id }) { route ->
                                RouteCard(
                                    route = route,
                                    isFavorite = state.favoriteRouteIds.contains(route.id),
                                    onFavoriteToggle = { viewModel.toggleFavoriteRoute(route.id) },
                                    onClick = { onRouteSelected(route.id) }
                                )
                            }
                        }
                    }
                }
                SearchTab.STOPS -> {
                    if (state.query.isBlank()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nhập tên đường, số nhà, mã trạm hoặc tên trạm để tìm kiếm",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (state.stops.isEmpty() && !state.isLoading && !state.isSyncing) {
                        EmptyState(
                            title = "Không tìm thấy trạm dừng",
                            message = "Không có trạm dừng nào khớp với từ khóa '${state.query}'.",
                            actionLabel = "Xóa tìm kiếm",
                            onActionClick = { viewModel.onQueryChanged("") }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.stops, key = { it.id }) { stop ->
                                StopCard(
                                    stop = stop,
                                    isFavorite = state.favoriteStopIds.contains(stop.id),
                                    onFavoriteToggle = { viewModel.toggleFavoriteStop(stop.id) },
                                    onClick = { onStopSelected(stop.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
