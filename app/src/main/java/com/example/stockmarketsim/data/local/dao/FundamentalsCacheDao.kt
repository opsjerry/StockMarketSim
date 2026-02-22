package com.example.stockmarketsim.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.stockmarketsim.data.local.entity.FundamentalsCacheEntity

@Dao
interface FundamentalsCacheDao {
    @Query("SELECT * FROM fundamentals_cache WHERE symbol = :symbol LIMIT 1")
    suspend fun get(symbol: String): FundamentalsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FundamentalsCacheEntity)

    @Query("DELETE FROM fundamentals_cache WHERE fetchTimestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM fundamentals_cache")
    suspend fun clearAll()
}
