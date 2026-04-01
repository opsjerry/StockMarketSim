package com.example.stockmarketsim.domain.analysis

import com.example.stockmarketsim.domain.model.StockQuote
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Full numeric details of a regime detection run — used to emit the enhanced
 * Macro Dashboard KPI log block without re-computing inside the use-case.
 */
data class RegimeDetail(
    val niftyPrice: Double,
    val sma200: Double,
    val distPct: Double,          // (niftyPrice - sma200) / sma200 * 100
    val hv60Pct: Double,          // Annualized HV60 in % (e.g. 14.2)
    val fastBear20dPct: Double,   // 20-day return in % (negative = drop)
    val inflation: Double,
    val isUptrend: Boolean,
    val isHighVol: Boolean,
    val isHighInflation: Boolean,
    val fastBearTriggered: Boolean,
    val signal: RegimeSignal
)

object RegimeFilter {

    /**
     * Lightweight signal-only path (unchanged public API, used by Backtester).
     */
    fun detectRegime(
        benchmarkHistory: List<StockQuote>,
        inflation: Double = 0.0,
        timeFrame: Int = 200,
        onLog: ((String) -> Unit)? = null
    ): RegimeSignal = detectRegimeDetail(benchmarkHistory, inflation, timeFrame, onLog).signal

    /**
     * Full detail path — used by RunDailySimulationUseCase for the enhanced KPI log.
     * Returns a RegimeDetail with all raw numeric fields the caller can format freely.
     */
    fun detectRegimeDetail(
        benchmarkHistory: List<StockQuote>,
        inflation: Double = 0.0,
        timeFrame: Int = 200,
        onLog: ((String) -> Unit)? = null
    ): RegimeDetail {
        val neutralDetail = RegimeDetail(
            niftyPrice = benchmarkHistory.lastOrNull()?.close ?: 0.0,
            sma200 = 0.0, distPct = 0.0, hv60Pct = 0.0, fastBear20dPct = 0.0,
            inflation = inflation, isUptrend = false, isHighVol = false,
            isHighInflation = false, fastBearTriggered = false, signal = RegimeSignal.NEUTRAL
        )
        if (benchmarkHistory.size < timeFrame) return neutralDetail

        // 0. Fast-Bear Tripwire: Nifty down > 7% in 20 days → immediate BEARISH.
        var fastBear20dPct = 0.0
        var fastBearTriggered = false
        if (benchmarkHistory.size >= 21) {
            val recent20 = benchmarkHistory.takeLast(21)
            fastBear20dPct = (recent20.last().close - recent20.first().close) / recent20.first().close * 100
            if (fastBear20dPct < -7.0) {
                fastBearTriggered = true
                val drop = "%,.1f".format(-fastBear20dPct)
                android.util.Log.w("RegimeFilter", "⚡ Fast-Bear triggered: Nifty −$drop% in 20 days")
                onLog?.invoke("🛑 Regime: BEARISH (Fast-Bear tripwire: Nifty dropped $drop% in 20 days)")
                return RegimeDetail(
                    niftyPrice = benchmarkHistory.last().close,
                    sma200 = 0.0, distPct = 0.0, hv60Pct = 0.0,
                    fastBear20dPct = fastBear20dPct, inflation = inflation,
                    isUptrend = false, isHighVol = false, isHighInflation = false,
                    fastBearTriggered = true, signal = RegimeSignal.BEARISH
                )
            }
        }

        // 1. Trend (SMA-200)
        val currentPrice = benchmarkHistory.last().close
        val smaStart = benchmarkHistory.size - timeFrame
        var smaSum = 0.0
        for (i in smaStart until benchmarkHistory.size) smaSum += benchmarkHistory[i].close
        val sma = smaSum / timeFrame
        val isUptrend = currentPrice > sma
        val distPct = if (sma > 0) ((currentPrice - sma) / sma) * 100 else 0.0

        // 2. Volatility (HV60)
        val volWindow = benchmarkHistory.takeLast(61)
        val n = volWindow.size - 1
        var sumR = 0.0; var sumR2 = 0.0
        for (i in 1 until volWindow.size) {
            val r = ln(volWindow[i].close / volWindow[i - 1].close)
            sumR += r; sumR2 += r * r
        }
        val meanR = sumR / n
        val variance = (sumR2 / n) - (meanR * meanR)
        val volatility = sqrt(if (variance > 0) variance else 0.0) * sqrt(252.0)
        val isHighVol = volatility > 0.20

        // 3. Inflation
        val isHighInflation = inflation > 6.0

        // Terse inline log (backward-compat)
        val trendStr = if (isUptrend) "Uptrend" else "Downtrend"
        val distStr = "${if (distPct >= 0) "+" else ""}${"%.1f".format(distPct)}%"
        onLog?.invoke("🔬 Macro: $trendStr (SMA $distStr) | Vol: ${"%.1f".format(volatility * 100)}% (Limit 20%) | CPI: $inflation% (Limit 6%)")

        val signal = when {
            isUptrend && !isHighVol && !isHighInflation -> RegimeSignal.BULLISH
            !isUptrend && isHighVol -> RegimeSignal.BEARISH
            isHighInflation -> RegimeSignal.BEARISH
            else -> RegimeSignal.NEUTRAL
        }

        return RegimeDetail(
            niftyPrice = currentPrice,
            sma200 = sma,
            distPct = distPct,
            hv60Pct = volatility * 100,
            fastBear20dPct = fastBear20dPct,
            inflation = inflation,
            isUptrend = isUptrend,
            isHighVol = isHighVol,
            isHighInflation = isHighInflation,
            fastBearTriggered = false,
            signal = signal
        )
    }
}

enum class RegimeSignal {
    BULLISH, BEARISH, NEUTRAL
}
