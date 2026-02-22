package com.example.stockmarketsim.domain.ml

import java.util.Arrays
import kotlin.math.abs
import kotlin.math.max

/**
 * High-performance, zero-allocation technical indicator math designed for 
 * fast inner-loop execution during the simulation/tournament running across 165 stocks.
 * Complies with App Bible Section 5B (Zero-Allocation Architecture).
 *
 * TODO: These indicators are NOT currently used by the LSTM model.
 * They were built in preparation for the Multi-Factor XGBoost upgrade (Phase 1).
 * Wire these into the model input tensor when retraining with multi-factor features.
 */
object TechnicalIndicators {

    /**
     * Computes the 14-day RSI without allocating new lists.
     * @param closingPrices Array of closing prices.
     * @param endIdx The exclusive end index to calculate up to (usually today).
     * @param period The RSI period (default 14).
     * @return RSI value from 0.0 to 100.0, or 50.0 if not enough data.
     */
    fun calculateRsiZeroAlloc(
        closingPrices: DoubleArray,
        endIdx: Int,
        period: Int = 14
    ): Double {
        if (endIdx <= period) return 50.0 // Not enough data
        
        var sumGain = 0.0
        var sumLoss = 0.0
        
        val startIdx = endIdx - period
        
        // Use Simple Moving Average to match Python's rolling(14).mean()
        for (i in startIdx until endIdx) {
            val change = closingPrices[i] - closingPrices[i - 1]
            if (change > 0) sumGain += change else sumLoss -= change
        }
        
        val avgGain = sumGain / period
        val avgLoss = sumLoss / period
        
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    /**
     * Computes the SMA ratio (Fast / Slow) without allocating new lists.
     * E.g. 50-day / 200-day SMA.
     * @return Ratio > 1.0 means uptrend, < 1.0 means downtrend.
     */
    fun calculateSmaRatioZeroAlloc(
        closingPrices: DoubleArray,
        endIdx: Int,
        fastPeriod: Int = 50,
        slowPeriod: Int = 200
    ): Double {
        if (endIdx < slowPeriod) return 1.0 // Neutral default
        
        var fastSum = 0.0
        for (i in endIdx - fastPeriod until endIdx) {
            fastSum += closingPrices[i]
        }
        val fastSma = fastSum / fastPeriod
        
        var slowSum = 0.0
        for (i in endIdx - slowPeriod until endIdx) {
            slowSum += closingPrices[i]
        }
        val slowSma = slowSum / slowPeriod
        
        return if (slowSma != 0.0) fastSma / slowSma else 1.0
    }

    /**
     * Computes Average True Range (ATR) as a percentage of the current close price.
     * This orthagonalizes the volatility feature across cheap and expensive stocks.
     */
    fun calculateAtrPctZeroAlloc(
        highs: DoubleArray,
        lows: DoubleArray,
        closes: DoubleArray,
        endIdx: Int,
        period: Int = 14
    ): Double {
        if (endIdx <= period) return 0.0
        
        var atrSum = 0.0
        val startIdx = endIdx - period
        
        for (i in startIdx until endIdx) {
            val tr1 = highs[i] - lows[i]
            val tr2 = if (i > 0) abs(highs[i] - closes[i - 1]) else 0.0
            val tr3 = if (i > 0) abs(lows[i] - closes[i - 1]) else 0.0
            
            val tr = max(tr1, max(tr2, tr3))
            atrSum += tr
        }
        
        val atr = atrSum / period
        val currentClose = closes[endIdx - 1]
        
        return if (currentClose > 0.0) atr / currentClose else 0.0
    }
    
    /**
     * Computes Relative Volume (Today's Volume vs 20-day Average)
     */
    fun calculateRelativeVolumeZeroAlloc(
        volumes: DoubleArray,
        endIdx: Int,
        period: Int = 20
    ): Double {
         if (endIdx < period) return 1.0
         
         var sumVol = 0.0
         // Don't include "today" in the historical avg for true relative volume benchmark
         for (i in endIdx - period - 1 until endIdx - 1) {
             sumVol += volumes[i]
         }
         val avgVol = sumVol / period
         val currentVol = volumes[endIdx - 1]
         
         return if (avgVol > 0.0) currentVol / avgVol else 1.0
    }
}
