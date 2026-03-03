package com.example.stockmarketsim.domain.model

data class PortfolioItem(
    val id: Int = 0,
    val symbol: String,
    val quantity: Double,
    val averagePrice: Double,
    val highestPrice: Double = averagePrice,  // Track peak for trailing stops
    val purchaseDate: Long = System.currentTimeMillis() // For stop-loss honeymoon period
) {
    fun currentValue(currentPrice: Double): Double {
        return quantity * currentPrice
    }
}
