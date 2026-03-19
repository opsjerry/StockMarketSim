package com.example.stockmarketsim.domain.analysis

import com.example.stockmarketsim.domain.model.StockQuote
import kotlin.math.ln
import kotlin.math.sqrt

object RegimeFilter {

    fun detectRegime(
        benchmarkHistory: List<StockQuote>,
        inflation: Double = 0.0,
        timeFrame: Int = 200, // SMA(200): Industry-standard macro regime indicator
        onLog: ((String) -> Unit)? = null
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
                val drop = "%,.1f".format(-ret20d * 100)
                android.util.Log.w("RegimeFilter",
                    "⚡ Fast-Bear triggered: Nifty −$drop% in 20 days")
                onLog?.invoke("🛑 Regime: BEARISH (Fast-Bear tripwire: Nifty dropped $drop% in 20 days)")
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

        // 2. Volatility (HV60) — 60-trading-day realized vol window.
        // Rationale: HV252 (1-year) retains crisis shocks (e.g. 2020 COVID crash) for 12 months,
        // creating a "vol memory" that keeps the regime stuck BEARISH long after recovery.
        // HV30 is too noisy (95% CI spans ±40% of estimate, high whipsaw risk).
        // HV60 (≈1 fiscal quarter) is the practitioner sweet spot: statistically sound
        // (~60 log-return observations) and responsive enough to detect a genuine regime
        // shift within 30–45 trading days. The 20% annualized threshold is unchanged.
        val volWindow = benchmarkHistory.takeLast(61) // 61 prices → 60 log-returns
        val n = volWindow.size - 1
        var sumR = 0.0
        var sumR2 = 0.0
        for (i in 1 until volWindow.size) {
            val r = ln(volWindow[i].close / volWindow[i - 1].close)
            sumR += r
            sumR2 += r * r
        }
        val meanR = sumR / n
        val variance = (sumR2 / n) - (meanR * meanR)
        val volatility = sqrt(if (variance > 0) variance else 0.0) * sqrt(252.0) // Annualized
        val isHighVol = volatility > 0.20 // 20% annualized threshold

        // 3. Inflation Check (India CPI via World Bank — RBI tolerance band: 2–6%)
        // isHighInflation triggers BEARISH when CPI exceeds the upper band (6%).
        // AlphaVantage US CPI (old threshold 4.0%) replaced with India-specific threshold.
        val isHighInflation = inflation > 6.0

        val trendStr = if (isUptrend) "Uptrend" else "Downtrend"
        val distPct = if (sma > 0) ((currentPrice - sma) / sma) * 100 else 0.0
        val distStr = "${if(distPct >= 0) "+" else ""}${"%.1f".format(distPct)}%"
        val volStr = "${"%.1f".format(volatility * 100)}%"
        onLog?.invoke("🔬 Macro: $trendStr (SMA $distStr) | Vol: $volStr (Limit 20%) | CPI: $inflation% (Limit 6%)")

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
