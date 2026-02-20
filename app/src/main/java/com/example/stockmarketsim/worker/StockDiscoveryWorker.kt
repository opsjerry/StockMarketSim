package com.example.stockmarketsim.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class StockDiscoveryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val stockRepository: com.example.stockmarketsim.domain.repository.StockRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            android.util.Log.d("StockDiscoveryWorker", "Starting Weekly Stock Discovery...")
            val count = stockRepository.syncUniverseFromDiscovery { log ->
                 android.util.Log.d("StockDiscoveryWorker", log)
            }
            android.util.Log.d("StockDiscoveryWorker", "Discovery Complete. Universe Updated with $count stocks.")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("StockDiscoveryWorker", "Discovery Failed", e)
            if (runAttemptCount < 3) {
                // Exponential Backoff
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
