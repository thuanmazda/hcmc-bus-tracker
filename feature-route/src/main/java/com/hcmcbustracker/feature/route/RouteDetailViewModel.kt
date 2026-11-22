package com.hcmcbustracker.feature.route

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcmcbustracker.core.database.repository.BusRepository
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.Route
import com.hcmcbustracker.core.model.RoutePath
import com.hcmcbustracker.core.model.RouteVariant
import com.hcmcbustracker.core.model.Timetable
import com.hcmcbustracker.core.model.Trip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RouteDetailUiState(
    val route: Route? = null,
    val variants: List<RouteVariant> = emptyList(),
    val selectedVariant: RouteVariant? = null,
    val stops: List<BusStop> = emptyList(),
    val path: RoutePath? = null,
    val timetables: List<Timetable> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val hourlyTripCounts: Map<Int, Int>? = null,
    val isTimetableLoading: Boolean = false,
    val timetableErrorMessage: String? = null,
    val isFavorite: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class RouteDetailViewModel @Inject constructor(
    private val repository: BusRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val routeId: Int = checkNotNull(savedStateHandle["routeId"])
    private val _selectedVarId = MutableStateFlow<Int?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _timetables = MutableStateFlow<List<Timetable>>(emptyList())
    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    private val _hourlyTripCounts = MutableStateFlow<Map<Int, Int>?>(null)
    private val _isTimetableLoading = MutableStateFlow(false)
    private val _timetableErrorMessage = MutableStateFlow<String?>(null)

    private val routeFlow = repository.getRouteById(routeId)
    private val variantsFlow = repository.getVariantsForRoute(routeId)
    private val isFavoriteFlow = repository.isRouteFavorite(routeId)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val stopsFlow = _selectedVarId.flatMapLatest { varId ->
        if (varId != null) repository.getStopsForRouteVar(routeId, varId)
        else flowOf(emptyList())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val pathFlow = _selectedVarId.flatMapLatest { varId ->
        if (varId != null) repository.getRoutePath(routeId, varId)
        else flowOf(null)
    }

    private data class RouteTimetableBundle(
        val timetables: List<Timetable>,
        val trips: List<Trip>,
        val hourlyTripCounts: Map<Int, Int>?,
        val isTimetableLoading: Boolean,
        val timetableErrorMessage: String?
    )

    private val timetableDataFlow = combine(
        _timetables,
        _trips,
        _hourlyTripCounts,
        _isTimetableLoading,
        _timetableErrorMessage
    ) { timetables, trips, hourlyCounts, isTtLoading, ttError ->
        RouteTimetableBundle(timetables, trips, hourlyCounts, isTtLoading, ttError)
    }

    private data class RouteDetailMeta(
        val selectedVarId: Int?,
        val isLoading: Boolean,
        val isFavorite: Boolean,
        val errorMessage: String?,
        val timetableBundle: RouteTimetableBundle
    )

    private val metaFlow = combine(
        _selectedVarId,
        _isLoading,
        isFavoriteFlow,
        _errorMessage,
        timetableDataFlow
    ) { varId, loading, isFav, error, timetableBundle ->
        RouteDetailMeta(varId, loading, isFav, error, timetableBundle)
    }

    val uiState: StateFlow<RouteDetailUiState> = combine(
        routeFlow,
        variantsFlow,
        stopsFlow,
        pathFlow,
        metaFlow
    ) { route, variants, stops, path, meta ->
        val currentVar = variants.find { it.id == meta.selectedVarId } ?: variants.firstOrNull()

        // Auto select first variant if none selected
        if (meta.selectedVarId == null && currentVar != null) {
            _selectedVarId.value = currentVar.id
            loadVariantStopsAndPath(currentVar.id)
        }

        RouteDetailUiState(
            route = route,
            variants = variants,
            selectedVariant = currentVar,
            stops = stops,
            path = path,
            timetables = meta.timetableBundle.timetables,
            trips = meta.timetableBundle.trips,
            hourlyTripCounts = meta.timetableBundle.hourlyTripCounts,
            isTimetableLoading = meta.timetableBundle.isTimetableLoading,
            timetableErrorMessage = meta.timetableBundle.timetableErrorMessage,
            isFavorite = meta.isFavorite,
            isLoading = meta.isLoading,
            errorMessage = meta.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RouteDetailUiState(isLoading = true)
    )

    init {
        loadRouteDetails()
        loadTimetableSchedule()
    }

    fun selectVariant(varId: Int) {
        if (_selectedVarId.value == varId) return
        _selectedVarId.value = varId
        loadVariantStopsAndPath(varId)
        loadTimetableSchedule()
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            repository.toggleFavoriteRoute(routeId)
        }
    }

    fun refreshRoute() {
        loadRouteDetails(forceNetworkRefresh = true)
        loadTimetableSchedule()
    }

    private fun loadRouteDetails(forceNetworkRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val currentRoute = routeFlow.firstOrNull()
            if (currentRoute == null || forceNetworkRefresh) {
                val syncResult = repository.syncRouteDetailAndVariants(routeId)
                if (syncResult.isError) {
                    _errorMessage.value = "Không thể tải thông tin tuyến. Đang dùng dữ liệu ngoại tuyến."
                }
            }

            val variants = variantsFlow.firstOrNull().orEmpty()
            val targetVarId = _selectedVarId.value ?: variants.firstOrNull()?.id
            if (targetVarId != null) {
                _selectedVarId.value = targetVarId
                loadVariantLocalOrFetch(targetVarId, forceNetworkRefresh)
            }

            _isLoading.value = false
        }
    }

    fun loadTimetableSchedule() {
        viewModelScope.launch {
            _isTimetableLoading.value = true
            _timetableErrorMessage.value = null
            val res = repository.getTimetablesForRoute(routeId)
            if (res.isSuccess) {
                val tables = res.getOrDefault(emptyList())
                _timetables.value = tables

                val targetVarId = _selectedVarId.value
                val matchedTable = tables.firstOrNull { it.varId == targetVarId } ?: tables.firstOrNull()
                if (matchedTable != null) {
                    val tripRes = repository.getTripsForTimetable(routeId, matchedTable.id)
                    if (tripRes.isSuccess) {
                        val trips = tripRes.getOrDefault(emptyList())
                        _trips.value = trips
                        val counts = mutableMapOf<Int, Int>()
                        for (trip in trips) {
                            val hour = trip.startTime.replace("h", ":").split(":").firstOrNull()?.trim()?.toIntOrNull()
                            if (hour != null && hour in 0..23) {
                                counts[hour] = (counts[hour] ?: 0) + 1
                            }
                        }
                        _hourlyTripCounts.value = counts.ifEmpty { null }
                    } else {
                        _trips.value = emptyList()
                        _hourlyTripCounts.value = null
                    }
                } else {
                    _trips.value = emptyList()
                    _hourlyTripCounts.value = null
                }
            } else {
                _timetables.value = emptyList()
                _trips.value = emptyList()
                _hourlyTripCounts.value = null
                _timetableErrorMessage.value = "Chưa có thông tin lịch trình cho tuyến này."
            }
            _isTimetableLoading.value = false
        }
    }

    private fun loadVariantStopsAndPath(varId: Int) {
        viewModelScope.launch {
            loadVariantLocalOrFetch(varId, forceNetworkRefresh = false)
        }
    }

    private suspend fun loadVariantLocalOrFetch(varId: Int, forceNetworkRefresh: Boolean) {
        val localStops = repository.getStopsForRouteVar(routeId, varId).firstOrNull().orEmpty()
        val localPath = repository.getRoutePath(routeId, varId).firstOrNull()

        if (localStops.isEmpty() || forceNetworkRefresh) {
            repository.syncStopsForRouteVar(routeId, varId)
        }
        if (localPath == null || forceNetworkRefresh) {
            repository.ensureRoutePath(routeId, varId)
        }
    }
}
