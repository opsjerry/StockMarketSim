package com.example.stockmarketsim.domain.strategy

import com.example.stockmarketsim.domain.model.StockQuote

class ConfigurableMomentumStrategy(
    private val lookbackPeriod: Int,
    override val id: String = "MOMENTUM_SMA_$lookbackPeriod",
    override val name: String = "Momentum (SMA $lookbackPeriod)"
) : Strategy {
    
    override val description: String = "Buys when price > SMA $lookbackPeriod"

    override suspend fun calculateallocation(
        candidates: List<String>,
        marketData: Map<String, List<StockQuote>>,
        cursors: Map<String, Int>
    ): Map<String, Double> {
        val scored = mutableListOf<Pair<String, Double>>()
        
        candidates.forEach { symbol ->
            val history = marketData[symbol] ?: return@forEach
            val currentIdx = cursors[symbol] ?: return@forEach
            
            if (currentIdx < lookbackPeriod) return@forEach
            
            val latest = history[currentIdx]
            
            // Optimization: Zero-allocation SMA calculation
            var sum = 0.0
            val startIndex = currentIdx - lookbackPeriod + 1
            for (i in startIndex..currentIdx) {
                sum += history[i].close
            }
            val sma = sum / lookbackPeriod
            
            // Score: % distance from SMA (Strength of trend)
            if (latest.close > sma) {
                val distance = (latest.close - sma) / sma
                scored.add(symbol to distance)
            }
        }
        
        if (scored.isEmpty()) return emptyMap()
        
        val topSelected = scored.sortedByDescending { it.second }.take(20)
        val totalScore = topSelected.sumOf { it.second }
        if (totalScore <= 0) return emptyMap()
        return topSelected.associate { it.first to (it.second / totalScore) }
    }

    override suspend fun getSignal(symbol: String, history: List<StockQuote>, currentIdx: Int): TradeSignal {
        if (currentIdx < lookbackPeriod) return TradeSignal.HOLD
        
        val latest = history[currentIdx]
        
        // Optimization: Zero-allocation SMA calculation
        var sum = 0.0
        val startIndex = currentIdx - lookbackPeriod + 1
        for (i in startIndex..currentIdx) {
            sum += history[i].close
        }
        val sma = sum / lookbackPeriod
        
        return if (latest.close > sma) {
            TradeSignal.BUY
        } else {
            TradeSignal.SELL
        }
    }
}
