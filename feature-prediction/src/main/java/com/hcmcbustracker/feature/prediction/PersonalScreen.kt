package com.hcmcbustracker.feature.prediction

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hcmcbustracker.core.designsystem.component.EmptyState
import com.hcmcbustracker.core.designsystem.component.RouteCard
import com.hcmcbustracker.core.designsystem.component.StopCard
import com.hcmcbustracker.core.designsystem.theme.CardShape
import com.hcmcbustracker.core.designsystem.theme.TransitDimens
import com.hcmcbustracker.core.designsystem.theme.borderSubtle
import com.hcmcbustracker.core.model.AppThemeMode
import com.hcmcbustracker.core.model.DatasetSyncStatus
import com.hcmcbustracker.core.model.SearchHistoryItem
import com.hcmcbustracker.core.model.SearchHistoryType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalScreen(
    viewModel: PersonalViewModel,
    onStopSelected: (Int) -> Unit,
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
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cá nhân & Cài đặt",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
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
            // Segmented Tabs: Trạm yêu thích / Tuyến yêu thích / Cài đặt
            TabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = state.selectedTab == PersonalTab.STOPS,
                    onClick = { viewModel.selectTab(PersonalTab.STOPS) },
                    text = { Text("Trạm (${state.favoriteStops.size})") }
                )
                Tab(
                    selected = state.selectedTab == PersonalTab.ROUTES,
                    onClick = { viewModel.selectTab(PersonalTab.ROUTES) },
                    text = { Text("Tuyến (${state.favoriteRoutes.size})") }
                )
                Tab(
                    selected = state.selectedTab == PersonalTab.SETTINGS,
                    onClick = { viewModel.selectTab(PersonalTab.SETTINGS) },
                    text = { Text("Cài đặt") }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                when (state.selectedTab) {
                    PersonalTab.STOPS -> {
                        FavoriteStopsTab(
                            stops = state.favoriteStops,
                            disabledStopIds = state.userPreferences.disabledStopNotificationIds,
                            onStopClick = onStopSelected,
                            onRemoveFavorite = viewModel::removeFavoriteStop,
                            onToggleNotification = viewModel::toggleStopNotification
                        )
                    }
                    PersonalTab.ROUTES -> {
                        FavoriteRoutesTab(
                            routes = state.favoriteRoutes,
                            onRouteClick = onRouteSelected,
                            onRemoveFavorite = viewModel::removeFavoriteRoute
                        )
                    }
                    PersonalTab.SETTINGS -> {
                        SettingsTab(
                            state = state,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteStopsTab(
    stops: List<com.hcmcbustracker.core.model.BusStop>,
    disabledStopIds: Set<Int>,
    onStopClick: (Int) -> Unit,
    onRemoveFavorite: (Int) -> Unit,
    onToggleNotification: (Int, Boolean) -> Unit
) {
    if (stops.isEmpty()) {
        EmptyState(
            title = "Chưa có trạm dừng yêu thích",
            message = "Nhấn vào biểu tượng trái tim tại bất kỳ trạm dừng nào để lưu vào danh sách truy cập nhanh.",
            actionLabel = null,
            onActionClick = null
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = stops, key = { it.id }) { stop ->
                val isMuted = disabledStopIds.contains(stop.id)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(bottom = 6.dp)) {
                        StopCard(
                            stop = stop,
                            isFavorite = true,
                            onFavoriteToggle = { onRemoveFavorite(stop.id) },
                            onClick = { onStopClick(stop.id) }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isMuted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.clickable { onToggleNotification(stop.id, isMuted) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (isMuted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isMuted) "Đã tắt chuông xe đến" else "Báo khi xe sắp đến",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = if (isMuted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteRoutesTab(
    routes: List<com.hcmcbustracker.core.model.Route>,
    onRouteClick: (Int) -> Unit,
    onRemoveFavorite: (Int) -> Unit
) {
    if (routes.isEmpty()) {
        EmptyState(
            title = "Chưa có tuyến xe buýt yêu thích",
            message = "Nhấn vào biểu tượng trái tim tại danh sách tuyến xe buýt để theo dõi lộ trình nhanh chóng.",
            actionLabel = null,
            onActionClick = null
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = routes, key = { it.id }) { route ->
                RouteCard(
                    route = route,
                    isFavorite = true,
                    onFavoriteToggle = { onRemoveFavorite(route.id) },
                    onClick = { onRouteClick(route.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsTab(
    state: PersonalUiState,
    viewModel: PersonalViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App Identity Header
        item {
            val context = LocalContext.current
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsBus,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "HCMC Bus Tracker",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                            Text(
                                text = "Hệ thống thông tin xe buýt thời gian thực TP.HCM",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Phiên bản 1.1.0 (Build 20261115)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/thuanmazda/hcmc-bus-tracker"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Xem mã nguồn dự án trên GitHub", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Theme Mode Selector
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Brightness4,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Giao diện hiển thị",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.userPreferences.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.displayName) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Polling Interval Selector
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Chu kỳ làm mới dự báo trực tiếp",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tần suất tự động yêu cầu vị trí và ETA xe từ máy chủ EBMS khi đang theo dõi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(10, 15, 30, 60).forEach { seconds ->
                            FilterChip(
                                selected = state.userPreferences.pollingIntervalSeconds == seconds,
                                onClick = { viewModel.setPollingIntervalSeconds(seconds) },
                                label = { Text("${seconds}s${if (seconds == 15) " (mặc định)" else ""}") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Notifications Setting & Configurable Thresholds
        item {
            val context = LocalContext.current
            var hasNotificationPermission by remember {
                mutableStateOf(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    } else {
                        NotificationManagerCompat.from(context).areNotificationsEnabled()
                    }
                )
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                hasNotificationPermission = isGranted
                if (!isGranted) {
                    viewModel.setNotificationsEnabled(false)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Thông báo xe sắp đến trạm",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Gửi thông báo ước lượng khi xe đang tiến gần điểm dừng",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = state.userPreferences.notificationsEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && !hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                viewModel.setNotificationsEnabled(enabled)
                            }
                        )
                    }

                    if (state.userPreferences.notificationsEnabled && !hasNotificationPermission) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(10.dp)
                                    .clickable {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.fromParts("package", context.packageName, null)
                                            }
                                            context.startActivity(intent)
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Quyền thông báo Android hiện chưa được cấp",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "Nhấn vào đây để cấp quyền nhằm nhận cảnh báo khi xe đến gần.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    if (state.userPreferences.notificationsEnabled) {
                        Spacer(modifier = Modifier.height(14.dp))

                        // Time threshold selection
                        Text(
                            text = "Ngưỡng thời gian cảnh báo:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(3, 5, 10).forEach { mins ->
                                FilterChip(
                                    selected = state.userPreferences.alertTimeMinutes == mins,
                                    onClick = {
                                        viewModel.setArrivalAlertThresholds(
                                            mins,
                                            state.userPreferences.alertDistanceMeters
                                        )
                                    },
                                    label = { Text("$mins phút") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Distance threshold selection
                        Text(
                            text = "Ngưỡng khoảng cách cảnh báo:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(500.0, 1000.0, 1500.0).forEach { dist ->
                                val label = if (dist >= 1000.0) "${(dist / 1000).toInt()} km" else "${dist.toInt()} m"
                                FilterChip(
                                    selected = state.userPreferences.alertDistanceMeters == dist,
                                    onClick = {
                                        viewModel.setArrivalAlertThresholds(
                                            state.userPreferences.alertTimeMinutes,
                                            dist
                                        )
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "ℹ️ Lưu ý: Ứng dụng chỉ phát thông báo khi có dữ liệu GPS mới (< 3 phút) và xe cách trạm dưới ngưỡng đã chọn. Không tự ý polling ngầm liên tục gây hao pin.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Search History Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Lịch sử tìm kiếm (${state.searchHistory.size} mục)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        if (state.searchHistory.isNotEmpty()) {
                            TextButton(onClick = viewModel::clearSearchHistory) {
                                Text(
                                    text = "Xóa tất cả",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (state.searchHistory.isEmpty()) {
                        Text(
                            text = "Chưa có truy vấn tìm kiếm nào được lưu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.searchHistory.forEach { item ->
                                val typeIcon = when (item.type) {
                                    SearchHistoryType.ROUTE -> Icons.Default.Navigation
                                    SearchHistoryType.STOP -> Icons.Default.Place
                                    SearchHistoryType.PLATE -> Icons.Default.DirectionsBus
                                    SearchHistoryType.GENERAL -> Icons.Default.Search
                                }
                                InputChip(
                                    selected = false,
                                    onClick = {},
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
                                                .clickable { viewModel.removeSearchHistoryItem(item.query) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Dữ liệu ứng dụng -> Dữ liệu tuyến xe
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Dữ liệu ứng dụng",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Dữ liệu tuyến xe ngoại tuyến",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        val meta = state.staticDatasetMetadata
                        val statusBadge = when {
                            state.isSyncing -> Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), "Đang cập nhật")
                            meta?.lastSyncStatus == DatasetSyncStatus.VALID -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Hợp lệ")
                            meta?.lastSyncStatus == DatasetSyncStatus.FAILED -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "Lỗi")
                            else -> Triple(Color(0xFFF5F5F5), Color(0xFF616161), "Chưa khởi tạo")
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = statusBadge.first
                        ) {
                            Text(
                                text = statusBadge.second.let { statusBadge.third },
                                color = statusBadge.second,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "• Dữ liệu ngoại tuyến: Danh mục toàn bộ tuyến xe, biến thể và trạm dừng được lưu trong cơ sở dữ liệu SQLite/Room trên thiết bị để tra cứu không cần kết nối mạng.\n• Dữ liệu trực tiếp: Dự báo thời gian đến trạm (ETA) và vị trí xe GPS được truy vấn trực tiếp từ EBMS theo phiên làm việc, không lưu đè lên dữ liệu tĩnh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val meta = state.staticDatasetMetadata
                    val sdf = SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.getDefault())
                    val updatedAtText = (meta?.datasetUpdatedAt ?: state.lastSyncTimestamp)?.let {
                        sdf.format(Date(it))
                    } ?: "Chưa có dữ liệu"

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            DatasetInfoRow("Phiên bản:", meta?.datasetVersion ?: "--")
                            DatasetInfoRow("Cập nhật gần nhất:", updatedAtText)
                            DatasetInfoRow("Nguồn dữ liệu:", meta?.source ?: "https://apicms.ebms.vn")
                            DatasetInfoRow("Tổng số tuyến:", "${meta?.totalRoutes ?: 0} tuyến")
                            DatasetInfoRow("Tổng số chiều tuyến:", "${meta?.totalVariants ?: 0} biến thể")
                            DatasetInfoRow("Tổng số điểm dừng:", "${meta?.totalStops ?: 0} trạm")
                            DatasetInfoRow("Polyline hợp lệ:", "${meta?.totalPolylines ?: 0} lộ trình")
                        }
                    }

                    // Active update progress
                    state.syncProgress?.let { progress ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = progress.phase,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val fraction = if (progress.totalSteps > 0) {
                            progress.completedSteps.toFloat() / progress.totalSteps.toFloat()
                        } else 0f
                        LinearProgressIndicator(
                            progress = { fraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (state.syncSuccessMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.syncSuccessMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (state.errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = viewModel::updateFullStaticDataset,
                            enabled = !state.isSyncing,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Đang cập nhật...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Cập nhật dữ liệu")
                            }
                        }

                        if (state.isSyncing && state.syncProgress?.isCancellable == true) {
                            OutlinedButton(onClick = viewModel::cancelDatasetUpdate) {
                                Text("Hủy")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = viewModel::clearCache,
                        enabled = !state.isClearingCache && !state.isSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Xóa bộ nhớ đệm lộ trình")
                    }
                }
            }
        }

        // Attribution & Legal
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bản quyền & Ghi nhận nguồn dữ liệu",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "• Nguồn dữ liệu: Trung tâm Quản lý Giao thông công cộng TP.HCM (buyttphcm.com.vn / EBMS API)\n• Bản đồ số: OpenStreetMap contributors & MapLibre Native SDK\n• Bản quyền dữ liệu bản đồ: © OpenStreetMap contributors",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "⚠️ Miễn trừ trách nhiệm: Thời gian đến trạm (ETA) và khoảng cách là dự báo ước lượng dựa trên dữ liệu GPS từ hệ thống EBMS, có thể thay đổi tùy tình trạng giao thông thực tế tại TP.HCM.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DatasetInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
