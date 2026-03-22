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
     * Zero-allocation: uses only the last `period` true ranges via cursor math.
     */
    fun calculateATR(history: List<StockQuote>, period: Int = 14): Double {
        if (history.size < period + 1) return 0.0
        
        // Zero-allocation: compute ATR using only the last `period` true ranges
        val startIdx = history.size - period
        var atrSum = 0.0
        
        for (i in startIdx until history.size) {
            val high = history[i].high
            val low = history[i].low
            val prevClose = history[i - 1].close
            
            val tr = max(high - low, max(abs(high - prevClose), abs(low - prevClose)))
            atrSum += tr
        }
        
        return atrSum / period
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
     * Phase 3: Weights are re-scaled by inverse ATR% so high-volatility stocks
     * get proportionally smaller positions — without changing the strategy signals.
     *
     * @param marketData Optional price history per symbol for ATR% computation.
     *        Pass emptyMap() to skip inverse-vol weighting (backtester compat).
     */
    fun applyRiskManagement(
        signals: List<StrategySignal>,
        totalEquity: Double,
        isBearMarket: Boolean,
        marketData: Map<String, List<com.example.stockmarketsim.domain.model.StockQuote>> = emptyMap()
    ): Map<String, Double> {
        val allocations = mutableMapOf<String, Double>()

        // Filter BUY signals
        val buySignals = signals.filter { it.signal == "BUY" }

        if (buySignals.isEmpty()) return allocations

        // Position Sizing Logic
        // In Bear Market, reduce exposure
        val maxAllocationPerStock = if (isBearMarket) 0.05 else 0.10 // 5% vs 10%
        val maxTotalExposure      = if (isBearMarket) 0.50 else 1.0  // 50% cash in Bear

        // Step 1: Compute raw signal-weighted allocations (unchanged behaviour)
        val rawAllocations = mutableMapOf<String, Double>()
        var currentExposure = 0.0
        for (signal in buySignals) {
            if (currentExposure + maxAllocationPerStock > maxTotalExposure) break
            val weight = (signal.confidence.coerceIn(0.5, 1.5)) * maxAllocationPerStock
            rawAllocations[signal.symbol] = weight
            currentExposure += weight
        }

        // Step 2: Phase 3 — Inverse-volatility reweighting
        // For each symbol, compute ATR% = ATR(14) / lastClose.
        // Allocations are scaled by (1 / ATR%), then renormalized.
        // Skipped when marketData is empty (backtester, unit tests).
        if (marketData.isNotEmpty()) {
            val invVolWeights = mutableMapOf<String, Double>()
            for ((sym, rawWeight) in rawAllocations) {
                val history = marketData[sym]
                val lastClose = history?.lastOrNull()?.close ?: 0.0
                val atr = if (history != null) calculateATR(history, 14) else 0.0
                val atrPct = if (lastClose > 0 && atr > 0) atr / lastClose else 0.05  // fallback 5%
                // Inverse ATR%: lower-volatility stocks get higher weight
                invVolWeights[sym] = rawWeight / atrPct
            }

            // Renormalize to preserve total raw exposure
            val totalRaw    = rawAllocations.values.sum()
            val totalInvVol = invVolWeights.values.sum()

            if (totalInvVol > 0) {
                var remainingTarget = totalRaw
                var currentInvTotal = totalInvVol
                val finalAllocations = mutableMapOf<String, Double>()
                val cappedSymbols = mutableSetOf<String>()

                // Iterative redistribution (Water-filling algorithm)
                var redistributed = true
                while (redistributed && remainingTarget > 0.0001 && cappedSymbols.size < invVolWeights.size) {
                    redistributed = false
                    val weightPerUnit = if (currentInvTotal > 0) remainingTarget / currentInvTotal else 0.0

                    for ((sym, invW) in invVolWeights) {
                        if (sym in cappedSymbols) continue

                        val proposedWeight = invW * weightPerUnit
                        if (proposedWeight >= maxAllocationPerStock) {
                            finalAllocations[sym] = maxAllocationPerStock
                            cappedSymbols.add(sym)
                            remainingTarget -= maxAllocationPerStock
                            currentInvTotal -= invW
                            redistributed = true
                        }
                    }

                    if (!redistributed && currentInvTotal > 0) {
                        // All remaining uncapped stocks get their proportional weight
                        for ((sym, invW) in invVolWeights) {
                            if (sym !in cappedSymbols) {
                                finalAllocations[sym] = invW * weightPerUnit
                            }
                        }
                    }
                }
                allocations.putAll(finalAllocations)
            } else {
                allocations.putAll(rawAllocations)
            }
        } else {
            allocations.putAll(rawAllocations)
        }

        return allocations
    }
}
