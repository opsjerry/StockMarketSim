package com.example.stockmarketsim.proof

import com.example.stockmarketsim.domain.model.StockQuote
import com.example.stockmarketsim.domain.strategy.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * REGRESSION SUITE: Strategy Signal Correctness
 *
 * Tests that each strategy produces correct BUY/SELL/HOLD signals
 * for known market conditions, and gracefully handles insufficient data.
 */
class StrategyRegressionTest {

    private val baseDate = 1704067200000L

    /**
     * Generates a steadily RISING price history (bullish).
     */
    private fun generateBullishHistory(days: Int = 50, startPrice: Double = 100.0): List<StockQuote> {
        return (0 until days).map { i ->
            val price = startPrice + i * 2.0
            StockQuote("TEST", baseDate + i * 86400000L, price, price + 1.0, price - 1.0, price, 5000)
        }
    }

    /**
     * Generates a steadily FALLING price history (bearish).
     */
    private fun generateBearishHistory(days: Int = 50, startPrice: Double = 200.0): List<StockQuote> {
        return (0 until days).map { i ->
            val price = startPrice - i * 2.0
            StockQuote("TEST", baseDate + i * 86400000L, price, price + 1.0, price - 1.0, price, 5000)
        }
    }

    /**
     * Generates a FLAT price history.
     */
    private fun generateFlatHistory(days: Int = 50, price: Double = 100.0): List<StockQuote> {
        return (0 until days).map { i ->
            StockQuote("TEST", baseDate + i * 86400000L, price, price + 0.5, price - 0.5, price, 5000)
        }
    }

    // =====================================================================
    // MomentumStrategy (SMA 20)
    // =====================================================================

    @Test
    fun `momentum BUY when price above SMA 20`() = runBlocking {
        val strategy = MomentumStrategy()
        val history = generateBullishHistory(30)
        val signal = strategy.getSignal("TEST", history, history.size - 1)
        assertEquals("Should BUY in uptrend", TradeSignal.BUY, signal)
    }

    @Test
    fun `momentum SELL when price below SMA 20`() = runBlocking {
        val strategy = MomentumStrategy()
        val history = generateBearishHistory(30)
        val signal = strategy.getSignal("TEST", history, history.size - 1)
        assertEquals("Should SELL in downtrend", TradeSignal.SELL, signal)
    }

    @Test
    fun `momentum HOLD with insufficient data`() = runBlocking {
        val strategy = MomentumStrategy()
        val history = generateFlatHistory(10)  // Only 10 days, need 20
        val signal = strategy.getSignal("TEST", history, history.size - 1)
        assertEquals("Insufficient data should HOLD", TradeSignal.HOLD, signal)
    }

    // =====================================================================
    // ConfigurableMomentumStrategy
    // =====================================================================

    @Test
    fun `configurable momentum with SMA 50 needs 50 days`() = runBlocking {
        val strategy = ConfigurableMomentumStrategy(50)
        val shortHistory = generateFlatHistory(30)
        val signal = strategy.getSignal("TEST", shortHistory, shortHistory.size - 1)
        assertEquals("SMA 50 with 30 days should HOLD", TradeSignal.HOLD, signal)
    }

    @Test
    fun `configurable momentum BUY with sufficient uptrend`() = runBlocking {
        val strategy = ConfigurableMomentumStrategy(20)
        val history = generateBullishHistory(50)
        val signal = strategy.getSignal("TEST", history, history.size - 1)
        assertEquals("Should BUY with price above SMA 20", TradeSignal.BUY, signal)
    }

    // =====================================================================
    // SafeHavenStrategy
    // =====================================================================

    @Test
    fun `safe haven BUY for very low volatility stock`() = runBlocking {
        val strategy = SafeHavenStrategy()
        // Generate a very stable stock (minimal price movement)
        val stableHistory = (0 until 30).map { i ->
            val price = 100.0 + Math.sin(i * 0.01) * 0.1  // Tiny tiny movement
            StockQuote("STABLE", baseDate + i * 86400000L, price, price + 0.01, price - 0.01, price, 5000)
        }
        val signal = strategy.getSignal("STABLE", stableHistory, stableHistory.size - 1)
        assertEquals("Very stable stock should get BUY", TradeSignal.BUY, signal)
    }

    @Test
    fun `safe haven HOLD for volatile stock`() = runBlocking {
        val strategy = SafeHavenStrategy()
        // Wild swings
        val volatileHistory = (0 until 30).map { i ->
            val price = 100.0 + if (i % 2 == 0) 20.0 else -20.0
            StockQuote("WILD", baseDate + i * 86400000L, price, price + 10, price - 10, price, 5000)
        }
        val signal = strategy.getSignal("WILD", volatileHistory, volatileHistory.size - 1)
        assertEquals("Volatile stock should get HOLD", TradeSignal.HOLD, signal)
    }

    @Test
    fun `safe haven HOLD with insufficient data`() = runBlocking {
        val strategy = SafeHavenStrategy()
        val shortHistory = generateFlatHistory(5)
        val signal = strategy.getSignal("TEST", shortHistory, shortHistory.size - 1)
        assertEquals("Insufficient data should HOLD", TradeSignal.HOLD, signal)
    }

    // =====================================================================
    // BollingerBreakoutStrategy
    // =====================================================================

    @Test
    fun `bollinger breakout BUY on massive price spike`() = runBlocking {
        val strategy = BollingerBreakoutStrategy(period = 20, stdDevMultiplier = 2.0)
        val history = generateFlatHistory(25)

        // Spike last price well above upper band
        val spiked = history.dropLast(1) + history.last().copy(close = 200.0, volume = 10000)
        val signal = strategy.getSignal("TEST", spiked, spiked.size - 1)
        assertEquals("Price spike above upper Bollinger should BUY", TradeSignal.BUY, signal)
    }

    @Test
    fun `bollinger breakout HOLD within bands`() = runBlocking {
        val strategy = BollingerBreakoutStrategy(period = 20, stdDevMultiplier = 2.0)
        val history = generateFlatHistory(25)
        val signal = strategy.getSignal("TEST", history, history.size - 1)
        // With flat price, close should be within bands
        assertTrue("Flat price should not trigger breakout BUY",
            signal == TradeSignal.HOLD || signal == TradeSignal.SELL)
    }

    @Test
    fun `bollinger breakout HOLD with insufficient data`() = runBlocking {
        val strategy = BollingerBreakoutStrategy(period = 20, stdDevMultiplier = 2.0)
        val shortHistory = generateFlatHistory(10)
        val signal = strategy.getSignal("TEST", shortHistory, shortHistory.size - 1)
        assertEquals("Insufficient data should HOLD", TradeSignal.HOLD, signal)
    }

    // =====================================================================
    // BollingerMeanReversionStrategy
    // =====================================================================

    @Test
    fun `bollinger reversion BUY below lower band`() = runBlocking {
        val strategy = BollingerMeanReversionStrategy(period = 20, stdDevMultiplier = 2.0)
        val history = generateFlatHistory(25)

        // Drop last price well below lower band
        val dipped = history.dropLast(1) + history.last().copy(close = 50.0)
        val signal = strategy.getSignal("TEST", dipped, dipped.size - 1)
        assertEquals("Price dip below lower Bollinger should BUY", TradeSignal.BUY, signal)
    }

    // =====================================================================
    // RsiStrategy
    // =====================================================================

    @Test
    fun `RSI HOLD with insufficient data`() = runBlocking {
        val strategy = RsiStrategy(period = 14)
        val shortHistory = generateFlatHistory(5)
        val signal = strategy.getSignal("TEST", shortHistory, shortHistory.size - 1)
        assertEquals("Insufficient data should HOLD", TradeSignal.HOLD, signal)
    }

    @Test
    fun `RSI strategy processes sufficient data without crash`() = runBlocking {
        val strategy = RsiStrategy(period = 14)
        val history = generateBullishHistory(50)
        val signal = strategy.getSignal("TEST", history, history.size - 1)
        assertNotNull("RSI strategy should return a signal", signal)
    }

    // =====================================================================
    // MacdStrategy
    // =====================================================================

    @Test
    fun `MACD HOLD with insufficient data`() = runBlocking {
        val strategy = MacdStrategy()
        val shortHistory = generateFlatHistory(10)
        val signal = strategy.getSignal("TEST", shortHistory, shortHistory.size - 1)
        assertEquals("MACD with insufficient data should HOLD", TradeSignal.HOLD, signal)
    }

    @Test
    fun `MACD processes long history without crash`() = runBlocking {
        val strategy = MacdStrategy()
        val history = generateBullishHistory(60)
        val signal = strategy.getSignal("TEST", history, history.size - 1)
        assertNotNull("MACD should return a valid signal", signal)
    }

    // =====================================================================
    // HybridMomentumRsiStrategy
    // =====================================================================

    @Test
    fun `hybrid strategy HOLD with insufficient data`() = runBlocking {
        val strategy = HybridMomentumRsiStrategy(smaPeriod = 50)
        val shortHistory = generateFlatHistory(20)
        val signal = strategy.getSignal("TEST", shortHistory, shortHistory.size - 1)
        assertEquals("Hybrid with insufficient data should HOLD", TradeSignal.HOLD, signal)
    }

    @Test
    fun `hybrid strategy processes bullish data`() = runBlocking {
        val strategy = HybridMomentumRsiStrategy(smaPeriod = 50)
        val history = generateBullishHistory(80)
        val signal = strategy.getSignal("TEST", history, history.size - 1)
        assertNotNull("Hybrid strategy should return a signal", signal)
    }

    // =====================================================================
    // VptStrategy
    // =====================================================================

    @Test
    fun `VPT HOLD with insufficient data`() = runBlocking {
        val strategy = VptStrategy(smaPeriod = 20)
        val shortHistory = generateFlatHistory(10)
        val signal = strategy.getSignal("TEST", shortHistory, shortHistory.size - 1)
        assertEquals("VPT with insufficient data should HOLD", TradeSignal.HOLD, signal)
    }

    // =====================================================================
    // YearlyHighBreakoutStrategy
    // =====================================================================

    @Test
    fun `yearly high breakout HOLD with insufficient data`() = runBlocking {
        val strategy = YearlyHighBreakoutStrategy()
        val shortHistory = generateFlatHistory(50)
        val signal = strategy.getSignal("TEST", shortHistory, shortHistory.size - 1)
        // Needs 365 days of data typically
        assertEquals("YearlyHigh with insufficient data should HOLD", TradeSignal.HOLD, signal)
    }

    // =====================================================================
    // Allocation Tests
    // =====================================================================

    @Test
    fun `momentum calculateallocation returns valid weights`() = runBlocking {
        val strategy = MomentumStrategy()
        val marketData = mapOf(
            "A.NS" to generateBullishHistory(30),
            "B.NS" to generateBearishHistory(30)
        )
        val cursors = mapOf(
            "A.NS" to marketData["A.NS"]!!.lastIndex,
            "B.NS" to marketData["B.NS"]!!.lastIndex
        )

        val allocations = strategy.calculateallocation(listOf("A.NS", "B.NS"), marketData, cursors)

        for ((_, weight) in allocations) {
            assertTrue("Allocation weight should be >= 0", weight >= 0)
            assertTrue("Allocation weight should be <= 1", weight <= 1.0)
        }

        val totalWeight = allocations.values.sum()
        assertTrue("Total weight should be <= 1.0", totalWeight <= 1.01)
    }

    @Test
    fun `safe haven allocates inverse-vol weights`() = runBlocking {
        val strategy = SafeHavenStrategy()

        // Create stable stock and volatile stock
        val stableHistory = generateFlatHistory(30, 100.0)
        val volatileHistory = (0 until 30).map { i ->
            val price = 100.0 + if (i % 2 == 0) 10.0 else -10.0
            StockQuote("WILD", baseDate + i * 86400000L, price, price + 5, price - 5, price, 5000)
        }

        val marketData = mapOf(
            "STABLE.NS" to stableHistory.map { it.copy(symbol = "STABLE.NS") },
            "WILD.NS" to volatileHistory.map { it.copy(symbol = "WILD.NS") }
        )

        val cursors = mapOf(
            "STABLE.NS" to stableHistory.lastIndex,
            "WILD.NS" to volatileHistory.lastIndex
        )

        val allocations = strategy.calculateallocation(
            listOf("STABLE.NS", "WILD.NS"), marketData, cursors
        )

        if (allocations.containsKey("STABLE.NS") && allocations.containsKey("WILD.NS")) {
            assertTrue("Stable stock should have higher weight than volatile",
                allocations["STABLE.NS"]!! > allocations["WILD.NS"]!!)
        }
    }
}
