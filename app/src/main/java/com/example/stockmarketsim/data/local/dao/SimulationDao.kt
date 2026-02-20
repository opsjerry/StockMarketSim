package com.example.stockmarketsim.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.stockmarketsim.data.local.entity.SimulationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SimulationDao {
    
    @Query("SELECT * FROM simulations ORDER BY id DESC")
    fun getSimulations(): Flow<List<SimulationEntity>>
    
    @Query("SELECT * FROM simulations WHERE id = :id")
    suspend fun getSimulationById(id: Int): SimulationEntity?

    @Query("SELECT * FROM simulations WHERE id = :id")
    fun getSimulationByIdFlow(id: Int): Flow<SimulationEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSimulation(simulation: SimulationEntity): Long
    
    @Update
    suspend fun updateSimulation(simulation: SimulationEntity)
    
    @Delete
    suspend fun deleteSimulation(simulation: SimulationEntity)
    
    @Query("SELECT COUNT(*) FROM simulations WHERE status = 'ACTIVE'")
    suspend fun getActiveSimulationCount(): Int
}
