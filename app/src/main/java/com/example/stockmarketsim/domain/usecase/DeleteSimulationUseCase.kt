package com.example.stockmarketsim.domain.usecase

import com.example.stockmarketsim.domain.model.Simulation
import com.example.stockmarketsim.domain.repository.SimulationRepository
import javax.inject.Inject

class DeleteSimulationUseCase @Inject constructor(
    private val repository: SimulationRepository,
    private val logManager: com.example.stockmarketsim.data.manager.SimulationLogManager
) {
    suspend operator fun invoke(simulation: Simulation) {
        logManager.clearLogs(simulation.id)
        repository.deleteSimulation(simulation)
    }
}
