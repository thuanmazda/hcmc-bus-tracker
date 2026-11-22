package com.hcmcbustracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hcmcbustracker.core.database.repository.BusRepository
import com.hcmcbustracker.core.designsystem.theme.HcmcBusTrackerTheme
import com.hcmcbustracker.core.model.AppThemeMode
import com.hcmcbustracker.core.model.UserPreferences
import com.hcmcbustracker.feature.map.HomeScreen
import com.hcmcbustracker.feature.map.HomeViewModel
import com.hcmcbustracker.feature.map.MapScreen
import com.hcmcbustracker.feature.map.MapViewModel
import com.hcmcbustracker.feature.prediction.PersonalScreen
import com.hcmcbustracker.feature.prediction.PersonalViewModel
import com.hcmcbustracker.feature.prediction.PredictionViewModel
import com.hcmcbustracker.feature.prediction.StopPredictionScreen
import com.hcmcbustracker.feature.route.RouteDetailScreen
import com.hcmcbustracker.feature.route.RouteDetailViewModel
import com.hcmcbustracker.feature.route.RoutesScreen
import com.hcmcbustracker.feature.route.RoutesViewModel
import com.hcmcbustracker.feature.search.SearchScreen
import com.hcmcbustracker.feature.search.SearchViewModel
import com.hcmcbustracker.feature.search.StopsScreen
import com.hcmcbustracker.feature.search.StopsViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var repository: BusRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val userPrefs by repository.getUserPreferences().collectAsState(initial = UserPreferences())
            val darkTheme = when (userPrefs.themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            HcmcBusTrackerTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BusTrackerAppNavHost()
                }
            }
        }
    }
}

@Composable
fun BusTrackerAppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "main_tabs"
    ) {
        composable("main_tabs") {
            MainTabsShell(
                onRouteSelected = { routeId ->
                    navController.navigate("route_detail/$routeId")
                },
                onStopSelected = { stopId ->
                    navController.navigate("stop_prediction/$stopId")
                },
                onNavigateToSearch = {
                    navController.navigate("search")
                },
                onNavigateToMap = {
                    navController.navigate("interactive_map")
                }
            )
        }

        composable("interactive_map") {
            val mapViewModel: MapViewModel = hiltViewModel()
            MapScreen(
                viewModel = mapViewModel,
                onStopClicked = { stopId ->
                    navController.navigate("stop_prediction/$stopId")
                }
            )
        }

        composable(
            route = "interactive_map?routeId={routeId}&varId={varId}&stopId={stopId}&plate={plate}&distance={distance}&speed={speed}&dts={dts}&eta={eta}",
            arguments = listOf(
                navArgument("routeId") {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("varId") {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("stopId") {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("plate") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("distance") {
                    type = NavType.StringType
                    defaultValue = "0.0"
                },
                navArgument("speed") {
                    type = NavType.StringType
                    defaultValue = "0.0"
                },
                navArgument("dts") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("eta") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStackEntry ->
            val routeId = backStackEntry.arguments?.getInt("routeId")?.takeIf { it > 0 }
            val varId = backStackEntry.arguments?.getInt("varId")?.takeIf { it > 0 }
            val stopId = backStackEntry.arguments?.getInt("stopId")?.takeIf { it > 0 }
            val plate = backStackEntry.arguments?.getString("plate")?.takeIf { it.isNotBlank() }
            val distance = backStackEntry.arguments?.getString("distance")?.toDoubleOrNull() ?: 0.0
            val speed = backStackEntry.arguments?.getString("speed")?.toDoubleOrNull() ?: 0.0
            val rawDts = backStackEntry.arguments?.getString("dts")?.takeIf { it.isNotBlank() }
            val dts = if (rawDts != null) {
                try {
                    java.net.URLDecoder.decode(rawDts, "UTF-8")
                } catch (_: Exception) {
                    rawDts
                }
            } else null
            val eta = backStackEntry.arguments?.getInt("eta")?.takeIf { it >= 0 }

            val mapViewModel: MapViewModel = hiltViewModel()
            MapScreen(
                viewModel = mapViewModel,
                initialRouteId = routeId,
                initialVarId = varId,
                initialStopId = stopId,
                initialVehiclePlate = plate,
                initialVehicleDistance = distance,
                initialVehicleSpeed = speed,
                initialVehicleDts = dts,
                initialVehicleEta = eta,
                onStopClicked = { clickedStopId ->
                    navController.navigate("stop_prediction/$clickedStopId")
                }
            )
        }

        composable("search") {
            val searchViewModel: SearchViewModel = hiltViewModel()
            SearchScreen(
                viewModel = searchViewModel,
                onRouteSelected = { routeId ->
                    navController.navigate("route_detail/$routeId")
                },
                onStopSelected = { stopId ->
                    navController.navigate("stop_prediction/$stopId")
                },
                onVehicleSelected = { vehicle ->
                    val encodedDts = if (!vehicle.gpsTimestampIso.isNullOrBlank()) {
                        try {
                            java.net.URLEncoder.encode(vehicle.gpsTimestampIso, "UTF-8")
                        } catch (_: Exception) {
                            vehicle.gpsTimestampIso
                        }
                    } else ""
                    navController.navigate(
                        "interactive_map?routeId=${vehicle.routeId}&varId=${vehicle.varId}&stopId=${vehicle.checkpointStopId}&plate=${vehicle.plateNumber}&distance=${vehicle.distanceMeters}&speed=${vehicle.speedKmH}&dts=$encodedDts&eta=${vehicle.etaSeconds}"
                    )
                }
            )
        }

        composable(
            route = "route_detail/{routeId}",
            arguments = listOf(navArgument("routeId") { type = NavType.IntType })
        ) {
            val viewModel: RouteDetailViewModel = hiltViewModel()
            RouteDetailScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onStopClick = { stopId ->
                    navController.navigate("stop_prediction/$stopId")
                },
                onMapClick = { routeId ->
                    navController.navigate("interactive_map?routeId=$routeId")
                },
                onTrackRouteOnMap = { routeId, varId ->
                    val varQuery = if (varId != null && varId > 0) "&varId=$varId" else ""
                    navController.navigate("interactive_map?routeId=$routeId$varQuery")
                }
            )
        }

        composable(
            route = "stop_prediction/{stopId}",
            arguments = listOf(navArgument("stopId") { type = NavType.IntType })
        ) {
            val viewModel: PredictionViewModel = hiltViewModel()
            StopPredictionScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onRouteClick = { routeId ->
                    navController.navigate("route_detail/$routeId")
                },
                onViewVehicleOnMap = { routeId, varId, stopId, plate, distance, speed, dts, eta ->
                    val encodedDts = if (!dts.isNullOrBlank()) {
                        try {
                            java.net.URLEncoder.encode(dts, "UTF-8")
                        } catch (_: Exception) {
                            dts
                        }
                    } else ""
                    val etaParam = eta ?: -1
                    navController.navigate(
                        "interactive_map?routeId=$routeId&varId=$varId&stopId=$stopId&plate=$plate&distance=$distance&speed=$speed&dts=$encodedDts&eta=$etaParam"
                    )
                }
            )
        }
    }
}

enum class NavigationTab(val label: String) {
    HOME("Trang chủ"),
    MAP("Bản đồ"),
    ROUTES("Tuyến xe"),
    PERSONAL("Cá nhân")
}

@Composable
fun MainTabsShell(
    onRouteSelected: (Int) -> Unit,
    onStopSelected: (Int) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(NavigationTab.HOME.ordinal) }

    // Android Back handler: if not on Home tab, return to Home first before exiting
    BackHandler(enabled = selectedTab != NavigationTab.HOME.ordinal) {
        selectedTab = NavigationTab.HOME.ordinal
    }

    val homeViewModel: HomeViewModel = hiltViewModel()
    val mapViewModel: MapViewModel = hiltViewModel()
    val routesViewModel: RoutesViewModel = hiltViewModel()
    val personalViewModel: PersonalViewModel = hiltViewModel()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.HOME.ordinal,
                    onClick = { selectedTab = NavigationTab.HOME.ordinal },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Trang chủ") },
                    label = {
                        Text(
                            text = "Trang chủ",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selectedTab == NavigationTab.HOME.ordinal) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                )
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.MAP.ordinal,
                    onClick = { selectedTab = NavigationTab.MAP.ordinal },
                    icon = { Icon(Icons.Default.Map, contentDescription = "Bản đồ") },
                    label = {
                        Text(
                            text = "Bản đồ",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selectedTab == NavigationTab.MAP.ordinal) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                )
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.ROUTES.ordinal,
                    onClick = { selectedTab = NavigationTab.ROUTES.ordinal },
                    icon = { Icon(Icons.Default.DirectionsBus, contentDescription = "Tuyến xe") },
                    label = {
                        Text(
                            text = "Tuyến xe",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selectedTab == NavigationTab.ROUTES.ordinal) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                )
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.PERSONAL.ordinal,
                    onClick = { selectedTab = NavigationTab.PERSONAL.ordinal },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Cá nhân") },
                    label = {
                        Text(
                            text = "Cá nhân",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selectedTab == NavigationTab.PERSONAL.ordinal) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            NavigationTab.HOME.ordinal -> {
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToSearch = onNavigateToSearch,
                    onNavigateToMap = { selectedTab = NavigationTab.MAP.ordinal },
                    onNavigateToRoutes = { selectedTab = NavigationTab.ROUTES.ordinal },
                    onNavigateToPlateSearch = onNavigateToSearch,
                    onRouteSelected = onRouteSelected,
                    onStopSelected = onStopSelected,
                    onQuickPredictionClick = {
                        if (homeViewModel.uiState.value.favoriteStops.isNotEmpty()) {
                            homeViewModel.refreshFavoriteEta()
                        } else {
                            onNavigateToSearch()
                        }
                    },
                    modifier = Modifier.padding(padding)
                )
            }
            NavigationTab.MAP.ordinal -> {
                MapScreen(
                    viewModel = mapViewModel,
                    onStopClicked = onStopSelected,
                    modifier = Modifier.padding(padding)
                )
            }
            NavigationTab.ROUTES.ordinal -> {
                RoutesScreen(
                    viewModel = routesViewModel,
                    onRouteSelected = onRouteSelected,
                    modifier = Modifier.padding(padding)
                )
            }
            NavigationTab.PERSONAL.ordinal -> {
                PersonalScreen(
                    viewModel = personalViewModel,
                    onStopSelected = onStopSelected,
                    onRouteSelected = onRouteSelected,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}
