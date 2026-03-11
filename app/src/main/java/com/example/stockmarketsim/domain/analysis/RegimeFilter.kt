package com.example.stockmarketsim.domain.analysis

import com.example.stockmarketsim.domain.model.StockQuote
import kotlin.math.ln
import kotlin.math.sqrt

object RegimeFilter {

    fun detectRegime(
        benchmarkHistory: List<StockQuote>,
        inflation: Double = 0.0,
        timeFrame: Int = 200 // SMA(200): Industry-standard macro regime indicator
    ): RegimeSignal {
        if (benchmarkHistory.size < timeFrame) return RegimeSignal.NEUTRAL

        // 0. Fast-Bear Tripwire: Nifty down > 7% in 20 days → immediate BEARISH.
        // This fires ahead of SMA(200) which lags 4–8 weeks on event-driven shocks
        // (crude oil spikes, geopolitical escalation, sudden FII outflows).
        // 7% chosen: covers a 2-standard-deviation 1-month move on Nifty (~3.5% σ/mo).
        if (benchmarkHistory.size >= 21) {
            val recent20 = benchmarkHistory.takeLast(21)
            val ret20d = (recent20.last().close - recent20.first().close) / recent20.first().close
            if (ret20d < -0.07) {
                android.util.Log.w("RegimeFilter",
                    "⚡ Fast-Bear triggered: Nifty −${"%,.1f".format(-ret20d * 100)}% in 20 days")
                return RegimeSignal.BEARISH
            }
        }

        // 1. Trend (SMA) — zero-allocation cursor loop
        val currentPrice = benchmarkHistory.last().close
        val smaStart = benchmarkHistory.size - timeFrame
        var smaSum = 0.0
        for (i in smaStart until benchmarkHistory.size) {
            smaSum += benchmarkHistory[i].close
        }
        val sma = smaSum / timeFrame
        val isUptrend = currentPrice > sma

        // 2. Volatility — zero-allocation: compute mean and variance in one pass
        val n = benchmarkHistory.size - 1
        var sumR = 0.0
        var sumR2 = 0.0
        for (i in 1 until benchmarkHistory.size) {
            val r = ln(benchmarkHistory[i].close / benchmarkHistory[i - 1].close)
            sumR += r
            sumR2 += r * r
        }
        val meanR = sumR / n
        val variance = (sumR2 / n) - (meanR * meanR)
        val volatility = sqrt(if (variance > 0) variance else 0.0) * sqrt(252.0) // Annualized
        val isHighVol = volatility > 0.20 // 20% VIX threshold

        // 3. Inflation Check (India CPI via World Bank — RBI tolerance band: 2–6%)
        // isHighInflation triggers BEARISH when CPI exceeds the upper band (6%).
        // AlphaVantage US CPI (old threshold 4.0%) replaced with India-specific threshold.
        val isHighInflation = inflation > 6.0

        return when {
            isUptrend && !isHighVol && !isHighInflation -> RegimeSignal.BULLISH
            !isUptrend && isHighVol -> RegimeSignal.BEARISH
            isHighInflation -> RegimeSignal.BEARISH
            else -> RegimeSignal.NEUTRAL
        }
    }
}

enum class RegimeSignal {
    BULLISH, BEARISH, NEUTRAL
}
