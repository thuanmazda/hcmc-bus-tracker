package com.hcmcbustracker.feature.prediction

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hcmcbustracker.core.designsystem.component.EmptyState
import com.hcmcbustracker.core.designsystem.component.ErrorState
import com.hcmcbustracker.core.designsystem.component.LoadingState
import com.hcmcbustracker.core.designsystem.component.RouteBadge
import com.hcmcbustracker.core.model.ArrivalVehicle
import com.hcmcbustracker.core.model.Prediction
import com.hcmcbustracker.core.model.PredictionFeedStatus
import com.hcmcbustracker.feature.prediction.notification.BusTrackingForegroundService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopPredictionScreen(
    viewModel: PredictionViewModel,
    onBackClick: () -> Unit,
    onRouteClick: (Int) -> Unit,
    onViewVehicleOnMap: ((routeId: Int, varId: Int, stopId: Int, plate: String, distanceMeters: Double, speedKmH: Double, dts: String?, etaSeconds: Int?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Strict lifecycle observer: pause polling on background, resume on foreground, cancel on exit
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startRealtimePolling()
                Lifecycle.Event.ON_PAUSE -> viewModel.stopRealtimePolling()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopRealtimePolling()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.stop?.name ?: "Dự báo xe đến trạm",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 16.sp
                        )
                        state.stop?.fullAddress?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
                            contentDescription = "Yêu thích",
                            tint = if (state.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Làm mới")
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
            // Live Status Banner with Data Freshness metrics
            StatusBanner(
                state = state,
                onRefresh = viewModel::refresh
            )

            when {
                state.status == PredictionFeedStatus.LOADING && state.predictions.isEmpty() -> {
                    LoadingState(message = "Đang kết nối hệ thống dự báo xe buýt EBMS...")
                }
                (state.status == PredictionFeedStatus.API_ERROR || state.status == PredictionFeedStatus.OFFLINE) && state.predictions.isEmpty() -> {
                    ErrorState(
                        message = state.errorMessage ?: "Không thể kết nối máy chủ",
                        onRetry = viewModel::refresh
                    )
                }
                state.status == PredictionFeedStatus.EMPTY || state.predictions.isEmpty() -> {
                    EmptyState(
                        title = "Chưa có xe đang đến",
                        message = "Hiện không có xe buýt nào đang di chuyển về trạm này theo dữ liệu thời gian thực từ EBMS.",
                        actionLabel = "Làm mới dữ liệu",
                        onActionClick = viewModel::refresh
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        items(state.predictions, key = { "${it.routeId}_${it.varId}" }) { prediction ->
                            val key = "${prediction.routeId}_${prediction.varId}"
                            val nextStops = state.expandedNextStops[key]

                            PredictionRouteCard(
                                prediction = prediction,
                                nextStops = nextStops,
                                onToggleNextStops = { viewModel.toggleNextStops(prediction) },
                                onRouteBadgeClick = { onRouteClick(prediction.routeId) },
                                onViewVehicleOnMap = onViewVehicleOnMap
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Top Status Banner showing real-time feed condition, elapsed time, and response timestamp.
 */
@Composable
private fun StatusBanner(
    state: PredictionUiState,
    onRefresh: () -> Unit
) {
    Surface(
        color = when (state.status) {
            PredictionFeedStatus.LIVE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            PredictionFeedStatus.STALE -> Color(0xFFFFF3E0) // Warm amber light
            PredictionFeedStatus.OFFLINE -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
            PredictionFeedStatus.API_ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            PredictionFeedStatus.LOADING -> MaterialTheme.colorScheme.surfaceVariant
            PredictionFeedStatus.EMPTY -> MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Status dot indicator
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = when (state.status) {
                                PredictionFeedStatus.LIVE -> Color(0xFF2E7D32) // Green
                                PredictionFeedStatus.STALE -> Color(0xFFF57C00) // Orange/Amber
                                PredictionFeedStatus.OFFLINE -> Color(0xFFD32F2F) // Red
                                PredictionFeedStatus.API_ERROR -> Color(0xFFD32F2F) // Red
                                PredictionFeedStatus.LOADING -> Color(0xFF1976D2) // Blue
                                PredictionFeedStatus.EMPTY -> Color(0xFF757575) // Gray
                            },
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = when (state.status) {
                            PredictionFeedStatus.LIVE -> "LIVE • Cập nhật ${if (state.secondsAgo == 0) "vừa xong" else "${state.secondsAgo}s trước"}"
                            PredictionFeedStatus.STALE -> "DỮ LIỆU CŨ • Cập nhật ${state.secondsAgo}s trước"
                            PredictionFeedStatus.OFFLINE -> "NGOẠI TUYẾN • Không có mạng internet"
                            PredictionFeedStatus.API_ERROR -> "LỖI KẾT NỐI • Máy chủ không phản hồi"
                            PredictionFeedStatus.LOADING -> "ĐANG TẢI DỮ LIỆU..."
                            PredictionFeedStatus.EMPTY -> "TRẠM TRỐNG • Chưa có xe đến"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (state.status) {
                            PredictionFeedStatus.LIVE -> Color(0xFF1B5E20)
                            PredictionFeedStatus.STALE -> Color(0xFFE65100)
                            PredictionFeedStatus.OFFLINE -> Color(0xFFB71C1C)
                            PredictionFeedStatus.API_ERROR -> Color(0xFFB71C1C)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    state.lastResponseTime?.let { time ->
                        val unchangedHint = if (state.isGpsTimestampUnchanged) " • GPS nguồn chưa đổi" else ""
                        Text(
                            text = "Nhận phản hồi: $time$unchangedHint",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state.isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Làm mới",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PredictionRouteCard(
    prediction: Prediction,
    nextStops: List<Prediction>?,
    onToggleNextStops: () -> Unit,
    onRouteBadgeClick: () -> Unit,
    onViewVehicleOnMap: ((routeId: Int, varId: Int, stopId: Int, plate: String, distanceMeters: Double, speedKmH: Double, dts: String?, etaSeconds: Int?) -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Route Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RouteBadge(
                    routeNo = prediction.routeNo,
                    modifier = Modifier.clickable(onClick = onRouteBadgeClick)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hướng đi: ${prediction.destinationName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = prediction.routeName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))

            // Display arriving vehicles (Xe sắp đến & Xe kế tiếp)
            if (prediction.vehicles.isEmpty()) {
                Text(
                    text = "Hiện không có thông tin xe đang chạy trên hướng tuyến này",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val context = LocalContext.current
                prediction.vehicles.forEachIndexed { index, vehicle ->
                    VehicleRow(
                        label = if (index == 0) "Xe sắp đến" else "Xe kế tiếp",
                        vehicle = vehicle,
                        onVehicleMapClick = if (onViewVehicleOnMap != null) {
                            {
                                onViewVehicleOnMap(
                                    prediction.routeId,
                                    prediction.varId,
                                    prediction.stopId,
                                    vehicle.plateNumber,
                                    vehicle.distanceMeters,
                                    vehicle.speedKmH,
                                    vehicle.timestampIso,
                                    vehicle.etaSeconds
                                )
                            }
                        } else null,
                        onTrackVehicle = {
                            BusTrackingForegroundService.startTracking(
                                context = context,
                                stopId = prediction.stopId,
                                stopName = prediction.stopName ?: "Trạm dừng",
                                routeNo = prediction.routeNo,
                                plateNumber = vehicle.plateNumber
                            )
                            Toast.makeText(
                                context,
                                "Bắt đầu theo dõi xe ${vehicle.formattedPlate} đến trạm",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                    if (index < prediction.vehicles.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // Expand / Collapse Next Stops Action
            if (prediction.vehicles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleNextStops)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (nextStops != null) "Thu gọn trạm tiếp theo ▲" else "Xem dự kiến trạm tiếp theo ▼",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Expanded Next Stops Section
            AnimatedVisibility(visible = nextStops != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Dự kiến các trạm tiếp theo của xe này:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    nextStops?.forEachIndexed { index, nextStop ->
                        val vehicleAtNextStop = nextStop.vehicles.firstOrNull()
                        val etaText = vehicleAtNextStop?.formattedEta ?: "--"
                        val distText = vehicleAtNextStop?.formattedDistance ?: ""

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}. ${nextStop.stopName}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (distText.isNotBlank()) {
                                    Text(
                                        text = distText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = etaText,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
fun VehicleRow(
    label: String,
    vehicle: ArrivalVehicle,
    onVehicleMapClick: (() -> Unit)? = null,
    onTrackVehicle: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Top: Plate & Approaching Chip / ETA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = vehicle.formattedPlate,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ETA Badge (Red for approaching < 60s, Primary for standard)
                Surface(
                    color = if (vehicle.isApproaching) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = vehicle.formattedEta,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (vehicle.isApproaching) {
                            MaterialTheme.colorScheme.onError
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Middle: Distance & Speed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Cách ${vehicle.formattedDistance}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = vehicle.formattedSpeed,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Bottom: Timestamps (GPS vs Response) & Stale Alert
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GPS: ${vehicle.gpsTimeFormatted} • Nhận: ${vehicle.responseTimeFormatted}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                if (vehicle.isGpsStale()) {
                    Surface(
                        color = Color(0xFFFFECB3), // Amber light
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "⚠️ Tín hiệu GPS cũ",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color(0xFFE65100),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            if (onVehicleMapClick != null || onTrackVehicle != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onVehicleMapClick != null) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = onVehicleMapClick)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(vertical = 7.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Xem bản đồ",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (onTrackVehicle != null) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = onTrackVehicle)
                                .background(
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(vertical = 7.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Theo dõi xe",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}
