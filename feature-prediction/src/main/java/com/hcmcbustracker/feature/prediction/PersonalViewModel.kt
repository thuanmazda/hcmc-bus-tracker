package com.hcmcbustracker.feature.prediction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcmcbustracker.core.database.repository.BusRepository
import com.hcmcbustracker.core.model.AppThemeMode
import com.hcmcbustracker.core.model.BusStop
import com.hcmcbustracker.core.model.DatasetSyncProgress
import com.hcmcbustracker.core.model.DatasetUpdateOutcome
import com.hcmcbustracker.core.model.Route
import com.hcmcbustracker.core.model.SearchHistoryItem
import com.hcmcbustracker.core.model.StaticDatasetMetadata
import com.hcmcbustracker.core.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PersonalTab {
    STOPS, ROUTES, SETTINGS
}

data class PersonalUiState(
    val selectedTab: PersonalTab = PersonalTab.STOPS,
    val favoriteStops: List<BusStop> = emptyList(),
    val favoriteRoutes: List<Route> = emptyList(),
    val searchHistory: List<SearchHistoryItem> = emptyList(),
    val userPreferences: UserPreferences = UserPreferences(),
    val lastSyncTimestamp: Long? = null,
    val staticDatasetMetadata: StaticDatasetMetadata? = null,
    val syncProgress: DatasetSyncProgress? = null,
    val isSyncing: Boolean = false,
    val isClearingCache: Boolean = false,
    val syncSuccessMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class PersonalViewModel @Inject constructor(
    private val repository: BusRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(PersonalTab.STOPS)
    private val _isSyncing = MutableStateFlow(false)
    private val _isClearingCache = MutableStateFlow(false)
    private val _syncSuccessMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _lastSyncTimestamp = MutableStateFlow<Long?>(null)

    private var datasetUpdateJob: Job? = null

    private data class SyncMeta(
        val isSyncing: Boolean,
        val isClearingCache: Boolean,
        val syncSuccessMessage: String?,
        val errorMessage: String?
    )

    private val syncMetaFlow = combine(
        _isSyncing,
        _isClearingCache,
        _syncSuccessMessage,
        _errorMessage
    ) { syncing, clearing, success, error ->
        SyncMeta(syncing, clearing, success, error)
    }

    private data class DatasetStatus(
        val metadata: StaticDatasetMetadata?,
        val progress: DatasetSyncProgress?
    )

    private val datasetStatusFlow = combine(
        repository.observeStaticDatasetMetadata().onStart { emit(null) },
        repository.observeDatasetSyncProgress().onStart { emit(null) }
    ) { meta, progress ->
        DatasetStatus(meta, progress)
    }

    private data class PersonalMeta(
        val tab: PersonalTab,
        val lastSync: Long?,
        val syncMeta: SyncMeta,
        val datasetStatus: DatasetStatus
    )

    private val personalMetaFlow = combine(
        _selectedTab,
        _lastSyncTimestamp,
        syncMetaFlow,
        datasetStatusFlow
    ) { tab, lastSync, syncMeta, datasetStatus ->
        PersonalMeta(tab, lastSync, syncMeta, datasetStatus)
    }

    private data class FavoritesAndHistory(
        val stops: List<BusStop>,
        val routes: List<Route>,
        val history: List<SearchHistoryItem>
    )

    private val favoritesAndHistoryFlow = combine(
        repository.getFavoriteStops(),
        repository.getFavoriteRoutes(),
        repository.getSearchHistory()
    ) { stops, routes, history ->
        FavoritesAndHistory(stops, routes, history)
    }

    val uiState: StateFlow<PersonalUiState> = combine(
        favoritesAndHistoryFlow,
        repository.getUserPreferences(),
        personalMetaFlow
    ) { favAndHist, prefs, meta ->
        PersonalUiState(
            selectedTab = meta.tab,
            favoriteStops = favAndHist.stops,
            favoriteRoutes = favAndHist.routes,
            searchHistory = favAndHist.history,
            userPreferences = prefs,
            lastSyncTimestamp = meta.datasetStatus.metadata?.datasetUpdatedAt ?: meta.lastSync,
            staticDatasetMetadata = meta.datasetStatus.metadata,
            syncProgress = meta.datasetStatus.progress,
            isSyncing = meta.syncMeta.isSyncing || meta.datasetStatus.progress != null,
            isClearingCache = meta.syncMeta.isClearingCache,
            syncSuccessMessage = meta.syncMeta.syncSuccessMessage,
            errorMessage = meta.syncMeta.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PersonalUiState()
    )

    init {
        loadLastSyncTime()
    }

    fun selectTab(tab: PersonalTab) {
        _selectedTab.value = tab
    }

    private fun loadLastSyncTime() {
        viewModelScope.launch {
            _lastSyncTimestamp.value = repository.getLastSyncTimestamp("ROUTES")
        }
    }

    fun removeFavoriteStop(stopId: Int) {
        viewModelScope.launch {
            repository.toggleFavoriteStop(stopId)
        }
    }

    fun removeFavoriteRoute(routeId: Int) {
        viewModelScope.launch {
            repository.toggleFavoriteRoute(routeId)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun setPollingIntervalSeconds(seconds: Int) {
        viewModelScope.launch {
            repository.setPollingIntervalSeconds(seconds)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setNotificationsEnabled(enabled)
        }
    }

    fun setArrivalAlertThresholds(timeMinutes: Int, distanceMeters: Double) {
        viewModelScope.launch {
            repository.setArrivalAlertThresholds(timeMinutes, distanceMeters)
        }
    }

    fun toggleStopNotification(stopId: Int, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleStopNotification(stopId, enabled)
        }
    }

    fun removeSearchHistoryItem(query: String) {
        viewModelScope.launch {
            repository.removeSearchHistoryItem(query)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _isClearingCache.value = true
            _errorMessage.value = null
            repository.clearCache()
            _syncSuccessMessage.value = "Đã xóa toàn bộ bộ nhớ đệm (polyline paths) thành công!"
            _isClearingCache.value = false
        }
    }

    fun updateFullStaticDataset() {
        if (datasetUpdateJob?.isActive == true) return
        datasetUpdateJob = viewModelScope.launch {
            _isSyncing.value = true
            _errorMessage.value = null
            _syncSuccessMessage.value = null
            try {
                when (val outcome = repository.updateFullStaticDatasetFromEbms()) {
                    is DatasetUpdateOutcome.Updated -> {
                        val m = outcome.metadata
                        _syncSuccessMessage.value =
                            "Đã cập nhật hoàn tất: ${m.totalRoutes} tuyến, ${m.totalVariants} chiều, ${m.totalStops} trạm, ${m.totalPolylines} lộ trình."
                        loadLastSyncTime()
                    }
                    is DatasetUpdateOutcome.AlreadyUpToDate -> {
                        _syncSuccessMessage.value = "Dữ liệu đã mới nhất (không cần ghi lại database)."
                        loadLastSyncTime()
                    }
                    is DatasetUpdateOutcome.Failed -> {
                        _errorMessage.value = outcome.message
                    }
                }
            } catch (_: CancellationException) {
                _errorMessage.value = "Đã hủy cập nhật. Dữ liệu offline hiện tại vẫn được bảo toàn."
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun cancelDatasetUpdate() {
        datasetUpdateJob?.cancel()
    }

    fun syncAllData() {
        viewModelScope.launch {
            _isSyncing.value = true
            _errorMessage.value = null
            _syncSuccessMessage.value = null

            val routeResult = repository.syncRoutes()
            val stopResult = repository.syncStops()

            if (routeResult.isSuccess && stopResult.isSuccess) {
                _syncSuccessMessage.value = "Đã đồng bộ toàn bộ dữ liệu mới nhất thành công!"
                loadLastSyncTime()
            } else {
                _errorMessage.value = "Đồng bộ gặp gián đoạn. Dữ liệu đã lưu ngoại tuyến vẫn sẵn sàng sử dụng."
            }
            _isSyncing.value = false
        }
    }
}
