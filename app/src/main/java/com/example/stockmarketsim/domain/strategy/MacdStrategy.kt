package com.example.stockmarketsim.domain.strategy

import com.example.stockmarketsim.domain.model.StockQuote

class MacdStrategy(
    private val fastPeriod: Int = 12,
    private val slowPeriod: Int = 26,
    private val signalPeriod: Int = 9,
    override val id: String = "MACD_BASIC",
    override val name: String = "Cycle Master (MACD)"
) : Strategy {

    override val description: String = "Uses MACD crossovers to find the perfect entry point in a moving cycle."

    override suspend fun calculateallocation(
        candidates: List<String>,
        marketData: Map<String, List<StockQuote>>,
        cursors: Map<String, Int>
    ): Map<String, Double> {
        val scored = mutableListOf<Pair<String, Double>>()
        for (symbol in candidates) {
            val history = marketData[symbol] ?: continue
            val currentIdx = cursors[symbol] ?: continue
            
            if (currentIdx < 60) continue
            
            val (macd, signal) = calculateMACD(history, currentIdx)
            
            // Score: MACD Histogram strength (Bullish convergence/divergence)
            if (macd > signal) {
                scored.add(symbol to (macd - signal))
            }
        }
        
        if (scored.isEmpty()) return emptyMap()
        
        // Take Top 20 strongest crossovers — signal-proportional sizing
        val topSelected = scored.sortedByDescending { it.second }.take(20)
        
        val totalScore = topSelected.sumOf { it.second }
        if (totalScore <= 0) return emptyMap()
        return topSelected.associate { it.first to (it.second / totalScore) }
    }

    override suspend fun getSignal(symbol: String, history: List<StockQuote>, currentIdx: Int): TradeSignal {
        if (currentIdx < 60) return TradeSignal.HOLD
        val (macd, signal) = calculateMACD(history, currentIdx)
        return if (macd > signal) TradeSignal.BUY else TradeSignal.SELL
    }

    private fun calculateMACD(history: List<StockQuote>, currentIdx: Int): Pair<Double, Double> {
        // 1. Calculate Fast EMA (12) and Slow EMA (26) up to currentIdx
        val fastK = 2.0 / (fastPeriod + 1)
        val slowK = 2.0 / (slowPeriod + 1)
        val signalK = 2.0 / (signalPeriod + 1)

        var fastEma = history[0].close
        var slowEma = history[0].close
        
        val macdLineValues = mutableListOf<Double>() // Optimization: Could use DoubleArray or circular buffer
        
        // Iterate up to currentIdx
        for (i in 1..currentIdx) {
            val price = history[i].close
            fastEma = (price * fastK) + (fastEma * (1 - fastK))
            slowEma = (price * slowK) + (slowEma * (1 - slowK))
            
            // Allow EMA to stabilize before recording MACD line
            if (i >= slowPeriod) {
                macdLineValues.add(fastEma - slowEma)
            }
        }
        
        if (macdLineValues.size < signalPeriod) return 0.0 to 0.0

        // 2. Calculate Signal Line (9-day EMA of MACD line)
        // Optimization: We only need the LAST signal value
        var signalLine = macdLineValues[0]
        for (i in 1 until macdLineValues.size) {
             signalLine = (macdLineValues[i] * signalK) + (signalLine * (1 - signalK))
        }

        val macdCurrent = macdLineValues.last()
        return macdCurrent to signalLine
    }

    private fun calculateMACD(history: List<StockQuote>): Pair<Double, Double> {
        if (history.isEmpty()) return 0.0 to 0.0

        // 1. Calculate Fast EMA (12) and Slow EMA (26) over the entire history in O(N)
        // We only care about the MACD values from (slowPeriod-1) onwards to be accurate
        val fastK = 2.0 / (fastPeriod + 1)
        val slowK = 2.0 / (slowPeriod + 1)
        val signalK = 2.0 / (signalPeriod + 1)

        var fastEma = history[0].close
        var slowEma = history[0].close
        
        val macdLineValues = mutableListOf<Double>()

        for (i in 1 until history.size) {
            val price = history[i].close
            fastEma = (price * fastK) + (fastEma * (1 - fastK))
            slowEma = (price * slowK) + (slowEma * (1 - slowK))
            
            // Allow EMA to stabilize before recording MACD line
            if (i >= slowPeriod) {
                macdLineValues.add(fastEma - slowEma)
            }
        }
        
        if (macdLineValues.size < signalPeriod) return 0.0 to 0.0

        // 2. Calculate Signal Line (9-day EMA of MACD line) in O(N)
        var signalLine = macdLineValues[0]
        for (i in 1 until macdLineValues.size) {
             signalLine = (macdLineValues[i] * signalK) + (signalLine * (1 - signalK))
        }

        val macdCurrent = macdLineValues.last()
        return macdCurrent to signalLine
    }

    // Removed calculateEMA helper as it was causing the O(N^2) issue by being called in a loop
}
