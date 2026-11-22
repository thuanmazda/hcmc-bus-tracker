package com.hcmcbustracker.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcmcbustracker.core.database.repository.BusRepository
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.NetworkResult
import com.hcmcbustracker.core.model.Prediction
import com.hcmcbustracker.core.model.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Realtime ETA container for favorite stop on Transit Hub.
 */
data class FavoriteStopLiveEta(
    val stop: BusStop,
    val predictions: List<Prediction> = emptyList(),
    val isLoading: Boolean = false,
    val lastUpdatedMillis: Long = System.currentTimeMillis()
)

data class HomeUiState(
    val query: String = "",
    val favoriteStops: List<BusStop> = emptyList(),
    val favoriteRoutes: List<Route> = emptyList(),
    val favoriteEta: FavoriteStopLiveEta? = null,
    val popularRoutes: List<Route> = emptyList(),
    val totalRoutesCount: Int = 0,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: BusRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _isSyncing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _favoriteEta = MutableStateFlow<FavoriteStopLiveEta?>(null)

    // Top popular routes (sorted properly by RouteNumberComparator in repository)
    private val allRoutesFlow = repository.getAllRoutes()
    private val popularRoutesFlow = allRoutesFlow.map { routes ->
        routes.take(4)
    }

    private val favoritesCombinedFlow = combine(
        repository.getFavoriteStops(),
        repository.getFavoriteRoutes()
    ) { stops, routes -> stops to routes }

    private val metaFlow = combine(
        _isSyncing,
        _errorMessage,
        _favoriteEta,
        allRoutesFlow.map { it.size }
    ) { isSyncing, errorMessage, favoriteEta, totalCount ->
        MetaState(isSyncing, errorMessage, favoriteEta, totalCount)
    }

    private data class MetaState(
        val isSyncing: Boolean,
        val errorMessage: String?,
        val favoriteEta: FavoriteStopLiveEta?,
        val totalCount: Int
    )

    val uiState: StateFlow<HomeUiState> = combine(
        _query,
        favoritesCombinedFlow,
        popularRoutesFlow,
        metaFlow
    ) { query, favorites, popular, meta ->
        val (favStops, favRoutes) = favorites
        HomeUiState(
            query = query,
            favoriteStops = favStops,
            favoriteRoutes = favRoutes,
            favoriteEta = meta.favoriteEta,
            popularRoutes = popular,
            totalRoutesCount = meta.totalCount,
            isSyncing = meta.isSyncing,
            errorMessage = meta.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    init {
        initialSyncIfNeeded()
        observeFavoritesForEta()
    }

    private fun initialSyncIfNeeded() {
        viewModelScope.launch {
            _isSyncing.value = true
            val seeded = repository.ensureInitialStaticDatasetLoaded()
            val count = repository.getRouteCount()
            if (!seeded && count == 0) {
                repository.syncRoutes()
                repository.syncStops()
            }
            _isSyncing.value = false
        }
    }

    private fun observeFavoritesForEta() {
        viewModelScope.launch {
            repository.getFavoriteStops()
                .distinctUntilChanged { old, new ->
                    old.firstOrNull()?.id == new.firstOrNull()?.id
                }
                .collect { stops ->
                    val topStop = stops.firstOrNull()
                    if (topStop != null) {
                        fetchEtaForStop(topStop)
                    } else {
                        _favoriteEta.value = null
                    }
                }
        }
    }

    fun refreshFavoriteEta() {
        val currentStop = _favoriteEta.value?.stop ?: uiState.value.favoriteStops.firstOrNull()
        if (currentStop != null) {
            fetchEtaForStop(currentStop)
        }
    }

    private fun fetchEtaForStop(stop: BusStop) {
        viewModelScope.launch {
            _favoriteEta.value = _favoriteEta.value?.copy(isLoading = true)
                ?: FavoriteStopLiveEta(stop = stop, isLoading = true)

            when (val result = repository.fetchRealtimePrediction(stop.id)) {
                is NetworkResult.Success -> {
                    _favoriteEta.value = FavoriteStopLiveEta(
                        stop = stop,
                        predictions = result.data,
                        isLoading = false,
                        lastUpdatedMillis = System.currentTimeMillis()
                    )
                }
                is NetworkResult.Error -> {
                    _favoriteEta.value = FavoriteStopLiveEta(
                        stop = stop,
                        predictions = emptyList(),
                        isLoading = false,
                        lastUpdatedMillis = System.currentTimeMillis()
                    )
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    fun toggleFavoriteStop(stopId: Int) {
        viewModelScope.launch {
            repository.toggleFavoriteStop(stopId)
        }
    }

    fun toggleFavoriteRoute(routeId: Int) {
        viewModelScope.launch {
            repository.toggleFavoriteRoute(routeId)
        }
    }

    fun syncData() {
        viewModelScope.launch {
            _isSyncing.value = true
            _errorMessage.value = null
            repository.syncRoutes()
            repository.syncStops()
            _isSyncing.value = false
        }
    }
}
