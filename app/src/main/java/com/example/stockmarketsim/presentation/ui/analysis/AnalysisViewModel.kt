package com.example.stockmarketsim.presentation.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stockmarketsim.domain.model.SimulationStatus
import com.example.stockmarketsim.domain.repository.SimulationRepository
import com.example.stockmarketsim.domain.reporting.PdfReportGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val repository: SimulationRepository,
    private val pdfGenerator: PdfReportGenerator,
    private val stockRepository: com.example.stockmarketsim.domain.repository.StockRepository,
    private val runDailySimulationUseCase: com.example.stockmarketsim.domain.usecase.RunDailySimulationUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<AnalysisState>(AnalysisState.Loading)
    val state = _state.asStateFlow()

    fun loadAnalysis(simulationId: Int) {
        viewModelScope.launch {
            val simulation = repository.getSimulationById(simulationId)
            if (simulation != null) {
                // Poll for file results (Simple IPC)
                val context = pdfGenerator.context 
                
                var file = File(context.filesDir, "analysis_results_$simulationId.json")
                
                // Retry for 5 seconds
                var retries = 0
                while (!file.exists() && retries < 10) {
                    kotlinx.coroutines.delay(1000)
                    retries++
                }
                
                if (file.exists()) {
                    val json = file.readText()
                    // Simple Parse
                    val recommendations = parseRecommendations(json)
                    _state.value = AnalysisState.Success(
                        simulationName = simulation.name,
                        amount = simulation.initialAmount,
                        duration = simulation.durationMonths,
                        strategies = recommendations
                    )
                } else {
                    // Fallback or still loading
                    _state.value = AnalysisState.Error("Analysis taking longer than expected. Please come back later.")
                }
            } else {
                _state.value = AnalysisState.Error("Simulation not found")
            }
        }
    }

    private fun parseRecommendations(json: String): List<StrategyRecommendation> {
        val list = mutableListOf<StrategyRecommendation>()
        val pattern = java.util.regex.Pattern.compile(
            "\\{.*?\"id\": \"(.*?)\",.*?\"name\": \"(.*?)\",.*?\"description\": \"(.*?)\",.*?\"score\": (.*?),.*?\"final_value\": (.*?),.*?\"alpha\": (.*?),.*?\"benchmark_return\": (.*?),.*?\"return_pct\": (.*?),.*?\"drawdown\": (.*?),.*?\"sharpe\": (.*?)\\s*\\}", 
            java.util.regex.Pattern.DOTALL
        )
        val matcher = pattern.matcher(json)
        
        while (matcher.find()) {
            val id = matcher.group(1) ?: "MOMENTUM"
            val name = matcher.group(2) ?: "Unknown"
            val desc = matcher.group(3) ?: ""
            val score = matcher.group(4)?.toDoubleOrNull() ?: 0.0
            val finalValue = matcher.group(5)?.toDoubleOrNull() ?: 0.0
            val alpha = matcher.group(6)?.toDoubleOrNull() ?: 0.0
            val benchReturn = matcher.group(7)?.toDoubleOrNull() ?: 0.0
            val rPct = matcher.group(8)?.toDoubleOrNull() ?: 0.0
            val drawdown = matcher.group(9)?.toDoubleOrNull() ?: 0.0
            val sharpe = matcher.group(10)?.toDoubleOrNull() ?: 0.0
            
            list.add(StrategyRecommendation(id, name, desc, score, finalValue, alpha, benchReturn, rPct, drawdown, sharpe))
        }
        return list
    }

    fun selectStrategy(simulationId: Int, strategyId: String, onComplete: () -> Unit) {
        val currentState = _state.value
        if (currentState is AnalysisState.Success && currentState.isActivating) return
        
        if (currentState is AnalysisState.Success) {
            _state.value = currentState.copy(isActivating = true)
        }

        viewModelScope.launch {
            val simulation = repository.getSimulationById(simulationId)
            if (simulation != null) {
                // 2. Activate Strategy
                val updated = simulation.copy(
                    status = SimulationStatus.ACTIVE,
                    strategyId = strategyId
                )
                repository.updateSimulation(updated)
                
                // 3. Trigger Initial Buy (Simulate immediately)
                try {
                    runDailySimulationUseCase() 
                    onComplete()
                } catch (e: Exception) {
                    _state.value = AnalysisState.Error("Failed to execute initial simulation: ${e.message}")
                }
            } else {
                 _state.value = AnalysisState.Error("Simulation not found during activation.")
            }
        }
    }
    
    fun generatePdf(simulationId: Int, onResult: (File?) -> Unit) {
        viewModelScope.launch {
            val simulation = repository.getSimulationById(simulationId)
            if (simulation != null) {
                val file = pdfGenerator.generatePreSimulationReport(simulation)
                onResult(file)
            } else {
                onResult(null)
            }
        }
    }
}

sealed class AnalysisState {
    object Loading : AnalysisState()
    data class Success(
        val simulationName: String, 
        val amount: Double, 
        val duration: Int, 
        val strategies: List<StrategyRecommendation>,
        val isActivating: Boolean = false
    ) : AnalysisState()
    data class Error(val message: String) : AnalysisState()
}

data class StrategyRecommendation(
    val id: String,
    val name: String,
    val description: String,
    val score: Double,
    val finalValue: Double,
    val alpha: Double,
    val benchmarkReturn: Double,
    val returnPct: Double = 0.0,
    val drawdown: Double = 0.0,
    val sharpe: Double = 0.0
)
