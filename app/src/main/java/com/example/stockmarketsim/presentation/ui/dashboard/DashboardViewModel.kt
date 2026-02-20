package com.example.stockmarketsim.presentation.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stockmarketsim.domain.model.Simulation
import com.example.stockmarketsim.domain.usecase.GetSimulationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    getSimulationsUseCase: GetSimulationsUseCase,
    private val deleteSimulationUseCase: com.example.stockmarketsim.domain.usecase.DeleteSimulationUseCase,
    private val repository: com.example.stockmarketsim.domain.repository.SimulationRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    val simulations: StateFlow<List<Simulation>> = getSimulationsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteSimulation(simulation: Simulation) {
        viewModelScope.launch {
            deleteSimulationUseCase(simulation)
        }
    }

    fun retrySimulation(simulationId: Int) {
        viewModelScope.launch {
            val simulation = repository.getSimulationById(simulationId) ?: return@launch
            
            // 1. Reset Status
            repository.updateSimulation(simulation.copy(status = com.example.stockmarketsim.domain.model.SimulationStatus.ANALYZING))
            
            // 2. Re-trigger Worker
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.stockmarketsim.worker.AnalysisWorker>()
                .setInputData(androidx.work.workDataOf("SIMULATION_ID" to simulationId))
                .build()
                
            androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
