package com.example.stockmarketsim.domain.strategy

import com.example.stockmarketsim.domain.model.StockQuote
import kotlin.math.pow
import kotlin.math.sqrt

class BollingerBreakoutStrategy(
    private val period: Int = 20,
    private val stdDevMultiplier: Double = 2.0,
    override val id: String = "BOLLINGER_BREAKOUT_${period}_${(stdDevMultiplier * 10).toInt()}",
    override val name: String = "Volatility Breakout (BB $period, ${stdDevMultiplier}x)"
) : Strategy {

    override val description: String = "Buys when Price > Upper Bollinger Band ($period, $stdDevMultiplier)"

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
            val startIndex = currentIdx - period + 1
            
            for (i in startIndex..currentIdx) {
                val price = history[i].close
                sum += price
                sumSq += price * price
            }
            
            val mean = sum / count
            val variance = (sumSq / count) - (mean * mean)
            val stdDev = sqrt(variance.coerceAtLeast(0.0))
            val upperBand = mean + (stdDev * stdDevMultiplier)
            
            // Score: % distance above upper band (breakout strength)
            if (latest.close > upperBand && upperBand > 0) {
                scored.add(symbol to ((latest.close - upperBand) / upperBand))
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
        val upperBand = mean + (stdDev * stdDevMultiplier)
        
        return if (latest.close > upperBand) TradeSignal.BUY else if (latest.close < mean) TradeSignal.SELL else TradeSignal.HOLD
    }
}
