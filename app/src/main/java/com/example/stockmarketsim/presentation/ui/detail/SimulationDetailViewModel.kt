package com.example.stockmarketsim.presentation.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stockmarketsim.domain.model.PortfolioItem
import com.example.stockmarketsim.domain.model.Simulation
import com.example.stockmarketsim.data.local.dao.TransactionDao
import com.example.stockmarketsim.data.local.entity.TransactionEntity
import com.example.stockmarketsim.data.repository.SimulationRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.stockmarketsim.presentation.ui.analysis.StrategyRecommendation
import com.example.stockmarketsim.data.manager.SimulationLogManager
import java.io.File
import java.util.concurrent.TimeUnit

@HiltViewModel
class SimulationDetailViewModel @Inject constructor(
    private val repository: SimulationRepositoryImpl,
    private val transactionDao: TransactionDao,
    private val stockRepository: com.example.stockmarketsim.domain.repository.StockRepository,
    private val logManager: SimulationLogManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _simulation = MutableStateFlow<Simulation?>(null)
    private val _holdings = MutableStateFlow<List<PortfolioItem>>(emptyList())
    
    val simulation = _simulation.asStateFlow()
    val holdings = _holdings.asStateFlow()
    
    private val _totalEquity = MutableStateFlow(0.0)
    val totalEquity = _totalEquity.asStateFlow()

    private val _holdingsValue = MutableStateFlow<Map<String, Double>>(emptyMap())
    val holdingsValue = _holdingsValue.asStateFlow()
    
    private val _transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val transactions = _transactions.asStateFlow()

    private val _history = MutableStateFlow<List<Pair<Long, Double>>>(emptyList())
    val history: StateFlow<List<Pair<Long, Double>>> = _history.asStateFlow()

    private val _recommendations = MutableStateFlow<List<StrategyRecommendation>>(emptyList())
    val recommendations = _recommendations.asStateFlow()

    private val _sharpeRatio = MutableStateFlow(0.0)
    val sharpeRatio = _sharpeRatio.asStateFlow()
    
    private val _alpha = MutableStateFlow(0.0)
    val alpha = _alpha.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing = _isAnalyzing.asStateFlow()

    private val _isSwitching = MutableStateFlow(false)
    val isSwitching = _isSwitching.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private var currentSimulationJob: kotlinx.coroutines.Job? = null

    fun loadSimulation(simulationId: Int) {
        if (_simulation.value?.id == simulationId && currentSimulationJob?.isActive == true) return
        
        currentSimulationJob?.cancel()
        currentSimulationJob = viewModelScope.launch {
            repository.getSimulationByIdFlow(simulationId).collect { sim ->
                if (sim != null) {
                    _simulation.value = sim
                    
                    // Fetch Prices for Holdings
                    val portfolio = repository.getPortfolio(simulationId)
                    _holdings.value = portfolio
                    
                    // Calculate Total Equity & Holdings Value Map
                    val currentPrices = try {
                        stockRepository.getBatchStockHistory(portfolio.map { it.symbol }, com.example.stockmarketsim.domain.model.TimeFrame.DAILY, 1)
                    } catch (e: Exception) {
                        emptyMap()
                    }
                    val priceMap = currentPrices.mapValues { it.value.lastOrNull()?.close ?: 0.0 }
                    
                    var holdingsTotal = 0.0
                    val allocMap = mutableMapOf<String, Double>()
                    
                    for (item in portfolio) {
                        val price = priceMap[item.symbol] ?: item.averagePrice
                        val value = item.quantity * price
                        holdingsTotal += value
                        allocMap[item.symbol] = value
                    }
                    
                    val totalEq = sim.currentAmount + holdingsTotal
                    _totalEquity.value = totalEq
                    
                    // Sort map by value desc
                    _holdingsValue.value = allocMap.toList().sortedByDescending { it.second }.toMap()

                    // Check for existing analysis results
                    loadAnalysisResults(simulationId)
                } else {
                     _totalEquity.value = 0.0
                }
            }
        }
        
        viewModelScope.launch {
            repository.getHistory(simulationId).collect {
                _history.value = it
                if (it.size > 2) {
                    val returns = mutableListOf<Double>()
                    for(i in 1 until it.size) {
                       returns.add((it[i].second - it[i-1].second) / it[i-1].second)
                    }
                    val avgReturn = returns.average()
                    val variance = returns.map { r -> (r - avgReturn) * (r - avgReturn) }.average()
                    val stdDev = Math.sqrt(variance)
                    
                    val annualizedReturn = avgReturn * 252
                    val annualizedStdDev = stdDev * Math.sqrt(252.0)
                    
                    _sharpeRatio.value = if(annualizedStdDev > 0) (annualizedReturn - 0.05) / annualizedStdDev else 0.0
                    _alpha.value = (annualizedReturn - 0.12) * 100 // Assuming 12% Nifty benchmark
                }
            }
        }

        viewModelScope.launch {
            transactionDao.getTransactions(simulationId).collect {
                _transactions.value = it
            }
        }
    }

    fun runStrategyTournament() {
        val simId = _simulation.value?.id ?: return
        _isAnalyzing.value = true
        
        viewModelScope.launch {
            val workRequest = OneTimeWorkRequestBuilder<com.example.stockmarketsim.worker.AnalysisWorker>()
                .setInputData(workDataOf("SIMULATION_ID" to simId))
                .build()
            
            WorkManager.getInstance(context).enqueue(workRequest)

            // Start polling for results
            var retries = 0
            while (retries < 60) { // Max 1 minute
                delay(2000)
                if (loadAnalysisResults(simId)) break
                retries++
            }
            _isAnalyzing.value = false
        }
    }

    private fun loadAnalysisResults(simulationId: Int): Boolean {
        val file = File(context.filesDir, "analysis_results_$simulationId.json")
        if (file.exists()) {
            try {
                val json = file.readText()
                val list = parseRecommendations(json)
                _recommendations.value = list
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    private fun parseRecommendations(json: String): List<StrategyRecommendation> {
        val list = mutableListOf<StrategyRecommendation>()
        val pattern = java.util.regex.Pattern.compile(
            "\\{.*?\"id\": \"(.*?)\",.*?\"name\": \"(.*?)\",.*?\"description\": \"(.*?)\",.*?\"score\": (.*?),.*?\"final_value\": (.*?),.*?\"alpha\": (.*?),.*?\"benchmark_return\": (.*?),.*?\"return_pct\": (.*?)\\s*\\}", 
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
            
            list.add(StrategyRecommendation(id, name, desc, score, finalValue, alpha, benchReturn, rPct))
        }
        return list
    }

    fun switchStrategy(strategyId: String, newTargetReturn: Double?) {
        val sim = _simulation.value ?: return
        if (_isSwitching.value) return
        
        _isSwitching.value = true
        viewModelScope.launch {
            try {
                android.util.Log.d("StockSim", "Switching Strategy to $strategyId, target $newTargetReturn")
                
                // Round target return to 2 decimals
                val roundedTarget = newTargetReturn?.let { 
                    kotlin.math.round(it * 100) / 100.0 
                } ?: sim.targetReturnPercentage

                val updated = sim.copy(
                    strategyId = strategyId,
                    targetReturnPercentage = roundedTarget
                )
                repository.updateSimulation(updated)
                
                // Log the manual switch event
                logManager.log(sim.id, "[USER] Strategy manually switched to: $strategyId (Target: $roundedTarget%)")
                
                // Force immediate state update for UI
                _simulation.value = updated
                
                _message.emit("Strategy switched successfully to $strategyId")
            } catch (e: Exception) {
                e.printStackTrace()
                _message.emit("Failed to switch strategy: ${e.message}")
            } finally {
                _isSwitching.value = false
            }
        }
    }

    fun toggleLiveTrading(enabled: Boolean) {
        val sim = _simulation.value ?: return
        viewModelScope.launch {
            try {
                val updated = sim.copy(isLiveTradingEnabled = enabled)
                repository.updateSimulation(updated)
                _simulation.value = updated
                
                val status = if (enabled) "ENABLED" else "DISABLED"
                logManager.log(sim.id, "[USER] Live Trading $status.")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

