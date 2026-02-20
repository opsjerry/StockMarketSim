package com.example.stockmarketsim.proof

import com.example.stockmarketsim.domain.analysis.RiskEngine
import com.example.stockmarketsim.domain.model.StockQuote
import com.example.stockmarketsim.domain.strategy.StrategySignal
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * REGRESSION SUITE: Risk Engine
 *
 * Tests ATR calculation, stop-loss enforcement, volatility detection,
 * and position sizing under various market conditions.
 */
class RiskEngineRegressionTest {

    // =====================================================================
    // ATR Calculation
    // =====================================================================

    @Test
    fun `ATR calculation with known values`() {
        // Create 16 days of data with known high/low/close for ATR(14)
        val history = mutableListOf<StockQuote>()
        val baseDate = 1704067200000L

        // Day 0: seed
        history.add(StockQuote("TEST", baseDate, 100.0, 102.0, 98.0, 101.0, 1000))

        // Days 1–15: predictable pattern (high=close+2, low=close-2)
        for (i in 1..15) {
            val close = 100.0 + i
            history.add(StockQuote("TEST", baseDate + i * 86400000L, close, close + 2.0, close - 2.0, close, 1000))
        }

        val atr = RiskEngine.calculateATR(history, 14)
        // True Range for each day: max(H-L, |H-prevC|, |L-prevC|) = max(4.0, ...) = 4.0
        // ATR = average of last 14 TRs ≈ 4.0
        assertTrue("ATR should be approximately 4.0", abs(atr - 4.0) < 0.5)
        assertTrue("ATR should be positive", atr > 0)
    }

    @Test
    fun `ATR returns zero with insufficient data`() {
        val history = listOf(
            StockQuote("TEST", 1704067200000L, 100.0, 102.0, 98.0, 100.0, 1000)
        )
        val atr = RiskEngine.calculateATR(history, 14)
        assertEquals("ATR with 1 data point should be 0.0", 0.0, atr, 0.001)
    }

    @Test
    fun `ATR returns zero with exactly period data points`() {
        // Need period+1 points minimum; with exactly period, should return 0
        val history = (0 until 14).map { i ->
            StockQuote("TEST", 1704067200000L + i * 86400000L, 100.0, 102.0, 98.0, 100.0, 1000)
        }
        val atr = RiskEngine.calculateATR(history, 14)
        assertEquals("ATR with exactly period points should be 0.0", 0.0, atr, 0.001)
    }

    @Test
    fun `ATR with empty history returns zero`() {
        val atr = RiskEngine.calculateATR(emptyList(), 14)
        assertEquals(0.0, atr, 0.001)
    }

    // =====================================================================
    // ATR Stop-Loss Price
    // =====================================================================

    @Test
    fun `stop-loss never allows more than 7 percent drop`() {
        // Even with very high ATR, stop should not exceed 7% from peak
        val peakPrice = 1000.0
        val veryHighATR = 200.0  // Would suggest stop at 1000 - 200*2 = 600 (40% drop)

        val stopPrice = RiskEngine.calculateATRStopPrice(peakPrice, veryHighATR, 2.0, false)
        val maxDrop = peakPrice * 0.93  // 7% floor

        assertTrue("Stop price should respect 7% hard floor", stopPrice >= maxDrop)
        assertEquals("Stop price should equal 7% floor when ATR is extreme", maxDrop, stopPrice, 0.01)
    }

    @Test
    fun `stop-loss with zero ATR uses 7 percent failsafe`() {
        val peakPrice = 500.0
        val stopPrice = RiskEngine.calculateATRStopPrice(peakPrice, 0.0, 2.0, false)
        assertEquals("Zero ATR should use 7% failsafe", 500.0 * 0.93, stopPrice, 0.01)
    }

    @Test
    fun `stop-loss with negative ATR uses 7 percent failsafe`() {
        val peakPrice = 500.0
        val stopPrice = RiskEngine.calculateATRStopPrice(peakPrice, -5.0, 2.0, false)
        assertEquals("Negative ATR should use 7% failsafe", 500.0 * 0.93, stopPrice, 0.01)
    }

    @Test
    fun `volatile stock gets tighter stop`() {
        val peakPrice = 1000.0
        val atr = 20.0

        val normalStop = RiskEngine.calculateATRStopPrice(peakPrice, atr, 2.0, false)
        val volatileStop = RiskEngine.calculateATRStopPrice(peakPrice, atr, 2.0, true)

        // Volatile uses 1.5x ATR vs normal 2.0x ATR
        assertTrue("Volatile stop should be tighter (higher price)", volatileStop > normalStop)
        assertEquals("Normal stop: peak - 2.0*ATR", 1000.0 - 2.0 * 20.0, normalStop, 0.01)
        assertEquals("Volatile stop: peak - 1.5*ATR", 1000.0 - 1.5 * 20.0, volatileStop, 0.01)
    }

    @Test
    fun `stop-loss with small ATR stays within 7 percent`() {
        val peakPrice = 100.0
        val smallATR = 2.0

        val stopPrice = RiskEngine.calculateATRStopPrice(peakPrice, smallATR, 2.0, false)
        // 100 - 2*2 = 96 → 4% drop, within 7% limit
        assertEquals(96.0, stopPrice, 0.01)
        assertTrue("Small ATR stop should be above 7% floor", stopPrice > peakPrice * 0.93)
    }

    // =====================================================================
    // Volatility Detection
    // =====================================================================

    @Test
    fun `volatile detection with calm market returns false`() {
        // 5 days of <1% moves
        val history = (0..5).map { i ->
            val price = 100.0 + i * 0.5  // 0.5% daily moves
            StockQuote("TEST", 1704067200000L + i * 86400000L, price, price + 0.1, price - 0.1, price, 1000)
        }
        assertFalse("Calm market should not be volatile", RiskEngine.isVolatile(history))
    }

    @Test
    fun `volatile detection with wild swings returns true`() {
        val baseDate = 1704067200000L
        val history = listOf(
            StockQuote("TEST", baseDate, 100.0, 101.0, 99.0, 100.0, 1000),
            StockQuote("TEST", baseDate + 86400000L, 100.0, 105.0, 95.0, 105.0, 1000),   // +5%
            StockQuote("TEST", baseDate + 2 * 86400000L, 105.0, 106.0, 99.0, 100.0, 1000), // -4.76%
            StockQuote("TEST", baseDate + 3 * 86400000L, 100.0, 104.0, 96.0, 104.0, 1000), // +4%
            StockQuote("TEST", baseDate + 4 * 86400000L, 104.0, 106.0, 100.0, 101.0, 1000), // -2.88%
            StockQuote("TEST", baseDate + 5 * 86400000L, 101.0, 102.0, 100.0, 101.5, 1000)
        )
        assertTrue("Wild swings should be detected as volatile", RiskEngine.isVolatile(history))
    }

    @Test
    fun `volatile detection with insufficient data returns false`() {
        val history = listOf(
            StockQuote("TEST", 1704067200000L, 100.0, 102.0, 98.0, 100.0, 1000)
        )
        assertFalse("Insufficient data should not be volatile", RiskEngine.isVolatile(history))
    }

    @Test
    fun `volatile detection with empty history returns false`() {
        assertFalse("Empty history should not be volatile", RiskEngine.isVolatile(emptyList()))
    }

    @Test
    fun `volatile detection with exactly 1 choppy day returns false`() {
        // Only 1 day >2% (need ≥2 for volatile)
        val baseDate = 1704067200000L
        val history = listOf(
            StockQuote("TEST", baseDate, 100.0, 101.0, 99.0, 100.0, 1000),
            StockQuote("TEST", baseDate + 86400000L, 100.0, 105.0, 95.0, 103.0, 1000),   // +3%
            StockQuote("TEST", baseDate + 2 * 86400000L, 103.0, 104.0, 102.0, 103.5, 1000), // +0.5%
            StockQuote("TEST", baseDate + 3 * 86400000L, 103.5, 105.0, 102.0, 103.0, 1000), // -0.5%
            StockQuote("TEST", baseDate + 4 * 86400000L, 103.0, 104.0, 102.0, 103.2, 1000)  // +0.2%
        )
        assertFalse("Only 1 choppy day should not be volatile", RiskEngine.isVolatile(history))
    }

    // =====================================================================
    // Position Sizing / Risk Management
    // =====================================================================

    @Test
    fun `applyRiskManagement filters to BUY signals only`() {
        val signals = listOf(
            StrategySignal("A.NS", "BUY", 1.0),
            StrategySignal("B.NS", "SELL", 1.0),
            StrategySignal("C.NS", "HOLD", 1.0),
            StrategySignal("D.NS", "BUY", 1.0)
        )
        val allocations = RiskEngine.applyRiskManagement(signals, 100000.0, false)

        assertTrue("BUY signal A.NS should be allocated", allocations.containsKey("A.NS"))
        assertTrue("BUY signal D.NS should be allocated", allocations.containsKey("D.NS"))
        assertFalse("SELL signal B.NS should not be allocated", allocations.containsKey("B.NS"))
        assertFalse("HOLD signal C.NS should not be allocated", allocations.containsKey("C.NS"))
    }

    @Test
    fun `applyRiskManagement in bear market uses smaller allocations`() {
        val signals = listOf(StrategySignal("A.NS", "BUY", 1.0))

        val bullAlloc = RiskEngine.applyRiskManagement(signals, 100000.0, false)
        val bearAlloc = RiskEngine.applyRiskManagement(signals, 100000.0, true)

        val bullWeight = bullAlloc["A.NS"] ?: 0.0
        val bearWeight = bearAlloc["A.NS"] ?: 0.0

        assertTrue("Bull market allocation should be larger", bullWeight > bearWeight)
    }

    @Test
    fun `applyRiskManagement with no BUY signals returns empty`() {
        val signals = listOf(
            StrategySignal("A.NS", "SELL", 1.0),
            StrategySignal("B.NS", "HOLD", 1.0)
        )
        val allocations = RiskEngine.applyRiskManagement(signals, 100000.0, false)
        assertTrue("No BUY signals should return empty allocations", allocations.isEmpty())
    }

    @Test
    fun `applyRiskManagement respects max total exposure`() {
        // Create many BUY signals to test exposure cap
        val signals = (1..50).map { StrategySignal("STOCK$it.NS", "BUY", 1.0) }
        val allocations = RiskEngine.applyRiskManagement(signals, 100000.0, false)

        val totalExposure = allocations.values.sum()
        assertTrue("Total exposure in bull market should not exceed 1.0", totalExposure <= 1.01)
    }

    @Test
    fun `applyRiskManagement respects max exposure in bear market`() {
        val signals = (1..50).map { StrategySignal("STOCK$it.NS", "BUY", 1.0) }
        val allocations = RiskEngine.applyRiskManagement(signals, 100000.0, true)

        val totalExposure = allocations.values.sum()
        assertTrue("Total exposure in bear market should not exceed 0.50", totalExposure <= 0.51)
    }

    @Test
    fun `applyRiskManagement with empty signals returns empty`() {
        val allocations = RiskEngine.applyRiskManagement(emptyList(), 100000.0, false)
        assertTrue("Empty signals should return empty allocations", allocations.isEmpty())
    }
}
