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
            
            // Calculate current SMA to ensure we only buy/hold in an uptrend
            var todayPriceSum = 0.0
            for (i in (currentIdx - smaPeriod + 1)..currentIdx) {
                todayPriceSum += history[i].close
            }
            val todaySma = todayPriceSum / smaPeriod
            val current = history[currentIdx]
            
            if (current.close > todaySma) {
                // Look for the strongest volume spike within the last 5 days
                val lookbackDays = 5
                var maxDecayedRelVol = 0.0
                
                val startWindow = maxOf(smaPeriod, currentIdx - lookbackDays + 1)
                for (i in startWindow..currentIdx) {
                    var volSum = 0.0
                    // Calculate avg volume BEFORE the day 'i'
                    for (j in (i - smaPeriod)..(i - 1)) {
                        volSum += history[j].volume
                    }
                    val avgVolume = volSum / smaPeriod
                    
                    if (avgVolume > 0) {
                        val relVol = history[i].volume.toDouble() / avgVolume
                        
                        if (relVol > volumeMultiplier) {
                            val daysAgo = currentIdx - i
                            val decayedScore = relVol * Math.pow(0.8, daysAgo.toDouble())
                            if (decayedScore > maxDecayedRelVol) {
                                maxDecayedRelVol = decayedScore
                            }
                        }
                    }
                }

                // Score: Highest Decayed Relative Volume Multiplier in the window
                if (maxDecayedRelVol > 0.0) {
                    scored.add(symbol to maxDecayedRelVol)
                }
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
