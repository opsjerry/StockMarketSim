package com.example.stockmarketsim.domain.strategy

import com.example.stockmarketsim.domain.model.StockQuote

interface Strategy {
    val id: String
    val name: String
    val description: String
    
    // Returns a Map of Symbol -> Target Weight (0.0 to 1.0)
    // Engine will try to rebalance portfolio to match these weights.
    // context: List of candidate stocks and their history.
    suspend fun calculateallocation(
        candidates: List<String>,
        marketData: Map<String, List<StockQuote>>, // Full History
        cursors: Map<String, Int> // Current Index for each stock
    ): Map<String, Double>
    
    // Simple Buy/Sell signal check for a single stock (used for logs)
    // Optional: Pass full history and index
    suspend fun getSignal(
        symbol: String,
        history: List<StockQuote>, // Full History
        currentIdx: Int // Index of "Today"
    ): TradeSignal

    // New: Calculate signals for the entire universe (used by Daily Runner)
    // Default implementation can iterate if not optimized
    suspend fun calculateSignals(
        universe: List<String>
    ): List<StrategySignal> {
        return emptyList() // Fallback
    }
}

enum class TradeSignal {
    BUY, SELL, HOLD
}
