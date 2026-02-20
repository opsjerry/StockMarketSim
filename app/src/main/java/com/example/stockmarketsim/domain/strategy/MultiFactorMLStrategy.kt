package com.example.stockmarketsim.domain.strategy

import com.example.stockmarketsim.domain.model.StockQuote
import com.example.stockmarketsim.domain.ml.IStockPriceForecaster
import com.example.stockmarketsim.domain.ml.TechnicalIndicators
import com.example.stockmarketsim.data.remote.IndianApiSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class MultiFactorMLStrategy(
    private val forecaster: IStockPriceForecaster,
    private val apiSource: IndianApiSource
) : Strategy {
    override val id = "MULTI_FACTOR_DNN"
    override val name = "Multi-Factor ML (Deep Neural Net)"
    override val description = "Combines Price Action, Volatility, Sentiment, and Fundamentals into a unified probability model."

    override suspend fun calculateallocation(
        candidates: List<String>,
        marketData: Map<String, List<StockQuote>>,
        cursors: Map<String, Int>
    ): Map<String, Double> = coroutineScope {
        val selected = mutableListOf<Pair<String, Double>>()

        // Pre-allocate zero-allocation arrays for TechnicalIndicators
        val closes = DoubleArray(250)
        val highs = DoubleArray(250)
        val lows = DoubleArray(250)
        val volumes = DoubleArray(250)
        val features = DoubleArray(6)

        val deferredResults = candidates.map { symbol ->
            async {
                val history = marketData[symbol] ?: return@async null
                val currentIdx = cursors[symbol] ?: return@async null
                
                // Need at least 200 days for SMA_200
                if (currentIdx < 200) return@async null
                
                // Copy data to primitive arrays (zero-allocation inner loops)
                val startIdx = currentIdx - 200
                for (i in 0..200) {
                    val q = history[startIdx + i]
                    closes[i] = q.close
                    highs[i] = q.high
                    lows[i] = q.low
                    volumes[i] = q.volume.toDouble()
                }
                
                val endIdx = 201
                val rsi = TechnicalIndicators.calculateRsiZeroAlloc(closes, endIdx, 14)
                val smaRatio = TechnicalIndicators.calculateSmaRatioZeroAlloc(closes, endIdx, 50, 200)
                val atrPct = TechnicalIndicators.calculateAtrPctZeroAlloc(highs, lows, closes, endIdx, 14)
                val relVol = TechnicalIndicators.calculateRelativeVolumeZeroAlloc(volumes, endIdx, 20)
                
                // Fetch fundamentals (cached/mapped)
                val fundamentals = apiSource.getFundamentals(symbol)
                
                // 6 Features mapped straight into TensorFlow: [RSI_14, SMA_Ratio, ATR_Pct, Relative_Volume, PE_Ratio, Sentiment_Score]
                features[0] = rsi
                features[1] = smaRatio
                features[2] = atrPct
                features[3] = relVol
                features[4] = fundamentals.peRatio
                features[5] = fundamentals.sentimentScore
                
                val probability = forecaster.predict(features, symbol, history[currentIdx].date)
                
                if (probability.isNaN()) return@async null
                
                // Phase 4: Risk Management - Absolute Conviction Threshold (60%)
                if (probability < 0.60f) {
                    return@async null
                }
                
                println("🧠 [$symbol] ML Conviction: ${"%.1f".format(probability * 100)}% | RSI: ${"%.1f".format(rsi)} | Sent: ${"%.1f".format(fundamentals.sentimentScore)}")
                
                symbol to probability.toDouble()
            }
        }

        deferredResults.awaitAll().filterNotNull().forEach { 
            selected.add(it)
        }

        if (selected.isEmpty()) {
            println("⚠️ [MultiFactorML] No trades met the 60% Conviction Threshold. Sitting in CASH.")
            return@coroutineScope emptyMap()
        }

        // Rank by probability
        val ranked = selected.sortedByDescending { it.second }
        
        // Take Top 10 High-Conviction Picks
        val top10 = ranked.take(10)
        
        // Equal Weight Allocation
        val weight = 1.0 / top10.size
        return@coroutineScope top10.associate { it.first to weight }
    }

    override suspend fun getSignal(symbol: String, history: List<StockQuote>, currentIdx: Int): TradeSignal {
        // Fallback for single stock testing (not typically used in the portfolio simulation loop)
        return TradeSignal.HOLD
    }
}
