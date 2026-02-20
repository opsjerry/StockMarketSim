package com.example.stockmarketsim.domain.strategy

import com.example.stockmarketsim.domain.model.StockQuote

class YearlyHighBreakoutStrategy(
    override val id: String = "YEARLY_HIGH_BREAKOUT",
    override val name: String = "All-Time High Runner"
) : Strategy {

    override val description: String = "Buys stocks specifically as they break their 52-week (approx 250 days) High."

    override suspend fun calculateallocation(
        candidates: List<String>,
        marketData: Map<String, List<StockQuote>>,
        cursors: Map<String, Int>
    ): Map<String, Double> {
        val scored = mutableListOf<Pair<String, Double>>()
        
        candidates.forEach { symbol ->
            val history = marketData[symbol] ?: return@forEach
            val currentIdx = cursors[symbol] ?: return@forEach
            
            if (currentIdx < 251) return@forEach
            
            val latest = history[currentIdx]
            
            // Find 52-week High (approx 250 days) in window [currentIdx - 250, currentIdx - 1]
            var yearlyHigh = 0.0
            val startIdx = currentIdx - 250
            for (i in startIdx until currentIdx) {
                if (history[i].high > yearlyHigh) yearlyHigh = history[i].high
            }
            
            // Score: % distance above 52-week high
            if (latest.close > yearlyHigh && yearlyHigh > 0) {
                val score = (latest.close - yearlyHigh) / yearlyHigh
                scored.add(symbol to score)
            }
        }
        
        if (scored.isEmpty()) return emptyMap()
        
        // Take Top 20 by breakout strength
        val topSelected = scored.sortedByDescending { it.second }.take(20)
        
        val totalScore = topSelected.sumOf { it.second }
        if (totalScore <= 0) return emptyMap()
        return topSelected.associate { it.first to (it.second / totalScore) }
    }

    override suspend fun getSignal(symbol: String, history: List<StockQuote>, currentIdx: Int): TradeSignal {
        if (currentIdx < 251) return TradeSignal.HOLD
        
        val latest = history[currentIdx]
        
        var yearlyHigh = 0.0
        val startIdx = currentIdx - 250
        for (i in startIdx until currentIdx) {
            if (history[i].high > yearlyHigh) yearlyHigh = history[i].high
        }
        
        return if (latest.close > yearlyHigh && yearlyHigh > 0) TradeSignal.BUY else TradeSignal.SELL
    }
}
