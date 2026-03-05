package com.example.stockmarketsim.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.stockmarketsim.domain.repository.StockRepository
import com.example.stockmarketsim.domain.usecase.RunDailySimulationUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class DailySimulationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val runDailySimulationUseCase: RunDailySimulationUseCase,
    private val stockRepository: StockRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Promote to Foreground Service to prevent OS killing the job during heavy Analysis
        setForeground(createForegroundInfo())

        // ── MARKET HOURS GUARD ───────────────────────────────────────────────────
        // NSE trades 09:15–15:30 IST. We allow a wider window (08:30–16:30) to
        // capture pre-market data availability and post-close price finalization.
        // Running outside this window wastes battery and fetches stale prices,
        // causing "frozen equity" log lines with no trades.
        val istCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
        val hourIst = istCal.get(java.util.Calendar.HOUR_OF_DAY)
        val minuteIst = istCal.get(java.util.Calendar.MINUTE)
        val totalMinutesIst = hourIst * 60 + minuteIst
        val MARKET_OPEN_MINUTES = 8 * 60 + 30   // 08:30 IST
        val MARKET_CLOSE_MINUTES = 16 * 60 + 30 // 16:30 IST
        if (totalMinutesIst < MARKET_OPEN_MINUTES || totalMinutesIst > MARKET_CLOSE_MINUTES) {
            android.util.Log.d(
                "DailySimulationWorker",
                "⏰ Outside market window ($hourIst:${"%02d".format(minuteIst)} IST). Skipping simulation."
            )
            return@withContext Result.success()
        }
        // ────────────────────────────────────────────────────────────────────────

        return@withContext try {
            // 1. Data Freshness Check

            // We verify if we have fresh data before running the simulation logic.
            // This acts as the "Decision Trigger".
            val proxySymbol = "RELIANCE.NS"
            val quote = stockRepository.getStockQuote(proxySymbol)
            
            // INCREASED THRESHOLD: Allow data up to 4 days old to handle weekends and holidays (e.g. NIFTY closed Jan 1st).
            val maxAgeMillis = 4 * 24 * 60 * 60 * 1000L 
            val isFresh = quote != null && (System.currentTimeMillis() - quote.date) <= maxAgeMillis
            
            if (!isFresh) {
                android.util.Log.d("DailySimulationWorker", "Market data stale (${quote?.date}). Forcing data sync inside simulation...")
                // We proceed anyway because RunDailySimulationUseCase handles data fetching robustly
            }

            // 2. Version Check for Logging
            val prefs = applicationContext.getSharedPreferences("sim_prefs", Context.MODE_PRIVATE)
            val lastVersion = prefs.getInt("last_run_version_code", -1)
            val packageInfo = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
            val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
            val currentVersionName = packageInfo.versionName
            
            var systemMessage: String? = null
            if (currentVersionCode != lastVersion) {
                systemMessage = "[SYSTEM] App updated to v$currentVersionName (Build $currentVersionCode). Resuming simulation with latest engine logic."
                prefs.edit().putInt("last_run_version_code", currentVersionCode).apply()
            }
            
            runDailySimulationUseCase(systemMessage = systemMessage)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("DailySimulationWorker", "Error running daily simulation (attempt ${runAttemptCount + 1})", e)
            if (runAttemptCount < 3) {
                android.util.Log.w("DailySimulationWorker", "⚠️ Retrying in ~${5 * (1 shl runAttemptCount)} minutes...")
                Result.retry()
            } else {
                android.util.Log.e("DailySimulationWorker", "❌ All retry attempts exhausted. Giving up until next scheduled run.")
                Result.failure()
            }
        }
    }

    private fun createForegroundInfo(): androidx.work.ForegroundInfo {
        val id = "simulation_channel"
        val title = "Stock Market Simulation"
        val cancel = "Stop"
        
        // Ensure Notification Channel exists
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                id, title, android.app.NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, id)
            .setContentTitle("Analyzing Market...")
            .setContentText("Running Auto-Pilot Strategy Tournament")
            .setSmallIcon(android.R.drawable.ic_menu_rotate) // Fallback icon
            .setOngoing(true)
            .build()
            
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ requires specifying the service type
            return androidx.work.ForegroundInfo(
                101, 
                notification, 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        }
        
        return androidx.work.ForegroundInfo(101, notification)
    }
}
