package com.example.stockmarketsim

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import androidx.hilt.work.HiltWorkerFactory
import javax.inject.Inject

@HiltAndroidApp
class StockMarketSimApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        scheduleDailySimulation()
    }

    private fun scheduleDailySimulation() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        // 1. Daily Simulation
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.stockmarketsim.worker.DailySimulationWorker>(
            12, java.util.concurrent.TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()
        
        // 2. Weekly Stock Discovery (Every 7 days)
        val discoveryRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.stockmarketsim.worker.StockDiscoveryWorker>(
            7, java.util.concurrent.TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .build()

        val workManager = androidx.work.WorkManager.getInstance(this)

        workManager.enqueueUniquePeriodicWork(
            "DailyStockSimulation",
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        
        workManager.enqueueUniquePeriodicWork(
            "WeeklyStockDiscovery",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            discoveryRequest
        )
    }
}
