package com.example.stockmarketsim.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.stockmarketsim.domain.usecase.CheckIntradayStopLossUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs every 30 minutes during NSE market hours (09:15–15:30 IST).
 *
 * Responsibilities:
 * - Fetch real-time Zerodha prices for all held positions (not the full 100-symbol universe)
 * - Check each position against its ATR stop price using CheckIntradayStopLossUseCase
 * - Execute confirmed stops (2-consecutive-check confirmation to avoid whipsaw)
 * - Log results to the sim log UI
 *
 * This worker does NOT run the strategy tournament or portfolio rebalancing.
 * The daily runner (DailySimulationWorker) handles those at market hours.
 *
 * Fallback: if Zerodha is inactive and Room cache is stale, stops are deferred to
 * the daily runner's end-of-day Yahoo-based check (guaranteed safety net).
 */
@HiltWorker
class IntradayStopLossWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val checkIntradayStopLossUseCase: CheckIntradayStopLossUseCase
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // ── NSE Market Hours Guard (09:15–15:30 IST) ─────────────────────────
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
        val hourIst = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val minuteIst = cal.get(java.util.Calendar.MINUTE)
        val totalMinutesIst = hourIst * 60 + minuteIst

        val MARKET_OPEN_MINUTES  = 9 * 60 + 15  // 09:15 IST (NSE open)
        val MARKET_CLOSE_MINUTES = 15 * 60 + 30 // 15:30 IST (NSE close)

        if (totalMinutesIst < MARKET_OPEN_MINUTES || totalMinutesIst > MARKET_CLOSE_MINUTES) {
            // Outside market hours — not an error, just skip silently.
            // Return success (not retry) to preserve the 30-min periodic schedule.
            android.util.Log.d("IntradayStopLossWorker",
                "⏰ Outside NSE hours ($hourIst:${"%02d".format(minuteIst)} IST). No intra-day stop check needed.")
            return Result.success()
        }

        return try {
            checkIntradayStopLossUseCase()
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            android.util.Log.w("IntradayStopLossWorker", "Cancelled mid-run — positions unchanged.")
            throw e
        } catch (e: Exception) {
            android.util.Log.e("IntradayStopLossWorker", "Error in intra-day stop check", e)
            // Return success (not retry) — next 30-min cycle will attempt again.
            // Do not retry on error: a failed check is safer than an infinite loop.
            Result.success()
        }
    }
}
