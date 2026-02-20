package com.example.stockmarketsim.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.stockmarketsim.data.local.entity.SimulationHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SimulationHistoryDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: SimulationHistoryEntity)

    @Query("SELECT * FROM simulation_history WHERE simulationId = :simulationId ORDER BY date ASC")
    fun getHistoryForSimulation(simulationId: Int): Flow<List<SimulationHistoryEntity>>
}
