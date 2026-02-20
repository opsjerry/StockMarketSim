package com.example.stockmarketsim.domain.strategy

import com.example.stockmarketsim.domain.model.StockQuote

class VptStrategy(
    private val smaPeriod: Int = 20,
    override val id: String = "VPT_$smaPeriod",
    override val name: String = "Volume Price Trend (VPT $smaPeriod)"
) : Strategy {

    override val description: String = "Uses Volume and Price momentum to identify trend strength."

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
            
            // Calculate VPT and VPT SMA
            // VPT is cumulative from start
            var currentVpt = 0.0
            
            // We need the last 'smaPeriod' VPT values to average them.
            // Since we can't allocate a list, we just track the sum of the last N values
            // But we need to know WHICH values to add. Use a small running window?
            // Actually, recalculating simple SMA is tricky without storage.
            // Let's use a small primitive array buffer.
            val vptWindow = DoubleArray(smaPeriod)
            var ptr = 0
            var count = 0
            
            for (i in 1..currentIdx) {
                 val close = history[i].close
                 val prevClose = history[i - 1].close
                 val volume = history[i].volume
                 
                 if (prevClose > 0) {
                     val pctChange = (close - prevClose) / prevClose
                     currentVpt += volume * pctChange
                 }
                 
                 // Add to window
                 vptWindow[ptr] = currentVpt
                 ptr = (ptr + 1) % smaPeriod
                 count++
            }
            
            if (count < smaPeriod) return@forEach
            
            val latestVpt = currentVpt
            val vptSma = vptWindow.average()
            
            // Score: % distance from VPT SMA
            if (latestVpt > vptSma && vptSma != 0.0) {
                val score = (latestVpt - vptSma) / Math.abs(vptSma)
                scored.add(symbol to score)
            }
        }
        
        if (scored.isEmpty()) return emptyMap()
        
        // Concentration: Take Top 20
        val topSelected = scored.sortedByDescending { it.second }.take(20)
        
        val totalScore = topSelected.sumOf { it.second }
        if (totalScore <= 0) return emptyMap()
        return topSelected.associate { it.first to (it.second / totalScore) }
    }

    override suspend fun getSignal(symbol: String, history: List<StockQuote>, currentIdx: Int): TradeSignal {
        if (currentIdx < smaPeriod) return TradeSignal.HOLD
        
        var currentVpt = 0.0
        val vptWindow = DoubleArray(smaPeriod)
        var ptr = 0
        var count = 0
        
        for (i in 1..currentIdx) {
             val close = history[i].close
             val prevClose = history[i - 1].close
             val volume = history[i].volume
             
             if (prevClose > 0) {
                 val pctChange = (close - prevClose) / prevClose
                 currentVpt += volume * pctChange
             }
             vptWindow[ptr] = currentVpt
             ptr = (ptr + 1) % smaPeriod
             count++
        }
        
        if (count < smaPeriod) return TradeSignal.HOLD
        
        val latestVpt = currentVpt
        val vptSma = vptWindow.average()
        
        return if (latestVpt > vptSma) TradeSignal.BUY else TradeSignal.SELL
    }

    private fun calculateVPT(history: List<StockQuote>): List<Double> {
        val vpt = mutableListOf<Double>()
        var currentVpt = 0.0
        
        for (i in 1 until history.size) {
            val close = history[i].close
            val prevClose = history[i - 1].close
            val volume = history[i].volume
            
            if (prevClose > 0) {
                val percentageChange = (close - prevClose) / prevClose
                currentVpt += volume * percentageChange
            }
            vpt.add(currentVpt)
        }
        return vpt
    }
}
