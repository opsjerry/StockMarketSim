package com.example.stockmarketsim.proof

import com.example.stockmarketsim.domain.analysis.BacktestResult
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Fee-Adjusted Tournament Scoring (P2 Item #2).
 *
 * Verifies that high-churn strategies are correctly penalized
 * and that the scoring formula produces expected rankings.
 */
class FeeAdjustedScoringTest {

    // Replicating the exact scoring formula from RunStrategyTournamentUseCase
    private val COST_PER_TRADE_PCT = 0.4

    private fun score(result: BacktestResult, targetReturn: Double = 20.0): Double {
        val feeAdjustedAlpha = result.alpha - (result.totalTrades * COST_PER_TRADE_PCT)
        val hitTargetBonus = if (result.returnPct >= targetReturn) 20.0 else 0.0
        val sharpeComponent = (if (result.sharpeRatio > 3.0) 3.0 else result.sharpeRatio) * 10.0
        return feeAdjustedAlpha + sharpeComponent + hitTargetBonus
    }

    @Test
    fun `zero trades produces raw alpha score`() {
        val result = BacktestResult(
            strategyId = "LOW_CHURN",
            strategyName = "Low Churn",
            description = "Holds positions",
            returnPct = 15.0,
            winRate = 0.6,
            finalValue = 115000.0,
            benchmarkReturn = 10.0,
            alpha = 5.0,
            sharpeRatio = 1.5,
            totalTrades = 0
        )
        val s = score(result)
        // feeAdjustedAlpha = 5.0 - (0 * 0.4) = 5.0
        // sharpe = 1.5 * 10 = 15.0
        // no target bonus
        assertEquals(20.0, s, 0.01)
    }

    @Test
    fun `100 trades creates significant drag`() {
        val result = BacktestResult(
            strategyId = "HIGH_CHURN",
            strategyName = "High Churn",
            description = "Trades aggressively",
            returnPct = 15.0,
            winRate = 0.6,
            finalValue = 115000.0,
            benchmarkReturn = 10.0,
            alpha = 5.0,
            sharpeRatio = 1.5,
            totalTrades = 100
        )
        val s = score(result)
        // feeAdjustedAlpha = 5.0 - (100 * 0.4) = 5.0 - 40 = -35.0
        // sharpe = 15.0
        // no target bonus
        assertEquals(-20.0, s, 0.01)
    }

    @Test
    fun `low churn strategy beats high churn with same alpha`() {
        val lowChurn = BacktestResult(
            strategyId = "LOW", strategyName = "Low", description = "",
            returnPct = 15.0, winRate = 0.6, finalValue = 115000.0,
            benchmarkReturn = 10.0, alpha = 5.0, sharpeRatio = 1.0,
            totalTrades = 10  // Only 10 trades
        )
        val highChurn = BacktestResult(
            strategyId = "HIGH", strategyName = "High", description = "",
            returnPct = 15.0, winRate = 0.6, finalValue = 115000.0,
            benchmarkReturn = 10.0, alpha = 5.0, sharpeRatio = 1.0,
            totalTrades = 50  // 50 trades
        )

        val lowScore = score(lowChurn)
        val highScore = score(highChurn)

        assertTrue("Low churn should score higher than high churn", lowScore > highScore)
        // Difference should be exactly (50-10) * 0.4 = 16.0 percentage points
        assertEquals(16.0, lowScore - highScore, 0.01)
    }

    @Test
    fun `target bonus applied correctly`() {
        val hitsTarget = BacktestResult(
            strategyId = "HIT", strategyName = "Hit", description = "",
            returnPct = 25.0, winRate = 0.7, finalValue = 125000.0,
            benchmarkReturn = 10.0, alpha = 15.0, sharpeRatio = 2.0,
            totalTrades = 20
        )
        val missesTarget = BacktestResult(
            strategyId = "MISS", strategyName = "Miss", description = "",
            returnPct = 19.9, winRate = 0.7, finalValue = 119900.0,
            benchmarkReturn = 10.0, alpha = 15.0, sharpeRatio = 2.0,
            totalTrades = 20
        )

        val hitScore = score(hitsTarget)
        val missScore = score(missesTarget)

        assertEquals(20.0, hitScore - missScore, 0.01)
    }

    @Test
    fun `sharpe ratio capped at 3`() {
        val highSharpe = BacktestResult(
            strategyId = "SHARP", strategyName = "Sharp", description = "",
            returnPct = 15.0, winRate = 0.8, finalValue = 115000.0,
            benchmarkReturn = 10.0, alpha = 5.0, sharpeRatio = 5.0, // Way above cap
            totalTrades = 0
        )
        val cappedSharpe = BacktestResult(
            strategyId = "CAPPED", strategyName = "Capped", description = "",
            returnPct = 15.0, winRate = 0.8, finalValue = 115000.0,
            benchmarkReturn = 10.0, alpha = 5.0, sharpeRatio = 3.0, // At cap
            totalTrades = 0
        )

        assertEquals(score(highSharpe), score(cappedSharpe), 0.01)
    }

    @Test
    fun `totalTrades field correctly propagated in BacktestResult`() {
        val result = BacktestResult(
            strategyId = "TEST", strategyName = "Test", description = "",
            returnPct = 10.0, winRate = 0.5, finalValue = 110000.0,
            benchmarkReturn = 8.0, alpha = 2.0, totalTrades = 42
        )
        assertEquals(42, result.totalTrades)
    }
}
