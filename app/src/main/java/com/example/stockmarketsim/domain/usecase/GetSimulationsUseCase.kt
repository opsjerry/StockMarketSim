package com.example.stockmarketsim.domain.usecase

import com.example.stockmarketsim.domain.model.Simulation
import com.example.stockmarketsim.domain.repository.SimulationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSimulationsUseCase @Inject constructor(
    private val repository: SimulationRepository
) {
    operator fun invoke(): Flow<List<Simulation>> {
        return repository.getSimulations()
    }
}
