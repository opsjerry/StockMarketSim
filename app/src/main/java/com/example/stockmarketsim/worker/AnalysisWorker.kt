package com.example.stockmarketsim.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.stockmarketsim.domain.repository.SimulationRepository
import com.example.stockmarketsim.domain.repository.StockRepository
import com.example.stockmarketsim.domain.strategy.StrategyProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@HiltWorker
class AnalysisWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val stockRepository: StockRepository,
    private val runStrategyTournamentUseCase: com.example.stockmarketsim.domain.usecase.RunStrategyTournamentUseCase,
    private val simulationRepository: SimulationRepository,
    private val logManager: com.example.stockmarketsim.data.manager.SimulationLogManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Promote to Foreground Service to prevent execution timeout (10 min limit)
        // Serial execution takes ~15-20 mins for full tournament
        try {
            setForeground(createForegroundInfo())
        } catch (e: Exception) {
            android.util.Log.e("AnalysisWorker", "Failed to start foreground service", e)
        }

        val simulationId = inputData.getInt("SIMULATION_ID", -1)
        if (simulationId == -1) return Result.failure()
        
        val simulation = simulationRepository.getSimulationById(simulationId)
        if (simulation == null) return Result.failure()
        
        try {
            logManager.log(simulationId, "🧠 Starting Intelligence Engine...")
            logManager.log(simulationId, "📡 Fetching latest market data (300 days history)...")

            // 1. Fetch Real Market Data (Universe + Benchmark)
            val universe = com.example.stockmarketsim.domain.model.StockUniverse.AllMarket + com.example.stockmarketsim.domain.model.StockUniverse.BENCHMARK_INDEX
            // Fetch last 300 days for backtesting (Need >200 for Regime Filter)
            val allData = stockRepository.getBatchStockHistory(
                symbols = universe, 
                timeFrame = com.example.stockmarketsim.domain.model.TimeFrame.DAILY, 
                limit = 300
            ) { msg ->
                logManager.log(simulationId, msg)
            }
            
            // Separate Strategy Universe from Benchmark
            val marketData = allData.filterKeys { it != com.example.stockmarketsim.domain.model.StockUniverse.BENCHMARK_INDEX }
            val benchmarkData = allData[com.example.stockmarketsim.domain.model.StockUniverse.BENCHMARK_INDEX] ?: emptyList()
            
            if (marketData.isEmpty()) {
                android.util.Log.e("AnalysisWorker", "No market data fetched!")
                logManager.log(simulationId, "❌ Failed to fetch market data. Check internet connection.")
                simulationRepository.updateSimulation(simulation.copy(status = com.example.stockmarketsim.domain.model.SimulationStatus.FAILED))
                return Result.failure() 
            }

            logManager.log(simulationId, "🏆 Running Strategy Tournament: Identifying best performing strategies for current market...")

            // 2. Run Backtests via UseCase (Optimized)
            val targetReturn = simulation.targetReturnPercentage
            val tournamentResult = runStrategyTournamentUseCase(
                marketData = marketData,
                benchmarkData = benchmarkData,
                initialCash = simulation.initialAmount,
                targetReturn = targetReturn
            )
            
            val results = tournamentResult.candidates
            
            if (results.isEmpty()) {
                 android.util.Log.e("AnalysisWorker", "Backtest yielded 0 results.")
                 logManager.log(simulationId, "❌ Strategy Tournament failed. No valid strategies found.")
                 simulationRepository.updateSimulation(simulation.copy(status = com.example.stockmarketsim.domain.model.SimulationStatus.FAILED))
                 return Result.failure()
            }
            
            // 4. Save Results to File (Shared with ViewModel)
            val file = java.io.File(applicationContext.filesDir, "analysis_results_$simulationId.json")
            val jsonBuilder = StringBuilder()
            jsonBuilder.append("[")
            
            // Take top 10
            results.take(10).forEachIndexed { index,  res ->
                val metTarget = res.returnPct >= targetReturn
                val targetStatus = if (metTarget) "✅ Target Met" else "❌ Target Missed"
                
                // Normalized Score: 1.0 means Target Met (100/100)
                val rawScore = if (targetReturn > 0) res.returnPct / targetReturn else 0.0
                val normalizedScore = rawScore.coerceIn(0.0, 1.0)

                jsonBuilder.append("""
                    {
                        "id": "${res.strategyId}",
                        "name": "${res.strategyName}",
                        "description": "${res.description} | $targetStatus | Return: ${"%.2f".format(res.returnPct)}%",
                        "score": $normalizedScore,
                        "final_value": ${res.finalValue},
                        "alpha": ${res.alpha},
                        "benchmark_return": ${res.benchmarkReturn},
                        "return_pct": ${"%.2f".format(res.returnPct)},
                        "drawdown": ${"%.2f".format(res.maxDrawdown)},
                        "sharpe": ${"%.2f".format(res.sharpeRatio)}
                    }
                """.trimIndent())
                if (index < 9 && index < results.size - 1) jsonBuilder.append(",")
            }
            jsonBuilder.append("]")
            
            file.writeText(jsonBuilder.toString())
            
            android.util.Log.d("AnalysisWorker", "Backtest Complete. Top Strategy: ${results.first().strategyName} (${results.first().returnPct}%)")
            logManager.log(simulationId, "✅ Backtest Complete. Top Strategy: ${results.first().strategyName}")

            // Only transition to ANALYSIS_COMPLETE if it was ANALYZING (initial setup)
            // If it's already ACTIVE, keep it ACTIVE.
            if (simulation.status == com.example.stockmarketsim.domain.model.SimulationStatus.ANALYZING) {
                simulationRepository.updateSimulation(simulation.copy(status = com.example.stockmarketsim.domain.model.SimulationStatus.ANALYSIS_COMPLETE))
            }

            return Result.success()
            
        } catch (e: Exception) {
            e.printStackTrace()
            simulationRepository.updateSimulation(simulation.copy(status = com.example.stockmarketsim.domain.model.SimulationStatus.FAILED))
            return Result.failure()
        }
        }


    private fun createForegroundInfo(): androidx.work.ForegroundInfo {
        val id = "simulation_channel" // Reuse channel from DailySimulationWorker
        val title = "Stock Market Simulator"
        
        // Ensure Notification Channel exists (idempotent)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                id, title, android.app.NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, id)
            .setContentTitle("Analyzing Market...")
            .setContentText("Running Strategy Tournament (Do not close app)")
            .setSmallIcon(android.R.drawable.ic_menu_rotate) 
            .setOngoing(true)
            .build()
            
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
            return androidx.work.ForegroundInfo(
                102, // Different ID than DailySimulationWorker (101)
                notification, 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        }
        
        return androidx.work.ForegroundInfo(102, notification)
    }
}

