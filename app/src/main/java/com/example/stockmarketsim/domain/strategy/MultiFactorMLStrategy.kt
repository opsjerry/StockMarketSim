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

        val deferredResults = candidates.map { symbol ->
            async {
                val history = marketData[symbol] ?: return@async null
                val currentIdx = cursors[symbol] ?: return@async null
                
                // Need at least 61 days for 60 log returns
                if (currentIdx < 61) return@async null
                
                // === MULTI-FACTOR FEATURE VECTOR (64 features) ===
                // [0-59]  60 daily log returns: ln(P_t / P_{t-1})
                // [60]    RSI(14), normalized to 0.0-1.0
                // [61]    SMA Ratio (50/200), centered around 1.0
                // [62]    ATR% (14-day), as fraction of price
                // [63]    Relative Volume (20-day), ratio vs average
                
                val features = DoubleArray(64)
                
                // Part 1: 60-day Log Return Sequence
                val startIdx = currentIdx - 60
                for (i in 0 until 60) {
                    val p_t = history[startIdx + i + 1].close
                    val p_prev = history[startIdx + i].close
                    features[i] = kotlin.math.ln(p_t / p_prev)
                }
                
                // Part 2: Technical Indicators (zero-allocation functions)
                // Extract price arrays for TA calculations
                val windowSize = currentIdx + 1  // All available data up to cursor
                val closes = DoubleArray(windowSize) { history[it].close }
                val highs = DoubleArray(windowSize) { history[it].high }
                val lows = DoubleArray(windowSize) { history[it].low }
                val volumes = DoubleArray(windowSize) { history[it].volume.toDouble() }
                
                features[60] = TechnicalIndicators.calculateRsiZeroAlloc(closes, windowSize, 14) / 100.0    // Normalize RSI to 0-1
                features[61] = TechnicalIndicators.calculateSmaRatioZeroAlloc(closes, windowSize, 50, 200)   // ~1.0 centered
                features[62] = TechnicalIndicators.calculateAtrPctZeroAlloc(highs, lows, closes, windowSize, 14) // 0.01-0.10 range
                features[63] = TechnicalIndicators.calculateRelativeVolumeZeroAlloc(volumes, windowSize, 20)  // ~1.0 centered
                
                // Fetch fundamentals (real API → Yahoo → cache → skip)
                val fundamentals = apiSource.getFundamentals(symbol)
                if (fundamentals == null) {
                    // No data from ANY source — skip this stock entirely
                    return@async null
                }
                
                val predictedReturn = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    forecaster.predict(features, symbol, history[currentIdx].date)
                }
                
                if (predictedReturn.isNaN()) return@async null
                
                // Phase 4: Risk Management - 0.4% Breakeven Threshold (covers 0.2% slippage × 2 = 0.4% round-trip cost)
                if (predictedReturn < 0.004f) {
                    return@async null
                }
                
                symbol to predictedReturn.toDouble()
            }
        }

        deferredResults.awaitAll().filterNotNull().forEach { 
            selected.add(it)
        }

        if (selected.isEmpty()) {
            println("⚠️ [MultiFactorML] No trades met the 0.4% Breakeven Threshold. Sitting in CASH.")
            return@coroutineScope emptyMap()
        }

        // Rank by predicted return
        val ranked = selected.sortedByDescending { it.second }
        
        // Take Top 10 High-Conviction Picks
        val top10 = ranked.take(10)
        
        // Log only the Top 10
        if (top10.isNotEmpty()) {
            val version = forecaster.getModelVersion()
            println("\n🚀 Top 10 MultiFactorML Picks (Model v$version) for ${marketData.values.firstOrNull()?.get(cursors.values.firstOrNull() ?: 0)?.date?.let { java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(it)) }}:")
            top10.forEach { (sym, ret) ->
                println("   🧠 [$sym] Multi-Factor (v$version) Predicted Return: ${"%.2f".format(ret * 100)}%")
            }
        }
        
        // Equal Weight Allocation
        val weight = 1.0 / top10.size
        return@coroutineScope top10.associate { it.first to weight }
    }

    override suspend fun getSignal(symbol: String, history: List<StockQuote>, currentIdx: Int): TradeSignal {
        // Fallback for single stock testing (not typically used in the portfolio simulation loop)
        return TradeSignal.HOLD
    }
}
