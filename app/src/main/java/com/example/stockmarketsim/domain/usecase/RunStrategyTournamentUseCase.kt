package com.example.stockmarketsim.domain.usecase

import com.example.stockmarketsim.domain.analysis.BacktestResult
import com.example.stockmarketsim.domain.analysis.Backtester
import com.example.stockmarketsim.domain.model.StockQuote
import com.example.stockmarketsim.domain.model.StockUniverse
import com.example.stockmarketsim.domain.strategy.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TournamentResult(
    val candidates: List<BacktestResult>,
    val evaluationStartDate: Long
)

class RunStrategyTournamentUseCase @Inject constructor(
    private val strategyProvider: StrategyProvider
) {
    suspend operator fun invoke(
        marketData: Map<String, List<StockQuote>>,
        benchmarkData: List<StockQuote>,
        initialCash: Double,
        targetReturn: Double
    ): TournamentResult {
        
        // 1. Generate Strategy Variations (Optimized for Performance)
        val strategies = mutableListOf<Strategy>()
        
        // A. Trend Following (Smoothed)
        for (sma in listOf(20, 50, 100, 200)) {
            strategies.add(ConfigurableMomentumStrategy(sma))
        }
        
        // B. Fast Trend Following (EMA)
        for (ema in listOf(20, 50, 100, 150)) {
            strategies.add(EmaMomentumStrategy(ema))
        }
        
        // C. Mean Reversion (RSI)
        for (rsi in listOf(14, 21, 28)) {
            strategies.add(RsiStrategy(period = rsi))
        }
        
        // D. Volatility Breakout & Mean Reversion (Bollinger)
        // Standard settings only to save iterations
        strategies.add(BollingerBreakoutStrategy(period = 20, stdDevMultiplier = 2.0))
        strategies.add(BollingerMeanReversionStrategy(period = 20, stdDevMultiplier = 2.0))
        
        // E. Specialized Models
        strategies.add(HybridMomentumRsiStrategy(smaPeriod = 50))
        strategies.add(HybridMomentumRsiStrategy(smaPeriod = 100))
        strategies.add(MacdStrategy())
        strategies.add(YearlyHighBreakoutStrategy())
        
        // F. Volume Analysis
        strategies.add(VptStrategy(smaPeriod = 20))
        
        strategies.add(strategyProvider.getStrategy("SAFE_HAVEN"))
        
        // EXPERT REVIEW: Add Relative Volume Strategies (ML Proxy)
        strategies.add(strategyProvider.getStrategy("REL_VOL_20_20")) // 2.0x Vol
        strategies.add(strategyProvider.getStrategy("REL_VOL_20_30")) // 3.0x Vol
        strategies.add(strategyProvider.getStrategy("REL_VOL_20_50")) // 5.0x Vol

        // G. Deep Learning (Re-enabled)
        strategies.add(strategyProvider.getStrategy("MULTI_FACTOR_DNN"))

        // 2. EXPERT REVIEW: Walk-Forward Validation
        // Instead of testing on the whole dataset (Optimization Bias), we split.
        // Train (80%): To identify "Good Candidates".
        // Test (20%): To pick the "True Winner" that generalizes well.
        
        // Find split date (80% mark)
        val firstSymbol = marketData.keys.firstOrNull() ?: return TournamentResult(emptyList(), 0L)
        val allDates = marketData[firstSymbol]?.map { it.date } ?: return TournamentResult(emptyList(), 0L)
        
        // QUANT CHECK: Minimum Window Size
        // We need at least 50 points to have a meaningful Train(40) + Test(10) split.
        if (allDates.size < 50) return TournamentResult(emptyList(), 0L)
        
        val splitIndex = (allDates.size * 0.8).toInt()
        
        // QUANT CHECK: Ensure Test Window has enough data (e.g., 20 days)
        // If 20% is less than 20 days, we force a split that gives at least 20 days, IF possible.
        // If total data is small, we might just have to accept what we have, but ideally > 20.
        val testWindowSize = allDates.size - splitIndex
        val finalSplitIndex = if (testWindowSize < 20 && allDates.size > 40) {
             allDates.size - 20 // Force at least 20 days for testing
        } else {
             splitIndex
        }
        
        val splitDate = allDates[finalSplitIndex]
        
        // Step A: Run All Strategies on TRAIN Data (Full History provided, window limits trading)
        // Step A: Run All Strategies on TRAIN Data (Full History provided, window limits trading)
        val backtester = Backtester()
        val processors = Runtime.getRuntime().availableProcessors()
        // MEMORY OPTIMIZED: Now safe to run in parallel.
        val concurrency = 4.coerceAtMost(processors) 
        val semaphore = Semaphore(concurrency)

        val trainResults = withContext(Dispatchers.Default) {
            strategies.map { strategy ->
                async {
                    semaphore.acquire()
                    try {
                        backtester.runBacktest(
                            strategy = strategy, 
                            marketData = marketData, 
                            initialCash = initialCash, 
                            benchmarkData = benchmarkData,
                            windowEnd = splitDate // Trading ends at splitDate
                        ) { }
                    } finally {
                        semaphore.release()
                    }
                }
            }.awaitAll()
        }
        
        // Step B: Filter Top 10 Candidates (Preserve Diversity)
        val topCandidates = trainResults.sortedByDescending { it.alpha }.take(10).map { result ->
             strategies.first { it.id == result.strategyId }
        }
        
        // Step C: Run Finalists on TEST Data (The "Forward" Walk)
        val testResults = withContext(Dispatchers.Default) {
            topCandidates.map { strategy ->
                async {
                    semaphore.acquire()
                    try {
                        backtester.runBacktest(
                            strategy = strategy, 
                            marketData = marketData, 
                            initialCash = initialCash, 
                            benchmarkData = benchmarkData,
                            windowStart = splitDate // Trading starts at splitDate
                        ) { }
                    } finally {
                        semaphore.release()
                    }
                }
            }.awaitAll()
        }
        
        // Step D: Score based on TEST (Forward) Performance
        // Fee-Adjusted: Penalize strategies that churn excessively.
        // Each round-trip trade costs ~0.4% (0.2% slippage each way).
        // A strategy with 100 trades is penalized 100 * 0.4 = 40% drag on alpha.
        val COST_PER_TRADE_PCT = 0.4 // 0.4% per round-trip (slippage + commission)
        
        val rankedResults = testResults.sortedByDescending { result ->
            val feeAdjustedAlpha = result.alpha - (result.totalTrades * COST_PER_TRADE_PCT)
            val hitTargetBonus = if (result.returnPct >= targetReturn) 20.0 else 0.0
            val sharpeComponent = (if (result.sharpeRatio > 3.0) 3.0 else result.sharpeRatio) * 10.0
            feeAdjustedAlpha + sharpeComponent + hitTargetBonus
        }
        
        return TournamentResult(rankedResults, splitDate)
    }
}
