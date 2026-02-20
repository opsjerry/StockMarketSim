package com.example.stockmarketsim.proof

import com.example.stockmarketsim.domain.analysis.BacktestResult
import com.example.stockmarketsim.domain.model.FundamentalData
import com.example.stockmarketsim.domain.model.Simulation
import com.example.stockmarketsim.domain.model.SimulationStatus
import com.example.stockmarketsim.domain.model.StockQuote
import org.junit.Assert.*
import org.junit.Test

/**
 * REGRESSION SUITE: Data Integrity
 *
 * Tests model invariants, field ranges, status transitions,
 * and edge cases for core domain data classes.
 */
class DataIntegrityRegressionTest {

    // =====================================================================
    // StockQuote Invariants
    // =====================================================================

    @Test
    fun `StockQuote accepts all valid fields`() {
        val quote = StockQuote("TEST.NS", 1704067200000L, 100.0, 105.0, 95.0, 102.0, 50000)
        assertEquals("TEST.NS", quote.symbol)
        assertEquals(100.0, quote.open, 0.001)
        assertEquals(105.0, quote.high, 0.001)
        assertEquals(95.0, quote.low, 0.001)
        assertEquals(102.0, quote.close, 0.001)
        assertEquals(50000L, quote.volume)
    }

    @Test
    fun `StockQuote with zero price does not crash`() {
        val quote = StockQuote("ZERO.NS", 1704067200000L, 0.0, 0.0, 0.0, 0.0, 0)
        assertNotNull(quote)
        assertEquals(0.0, quote.close, 0.001)
    }

    @Test
    fun `StockQuote with negative values`() {
        val quote = StockQuote("NEG.NS", 1704067200000L, -10.0, -5.0, -15.0, -8.0, 1000)
        assertNotNull("Negative prices should not crash", quote)
    }

    @Test
    fun `StockQuote copy preserves data`() {
        val original = StockQuote("A.NS", 1704067200000L, 100.0, 105.0, 95.0, 102.0, 50000)
        val copy = original.copy(close = 110.0)
        assertEquals("Original should not change", 102.0, original.close, 0.001)
        assertEquals("Copy should have new close", 110.0, copy.close, 0.001)
        assertEquals("Symbol should be preserved", "A.NS", copy.symbol)
    }

    // =====================================================================
    // FundamentalData Quality Threshold
    // =====================================================================

    @Test
    fun `FundamentalData meets quality with good metrics`() {
        val data = FundamentalData(
            symbol = "TEST.NS",
            returnOnEquity = 0.15,     // 15% > 12%
            debtToEquity = 0.5,        // < 1.0
            marketCap = 50000L,
            trailingPE = 20.0,
            bookValue = 100.0
        )
        assertTrue("Good fundamentals should pass quality filter", data.meetsQualityThreshold())
    }

    @Test
    fun `FundamentalData fails quality with low ROE`() {
        val data = FundamentalData(
            symbol = "TEST.NS",
            returnOnEquity = 0.08,     // 8% < 12%
            debtToEquity = 0.5,
            marketCap = 50000L,
            trailingPE = 20.0,
            bookValue = 100.0
        )
        assertFalse("Low ROE should fail quality filter", data.meetsQualityThreshold())
    }

    @Test
    fun `FundamentalData fails quality with high debt`() {
        val data = FundamentalData(
            symbol = "TEST.NS",
            returnOnEquity = 0.20,
            debtToEquity = 1.5,        // > 1.0
            marketCap = 50000L,
            trailingPE = 20.0,
            bookValue = 100.0
        )
        assertFalse("High debt should fail quality filter", data.meetsQualityThreshold())
    }

    @Test
    fun `FundamentalData passes quality with null values`() {
        val data = FundamentalData(
            symbol = "TEST.NS",
            returnOnEquity = null,
            debtToEquity = null,
            marketCap = null,
            trailingPE = null,
            bookValue = null
        )
        assertTrue("Missing data should pass filter (benefit of doubt)", data.meetsQualityThreshold())
    }

    @Test
    fun `FundamentalData at exact ROE threshold`() {
        val data = FundamentalData(
            symbol = "TEST.NS",
            returnOnEquity = 0.12,     // Exactly at threshold
            debtToEquity = 0.5,
            marketCap = 50000L,
            trailingPE = 20.0,
            bookValue = 100.0
        )
        assertTrue("ROE of exactly 0.12 should pass (>=)", data.meetsQualityThreshold())
    }

    @Test
    fun `FundamentalData at exact debt threshold`() {
        val data = FundamentalData(
            symbol = "TEST.NS",
            returnOnEquity = 0.15,
            debtToEquity = 1.0,        // Exactly at threshold
            marketCap = 50000L,
            trailingPE = 20.0,
            bookValue = 100.0
        )
        assertTrue("Debt/Equity of exactly 1.0 should pass (<=)", data.meetsQualityThreshold())
    }

    @Test
    fun `FundamentalData with extreme ROE value`() {
        val data = FundamentalData(
            symbol = "TEST.NS",
            returnOnEquity = Double.MAX_VALUE,
            debtToEquity = 0.5,
            marketCap = 50000L,
            trailingPE = 20.0,
            bookValue = 100.0
        )
        assertTrue("Extreme ROE (MAX_VALUE) should pass quality filter", data.meetsQualityThreshold())
    }

    @Test
    fun `FundamentalData with negative ROE`() {
        val data = FundamentalData(
            symbol = "TEST.NS",
            returnOnEquity = -0.05,    // Negative ROE (loss-making)
            debtToEquity = 0.5,
            marketCap = 50000L,
            trailingPE = 20.0,
            bookValue = 100.0
        )
        assertFalse("Negative ROE should fail quality filter", data.meetsQualityThreshold())
    }

    @Test
    fun `FundamentalData with zero debt`() {
        val data = FundamentalData(
            symbol = "TEST.NS",
            returnOnEquity = 0.15,
            debtToEquity = 0.0,        // Debt-free
            marketCap = 50000L,
            trailingPE = 20.0,
            bookValue = 100.0
        )
        assertTrue("Zero debt should pass", data.meetsQualityThreshold())
    }

    // =====================================================================
    // Simulation Status Transitions
    // =====================================================================

    @Test
    fun `Simulation created with CREATED status`() {
        val sim = Simulation(
            name = "Test",
            initialAmount = 100000.0,
            currentAmount = 100000.0,
            durationMonths = 6,
            startDate = System.currentTimeMillis(),
            targetReturnPercentage = 12.0
        )
        assertEquals(SimulationStatus.CREATED, sim.status)
    }

    @Test
    fun `SimulationStatus enum contains all expected values`() {
        val statuses = SimulationStatus.values()
        assertTrue("CREATED should exist", statuses.contains(SimulationStatus.CREATED))
        assertTrue("ANALYZING should exist", statuses.contains(SimulationStatus.ANALYZING))
        assertTrue("ANALYSIS_COMPLETE should exist", statuses.contains(SimulationStatus.ANALYSIS_COMPLETE))
        assertTrue("ACTIVE should exist", statuses.contains(SimulationStatus.ACTIVE))
        assertTrue("COMPLETED should exist", statuses.contains(SimulationStatus.COMPLETED))
        assertTrue("FAILED should exist", statuses.contains(SimulationStatus.FAILED))
        assertEquals("Should have exactly 6 statuses", 6, statuses.size)
    }

    @Test
    fun `Simulation defaults totalEquity to currentAmount`() {
        val sim = Simulation(
            name = "Test",
            initialAmount = 100000.0,
            currentAmount = 50000.0,
            durationMonths = 6,
            startDate = System.currentTimeMillis(),
            targetReturnPercentage = 12.0
        )
        assertEquals("totalEquity should default to currentAmount", 50000.0, sim.totalEquity, 0.001)
    }

    // =====================================================================
    // BacktestResult Invariants
    // =====================================================================

    @Test
    fun `BacktestResult field ranges are valid`() {
        val result = BacktestResult(
            strategyId = "TEST",
            strategyName = "Test Strategy",
            description = "Test desc",
            returnPct = 15.0,
            winRate = 0.65,
            finalValue = 115000.0,
            benchmarkReturn = 10.0,
            alpha = 5.0,
            maxDrawdown = 0.12,
            sharpeRatio = 1.5,
            totalTrades = 42
        )

        assertTrue("Win rate should be in [0,1]", result.winRate in 0.0..1.0)
        assertTrue("Max drawdown should be >= 0", result.maxDrawdown >= 0)
        assertTrue("Total trades should be >= 0", result.totalTrades >= 0)
        assertTrue("Final value should be positive", result.finalValue > 0)
        assertEquals("Alpha = return - benchmark", 5.0, result.alpha, 0.01)
    }

    @Test
    fun `BacktestResult with zero trades is valid`() {
        val result = BacktestResult(
            strategyId = "NOOP",
            strategyName = "No Trading",
            description = "Does nothing",
            returnPct = 0.0,
            winRate = 0.0,
            finalValue = 100000.0,
            benchmarkReturn = 0.0,
            alpha = 0.0,
            totalTrades = 0
        )
        assertEquals(0, result.totalTrades)
        assertEquals(0.0, result.returnPct, 0.001)
    }

    @Test
    fun `BacktestResult with negative return is valid`() {
        val result = BacktestResult(
            strategyId = "LOSER",
            strategyName = "Losing Strategy",
            description = "Loses money",
            returnPct = -20.0,
            winRate = 0.3,
            finalValue = 80000.0,
            benchmarkReturn = 10.0,
            alpha = -30.0
        )
        assertTrue("Negative return is valid", result.returnPct < 0)
        assertTrue("Negative alpha is valid", result.alpha < 0)
    }

    // =====================================================================
    // Empty/Null Data Handling
    // =====================================================================

    @Test
    fun `empty StockQuote list does not crash basic operations`() {
        val emptyList = emptyList<StockQuote>()
        assertEquals(0, emptyList.size)
        assertTrue(emptyList.isEmpty())
    }

    @Test
    fun `StockQuote list sorting by date`() {
        val quotes = listOf(
            StockQuote("A.NS", 1704240000000L, 100.0, 105.0, 95.0, 102.0, 1000),
            StockQuote("A.NS", 1704067200000L, 100.0, 105.0, 95.0, 100.0, 1000),
            StockQuote("A.NS", 1704153600000L, 100.0, 105.0, 95.0, 101.0, 1000)
        )

        val sorted = quotes.sortedBy { it.date }
        assertEquals("First date should be earliest", 1704067200000L, sorted.first().date)
        assertEquals("Last date should be latest", 1704240000000L, sorted.last().date)
    }
}
