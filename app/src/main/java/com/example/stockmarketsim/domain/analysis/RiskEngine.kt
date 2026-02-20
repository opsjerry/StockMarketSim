package com.example.stockmarketsim.domain.analysis

import com.example.stockmarketsim.domain.model.StockQuote
import com.example.stockmarketsim.domain.strategy.StrategySignal
import kotlin.math.abs
import kotlin.math.max

/**
 * Advanced Risk Management Logic.
 */
object RiskEngine {

    /**
     * Calculates the Average True Range (ATR) for a given history.
     */
    fun calculateATR(history: List<StockQuote>, period: Int = 14): Double {
        if (history.size < period + 1) return 0.0
        
        val trueRanges = mutableListOf<Double>()
        
        for (i in 1 until history.size) {
            val high = history[i].high
            val low = history[i].low
            val prevClose = history[i - 1].close
            
            val tr = max(high - low, max(abs(high - prevClose), abs(low - prevClose)))
            trueRanges.add(tr)
        }
        
        if (trueRanges.size < period) return 0.0
        
        return trueRanges.takeLast(period).average()
    }

    /**
     * Calculates a dynamic stop-loss price based on ATR and a multiplier.
     * Higher volatility results in a wider stop, BUT we cap it for "Turbulence".
     */
    fun calculateATRStopPrice(
        peakPrice: Double,
        atr: Double,
        multiplier: Double = 2.0, // Reduced from 2.5 for tighter control
        isVolatile: Boolean = false
    ): Double {
        if (atr <= 0) return peakPrice * 0.93 // Fail-safe: 7% Hard Stop
        
        // In turbulent markets, we tighten the stop significantly
        val efficientMultiplier = if (isVolatile) 1.5 else multiplier
        
        val riskAmount = atr * efficientMultiplier
        val stopPrice = peakPrice - riskAmount
        
        // HARD STOP: Never allow more than 7% loss from peak, even if ATR says otherwise
        val maxDrop = peakPrice * 0.93
        return max(stopPrice, maxDrop)
    }

    /**
     * Checks if a stock is currently "Volatile" (Turbulent).
     * We define turbulence as frequent large daily swings > 2% recently.
     */
    fun isVolatile(history: List<StockQuote>): Boolean {
        if (history.size < 5) return false
        
        // Check last 5 days for "Choppiness"
        val recent = history.takeLast(5)
        var chopCount = 0
        
        for (i in 1 until recent.size) {
            val change = abs((recent[i].close - recent[i-1].close) / recent[i-1].close)
            if (change > 0.02) { // > 2% Daily Move
                chopCount++
            }
        }
        
        // If 2 or more days in the last week were wild, it's Turbulent
        return chopCount >= 2
    }

    /**
     * Applies risk management rules to generate target allocations.
     * Checks signals and market conditions.
     */
    fun applyRiskManagement(
        signals: List<StrategySignal>,
        totalEquity: Double,
        isBearMarket: Boolean
    ): Map<String, Double> {
        val allocations = mutableMapOf<String, Double>()
        
        // Filter BUY signals
        val buySignals = signals.filter { it.signal == "BUY" }
        
        if (buySignals.isEmpty()) return allocations

        // Position Sizing Logic
        // In Bear Market, reduce exposure
        val maxAllocationPerStock = if (isBearMarket) 0.05 else 0.10 // 5% vs 10%
        val maxTotalExposure = if (isBearMarket) 0.50 else 1.0 // 50% Cash in Bear
        
        var currentExposure = 0.0
        
        for (signal in buySignals) {
            if (currentExposure + maxAllocationPerStock > maxTotalExposure) break
            
            // Weight allocation by signal confidence (0.5x to 1.5x base allocation)
            val weight = (signal.confidence.coerceIn(0.5, 1.5)) * maxAllocationPerStock
            
            allocations[signal.symbol] = weight
            currentExposure += weight
        }
        
        return allocations
    }
}
