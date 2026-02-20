package com.example.stockmarketsim.data.repository

import com.example.stockmarketsim.data.local.dao.SimulationDao
import com.example.stockmarketsim.data.local.entity.toDomain
import com.example.stockmarketsim.data.local.entity.toEntity
import com.example.stockmarketsim.domain.model.Simulation
import com.example.stockmarketsim.domain.repository.SimulationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SimulationRepositoryImpl @Inject constructor(
    private val dao: SimulationDao,
    private val portfolioDao: com.example.stockmarketsim.data.local.dao.PortfolioDao,
    private val historyDao: com.example.stockmarketsim.data.local.dao.SimulationHistoryDao
) : SimulationRepository {

    override fun getSimulations(): Flow<List<Simulation>> {
        return dao.getSimulations().map { entities ->
            entities.map { it.toDomain() } // Note: This doesn't attach portfolio yet. Domain model update needed for complex mapping.
        }
    }

    // Explicitly fetching portfolio for logic engine
    suspend fun getPortfolio(simulationId: Int): List<com.example.stockmarketsim.domain.model.PortfolioItem> {
        return portfolioDao.getPortfolioItems(simulationId).map { it.toDomain() }
    }
    
    suspend fun updatePortfolio(simulationId: Int, items: List<com.example.stockmarketsim.domain.model.PortfolioItem>) {
        portfolioDao.clearPortfolio(simulationId)
        portfolioDao.insertPortfolioItems(items.map { it.toEntity(simulationId) })
    }

    override suspend fun getSimulationById(id: Int): Simulation? {
        return dao.getSimulationById(id)?.toDomain()
    }

    override fun getSimulationByIdFlow(id: Int): Flow<Simulation?> {
        return dao.getSimulationByIdFlow(id).map { it?.toDomain() }
    }

    override suspend fun insertSimulation(simulation: Simulation): Long {
        return dao.insertSimulation(simulation.toEntity())
    }

    override suspend fun updateSimulation(simulation: Simulation) {
        dao.updateSimulation(simulation.toEntity())
    }

    override suspend fun deleteSimulation(simulation: Simulation) {
        dao.deleteSimulation(simulation.toEntity())
    }

    override suspend fun getActiveSimulationCount(): Int {
        return dao.getActiveSimulationCount()
    }

    override fun getHistory(simulationId: Int): Flow<List<Pair<Long, Double>>> {
        return historyDao.getHistoryForSimulation(simulationId).map { list ->
            list.map { it.date to it.totalEquity }
        }
    }

    override suspend fun insertHistory(simulationId: Int, date: Long, equity: Double) {
        historyDao.insertHistory(
            com.example.stockmarketsim.data.local.entity.SimulationHistoryEntity(
                simulationId = simulationId,
                date = date,
                totalEquity = equity
            )
        )
    }
}
