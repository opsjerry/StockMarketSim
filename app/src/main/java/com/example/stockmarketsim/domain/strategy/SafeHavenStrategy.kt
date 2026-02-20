package com.example.stockmarketsim.domain.strategy

import com.example.stockmarketsim.domain.model.StockQuote
import javax.inject.Inject

class SafeHavenStrategy @Inject constructor() : Strategy {
    override val id: String = "SAFE_HAVEN"
    override val name: String = "Safe Haven"
    override val description: String = "Low Volatility, Steady"

    override suspend fun calculateallocation(
        candidates: List<String>,
        marketData: Map<String, List<StockQuote>>,
        cursors: Map<String, Int>
    ): Map<String, Double> {
        val volatilityScores = candidates.mapNotNull { symbol ->
             val history = marketData[symbol] ?: return@mapNotNull null
             val currentIdx = cursors[symbol] ?: return@mapNotNull null
             if (currentIdx < 20) return@mapNotNull null
             
             // Calculate 20-day Volatility
             var sum = 0.0
             var sumSq = 0.0
             val startIdx = currentIdx - 19
             for (i in startIdx..currentIdx) {
                 val price = history[i].close
                 sum += price
                 sumSq += price * price
             }
             val mean = sum / 20.0
             val variance = (sumSq / 20.0) - (mean * mean)
             val stdDev = Math.sqrt(variance.coerceAtLeast(0.0))
             
             // Score: Lower is better (Stability)
             val volPct = if (mean > 0) stdDev / mean else 1.0
             symbol to volPct
        }.sortedBy { it.second } // Ascending: Lowest Volatility First
        
        if (volatilityScores.isEmpty()) return emptyMap()
        
        val topSafe = volatilityScores.take(10)
        
        // Inverse-volatility weighting
        val inverseVols = topSafe.map { it.first to (1.0 / (it.second + 0.001)) }
        val totalInverse = inverseVols.sumOf { it.second }
        
        return inverseVols.associate { it.first to (it.second / totalInverse) }
    }

    override suspend fun getSignal(symbol: String, history: List<StockQuote>, currentIdx: Int): TradeSignal {
        if (currentIdx < 19) return TradeSignal.HOLD
        
        var sum = 0.0
        var sumSq = 0.0
        val startIdx = currentIdx - 19
        for (i in startIdx..currentIdx) {
            val price = history[i].close
            sum += price
            sumSq += price * price
        }
        val mean = sum / 20.0
        val variance = (sumSq / 20.0) - (mean * mean)
        val stdDev = Math.sqrt(variance.coerceAtLeast(0.0))
        
        val volatilityPct = if (mean > 0) stdDev / mean else 1.0
        
        return if (volatilityPct < 0.02) {
            TradeSignal.BUY
        } else {
            TradeSignal.HOLD
        }
    }
}
