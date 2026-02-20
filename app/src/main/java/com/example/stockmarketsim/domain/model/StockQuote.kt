package com.example.stockmarketsim.domain.model

data class StockQuote(
    val symbol: String,
    val date: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)

// Enum to support Timeframes for history fetching
enum class TimeFrame {
    DAILY,
    WEEKLY,
    MONTHLY
}
