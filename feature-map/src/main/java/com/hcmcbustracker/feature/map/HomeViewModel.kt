package com.hcmcbustracker.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcmcbustracker.core.database.repository.BusRepository
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val query: String = "",
    val favoriteStops: List<BusStop> = emptyList(),
    val popularRoutes: List<Route> = emptyList(),
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

    // Top popular routes (e.g. 01, 150, 56)
    private val popularRoutesFlow = repository.getAllRoutes().map { routes ->
        routes.take(3)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        _query,
        repository.getFavoriteStops(),
        popularRoutesFlow,
        _isSyncing,
        _errorMessage
    ) { query, favorites, popular, isSyncing, errorMessage ->
        HomeUiState(
            query = query,
            favoriteStops = favorites.take(3),
            popularRoutes = popular,
            isSyncing = isSyncing,
            errorMessage = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    init {
        initialSyncIfNeeded()
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

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
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
