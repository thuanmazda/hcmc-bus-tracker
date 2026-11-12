package com.hcmcbustracker.feature.search

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
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.hcmcbustracker.core.designsystem.component.StopCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopsScreen(
    viewModel: StopsViewModel,
    onStopSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    val suggestions = listOf("Bến Thành", "Hàm Nghi", "Chợ Lớn", "Đại học Quốc Gia", "Bến xe Miền Đông", "Sân bay Tân Sơn Nhất")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Trạm dừng xe buýt",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::syncStops) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Đồng bộ danh sách trạm"
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
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                BusSearchBar(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChanged,
                    placeholder = "Tìm trạm dừng, tên đường (vd: Hàm Nghi, Chợ Lớn)..."
                )
            }

            // Quick Suggestion Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { keyword ->
                    FilterChip(
                        selected = state.query == keyword,
                        onClick = {
                            if (state.query == keyword) viewModel.onQueryChanged("")
                            else viewModel.onQueryChanged(keyword)
                        },
                        label = { Text(keyword, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Results Counter & Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.query.isBlank()) "Trạm dừng khu vực trung tâm (${state.stops.size})"
                    else "Kết quả tìm kiếm (${state.stops.size} trạm)",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Content Area
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading || (state.stops.isEmpty() && state.isSyncing) -> {
                        LoadingState(message = "Đang tải danh sách trạm dừng...")
                    }
                    state.errorMessage != null && state.stops.isEmpty() -> {
                        ErrorState(
                            message = state.errorMessage ?: "Đã xảy ra lỗi kết nối.",
                            onRetry = viewModel::syncStops
                        )
                    }
                    state.query.isNotBlank() && state.stops.isEmpty() -> {
                        EmptyState(
                            title = "Không tìm thấy trạm dừng nào",
                            message = "Không tìm thấy trạm nào khớp với \"${state.query}\". Bạn có thể tìm bằng tiếng Việt không dấu (vd: \"ham nghi\").",
                            actionLabel = "Xóa tìm kiếm",
                            onActionClick = { viewModel.onQueryChanged("") }
                        )
                    }
                    state.query.isBlank() && state.stops.isEmpty() -> {
                        EmptyState(
                            title = "Chưa có dữ liệu trạm dừng",
                            message = "Chưa có dữ liệu trạm dừng lưu trong máy. Bấm nút bên dưới để tải về từ hệ thống EBMS.",
                            actionLabel = "Tải dữ liệu ngay",
                            onActionClick = viewModel::syncStops
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                        ) {
                            items(
                                items = state.stops,
                                key = { it.id }
                            ) { stop ->
                                StopCard(
                                    stop = stop,
                                    isFavorite = state.favoriteStopIds.contains(stop.id),
                                    onFavoriteToggle = { viewModel.toggleFavorite(stop.id) },
                                    onClick = { onStopSelected(stop.id) },
                                    modifier = Modifier.padding(vertical = 5.dp)
                                )
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
}
