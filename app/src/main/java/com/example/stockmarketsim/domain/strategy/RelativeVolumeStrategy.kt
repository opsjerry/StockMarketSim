package com.example.stockmarketsim.domain.strategy

import com.example.stockmarketsim.domain.model.StockQuote

class RelativeVolumeStrategy(
    private val smaPeriod: Int = 20,
    private val volumeMultiplier: Double = 2.0
) : Strategy {
    override val id = "REL_VOL_${smaPeriod}_${(volumeMultiplier*10).toInt()}"
    override val name = "Relative Volume ($volumeMultiplier x)"
    override val description = "Buys when Volume spikes > ${volumeMultiplier}x Average, indicating institutional interest."

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
            
            val current = history[currentIdx]
            
            // Calculate AvgVolume and SMA over last 'smaPeriod'
            var volSum = 0.0
            var priceSum = 0.0
            val startIdx = currentIdx - smaPeriod + 1
            for (i in startIdx..currentIdx) {
                volSum += history[i].volume
                priceSum += history[i].close
            }
            val avgVolume = volSum / smaPeriod
            val sma = priceSum / smaPeriod
            
            // Score: Relative Volume Multiplier (Strength of breakout)
            if (current.volume > (avgVolume * volumeMultiplier) && current.close > sma) {
                val relVol = if (avgVolume > 0) current.volume.toDouble() / avgVolume else 0.0
                scored.add(symbol to relVol)
            }
        }

        if (scored.isEmpty()) return emptyMap()

        // Take Top 20 by volume ratio
        val topSelected = scored.sortedByDescending { it.second }.take(20)
        
        val totalScore = topSelected.sumOf { it.second }
        if (totalScore <= 0) return emptyMap()
        return topSelected.associate { it.first to (it.second / totalScore) }
    }

    override suspend fun getSignal(symbol: String, history: List<StockQuote>, currentIdx: Int): TradeSignal {
        if (currentIdx < smaPeriod) return TradeSignal.HOLD
        
        val current = history[currentIdx]
        
        var volSum = 0.0
        var priceSum = 0.0
        val startIdx = currentIdx - smaPeriod + 1
        for (i in startIdx..currentIdx) {
            volSum += history[i].volume
            priceSum += history[i].close
        }
        val avgVolume = volSum / smaPeriod
        val sma = priceSum / smaPeriod
        
        val isVolumeSpike = current.volume > (avgVolume * volumeMultiplier)
        val isUptrend = current.close > sma
        
        return if (isVolumeSpike && isUptrend) TradeSignal.BUY else TradeSignal.HOLD
    }
}
