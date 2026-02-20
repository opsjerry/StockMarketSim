package com.example.stockmarketsim.domain.repository

import com.example.stockmarketsim.domain.model.StockQuote
import com.example.stockmarketsim.domain.model.FundamentalData
import com.example.stockmarketsim.domain.model.TimeFrame

interface StockRepository {
    
    // Fetch latest EOD quote
    suspend fun getStockQuote(symbol: String): StockQuote?
    
    // Fetch historical data for charts & strategy backtesting
    suspend fun getStockHistory(symbol: String, timeFrame: TimeFrame, limit: Int): List<StockQuote>
    
    suspend fun getBatchStockHistory(symbols: List<String>, timeFrame: TimeFrame, limit: Int, onLog: (String) -> Unit = {}): Map<String, List<StockQuote>>
    
    // Search for stocks (e.g. for user adding to "Watchlist" or custom sim)
    suspend fun searchStock(query: String): List<String>
    
    suspend fun cleanupOldData(onLog: (String) -> Unit = {})

    // New Alpha Vantage integration methods
    suspend fun getSentimentScore(symbol: String): Double
    suspend fun getInflationRate(): Double
    
    // Fundamentals (Quality Filter)
    suspend fun getFundamentals(symbol: String): FundamentalData?
    suspend fun getBatchFundamentals(symbols: List<String>, onLog: (String) -> Unit = {}): Map<String, FundamentalData>
    
    // Dynamic Universe
    fun getActiveUniverse(): kotlinx.coroutines.flow.Flow<List<String>>
    suspend fun getActiveUniverseSnapshot(): List<String>
    suspend fun syncUniverseFromDiscovery(onLog: (String) -> Unit): Int
    suspend fun addStockToUniverse(symbol: String)
    suspend fun removeStockFromUniverse(symbol: String)
}
