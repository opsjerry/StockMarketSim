package com.example.stockmarketsim.presentation.ui.creation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stockmarketsim.domain.model.Simulation
import com.example.stockmarketsim.domain.model.SimulationStatus
import com.example.stockmarketsim.domain.usecase.CreateSimulationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.stockmarketsim.domain.usecase.RunDailySimulationUseCase

@HiltViewModel
class CreateSimulationViewModel @Inject constructor(
    private val createSimulationUseCase: CreateSimulationUseCase,
    private val runDailySimulationUseCase: RunDailySimulationUseCase,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    fun createSimulation(
        name: String,
        amount: Double,
        durationMonths: Int,
        targetReturn: Double,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            // 1. Validate Inputs
            if (name.isBlank()) {
                onError("Simulation Name cannot be empty.")
                return@launch
            }
            if (amount < 1000.0) {
                onError("Initial Investment must be at least ₹1,000.")
                return@launch
            }
            if (amount > 100_000_000.0) {
                onError("Initial Investment cannot exceed ₹10 Crores.")
                return@launch
            }
            if (durationMonths < 1 || durationMonths > 60) {
                onError("Duration must be between 1 and 60 months.")
                return@launch
            }
            if (targetReturn <= 0.0) {
                onError("Target Return must be greater than 0%.")
                return@launch
            }
            if (targetReturn > 1000.0) {
                onError("Target Return is too high (Max 1000%).")
                return@launch
            }

            try {
                val simulation = Simulation(
                    name = name.trim(),
                    initialAmount = amount,
                    currentAmount = amount,
                    durationMonths = durationMonths,
                    startDate = System.currentTimeMillis(),
                    targetReturnPercentage = targetReturn,
                    status = SimulationStatus.ACTIVE, // Immediately Active
                    strategyId = "MULTI_FACTOR_DNN" // Single Super Strategy
                )
                val simId = createSimulationUseCase(simulation)
                
                // Immediately start the intelligence engine
                runDailySimulationUseCase()
                
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Unknown error")
            }
        }
    }
}
