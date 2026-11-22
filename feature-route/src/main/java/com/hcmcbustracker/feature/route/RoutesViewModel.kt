package com.hcmcbustracker.feature.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcmcbustracker.core.database.repository.BusRepository
import com.hcmcbustracker.core.model.Route
import com.hcmcbustracker.core.model.sortedByRouteNumber
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Filter groups for transit routes in Ho Chi Minh City.
 */
enum class RouteFilterType(val labelVi: String) {
    ALL("Tất cả"),
    SUBSIDIZED("Có trợ giá"),
    NON_SUBSIDIZED("Không trợ giá"),
    ELECTRIC("Xe điện")
}

data class RoutesUiState(
    val query: String = "",
    val selectedFilter: RouteFilterType = RouteFilterType.ALL,
    val routes: List<Route> = emptyList(),
    val totalRouteCount: Int = 0,
    val favoriteRouteIds: Set<Int> = emptySet(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class RoutesViewModel @Inject constructor(
    private val repository: BusRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _selectedFilter = MutableStateFlow(RouteFilterType.ALL)
    private val _isSyncing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val favoriteIdsFlow = repository.getFavoriteRoutes().map { list ->
        list.map { it.id }.toSet()
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val rawRoutesFlow = _query
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.isBlank()) repository.getAllRoutes()
            else repository.searchRoutes(q)
        }

    private data class FilterParams(
        val query: String,
        val filter: RouteFilterType
    )

    private val filterParamsFlow = combine(_query, _selectedFilter) { query, filter ->
        FilterParams(query, filter)
    }

    val uiState: StateFlow<RoutesUiState> = combine(
        filterParamsFlow,
        rawRoutesFlow,
        favoriteIdsFlow,
        _isSyncing,
        _errorMessage
    ) { params, rawRoutes, favIds, isSyncing, errorMessage ->
        // 1. Natural Sort via RouteNumberComparator (ensures D4 does NOT precede 01, 02, etc.)
        val naturallySortedRoutes = rawRoutes.sortedByRouteNumber()

        // 2. Apply group filter if selected
        val filteredRoutes = when (params.filter) {
            RouteFilterType.ALL -> naturallySortedRoutes
            RouteFilterType.SUBSIDIZED -> naturallySortedRoutes.filter { route ->
                route.type?.contains("Có trợ giá", ignoreCase = true) == true
            }
            RouteFilterType.NON_SUBSIDIZED -> naturallySortedRoutes.filter { route ->
                route.type?.contains("Không trợ giá", ignoreCase = true) == true
            }
            RouteFilterType.ELECTRIC -> naturallySortedRoutes.filter { route ->
                route.routeNo.startsWith("D", ignoreCase = true) ||
                        route.type?.contains("điện", ignoreCase = true) == true ||
                        route.orgs?.contains("VinBus", ignoreCase = true) == true ||
                        route.name.contains("Vinhomes", ignoreCase = true)
            }
        }

        RoutesUiState(
            query = params.query,
            selectedFilter = params.filter,
            routes = filteredRoutes,
            totalRouteCount = naturallySortedRoutes.size,
            favoriteRouteIds = favIds,
            isLoading = naturallySortedRoutes.isEmpty() && isSyncing,
            isSyncing = isSyncing,
            errorMessage = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RoutesUiState(isLoading = true)
    )

    init {
        initialSyncIfNeeded()
    }

    private fun initialSyncIfNeeded() {
        viewModelScope.launch {
            repository.ensureInitialStaticDatasetLoaded()
            val count = repository.getRouteCount()
            if (count == 0) {
                syncRoutes()
            }
        }
    }

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    fun onFilterSelected(filter: RouteFilterType) {
        _selectedFilter.value = filter
    }

    fun toggleFavorite(routeId: Int) {
        viewModelScope.launch {
            repository.toggleFavoriteRoute(routeId)
        }
    }

    fun syncRoutes() {
        viewModelScope.launch {
            _isSyncing.value = true
            _errorMessage.value = null
            val result = repository.syncRoutes()
            if (result.isError) {
                _errorMessage.value = "Không thể cập nhật danh sách tuyến từ máy chủ. Dữ liệu hiện có vẫn được bảo toàn."
            }
            _isSyncing.value = false
        }
    }
}
