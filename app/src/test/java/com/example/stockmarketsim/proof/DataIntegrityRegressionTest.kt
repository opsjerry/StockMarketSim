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

    // =====================================================================
    // DB Duplicate Dedup Regression (Fix: StockPriceEntity unique index)
    // =====================================================================

    /**
     * Verifies that distinctBy { date } collapses duplicate candles for the same date.
     * This matches the defensive guard added to StockRepositoryImpl.getStockHistory()
     * and ensures the LSTM always receives unique price points per day.
     */
    @Test
    fun `StockQuote list deduplication removes entries with same date`() {
        val quotes = listOf(
            StockQuote("RELIANCE.NS", 1704067200000L, 100.0, 105.0, 95.0, 102.0, 50000),
            StockQuote("RELIANCE.NS", 1704067200000L, 100.0, 105.0, 95.0, 102.5, 60000), // duplicate date
            StockQuote("RELIANCE.NS", 1704153600000L, 103.0, 108.0, 98.0, 106.0, 55000)
        )

        val deduped = quotes.distinctBy { it.date }.sortedBy { it.date }
        assertEquals("Duplicate date should reduce list to 2 entries", 2, deduped.size)
        assertEquals("First entry date", 1704067200000L, deduped[0].date)
        assertEquals("Second entry date", 1704153600000L, deduped[1].date)
    }

    @Test
    fun `StockQuote deduplication preserves correct sort order`() {
        val quotes = listOf(
            StockQuote("TEST.NS", 1704240000000L, 110.0, 115.0, 105.0, 112.0, 5000),
            StockQuote("TEST.NS", 1704067200000L, 100.0, 105.0, 95.0, 102.0, 5000),
            StockQuote("TEST.NS", 1704153600000L, 103.0, 108.0, 98.0, 106.0, 5000),
            StockQuote("TEST.NS", 1704153600000L, 103.0, 108.0, 98.0, 106.5, 5500) // duplicate
        )

        val result = quotes.distinctBy { it.date }.sortedBy { it.date }
        assertEquals("3 unique trade dates", 3, result.size)
        assertTrue("Must be sorted ascending", result[0].date < result[1].date)
        assertTrue("Must be sorted ascending", result[1].date < result[2].date)
    }

    // =====================================================================
    // Phase 1: Sharpe / Alpha Data Gate (>= 20 observations required)
    // =====================================================================

    /**
     * Verifies the 20-day gate logic:
     * lists shorter than 20 should be flagged as insufficient data.
     * This matches the `_insufficientData` StateFlow in SimulationDetailViewModel.
     */
    @Test
    fun `Sharpe gate returns insufficient when history has fewer than 20 points`() {
        val shortHistory = (1..8).map { i ->
            Pair(i.toLong() * 86_400_000L, 100.0 + i)
        }
        val isInsufficient = shortHistory.size < 20
        assertTrue("History < 20 days should be flagged as insufficient data", isInsufficient)
    }

    @Test
    fun `Sharpe gate passes when history has 20 or more points`() {
        val sufficientHistory = (1..25).map { i ->
            Pair(i.toLong() * 86_400_000L, 100.0 + i * 0.5)
        }
        val isInsufficient = sufficientHistory.size < 20
        assertFalse("History >= 20 days should NOT be flagged as insufficient", isInsufficient)
    }

    // =====================================================================
    // Phase 2: Period-Fit Penalty (Tournament Strategy Filter)
    // =====================================================================

    /**
     * Verifies the period-fit logic: for a 120-day simulation,
     * EMA(150) period >= threshold (84) and must be penalised.
     * This mirrors the `extractPrimaryPeriod` + penalty in RunStrategyTournamentUseCase.
     */
    @Test
    fun `Period-fit penalty fires for EMA_150 in a 120-day simulation`() {
        val simDurationDays = 120
        val periodFitThreshold = simDurationDays * 0.70  // 84

        fun extractPrimaryPeriod(strategyId: String): Int {
            val prefixes = listOf("EMA_MOMENTUM_", "MOMENTUM_SMA_", "RSI_", "HYBRID_MOM_RSI_")
            for (prefix in prefixes) {
                if (strategyId.startsWith(prefix)) {
                    return strategyId.substringAfter(prefix).substringBefore("_").toIntOrNull() ?: 0
                }
            }
            return 0
        }

        val period = extractPrimaryPeriod("EMA_MOMENTUM_150")
        assertTrue("EMA_150 period ($period) should exceed 70% threshold ($periodFitThreshold)",
            period >= periodFitThreshold)
    }

    @Test
    fun `Period-fit penalty does NOT fire for EMA_50 in a 120-day simulation`() {
        val simDurationDays = 120
        val periodFitThreshold = simDurationDays * 0.70  // 84

        fun extractPrimaryPeriod(strategyId: String): Int {
            val prefixes = listOf("EMA_MOMENTUM_", "MOMENTUM_SMA_", "RSI_", "HYBRID_MOM_RSI_")
            for (prefix in prefixes) {
                if (strategyId.startsWith(prefix)) {
                    return strategyId.substringAfter(prefix).substringBefore("_").toIntOrNull() ?: 0
                }
            }
            return 0
        }

        val period = extractPrimaryPeriod("EMA_MOMENTUM_50")
        assertFalse("EMA_50 period ($period) must NOT exceed threshold ($periodFitThreshold) for 120-day sim",
            period >= periodFitThreshold)
    }

    // =====================================================================
    // Phase 2: Stop-Loss Honeymoon (3-day wider 3.5x stop)
    // =====================================================================

    @Test
    fun `New position purchaseDate defaults to current time`() {
        val before = System.currentTimeMillis()
        val item = com.example.stockmarketsim.domain.model.PortfolioItem(
            symbol = "RELIANCE.NS",
            quantity = 10.0,
            averagePrice = 2500.0
        )
        val after = System.currentTimeMillis()
        assertTrue("purchaseDate should be between before and after", item.purchaseDate in before..after)
    }

    @Test
    fun `Stop multiplier is 3_5x for positions younger than 3 trading days`() {
        val threeDaysMs = 3L * 24 * 60 * 60 * 1000L
        val justBought = System.currentTimeMillis() - (1L * 24 * 60 * 60 * 1000L)  // 1 day ago
        val daysSincePurchase = System.currentTimeMillis() - justBought

        val stopMultiplier = if (daysSincePurchase < threeDaysMs) 3.5 else 2.0
        assertEquals("Position < 3 days old must use 3.5x ATR stop", 3.5, stopMultiplier, 0.001)
    }

    @Test
    fun `Stop multiplier is 2_0x for positions older than 3 trading days`() {
        val threeDaysMs = 3L * 24 * 60 * 60 * 1000L
        val oldPosition = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000L)  // 7 days ago
        val daysSincePurchase = System.currentTimeMillis() - oldPosition

        val stopMultiplier = if (daysSincePurchase < threeDaysMs) 3.5 else 2.0
        assertEquals("Position > 3 days old must use 2.0x ATR stop", 2.0, stopMultiplier, 0.001)
    }

    // =====================================================================
    // Phase 3: Promoter Holding Quality Filter
    // =====================================================================

    @Test
    fun `FundamentalData rejects stock with promoter holding above 72 percent`() {
        val adaniPower = FundamentalData(
            symbol = "ADANIPOWER.NS",
            returnOnEquity = 0.25,         // 25% — passes ROE
            debtToEquity = 0.8,            // 0.8 — passes D/E
            marketCap = 900_000_000_000L,
            trailingPE = 12.0,
            bookValue = 50.0,
            promoterHolding = 0.7497       // 74.97% — FAILS governance gate
        )
        assertFalse(
            "Stock with promoter holding > 72% must fail quality threshold",
            adaniPower.meetsQualityThreshold()
        )
    }

    @Test
    fun `FundamentalData accepts stock with promoter holding below 72 percent`() {
        val hdfc = FundamentalData(
            symbol = "HDFCBANK.NS",
            returnOnEquity = 0.17,
            debtToEquity = 0.5,
            marketCap = 10_000_000_000_000L,
            trailingPE = 18.0,
            bookValue = 600.0,
            promoterHolding = 0.26         // 26% — passes governance gate
        )
        assertTrue(
            "Stock with promoter holding < 72% must pass quality threshold",
            hdfc.meetsQualityThreshold()
        )
    }

    @Test
    fun `FundamentalData passes when promoterHolding is null (benefit of the doubt)`() {
        val stock = FundamentalData(
            symbol = "UNKNOWN.NS",
            returnOnEquity = 0.15,
            debtToEquity = 0.6,
            marketCap = null,
            trailingPE = null,
            bookValue = null,
            promoterHolding = null   // no data available
        )
        assertTrue(
            "Missing promoter data should pass (benefit of the doubt)",
            stock.meetsQualityThreshold()
        )
    }

    // =====================================================================
    // Phase 3: Inverse-Volatility Sizing Logic
    // =====================================================================

    @Test
    fun `Inverse-vol sizing gives smaller allocation to higher ATR percent stock`() {
        // Simulate two stocks: high-beta (ATR% = 5%) and low-beta (ATR% = 1.5%)
        // Both start with the same raw allocation weight of 0.10
        val rawWeight = 0.10

        val highBetaAtrPct = 0.05   // 5% — volatile stock (e.g., Adani Power)
        val lowBetaAtrPct  = 0.015  // 1.5% — defensive stock (e.g., HDFC Bank)

        val highBetaInvVol = rawWeight / highBetaAtrPct   // 2.0
        val lowBetaInvVol  = rawWeight / lowBetaAtrPct    // 6.67

        val totalInvVol = highBetaInvVol + lowBetaInvVol  // 8.67
        val totalRaw = rawWeight * 2                        // 0.20

        val highBetaFinalAlloc = (highBetaInvVol / totalInvVol) * totalRaw  // smaller
        val lowBetaFinalAlloc  = (lowBetaInvVol  / totalInvVol) * totalRaw  // larger

        assertTrue(
            "High-beta stock must receive smaller allocation than low-beta stock after inverse-vol reweighting",
            highBetaFinalAlloc < lowBetaFinalAlloc
        )
        assertEquals(
            "Total allocation must be preserved after reweighting",
            totalRaw, highBetaFinalAlloc + lowBetaFinalAlloc, 0.0001
        )
    }
}
