package com.example.stockmarketsim.domain.strategy

import com.example.stockmarketsim.domain.model.StockQuote
import javax.inject.Inject

class MomentumStrategy @Inject constructor() : Strategy {
    override val id = "MOMENTUM"
    override val name = "Momentum Chaser"
    override val description = "Buys stocks trending above their 20-day Moving Average."

    override suspend fun calculateallocation(
        candidates: List<String>,
        marketData: Map<String, List<StockQuote>>,
        cursors: Map<String, Int>
    ): Map<String, Double> {
        val selected = mutableListOf<String>()
        
        candidates.forEach { symbol ->
            val history = marketData[symbol] ?: return@forEach
            val currentIdx = cursors[symbol] ?: return@forEach
            
            if (getSignal(symbol, history, currentIdx) == TradeSignal.BUY) {
                selected.add(symbol)
            }
        }
        
        if (selected.isEmpty()) return emptyMap()
        
        // Equal weight
        val weight = 1.0 / selected.size
        return selected.associateWith { weight }
    }

    override suspend fun getSignal(symbol: String, history: List<StockQuote>, currentIdx: Int): TradeSignal {
        if (currentIdx < 20) return TradeSignal.HOLD
        
        val latest = history[currentIdx]
        // Calculate SMA 20 using Zero-Allocation Loop
        var sum = 0.0
        val startIdx = currentIdx - 19
        for (i in startIdx..currentIdx) {
            sum += history[i].close
        }
        val sma20 = sum / 20.0
        
        return if (latest.close > sma20) {
            TradeSignal.BUY
        } else {
            TradeSignal.SELL
        }
    }
}
