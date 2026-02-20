package com.example.stockmarketsim.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.stockmarketsim.data.local.entity.PredictionEntity

@Dao
interface PredictionDao {
    @Query("SELECT * FROM predictions WHERE symbol = :symbol AND date = :date AND modelVersion = :modelVersion LIMIT 1")
    suspend fun getPrediction(symbol: String, date: Long, modelVersion: String): PredictionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrediction(prediction: PredictionEntity)
    
    @Query("DELETE FROM predictions WHERE modelVersion != :currentVersion")
    suspend fun deleteOldVersions(currentVersion: String)
}
