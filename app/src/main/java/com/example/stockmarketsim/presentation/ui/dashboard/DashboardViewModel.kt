package com.example.stockmarketsim.presentation.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stockmarketsim.domain.model.Simulation
import com.example.stockmarketsim.domain.usecase.GetSimulationsUseCase
import com.example.stockmarketsim.domain.analysis.RegimeFilter
import com.example.stockmarketsim.domain.analysis.RegimeSignal
import com.example.stockmarketsim.domain.model.TimeFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    getSimulationsUseCase: GetSimulationsUseCase,
    private val deleteSimulationUseCase: com.example.stockmarketsim.domain.usecase.DeleteSimulationUseCase,
    private val repository: com.example.stockmarketsim.domain.repository.SimulationRepository,
    private val stockRepository: com.example.stockmarketsim.domain.repository.StockRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    val simulations: StateFlow<List<Simulation>> = getSimulationsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _marketRegime = MutableStateFlow(RegimeSignal.NEUTRAL)
    val marketRegime: StateFlow<RegimeSignal> = _marketRegime.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val niftyHistory = stockRepository.getStockHistory("^NSEI", TimeFrame.DAILY, 365)
                val cpi = try { stockRepository.getInflationRate() } catch (e: Exception) { 0.0 }
                _marketRegime.value = RegimeFilter.detectRegime(niftyHistory, cpi)
            } catch (e: Exception) {
                android.util.Log.w("DashboardViewModel", "Regime fetch failed: ${e.message}")
            }
        }
    }

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
