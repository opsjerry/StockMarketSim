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

        // 1. Trend (SMA)
        val prices = benchmarkHistory.map { it.close }
        val currentPrice = prices.last()
        val sma = prices.takeLast(timeFrame).average()
        val isUptrend = currentPrice > sma

        // 2. Volatility (High/Low)
        val returns = prices.zipWithNext { a, b -> ln(b / a) }
        val volatility = calculateStdDev(returns) * sqrt(252.0) // Annualized
        val isHighVol = volatility > 0.20 // 20% VIX threshold

        // 3. Inflation Check (CPI)
        // If inflation > 4%, market is under pressure
        val isHighInflation = inflation > 4.0

        return when {
            isUptrend && !isHighVol && !isHighInflation -> RegimeSignal.BULLISH
            !isUptrend && isHighVol -> RegimeSignal.BEARISH
            isHighInflation -> RegimeSignal.BEARISH // Inflation kills valuation
            else -> RegimeSignal.NEUTRAL // Choppy or mixed
        }
    }

    private fun calculateStdDev(data: List<Double>): Double {
        if (data.isEmpty()) return 0.0
        val mean = data.average()
        val variance = data.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }
}

enum class RegimeSignal {
    BULLISH, BEARISH, NEUTRAL
}
