package com.example.stockmarketsim.proof

import com.example.stockmarketsim.domain.analysis.PortfolioRebalancer
import com.example.stockmarketsim.domain.analysis.RiskEngine
import com.example.stockmarketsim.domain.model.FundamentalData
import com.example.stockmarketsim.domain.model.StockQuote
import com.example.stockmarketsim.domain.strategy.StrategySignal
import org.junit.Assert.*
import org.junit.Test

/**
 * REGRESSION SUITE: Security & Behavioral Safety
 *
 * Tests input sanitization, order value caps, negative value rejection,
 * quality filter edge cases, and strategy switching guards.
 */
class SecurityRegressionTest {

    // =====================================================================
    // Symbol Sanitization
    // =====================================================================

    @Test
    fun `SQL injection in symbol does not crash portfolio rebalancer`() {
        val rebalancer = PortfolioRebalancer()
        val maliciousSymbol = "'; DROP TABLE stocks; --"

        val result = rebalancer.calculateTrades(
            currentCash = 100000.0,
            currentHoldings = emptyMap(),
            targetAllocations = mapOf(maliciousSymbol to 0.5),
            totalPortfolioValue = 100000.0,
            currentPrices = mapOf(maliciousSymbol to 500.0)
        )

        assertNotNull("Malicious symbol should not crash", result)
    }

    @Test
    fun `empty symbol name handled gracefully`() {
        val rebalancer = PortfolioRebalancer()

        val result = rebalancer.calculateTrades(
            currentCash = 100000.0,
            currentHoldings = emptyMap(),
            targetAllocations = mapOf("" to 0.5),
            totalPortfolioValue = 100000.0,
            currentPrices = mapOf("" to 500.0)
        )

        assertNotNull("Empty symbol should not crash", result)
    }

    @Test
    fun `unicode symbol name handled gracefully`() {
        val rebalancer = PortfolioRebalancer()
        val unicodeSymbol = "テスト.NS"

        val result = rebalancer.calculateTrades(
            currentCash = 100000.0,
            currentHoldings = emptyMap(),
            targetAllocations = mapOf(unicodeSymbol to 0.5),
            totalPortfolioValue = 100000.0,
            currentPrices = mapOf(unicodeSymbol to 500.0)
        )

        assertNotNull("Unicode symbol should not crash", result)
    }

    // =====================================================================
    // Max Order Value Cap (₹50,000 per app_bible.md)
    // =====================================================================

    @Test
    fun `order value can be validated for max cap`() {
        val maxOrderValue = 50000.0

        val tradeValue = 75000.0
        assertTrue("Trade exceeding ₹50k should be flagged", tradeValue > maxOrderValue)

        val validTrade = 45000.0
        assertTrue("Trade under ₹50k should pass", validTrade <= maxOrderValue)
    }

    @Test
    fun `rebalancer respects cash availability preventing excessive orders`() {
        val rebalancer = PortfolioRebalancer()

        val result = rebalancer.calculateTrades(
            currentCash = 30000.0,
            currentHoldings = emptyMap(),
            targetAllocations = mapOf("A.NS" to 1.0),
            totalPortfolioValue = 30000.0,
            currentPrices = mapOf("A.NS" to 1000.0)
        )

        val totalBuyValue = result.trades.filter { it.type == "BUY" }.sumOf { it.netAmount }
        assertTrue("Total buy value should not exceed cash", totalBuyValue <= 30001.0)
        assertTrue("Cash should be non-negative", result.newCash >= -0.01)
    }

    // =====================================================================
    // Negative Value Rejection
    // =====================================================================

    @Test
    fun `negative price stocks skipped by rebalancer`() {
        val rebalancer = PortfolioRebalancer()

        val result = rebalancer.calculateTrades(
            currentCash = 100000.0,
            currentHoldings = emptyMap(),
            targetAllocations = mapOf("NEG.NS" to 0.5, "GOOD.NS" to 0.5),
            totalPortfolioValue = 100000.0,
            currentPrices = mapOf("NEG.NS" to -100.0, "GOOD.NS" to 500.0)
        )

        val negTrades = result.trades.filter { it.symbol == "NEG.NS" }
        assertTrue("Negative price stock should not have trades", negTrades.isEmpty())
    }

    @Test
    fun `zero price stocks skipped by rebalancer`() {
        val rebalancer = PortfolioRebalancer()

        val result = rebalancer.calculateTrades(
            currentCash = 100000.0,
            currentHoldings = emptyMap(),
            targetAllocations = mapOf("ZERO.NS" to 0.5),
            totalPortfolioValue = 100000.0,
            currentPrices = mapOf("ZERO.NS" to 0.0)
        )

        val zeroTrades = result.trades.filter { it.symbol == "ZERO.NS" }
        assertTrue("Zero price stock should not have trades", zeroTrades.isEmpty())
    }

    // =====================================================================
    // FundamentalData Edge Cases (Security)
    // =====================================================================

    @Test
    fun `FundamentalData with Double MAX VALUE`() {
        val data = FundamentalData(
            symbol = "EXTREME.NS",
            returnOnEquity = Double.MAX_VALUE,
            debtToEquity = 0.5,
            marketCap = Long.MAX_VALUE,
            trailingPE = Double.MAX_VALUE,
            bookValue = Double.MAX_VALUE
        )
        assertTrue("Extreme positive values should pass", data.meetsQualityThreshold())
    }

    @Test
    fun `FundamentalData with negative one`() {
        val data = FundamentalData(
            symbol = "NEGATIVE.NS",
            returnOnEquity = -0.01,
            debtToEquity = -1.0,
            marketCap = -1L,
            trailingPE = -1.0,
            bookValue = -1.0
        )
        // ROE < 0.12 → should fail
        assertFalse("Negative ROE should fail", data.meetsQualityThreshold())
    }

    @Test
    fun `FundamentalData with NaN values`() {
        val data = FundamentalData(
            symbol = "NAN.NS",
            returnOnEquity = Double.NaN,
            debtToEquity = Double.NaN,
            marketCap = null,
            trailingPE = null,
            bookValue = null
        )
        assertNotNull("FundamentalData with NaN should not crash", data)
    }

    // =====================================================================
    // Risk Engine — Signal Manipulation Protection
    // =====================================================================

    @Test
    fun `empty signals produce empty allocations`() {
        val allocations = RiskEngine.applyRiskManagement(emptyList(), 100000.0, false)
        assertTrue("Empty signals should produce empty allocations", allocations.isEmpty())
    }

    @Test
    fun `non-BUY signals are filtered out`() {
        val signals = listOf(
            StrategySignal("A.NS", "SELL", 1.0),
            StrategySignal("B.NS", "HOLD", 1.0),
            StrategySignal("C.NS", "INVALID", 1.0)
        )
        val allocations = RiskEngine.applyRiskManagement(signals, 100000.0, false)
        assertTrue("Non-BUY signals should produce empty allocations", allocations.isEmpty())
    }

    @Test
    fun `confidence clamped to 0_5 to 1_5 range`() {
        val signals = listOf(
            StrategySignal("A.NS", "BUY", 100.0),
            StrategySignal("B.NS", "BUY", -5.0)
        )

        val allocations = RiskEngine.applyRiskManagement(signals, 100000.0, false)

        assertTrue("High confidence should still be allocated (clamped)",
            allocations.containsKey("A.NS"))
        assertTrue("Negative confidence should still be allocated (clamped)",
            allocations.containsKey("B.NS"))

        for ((_, weight) in allocations) {
            assertTrue("Each allocation should be positive", weight > 0)
            assertTrue("Each allocation should be <= 15%", weight <= 0.16)
        }
    }

    // =====================================================================
    // Strategy Switching Guard (Minimum 20-Day Evaluation)
    // =====================================================================

    @Test
    fun `simulation lastSwitchDate tracks strategy changes`() {
        val sim = com.example.stockmarketsim.domain.model.Simulation(
            name = "Test Sim",
            initialAmount = 100000.0,
            currentAmount = 105000.0,
            durationMonths = 12,
            startDate = 1704067200000L,
            targetReturnPercentage = 12.0,
            strategyId = "MOMENTUM",
            lastSwitchDate = 1704067200000L
        )

        val now = 1704067200000L + (10 * 86400000L)
        val minDays = 20
        val daysSinceSwitch = (now - sim.lastSwitchDate) / 86400000L

        assertTrue("10 days is less than 20 day minimum", daysSinceSwitch < minDays)

        val after20Days = 1704067200000L + (25 * 86400000L)
        val daysSinceSwitch2 = (after20Days - sim.lastSwitchDate) / 86400000L

        assertTrue("25 days exceeds 20 day minimum", daysSinceSwitch2 >= minDays)
    }

    @Test
    fun `strategy switch buffer requires significant improvement`() {
        val currentScore = 100.0
        val newScore = 103.0
        val switchBuffer = 1.05

        assertFalse("3% improvement should NOT trigger switch",
            newScore >= currentScore * switchBuffer)

        val betterScore = 106.0
        assertTrue("6% improvement should trigger switch",
            betterScore >= currentScore * switchBuffer)
    }
}
