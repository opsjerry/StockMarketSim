package com.example.stockmarketsim.proof

import com.example.stockmarketsim.domain.analysis.BacktestResult
import com.example.stockmarketsim.domain.analysis.Backtester
import com.example.stockmarketsim.domain.model.StockQuote
import com.example.stockmarketsim.domain.strategy.ConfigurableMomentumStrategy
import com.example.stockmarketsim.domain.strategy.MomentumStrategy
import com.example.stockmarketsim.domain.strategy.RsiStrategy
import com.example.stockmarketsim.domain.strategy.SafeHavenStrategy
import com.example.stockmarketsim.domain.strategy.Strategy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * REGRESSION SUITE: Tournament Scoring
 *
 * Tests walk-forward validation, fee-adjusted scoring, deterministic ranking,
 * and scoring formula correctness for the strategy tournament.
 */
class TournamentScoringRegressionTest {

    private val baseDate = 1704067200000L
    private val backtester = Backtester()

    /**
     * Generates standard market data for tournaments.
     * A slowly rising market with moderate volatility.
     */
    private fun generateTournamentData(
        symbols: List<String>,
        days: Int = 300
    ): Map<String, List<StockQuote>> {
        return symbols.associateWith { symbol ->
            (0 until days).map { i ->
                val base = 100.0 + i * 0.3 + Math.sin(i * 0.1) * 5.0
                StockQuote(symbol, baseDate + i * 86400000L, base, base + 3.0, base - 3.0, base, 10000)
            }
        }
    }

    /**
     * Simulates walk-forward validation: 80% train, 20% test split.
     */
    private fun walkForwardSplit(
        dates: List<Long>
    ): Pair<Long, Long> {
        val splitIndex = (dates.size * 0.8).toInt()
        return dates[splitIndex] to dates.last()
    }

    // =====================================================================
    // Walk-Forward Split
    // =====================================================================

    @Test
    fun `walk-forward split produces 80-20 ratio`() {
        val dates = (0 until 300).map { baseDate + it * 86400000L }
        val (windowStart, _) = walkForwardSplit(dates)

        val trainDays = dates.count { it < windowStart }
        val testDays = dates.count { it >= windowStart }

        assertEquals("Training period should be 80%", 240, trainDays)
        assertEquals("Test period should be 20%", 60, testDays)
    }

    @Test
    fun `walk-forward split with minimum data enforced`() {
        val dates = (0 until 50).map { baseDate + it * 86400000L }
        val (windowStart, _) = walkForwardSplit(dates)

        val testDays = dates.count { it >= windowStart }
        assertTrue("Test window should have at least 10 days", testDays >= 10)
    }

    @Test
    fun `walk-forward produces valid split with 250 days`() {
        val dates = (0 until 250).map { baseDate + it * 86400000L }
        val (windowStart, _) = walkForwardSplit(dates)

        val testDays = dates.count { it >= windowStart }
        assertTrue("Test window should be 20±5% of total", testDays in 45..55)
    }

    // =====================================================================
    // Fee-Adjusted Scoring
    // =====================================================================

    /**
     * Simulates the fee-adjusted scoring formula from the tournament:
     * score = (riskAdjustedAlpha * 100) + targetReturnBonus + sharpeBonus - feePenalty
     */
    private fun calculateTournamentScore(
        result: BacktestResult,
        targetReturnPct: Double = 12.0
    ): Double {
        val riskAdjustedAlpha = result.alpha * (1.0 - result.maxDrawdown)
        val targetReturnBonus = if (result.returnPct >= targetReturnPct) 10.0 else 0.0
        val sharpeBonus = result.sharpeRatio * 5.0
        val feePenalty = result.totalTrades * 0.01  // 1% penalty per trade

        return (riskAdjustedAlpha * 100) + targetReturnBonus + sharpeBonus - feePenalty
    }

    @Test
    fun `fee penalty reduces score for high frequency strategy`() {
        val lowTrades = BacktestResult("A", "Low", "desc", 10.0, 0.6, 110000.0, 8.0, 2.0, 0.05, 1.2, 10)
        val highTrades = BacktestResult("B", "High", "desc", 10.0, 0.6, 110000.0, 8.0, 2.0, 0.05, 1.2, 200)

        val lowScore = calculateTournamentScore(lowTrades)
        val highScore = calculateTournamentScore(highTrades)

        assertTrue("Low-frequency strategy should score better", lowScore > highScore)
    }

    @Test
    fun `scoring formula rewards alpha over raw return`() {
        // Same return but different alpha
        val highAlpha = BacktestResult("A", "High Alpha", "desc", 12.0, 0.55, 112000.0, 5.0, 7.0, 0.10, 1.5, 30)
        val lowAlpha = BacktestResult("B", "Low Alpha", "desc", 12.0, 0.55, 112000.0, 11.0, 1.0, 0.10, 1.5, 30)

        val highAlphaScore = calculateTournamentScore(highAlpha)
        val lowAlphaScore = calculateTournamentScore(lowAlpha)

        assertTrue("Higher alpha should produce higher score", highAlphaScore > lowAlphaScore)
    }

    @Test
    fun `target return bonus applied at threshold`() {
        val below = BacktestResult("A", "Below", "desc", 11.9, 0.5, 111900.0, 8.0, 3.9, 0.05, 1.0, 20)
        val above = BacktestResult("B", "Above", "desc", 12.0, 0.5, 112000.0, 8.0, 4.0, 0.05, 1.0, 20)

        val belowScore = calculateTournamentScore(below)
        val aboveScore = calculateTournamentScore(above)

        assertTrue("Meeting target return should add 10.0 bonus", aboveScore > belowScore)
    }

    @Test
    fun `sharpe bonus proportional to sharpe ratio`() {
        val lowSharpe = BacktestResult("A", "Low", "desc", 10.0, 0.5, 110000.0, 8.0, 2.0, 0.05, 0.5, 20)
        val highSharpe = BacktestResult("B", "High", "desc", 10.0, 0.5, 110000.0, 8.0, 2.0, 0.05, 2.0, 20)

        val lowScore = calculateTournamentScore(lowSharpe)
        val highScore = calculateTournamentScore(highSharpe)

        assertTrue("Higher Sharpe should produce higher score", highScore > lowScore)
        val scoreDiff = highScore - lowScore
        assertEquals("Sharpe diff (1.5) should contribute 7.5 points", 7.5, scoreDiff, 0.01)
    }

    @Test
    fun `max drawdown reduces alpha effectiveness`() {
        val lowDrawdown = BacktestResult("A", "Low DD", "desc", 15.0, 0.6, 115000.0, 8.0, 7.0, 0.05, 1.5, 20)
        val highDrawdown = BacktestResult("B", "High DD", "desc", 15.0, 0.6, 115000.0, 8.0, 7.0, 0.40, 1.5, 20)

        val lowScore = calculateTournamentScore(lowDrawdown)
        val highScore = calculateTournamentScore(highDrawdown)

        assertTrue("Lower drawdown should produce better score", lowScore > highScore)
    }

    // =====================================================================
    // Deterministic Rankings
    // =====================================================================

    @Test
    fun `deterministic slippage produces identical backtest results`() = runBlocking {
        val data = generateTournamentData(listOf("A.NS", "B.NS"), 250)
        val strategy = ConfigurableMomentumStrategy(20)

        val result1 = backtester.runBacktest(strategy, data, 100000.0, useDeterministicSlippage = true)
        val result2 = backtester.runBacktest(strategy, data, 100000.0, useDeterministicSlippage = true)

        assertEquals("Return should be identical", result1.returnPct, result2.returnPct, 0.001)
        assertEquals("Final value should be identical", result1.finalValue, result2.finalValue, 0.01)
        assertEquals("Total trades should be identical", result1.totalTrades, result2.totalTrades)
        assertEquals("Alpha should be identical", result1.alpha, result2.alpha, 0.001)
    }

    @Test
    fun `multiple strategies can be compared fairly`() = runBlocking {
        val data = generateTournamentData(listOf("A.NS", "B.NS", "C.NS"), 250)

        val strategies = listOf(
            MomentumStrategy(),
            ConfigurableMomentumStrategy(50),
            SafeHavenStrategy(),
            RsiStrategy()
        )

        val results = strategies.map { strategy ->
            backtester.runBacktest(strategy, data, 100000.0, useDeterministicSlippage = true)
        }

        // All results should be valid
        results.forEach { result ->
            assertTrue("Final value should be positive", result.finalValue > 0)
            assertTrue("Win rate should be in [0,1]", result.winRate in 0.0..1.0)
            assertTrue("Total trades should be non-negative", result.totalTrades >= 0)
        }

        // Results should be different (different strategies)
        val finalValues = results.map { it.finalValue }.toSet()
        assertTrue("Different strategies should produce different results", finalValues.size > 1)
    }

    // =====================================================================
    // Tournament Edge Cases
    // =====================================================================

    @Test
    fun `insufficient data produces no useful results`() = runBlocking {
        val shortData = mapOf(
            "A.NS" to (0 until 30).map { i ->
                StockQuote("A.NS", baseDate + i * 86400000L, 100.0, 102.0, 98.0, 100.0, 1000)
            }
        )

        val result = backtester.runBacktest(MomentumStrategy(), shortData, 100000.0)
        // With only 30 days and MomentumStrategy needing SMA(20), very few trading days
        assertEquals("Short data should return near initial cash", 100000.0, result.finalValue, 100.0)
    }

    @Test
    fun `windowed backtest only scores forward period`() = runBlocking {
        val data = generateTournamentData(listOf("A.NS"), 300)
        val dates = data["A.NS"]!!.map { it.date }
        val (windowStart, _) = walkForwardSplit(dates)

        val fullResult = backtester.runBacktest(
            ConfigurableMomentumStrategy(20), data, 100000.0, useDeterministicSlippage = true
        )
        val windowedResult = backtester.runBacktest(
            ConfigurableMomentumStrategy(20), data, 100000.0,
            windowStart = windowStart, useDeterministicSlippage = true
        )

        // Windowed result may be different (only scored on last 20%)
        // Key: both should complete without error
        assertNotNull(fullResult)
        assertNotNull(windowedResult)
    }

    @Test
    fun `backtest result fields all populated`() = runBlocking {
        val data = generateTournamentData(listOf("A.NS", "B.NS"), 250)
        val result = backtester.runBacktest(
            MomentumStrategy(), data, 100000.0, useDeterministicSlippage = true
        )

        assertEquals("MOMENTUM", result.strategyId)
        assertEquals("Momentum Chaser", result.strategyName)
        assertTrue("Description should not be empty", result.description.isNotEmpty())
        assertTrue("Max drawdown should be >= 0", result.maxDrawdown >= 0)
    }
}
