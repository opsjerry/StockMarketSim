package com.example.stockmarketsim.domain.strategy

import com.example.stockmarketsim.domain.model.StockQuote

class EmaMomentumStrategy(
    private val period: Int = 20,
    override val id: String = "EMA_MOMENTUM_$period",
    override val name: String = "Fast Trend (EMA $period)"
) : Strategy {

    override val description: String = "Captures fast trends using Exponential Moving Average ($period)."

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
            val ema = calculateEMA(history, period, currentIdx)
            
            // Score: % distance from EMA (Strength of trend)
            if (latest.close > ema) {
                val distance = (latest.close - ema) / ema
                scored.add(symbol to distance)
            }
        }
        
        if (scored.isEmpty()) return emptyMap()
        
        // Take Top 20 by trend strength
        val topSelected = scored.sortedByDescending { it.second }.take(20)
        
        // Signal-proportional sizing
        val totalScore = topSelected.sumOf { it.second }
        if (totalScore <= 0) return emptyMap()
        return topSelected.associate { it.first to (it.second / totalScore) }
    }

    override suspend fun getSignal(symbol: String, history: List<StockQuote>, currentIdx: Int): TradeSignal {
        if (currentIdx < period) return TradeSignal.HOLD
        val latest = history[currentIdx]
        val ema = calculateEMA(history, period, currentIdx)
        return if (latest.close > ema) TradeSignal.BUY else TradeSignal.SELL
    }

    private fun calculateEMA(history: List<StockQuote>, period: Int, currentIdx: Int): Double {
        if (history.isEmpty()) return 0.0
        val k = 2.0 / (period + 1)
        
        // Start with SMA as first EMA value for stability (or close estimate)
        val startIdx = (currentIdx - (period * 2)).coerceAtLeast(0)
        
        // Use first value in window as seed
        var ema = history[startIdx].close
        // Iterate up to currentIdx
        for (i in (startIdx + 1)..currentIdx) {
            val price = history[i].close
            ema = (price * k) + (ema * (1 - k))
        }
        return ema
    }
}
