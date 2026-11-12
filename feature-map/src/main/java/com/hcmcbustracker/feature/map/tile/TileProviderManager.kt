package com.hcmcbustracker.feature.map.tile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TileProviderManager @Inject constructor() {

    private val _currentProvider = MutableStateFlow(TileProvider.FPT_STIS)
    val currentProvider: StateFlow<TileProvider> = _currentProvider.asStateFlow()

    fun setTileProvider(provider: TileProvider) {
        _currentProvider.value = provider
    }

    fun getActiveStyleJson(): String {
        return _currentProvider.value.toStyleJson()
    }
}
