package com.example.stockmarketsim.domain.broker



interface BrokerSource {
    suspend fun placeBuyOrder(symbol: String, quantity: Int, price: Double, tag: String): String // Returns OrderID
    suspend fun placeSellOrder(symbol: String, quantity: Int, price: Double, tag: String): String // Returns OrderID
    suspend fun getHoldings(): List<Holding>
    suspend fun isConnected(): Boolean
}

data class Holding(
    val symbol: String,
    val quantity: Int,
    val averagePrice: Double,
    val currentPrice: Double,
    val pnl: Double
)
