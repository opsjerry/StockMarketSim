package com.example.stockmarketsim.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.stockmarketsim.data.local.entity.PortfolioItemEntity

@Dao
interface PortfolioDao {
    
    @Query("SELECT * FROM portfolio_items WHERE simulationId = :simulationId")
    suspend fun getPortfolioItems(simulationId: Int): List<PortfolioItemEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPortfolioItems(items: List<PortfolioItemEntity>)
    
    @Query("DELETE FROM portfolio_items WHERE simulationId = :simulationId")
    suspend fun clearPortfolio(simulationId: Int)
}
