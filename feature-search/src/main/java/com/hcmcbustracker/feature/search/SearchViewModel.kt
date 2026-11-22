package com.hcmcbustracker.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcmcbustracker.core.database.repository.BusRepository
import com.hcmcbustracker.core.model.ActiveVehicleSummary
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.LicensePlateNormalizer
import com.hcmcbustracker.core.model.Route
import com.hcmcbustracker.core.model.SearchHistoryItem
import com.hcmcbustracker.core.model.SearchHistoryType
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchTab(val label: String) {
    ALL("Tất cả"),
    ROUTES("Tuyến xe"),
    STOPS("Trạm dừng"),
    VEHICLES("Biển số xe")
}

data class SearchUiState(
    val query: String = "",
    val selectedTab: SearchTab = SearchTab.ALL,
    val routes: List<Route> = emptyList(),
    val stops: List<BusStop> = emptyList(),
    val vehicles: List<ActiveVehicleSummary> = emptyList(),
    val searchHistory: List<SearchHistoryItem> = emptyList(),
    val favoriteRouteIds: Set<Int> = emptySet(),
    val favoriteStopIds: Set<Int> = emptySet(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: BusRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _selectedTab = MutableStateFlow(SearchTab.ALL)
    private val _isSyncing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val favoriteRouteIdsFlow = repository.getFavoriteRoutes().map { list ->
        list.map { it.id }.toSet()
    }

    private val favoriteStopIdsFlow = repository.getFavoriteStops().map { list ->
        list.map { it.id }.toSet()
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val routesFlow = _query
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.isBlank()) repository.getAllRoutes()
            else repository.searchRoutes(q)
        }
        .map { it.sortedByRouteNumber() }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val stopsFlow = _query
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList())
            else repository.searchStops(q)
        }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val vehiclesFlow = _query
        .debounce(300)
        .distinctUntilChanged()
        .map { q ->
            if (q.isBlank()) emptyList()
            else repository.searchActiveVehiclesByPlate(q)
        }

    private data class DataResults(
        val routes: List<Route>,
        val stops: List<BusStop>,
        val vehicles: List<ActiveVehicleSummary>,
        val history: List<SearchHistoryItem>
    )

    private val dataResultsFlow = combine(
        routesFlow,
        stopsFlow,
        vehiclesFlow,
        repository.getSearchHistory()
    ) { routes, stops, vehicles, history ->
        DataResults(routes, stops, vehicles, history)
    }

    private data class SearchParams(
        val query: String,
        val tab: SearchTab,
        val isSyncing: Boolean,
        val error: String?
    )

    private val searchParamsFlow = combine(
        _query,
        _selectedTab,
        _isSyncing,
        _errorMessage
    ) { query, tab, syncing, error ->
        SearchParams(query, tab, syncing, error)
    }

    private val favoritesFlow = combine(
        favoriteRouteIdsFlow,
        favoriteStopIdsFlow
    ) { favRoutes, favStops -> favRoutes to favStops }

    val uiState: StateFlow<SearchUiState> = combine(
        searchParamsFlow,
        dataResultsFlow,
        favoritesFlow
    ) { params, data, favorites ->
        val (favRoutes, favStops) = favorites
        SearchUiState(
            query = params.query,
            selectedTab = params.tab,
            routes = data.routes,
            stops = data.stops,
            vehicles = data.vehicles,
            searchHistory = data.history,
            favoriteRouteIds = favRoutes,
            favoriteStopIds = favStops,
            isLoading = false,
            isSyncing = params.isSyncing,
            errorMessage = params.error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SearchUiState(isLoading = true)
    )

    init {
        initialSyncIfNeeded()
    }

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    fun onTabSelected(tab: SearchTab) {
        _selectedTab.value = tab
    }

    fun onHistoryItemClicked(item: SearchHistoryItem) {
        _query.value = item.query
        when (item.type) {
            SearchHistoryType.ROUTE -> _selectedTab.value = SearchTab.ROUTES
            SearchHistoryType.STOP -> _selectedTab.value = SearchTab.STOPS
            SearchHistoryType.PLATE -> _selectedTab.value = SearchTab.VEHICLES
            SearchHistoryType.GENERAL -> _selectedTab.value = SearchTab.ALL
        }
    }

    fun removeHistoryItem(query: String) {
        viewModelScope.launch {
            repository.removeSearchHistoryItem(query)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
        }
    }

    fun recordSearchSelection(query: String, type: SearchHistoryType = SearchHistoryType.GENERAL) {
        viewModelScope.launch {
            repository.addSearchHistory(query, type)
        }
    }

    fun toggleFavoriteRoute(routeId: Int) {
        viewModelScope.launch {
            repository.toggleFavoriteRoute(routeId)
        }
    }

    fun toggleFavoriteStop(stopId: Int) {
        viewModelScope.launch {
            repository.toggleFavoriteStop(stopId)
        }
    }

    fun triggerSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            _errorMessage.value = null

            val routeResult = repository.syncRoutes()
            val stopResult = repository.syncStops()

            if (routeResult.isError || stopResult.isError) {
                _errorMessage.value = "Đồng bộ một phần dữ liệu không thành công. Dữ liệu hiện có vẫn được bảo toàn."
            }
            _isSyncing.value = false
        }
    }

    private fun initialSyncIfNeeded() {
        viewModelScope.launch {
            val routeCount = repository.getRouteCount()
            val stopCount = repository.getStopCount()
            if (routeCount == 0 || stopCount == 0) {
                val loaded = repository.ensureInitialStaticDatasetLoaded()
                if (!loaded) {
                    triggerSync()
                }
            }
        }
    }
}
