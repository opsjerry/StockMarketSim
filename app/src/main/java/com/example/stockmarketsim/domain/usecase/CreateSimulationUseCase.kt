package com.example.stockmarketsim.domain.usecase

import com.example.stockmarketsim.domain.model.Simulation
import com.example.stockmarketsim.domain.repository.SimulationRepository
import javax.inject.Inject

class CreateSimulationUseCase @Inject constructor(
    private val repository: SimulationRepository
) {
    suspend operator fun invoke(simulation: Simulation): Long {
        val activeCount = repository.getActiveSimulationCount()
        if (activeCount >= 5) {
            throw IllegalStateException("Maximum limit of 5 active simulations reached.")
        }
        return repository.insertSimulation(simulation)
    }
}
