package com.hcmcbustracker.feature.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcmcbustracker.core.database.repository.BusRepository
import com.hcmcbustracker.core.model.Route
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

data class RoutesUiState(
    val query: String = "",
    val routes: List<Route> = emptyList(),
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
    private val _isSyncing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val favoriteIdsFlow = repository.getFavoriteRoutes().map { list ->
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

    val uiState: StateFlow<RoutesUiState> = combine(
        _query,
        routesFlow,
        favoriteIdsFlow,
        _isSyncing,
        _errorMessage
    ) { query, routes, favIds, isSyncing, errorMessage ->
        RoutesUiState(
            query = query,
            routes = routes,
            favoriteRouteIds = favIds,
            isLoading = routes.isEmpty() && isSyncing,
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
