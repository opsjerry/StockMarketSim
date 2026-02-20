package com.example.stockmarketsim.domain.strategy

data class StrategySignal(
    val symbol: String,
    val signal: String, // "BUY", "SELL", "HOLD"
    val confidence: Double = 1.0,
    val reason: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
