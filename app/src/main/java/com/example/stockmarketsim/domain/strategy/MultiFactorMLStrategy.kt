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
                
                // Allocate features array PER coroutine to ensure thread safety
                val features = DoubleArray(60)
                
                // Generate 60-day Log Return Sequence: ln(P_t / P_{t-1})
                val startIdx = currentIdx - 60
                for (i in 0 until 60) {
                    val p_t = history[startIdx + i + 1].close
                    val p_prev = history[startIdx + i].close
                    features[i] = kotlin.math.ln(p_t / p_prev)
                }
                
                // Basic Fundamentals for logging context
                val fundamentals = apiSource.getFundamentals(symbol)
                
                val predictedReturn = forecaster.predict(features, symbol, history[currentIdx].date)
                
                if (predictedReturn.isNaN()) return@async null
                
                // Phase 4: Risk Management - 0.05% Breakeven Threshold for Regression Model (Realistic for 1-day log returns)
                if (predictedReturn < 0.0005f) {
                    return@async null
                }
                
                symbol to predictedReturn.toDouble()
            }
        }

        deferredResults.awaitAll().filterNotNull().forEach { 
            selected.add(it)
        }

        if (selected.isEmpty()) {
            println("⚠️ [MultiFactorML] No trades met the 0.05% Breakeven Threshold. Sitting in CASH.")
            return@coroutineScope emptyMap()
        }

        // Rank by probability
        val ranked = selected.sortedByDescending { it.second }
        
        // Take Top 10 High-Conviction Picks
        val top10 = ranked.take(10)
        
        // Log only the Top 10 to avoid console spam
        if (top10.isNotEmpty()) {
            val version = forecaster.getModelVersion()
            println("\n🚀 Top 10 MultiFactorML Picks (Model v$version) for ${marketData.values.firstOrNull()?.get(cursors.values.firstOrNull() ?: 0)?.date?.let { java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(it)) }}:")
            top10.forEach { (sym, ret) ->
                println("   🧠 [$sym] LSTM (v$version) Predicted Return: ${"%.2f".format(ret * 100)}%")
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
