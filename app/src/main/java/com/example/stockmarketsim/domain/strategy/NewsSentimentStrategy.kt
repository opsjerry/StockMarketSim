package com.example.stockmarketsim.domain.strategy

import com.example.stockmarketsim.domain.model.StockQuote
import com.example.stockmarketsim.domain.repository.StockRepository

/**
 * Strategy that uses News Sentiment as a filter/signal for momentum.
 * Logic: Buy if Price > SMA20 AND Sentiment is Positive (> 0.15).
 * Sell if Price < SMA20 OR Sentiment is Negative (< -0.15).
 */
class NewsSentimentStrategy(
    private val repository: StockRepository
) : Strategy {
    override val id = "NEWS_SENTIMENT_MOMENTUM"
    override val name = "News Sentiment Momentum"
    override val description = "Combines Alpha Vantage News Sentiment scores with SMA momentum for high-conviction entries."

    override suspend fun calculateallocation(
        candidates: List<String>,
        marketData: Map<String, List<StockQuote>>,
        cursors: Map<String, Int>
    ): Map<String, Double> {
        val selected = mutableListOf<String>()

        candidates.forEach { symbol ->
            val history = marketData[symbol] ?: return@forEach
            val currentIdx = cursors[symbol] ?: return@forEach
            
            if (currentIdx < 20) return@forEach

            val currentPrice = history[currentIdx].close
            
            // SMA 20
            var sum = 0.0
            val startIdx = currentIdx - 19
            for (i in startIdx..currentIdx) {
                sum += history[i].close
            }
            val sma20 = sum / 20.0
            
            if (currentPrice > sma20) {
                // Fetch sentiment for conviction
                val sentiment = try { 
                    repository.getSentimentScore(symbol)
                } catch (e: Exception) { 0.0 }

                if (sentiment > 0.15) {
                    selected.add(symbol)
                }
            }
        }

        if (selected.isEmpty()) return emptyMap()

        val weight = 1.0 / selected.size
        return selected.associateWith { weight }
    }

    override suspend fun getSignal(symbol: String, history: List<StockQuote>, currentIdx: Int): TradeSignal {
        if (currentIdx < 20) return TradeSignal.HOLD

        val currentPrice = history[currentIdx].close
        
        // SMA 20
        var sum = 0.0
        val startIdx = currentIdx - 19
        for (i in startIdx..currentIdx) {
            sum += history[i].close
        }
        val sma20 = sum / 20.0
        
        val sentiment = try { 
            repository.getSentimentScore(symbol) 
        } catch (e: Exception) { 0.0 }

        return when {
            currentPrice > sma20 && sentiment > 0.15 -> TradeSignal.BUY
            currentPrice < sma20 || sentiment < -0.15 -> TradeSignal.SELL
            else -> TradeSignal.HOLD
        }
    }
}
