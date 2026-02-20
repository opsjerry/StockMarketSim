package com.example.stockmarketsim.domain.strategy

import com.example.stockmarketsim.domain.model.StockQuote

class HybridMomentumRsiStrategy(
    private val smaPeriod: Int = 50,
    private val rsiPeriod: Int = 14,
    override val id: String = "HYBRID_MOM_RSI_$smaPeriod",
    override val name: String = "Safe Haven Trend (Hybrid $smaPeriod)"
) : Strategy {

    override val description: String = "Momentum with a safety lock: Only buys if price is trending ($smaPeriod) but NOT overbought (RSI)."

    override suspend fun calculateallocation(
        candidates: List<String>,
        marketData: Map<String, List<StockQuote>>,
        cursors: Map<String, Int>
    ): Map<String, Double> {
        val scored = mutableListOf<Pair<String, Double>>()
        
        candidates.forEach { symbol ->
            val history = marketData[symbol] ?: return@forEach
            val currentIdx = cursors[symbol] ?: return@forEach
            
            if (currentIdx < smaPeriod) return@forEach
            
            val latest = history[currentIdx]
            
            // SMA
            var sum = 0.0
            val smaStart = currentIdx - smaPeriod + 1
            for (i in smaStart..currentIdx) {
                sum += history[i].close
            }
            val sma = sum / smaPeriod
            
            val rsi = calculateRSI(history, rsiPeriod, currentIdx)

            // Score: % distance from SMA (only if not overbought)
            if (latest.close > sma && rsi < 65.0) {
                val dist = (latest.close - sma) / sma
                scored.add(symbol to dist)
            }
        }
        
        if (scored.isEmpty()) return emptyMap()
        
        // Take Top 20 by trend conviction
        val topSelected = scored.sortedByDescending { it.second }.take(20)
        
        val totalScore = topSelected.sumOf { it.second }
        if (totalScore <= 0) return emptyMap()
        return topSelected.associate { it.first to (it.second / totalScore) }
    }

    override suspend fun getSignal(symbol: String, history: List<StockQuote>, currentIdx: Int): TradeSignal {
        if (currentIdx < smaPeriod) return TradeSignal.HOLD
        val latest = history[currentIdx]
        
        var sum = 0.0
        val smaStart = currentIdx - smaPeriod + 1
        for (i in smaStart..currentIdx) {
            sum += history[i].close
        }
        val sma = sum / smaPeriod
        
        val rsi = calculateRSI(history, rsiPeriod, currentIdx)

        return when {
            latest.close > sma && rsi < 65.0 -> TradeSignal.BUY
            latest.close < sma || rsi > 80.0 -> TradeSignal.SELL
            else -> TradeSignal.HOLD
        }
    }

    private fun calculateRSI(history: List<StockQuote>, period: Int, currentIdx: Int): Double {
        if (currentIdx < period) return 50.0
        var gains = 0.0
        var losses = 0.0
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
