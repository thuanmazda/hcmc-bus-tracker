package com.hcmcbustracker.app

import android.app.Application
import com.hcmcbustracker.core.database.repository.BusRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HcmcBusTrackerApp : Application() {

    @Inject
    lateinit var busRepository: BusRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Prepare validated offline static transit dataset immediately upon launch without blocking UI
        appScope.launch {
            busRepository.ensureInitialStaticDatasetLoaded()
        }
    }
}
