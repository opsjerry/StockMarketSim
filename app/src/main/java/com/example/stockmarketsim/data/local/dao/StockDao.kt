package com.example.stockmarketsim.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.stockmarketsim.data.local.entity.StockPriceEntity

@Dao
interface StockDao {
    
    @Query("SELECT * FROM stock_prices WHERE symbol = :symbol ORDER BY date DESC LIMIT 1")
    suspend fun getLatestPrice(symbol: String): StockPriceEntity?
    
    @Query("SELECT * FROM stock_prices WHERE symbol = :symbol AND date >= :startDate ORDER BY date ASC")
    suspend fun getHistory(symbol: String, startDate: Long): List<StockPriceEntity>

    @Query("SELECT * FROM stock_prices WHERE symbol IN (:symbols) AND date >= :startDate ORDER BY date ASC")
    suspend fun getBatchHistory(symbols: List<String>, startDate: Long): List<StockPriceEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrices(prices: List<StockPriceEntity>)
    
    @Query("DELETE FROM stock_prices WHERE date < :cutoffDate")
    suspend fun deleteOldData(cutoffDate: Long): Int
    
    // --- Dynamic Universe ---
    @Query("SELECT symbol FROM stock_universe WHERE isActive = 1 ORDER BY symbol ASC")
    fun getActiveUniverse(): kotlinx.coroutines.flow.Flow<List<String>>

    @Query("SELECT symbol FROM stock_universe WHERE isActive = 1 ORDER BY symbol ASC")
    suspend fun getActiveUniverseSnapshot(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUniverse(stocks: List<com.example.stockmarketsim.data.local.entity.StockUniverseEntity>)
    
    @Query("DELETE FROM stock_universe WHERE symbol = :symbol")
    suspend fun removeFromUniverse(symbol: String)
}
