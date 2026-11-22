package com.hcmcbustracker.feature.map

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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.hcmcbustracker.core.designsystem.component.ArrivalCard
import com.hcmcbustracker.core.designsystem.component.BusSearchBar
import com.hcmcbustracker.core.designsystem.component.OfflineBanner
import com.hcmcbustracker.core.designsystem.component.RouteCard
import com.hcmcbustracker.core.designsystem.component.SkeletonArrivalCard
import com.hcmcbustracker.core.designsystem.component.StopCard
import com.hcmcbustracker.core.designsystem.component.TransitGhostButton
import com.hcmcbustracker.core.designsystem.component.TransitSecondaryButton
import com.hcmcbustracker.core.designsystem.theme.BlueSecondary
import com.hcmcbustracker.core.designsystem.theme.CardShape
import com.hcmcbustracker.core.designsystem.theme.GreenPrimary
import com.hcmcbustracker.core.designsystem.theme.OrangeAccent
import com.hcmcbustracker.core.designsystem.theme.TransitDimens
import com.hcmcbustracker.core.designsystem.theme.borderSubtle
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

/**
 * Transit Hub - Home Screen (Tab 1).
 * Features multi-purpose search, quick license plate lookup, Live ETA at favorite stops,
 * hero interactive map preview, and popular routes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSearch: () -> Unit,
    onNavigateToMap: () -> Unit,
    onRouteSelected: (Int) -> Unit,
    onStopSelected: (Int) -> Unit,
    onQuickPredictionClick: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToRoutes: (() -> Unit)? = null,
    onNavigateToPlateSearch: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsBus,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = "HCMC Bus Tracker",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Giao thông công cộng TP.HCM",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Live status badge
                        Surface(
                            shape = RoundedCornerShape(TransitDimens.RadiusSm),
                            color = GreenPrimary.copy(alpha = 0.12f),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(GreenPrimary, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Trực tuyến",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = GreenPrimary
                                    )
                                )
                            }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = TransitDimens.SpacingBase),
            verticalArrangement = Arrangement.spacedBy(TransitDimens.SpacingBase)
        ) {
            item {
                OfflineBanner(isSyncing = state.isSyncing, isOffline = false)
            }

            // 1. All-in-One Multi-purpose Search Bar
            item {
                BusSearchBar(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChanged,
                    placeholder = "Tìm số tuyến, trạm dừng, biển số xe...",
                    onClick = onNavigateToSearch
                )
            }

            // 2. Hero Interactive Map Preview Card (Taps directly switch to Tab 2 Live Map)
            item {
                MapPreviewCard(onClick = onNavigateToMap)
            }

            // 3. Bento Quick Access Row
            item {
                BentoQuickActionsRow(
                    onPredictionClick = onQuickPredictionClick,
                    onLicensePlateClick = onNavigateToPlateSearch ?: onNavigateToSearch,
                    onTimetableClick = onNavigateToRoutes ?: onNavigateToSearch
                )
            }

            // 4. Live ETA at Favorite Stop Widget
            item {
                FavoriteStopLiveEtaSection(
                    favoriteEta = state.favoriteEta,
                    hasFavoriteStops = state.favoriteStops.isNotEmpty(),
                    onRefresh = viewModel::refreshFavoriteEta,
                    onStopSelected = onStopSelected,
                    onExploreStops = onNavigateToSearch
                )
            }

            // 5. Frequently Used / Popular Routes
            if (state.popularRoutes.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DirectionsBus,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(TransitDimens.IconSm)
                            )
                            Spacer(modifier = Modifier.width(TransitDimens.SpacingSm))
                            Text(
                                text = "Tuyến xe buýt phổ biến",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (onNavigateToRoutes != null) {
                            TransitGhostButton(
                                text = "Xem tất cả",
                                onClick = onNavigateToRoutes
                            )
                        }
                    }
                }

                items(
                    items = state.popularRoutes,
                    key = { it.id }
                ) { route ->
                    RouteCard(
                        route = route,
                        onClick = { onRouteSelected(route.id) }
                    )
                }
            }

            // 5b. Favorite Routes Section
            if (state.favoriteRoutes.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = OrangeAccent,
                            modifier = Modifier.size(TransitDimens.IconSm)
                        )
                        Spacer(modifier = Modifier.width(TransitDimens.SpacingSm))
                        Text(
                            text = "Tuyến xe yêu thích",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                items(
                    items = state.favoriteRoutes,
                    key = { "fav_route_${it.id}" }
                ) { route ->
                    RouteCard(
                        route = route,
                        isFavorite = true,
                        onFavoriteToggle = { viewModel.toggleFavoriteRoute(route.id) },
                        onClick = { onRouteSelected(route.id) }
                    )
                }
            }

            // 6. Favorite Stops Section
            if (state.favoriteStops.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = OrangeAccent,
                            modifier = Modifier.size(TransitDimens.IconSm)
                        )
                        Spacer(modifier = Modifier.width(TransitDimens.SpacingSm))
                        Text(
                            text = "Trạm dừng yêu thích",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                items(
                    items = state.favoriteStops,
                    key = { it.id }
                ) { stop ->
                    StopCard(
                        stop = stop,
                        isFavorite = true,
                        onFavoriteToggle = { viewModel.toggleFavoriteStop(stop.id) },
                        onClick = { onStopSelected(stop.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(TransitDimens.SpacingLg))
            }
        }
    }
}

/**
 * Live ETA widget displaying real-time bus arrivals for user's favorite stop.
 */
@Composable
private fun FavoriteStopLiveEtaSection(
    favoriteEta: FavoriteStopLiveEta?,
    hasFavoriteStops: Boolean,
    onRefresh: () -> Unit,
    onStopSelected: (Int) -> Unit,
    onExploreStops: () -> Unit
) {
    if (!hasFavoriteStops) {
        // Empty favorite stops prompt card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = TransitDimens.ElevationLow)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(TransitDimens.SpacingBase)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(TransitDimens.RadiusMd),
                        color = BlueSecondary.copy(alpha = 0.12f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = BlueSecondary,
                                modifier = Modifier.size(TransitDimens.IconMd)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(TransitDimens.SpacingMd))
                    Column {
                        Text(
                            text = "Dự báo ETA tại trạm đón",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Xem xe sắp đến ngay trên trang chủ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(TransitDimens.SpacingMd))

                Text(
                    text = "Lưu trạm bạn thường đón xe hàng ngày để theo dõi thời gian thực xe buýt đang đến trạm tại đây.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(TransitDimens.SpacingMd))

                TransitSecondaryButton(
                    text = "Khám phá & lưu trạm dừng",
                    icon = Icons.Default.Place,
                    onClick = onExploreStops,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        return
    }

    if (favoriteEta == null) return

    val stop = favoriteEta.stop

    Column(modifier = Modifier.fillMaxWidth()) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = BlueSecondary,
                    modifier = Modifier.size(TransitDimens.IconSm)
                )
                Spacer(modifier = Modifier.width(TransitDimens.SpacingSm))
                Column {
                    Text(
                        text = "Dự báo ETA tại trạm đón",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${stop.name} (${stop.code.ifBlank { "Trạm" }})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(TransitDimens.MinTouchTargetSize)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Làm mới ETA",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(TransitDimens.IconMd)
                )
            }
        }

        Spacer(modifier = Modifier.height(TransitDimens.SpacingSm))

        if (favoriteEta.isLoading) {
            SkeletonArrivalCard()
        } else {
            val vehiclesWithRoutes = favoriteEta.predictions.flatMap { pred ->
                pred.vehicles.map { vehicle ->
                    Pair(pred, vehicle)
                }
            }.sortedBy { it.second.etaSeconds }

            if (vehiclesWithRoutes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(TransitDimens.SpacingSm)) {
                    vehiclesWithRoutes.take(3).forEach { (pred, vehicle) ->
                        ArrivalCard(
                            routeNo = pred.routeNo,
                            destination = pred.routeName,
                            vehicle = vehicle,
                            onClick = { onStopSelected(stop.id) }
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(TransitDimens.SpacingBase),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Trạm ${stop.name} hiện chưa có xe đang đến trong 15-30 phút tới.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(TransitDimens.SpacingSm))
                        TransitGhostButton(
                            text = "Xem chi tiết trạm dừng",
                            onClick = { onStopSelected(stop.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern Bento Quick Actions Grid for HomeScreen.
 */
@Composable
private fun BentoQuickActionsRow(
    onPredictionClick: () -> Unit,
    onLicensePlateClick: () -> Unit,
    onTimetableClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Card 1: License Plate Lookup
        BentoActionCard(
            title = "Tra biển số",
            subtitle = "Định vị xe",
            icon = Icons.Default.Search,
            iconTint = OrangeAccent,
            containerColor = OrangeAccent.copy(alpha = 0.08f),
            onClick = onLicensePlateClick,
            modifier = Modifier.weight(1f)
        )

        // Card 2: Routes & Timetable
        BentoActionCard(
            title = "Lịch trình",
            subtitle = "Giờ xuất bến",
            icon = Icons.Default.CalendarToday,
            iconTint = GreenPrimary,
            containerColor = GreenPrimary.copy(alpha = 0.08f),
            onClick = onTimetableClick,
            modifier = Modifier.weight(1f)
        )

        // Card 3: ETA Prediction
        BentoActionCard(
            title = "Dự báo ETA",
            subtitle = "Xe sắp đến",
            icon = Icons.Default.AccessTime,
            iconTint = BlueSecondary,
            containerColor = BlueSecondary.copy(alpha = 0.08f),
            onClick = onPredictionClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BentoActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(96.dp)
            .clickable(onClick = onClick),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = TransitDimens.ElevationLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(TransitDimens.SpacingMd),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(TransitDimens.RadiusMd),
                color = containerColor,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(TransitDimens.IconSm)
                    )
                }
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Enhanced Hero Map Preview Card with Apple rounded corners and clear tap action.
 */
@Composable
fun MapPreviewCard(
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    remember { MapLibre.getInstance(context) }

    val mapView = remember {
        MapView(context).apply {
            getMapAsync { maplibreMap ->
                maplibreMap.setStyle("asset://osm_stis_style.json") {
                    maplibreMap.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(10.77079, 106.700152)) // District 1, HCMC
                        .zoom(14.0)
                        .build()

                    maplibreMap.uiSettings.isScrollGesturesEnabled = false
                    maplibreMap.uiSettings.isZoomGesturesEnabled = false
                    maplibreMap.uiSettings.isTiltGesturesEnabled = false
                    maplibreMap.uiSettings.isRotateGesturesEnabled = false
                }
            }
        }
    }

    DisposableEffect(mapView) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(TransitDimens.RadiusXl),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = TransitDimens.ElevationMedium)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize()
            )

            // Transparent overlay to capture clicks
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onClick)
            )

            // Preview Badge Header (Translucent Apple HIG Glass style)
            Surface(
                shape = RoundedCornerShape(TransitDimens.RadiusMd),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(TransitDimens.SpacingMd)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = TransitDimens.SpacingSm + 2.dp, vertical = TransitDimens.SpacingXs + 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(TransitDimens.IconSm)
                    )
                    Spacer(modifier = Modifier.width(TransitDimens.SpacingXs))
                    Text(
                        text = "Bản đồ xe buýt (Chạm để mở toàn màn hình)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // OpenStreetMap Attribution
            Surface(
                shape = RoundedCornerShape(TransitDimens.RadiusXs),
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(TransitDimens.SpacingSm)
            ) {
                Text(
                    text = "© OpenStreetMap contributors",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
