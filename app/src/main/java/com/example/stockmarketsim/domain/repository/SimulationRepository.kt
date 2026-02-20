package com.example.stockmarketsim.domain.repository

import com.example.stockmarketsim.domain.model.Simulation
import kotlinx.coroutines.flow.Flow

interface SimulationRepository {
    
    fun getSimulations(): Flow<List<Simulation>>
    
    suspend fun getSimulationById(id: Int): Simulation?

    fun getSimulationByIdFlow(id: Int): Flow<Simulation?>
    
    suspend fun insertSimulation(simulation: Simulation): Long
    
    suspend fun updateSimulation(simulation: Simulation)
    
    suspend fun deleteSimulation(simulation: Simulation)
    
    suspend fun getActiveSimulationCount(): Int
    
    fun getHistory(simulationId: Int): Flow<List<Pair<Long, Double>>>
    
    suspend fun insertHistory(simulationId: Int, date: Long, equity: Double)
}
