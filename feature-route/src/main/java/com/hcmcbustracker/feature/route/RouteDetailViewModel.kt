package com.hcmcbustracker.feature.route

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcmcbustracker.core.database.repository.BusRepository
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.Route
import com.hcmcbustracker.core.model.RoutePath
import com.hcmcbustracker.core.model.RouteVariant
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
    val isFavorite: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class RouteDetailViewModel @Inject constructor(
    private val repository: BusRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val routeId: Int = checkNotNull(savedStateHandle["routeId"])
    private val _selectedVarId = MutableStateFlow<Int?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

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

    private data class RouteDetailMeta(
        val selectedVarId: Int?,
        val isLoading: Boolean,
        val isFavorite: Boolean,
        val errorMessage: String?
    )

    private val metaFlow = combine(
        _selectedVarId,
        _isLoading,
        isFavoriteFlow,
        _errorMessage
    ) { varId, loading, isFav, error ->
        RouteDetailMeta(varId, loading, isFav, error)
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
    }

    fun selectVariant(varId: Int) {
        if (_selectedVarId.value == varId) return
        _selectedVarId.value = varId
        loadVariantStopsAndPath(varId)
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            repository.toggleFavoriteRoute(routeId)
        }
    }

    fun refreshRoute() {
        loadRouteDetails(forceNetworkRefresh = true)
    }

    private fun loadRouteDetails(forceNetworkRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            repository.ensureInitialStaticDatasetLoaded()

            var variants = repository.getVariantsForRoute(routeId).firstOrNull() ?: emptyList()
            if (forceNetworkRefresh || variants.isEmpty()) {
                val result = repository.syncRouteDetailAndVariants(routeId)
                if (result.isError && variants.isEmpty()) {
                    _errorMessage.value = "Không thể tải chi tiết tuyến từ máy chủ. Đang dùng dữ liệu ngoại tuyến."
                }
                variants = repository.getVariantsForRoute(routeId).firstOrNull() ?: emptyList()
            }

            val initialVar = variants.firstOrNull { it.isOutbound } ?: variants.firstOrNull()
            if (initialVar != null) {
                _selectedVarId.value = initialVar.id
                loadVariantLocalOrFetch(initialVar.id, forceNetworkRefresh)
            }
            _isLoading.value = false
        }
    }

    private fun loadVariantStopsAndPath(varId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            loadVariantLocalOrFetch(varId, forceNetworkRefresh = false)
            _isLoading.value = false
        }
    }

    private suspend fun loadVariantLocalOrFetch(varId: Int, forceNetworkRefresh: Boolean) {
        val localStops = repository.getStopsForRouteVar(routeId, varId).firstOrNull() ?: emptyList()
        if (forceNetworkRefresh || localStops.isEmpty()) {
            repository.syncStopsForRouteVar(routeId, varId)
        }
        val localPath = repository.getRoutePath(routeId, varId).firstOrNull()
        if (localPath == null || localPath.points.size < 2) {
            repository.ensureRoutePath(routeId, varId)
        }
    }
}
