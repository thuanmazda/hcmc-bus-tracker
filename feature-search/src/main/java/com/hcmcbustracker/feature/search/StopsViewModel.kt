package com.hcmcbustracker.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcmcbustracker.core.database.repository.BusRepository
import com.hcmcbustracker.core.model.BusStop
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

data class StopsUiState(
    val query: String = "",
    val stops: List<BusStop> = emptyList(),
    val favoriteStopIds: Set<Int> = emptySet(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class StopsViewModel @Inject constructor(
    private val repository: BusRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _isSyncing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val favoriteIdsFlow = repository.getFavoriteStops().map { list ->
        list.map { it.id }.toSet()
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val stopsFlow = _query
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.isBlank()) {
                // Return default hubs in Central HCMC (Bến Thành, Hàm Nghi, Chợ Lớn bounds)
                repository.getStopsInBounds(
                    minLat = 10.74,
                    maxLat = 10.79,
                    minLng = 106.65,
                    maxLng = 106.72
                )
            } else {
                repository.searchStops(q)
            }
        }

    val uiState: StateFlow<StopsUiState> = combine(
        _query,
        stopsFlow,
        favoriteIdsFlow,
        _isSyncing,
        _errorMessage
    ) { query, stops, favoriteIds, isSyncing, errorMessage ->
        StopsUiState(
            query = query,
            stops = stops,
            favoriteStopIds = favoriteIds,
            isLoading = stops.isEmpty() && isSyncing,
            isSyncing = isSyncing,
            errorMessage = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StopsUiState(isLoading = true)
    )

    init {
        initialSyncIfNeeded()
    }

    private fun initialSyncIfNeeded() {
        viewModelScope.launch {
            repository.ensureInitialStaticDatasetLoaded()
            val count = repository.getStopCount()
            if (count == 0) {
                syncStops()
            }
        }
    }

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    fun toggleFavorite(stopId: Int) {
        viewModelScope.launch {
            repository.toggleFavoriteStop(stopId)
        }
    }

    fun syncStops() {
        viewModelScope.launch {
            _isSyncing.value = true
            _errorMessage.value = null
            val result = repository.syncStops()
            if (result.isError) {
                _errorMessage.value = "Không thể cập nhật danh sách trạm dừng. Dữ liệu hiện có vẫn được bảo toàn."
            }
            _isSyncing.value = false
        }
    }
}
