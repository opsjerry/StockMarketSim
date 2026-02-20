package com.example.stockmarketsim.domain.strategy

import com.example.stockmarketsim.domain.model.StockQuote
import kotlin.math.pow
import kotlin.math.sqrt

class BollingerMeanReversionStrategy(
    private val period: Int = 20,
    private val stdDevMultiplier: Double = 2.0,
    override val id: String = "BB_MEAN_REVERSION_${period}_${(stdDevMultiplier * 10).toInt()}",
    override val name: String = "Mean Reversion (BB $period, ${stdDevMultiplier}x)"
) : Strategy {

    override val description: String = "Buys when Price < Lower Bollinger Band ($period, $stdDevMultiplier) (Oversold Dip)."

    override suspend fun calculateallocation(
        candidates: List<String>,
        marketData: Map<String, List<StockQuote>>,
        cursors: Map<String, Int>
    ): Map<String, Double> {
        val scored = mutableListOf<Pair<String, Double>>()
        
        candidates.forEach { symbol ->
            val history = marketData[symbol] ?: return@forEach
            val currentIdx = cursors[symbol] ?: return@forEach
            
            if (currentIdx < period) return@forEach
            
            val latest = history[currentIdx]
            
            // Optimization: Zero-allocation Bollinger calculation
            var sum = 0.0
            var sumSq = 0.0
            val count = period
            // Calculate over the period ending at currentIdx
            val startIndex = currentIdx - period + 1
            
            for (i in startIndex..currentIdx) {
                val price = history[i].close
                sum += price
                sumSq += price * price
            }
            
            val mean = sum / count
            val variance = (sumSq / count) - (mean * mean)
            val stdDev = sqrt(variance.coerceAtLeast(0.0)) // Prevent NaN if precision error
            
            val lowerBand = mean - (stdDev * stdDevMultiplier)
            
            // Score: % distance below lower band (dip depth)
            if (latest.close < lowerBand && lowerBand > 0) {
                scored.add(symbol to ((lowerBand - latest.close) / lowerBand))
            }
        }
        
        if (scored.isEmpty()) return emptyMap()
        
        val topSelected = scored.sortedByDescending { it.second }.take(20)
        val totalScore = topSelected.sumOf { it.second }
        if (totalScore <= 0) return emptyMap()
        return topSelected.associate { it.first to (it.second / totalScore) }
    }

    override suspend fun getSignal(symbol: String, history: List<StockQuote>, currentIdx: Int): TradeSignal {
        if (currentIdx < period) return TradeSignal.HOLD
        
        val latest = history[currentIdx]

        // Optimization: Zero-allocation Bollinger calculation
        var sum = 0.0
        var sumSq = 0.0
        val count = period
        val startIndex = currentIdx - period + 1
        
        for (i in startIndex..currentIdx) {
            val price = history[i].close
            sum += price
            sumSq += price * price
        }
        
        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)
        val stdDev = sqrt(variance.coerceAtLeast(0.0))
        
        val lowerBand = mean - (stdDev * stdDevMultiplier)
        val upperBand = mean + (stdDev * stdDevMultiplier)
        
        return if (latest.close < lowerBand) TradeSignal.BUY else if (latest.close > upperBand) TradeSignal.SELL else TradeSignal.HOLD
    }
}
