package com.example.stockmarketsim.domain.strategy

import com.example.stockmarketsim.domain.model.StockQuote

class RsiStrategy(
    private val period: Int = 14,
    private val buyThreshold: Int = 30,
    override val id: String = "RSI_MEAN_REVERSION_$period",
    override val name: String = "Mean Reversion (RSI $period)"
) : Strategy {

    override val description: String = "Buys when RSI < $buyThreshold (Oversold)"

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
            
            val rsi = calculateRSI(history, period, currentIdx)
            
            // Score: Distance from Oversold (Lower is better for reversal)
            if (rsi < buyThreshold) {
                scored.add(symbol to (buyThreshold - rsi))
            }
        }
        
        if (scored.isEmpty()) return emptyMap()
        
        // Take Top 20 most oversold — signal-proportional sizing
        val topSelected = scored.sortedByDescending { it.second }.take(20)
        
        val totalScore = topSelected.sumOf { it.second }
        if (totalScore <= 0) return emptyMap()
        return topSelected.associate { it.first to (it.second / totalScore) }
    }

    override suspend fun getSignal(symbol: String, history: List<StockQuote>, currentIdx: Int): TradeSignal {
        if (currentIdx < period) return TradeSignal.HOLD
        val rsi = calculateRSI(history, period, currentIdx)
        return if (rsi < buyThreshold) TradeSignal.BUY else if (rsi > 70) TradeSignal.SELL else TradeSignal.HOLD
    }

    private fun calculateRSI(history: List<StockQuote>, period: Int, currentIdx: Int): Double {
        if (currentIdx < period) return 50.0
        var gains = 0.0
        var losses = 0.0
        // Calculate change over the last 'period' days ending at currentIdx
        // Loop from (currentIdx - period + 1) to currentIdx
        val startIdx = currentIdx - period + 1
        for (i in startIdx..currentIdx) {
            val change = history[i].close - history[i - 1].close
            if (change > 0) gains += change else losses -= change
        }
        if (losses == 0.0) return 100.0
        val rs = (gains / period) / (losses / period)
        return 100.0 - (100.0 / (1.0 + rs))
    }
}
