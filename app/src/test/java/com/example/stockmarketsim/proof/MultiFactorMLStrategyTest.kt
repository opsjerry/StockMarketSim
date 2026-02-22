package com.example.stockmarketsim.proof

import com.example.stockmarketsim.data.remote.IndianApiSource
import com.example.stockmarketsim.domain.ml.IStockPriceForecaster
import com.example.stockmarketsim.domain.model.StockQuote
import com.example.stockmarketsim.domain.strategy.MultiFactorMLStrategy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * REGRESSION SUITE: MultiFactorMLStrategy
 *
 * Tests breakeven threshold, top-10 selection, equal-weight allocation,
 * NaN handling, and insufficient data guards.
 */
class MultiFactorMLStrategyTest {

    private val baseDate = 1704067200000L // 1 Jan 2024

    /**
     * Creates a mock forecaster that returns a fixed value for all predictions.
     */
    private fun createMockForecaster(fixedReturn: Float): IStockPriceForecaster {
        return object : IStockPriceForecaster {
            override fun initialize() {}
            override fun predict(features: DoubleArray, symbol: String?, date: Long?): Float = fixedReturn
            override fun getModelVersion(): Int = 1
            override fun getExpectedFeatureCount(): Int = 60
        }
    }

    /**
     * Creates a mock forecaster that returns NaN for all predictions.
     */
    private fun createNaNForecaster(): IStockPriceForecaster {
        return object : IStockPriceForecaster {
            override fun initialize() {}
            override fun predict(features: DoubleArray, symbol: String?, date: Long?): Float = Float.NaN
            override fun getModelVersion(): Int = 1
            override fun getExpectedFeatureCount(): Int = 60
        }
    }

    /**
     * Creates a mock forecaster that returns different values per symbol.
     */
    private fun createVariableForecaster(symbolReturns: Map<String, Float>): IStockPriceForecaster {
        return object : IStockPriceForecaster {
            override fun initialize() {}
            override fun predict(features: DoubleArray, symbol: String?, date: Long?): Float {
                return symbolReturns[symbol] ?: Float.NaN
            }
            override fun getModelVersion(): Int = 1
            override fun getExpectedFeatureCount(): Int = 60
        }
    }

    /**
     * Generates a price history with enough data for the strategy (>61 days).
     */
    private fun generateHistory(days: Int = 70, startPrice: Double = 100.0): List<StockQuote> {
        return (0 until days).map { i ->
            val price = startPrice + i * 0.5
            StockQuote("TEST", baseDate + i * 86400000L, price, price + 1.0, price - 1.0, price, 5000)
        }
    }

    /**
     * Creates a mock IndianApiSource — we can't easily construct one without
     * SettingsManager, so we use a stub that extends it.
     * For testing, we pass null and the strategy only uses it for logging context.
     */
    private fun createMockApiSource(): IndianApiSource {
        return org.mockito.Mockito.mock(IndianApiSource::class.java).also { mock ->
            runBlocking {
                org.mockito.Mockito.`when`(
                    mock.getFundamentals(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any()
                    )
                ).thenReturn(com.example.stockmarketsim.data.remote.IndianApiFundamentals())
            }
        }
    }

    // =====================================================================
    // Breakeven Threshold Tests
    // =====================================================================

    @Test
    fun `rejects trades below 0_4 percent breakeven threshold`() = runBlocking {
        // 0.3% predicted return — should be rejected (below 0.4% threshold)
        val forecaster = createMockForecaster(0.003f)
        val apiSource = createMockApiSource()
        val strategy = MultiFactorMLStrategy(forecaster, apiSource)
        
        val history = generateHistory(70)
        val marketData = mapOf("TEST.NS" to history)
        val cursors = mapOf("TEST.NS" to history.lastIndex)
        
        val allocations = strategy.calculateallocation(listOf("TEST.NS"), marketData, cursors)
        
        assertTrue("0.3% return should be rejected (below 0.4% threshold)", allocations.isEmpty())
    }

    @Test
    fun `accepts trades above 0_4 percent breakeven threshold`() = runBlocking {
        // 0.5% predicted return — should be accepted (above 0.4% threshold)
        val forecaster = createMockForecaster(0.005f)
        val apiSource = createMockApiSource()
        val strategy = MultiFactorMLStrategy(forecaster, apiSource)
        
        val history = generateHistory(70)
        val marketData = mapOf("TEST.NS" to history)
        val cursors = mapOf("TEST.NS" to history.lastIndex)
        
        val allocations = strategy.calculateallocation(listOf("TEST.NS"), marketData, cursors)
        
        assertFalse("0.5% return should be accepted (above 0.4% threshold)", allocations.isEmpty())
        assertTrue("TEST.NS should be allocated", allocations.containsKey("TEST.NS"))
    }

    // =====================================================================
    // Top-10 Selection & Equal Weighting
    // =====================================================================

    @Test
    fun `selects at most top 10 stocks`() = runBlocking {
        // Create 15 stocks, all with high enough predicted returns
        val forecaster = createMockForecaster(0.01f) // 1% — well above threshold
        val apiSource = createMockApiSource()
        val strategy = MultiFactorMLStrategy(forecaster, apiSource)
        
        val marketData = mutableMapOf<String, List<StockQuote>>()
        val candidates = mutableListOf<String>()
        val cursors = mutableMapOf<String, Int>()
        
        for (i in 1..15) {
            val symbol = "STOCK$i.NS"
            val history = generateHistory(70, startPrice = 100.0 + i)
            candidates.add(symbol)
            marketData[symbol] = history
            cursors[symbol] = history.lastIndex
        }
        
        val allocations = strategy.calculateallocation(candidates, marketData, cursors)
        
        assertTrue("Should select at most 10 stocks", allocations.size <= 10)
    }

    @Test
    fun `equal weight allocation for selected stocks`() = runBlocking {
        val forecaster = createMockForecaster(0.01f)
        val apiSource = createMockApiSource()
        val strategy = MultiFactorMLStrategy(forecaster, apiSource)
        
        val history1 = generateHistory(70, 100.0)
        val history2 = generateHistory(70, 110.0)
        val marketData = mapOf("A.NS" to history1, "B.NS" to history2)
        val cursors = mapOf("A.NS" to history1.lastIndex, "B.NS" to history2.lastIndex)
        
        val allocations = strategy.calculateallocation(listOf("A.NS", "B.NS"), marketData, cursors)
        
        if (allocations.size == 2) {
            val expectedWeight = 1.0 / 2.0
            assertEquals("Each stock should get equal weight", expectedWeight, allocations["A.NS"]!!, 0.001)
            assertEquals("Each stock should get equal weight", expectedWeight, allocations["B.NS"]!!, 0.001)
        }
    }

    // =====================================================================
    // NaN & Edge Case Handling
    // =====================================================================

    @Test
    fun `NaN predictions are excluded`() = runBlocking {
        val forecaster = createNaNForecaster()
        val apiSource = createMockApiSource()
        val strategy = MultiFactorMLStrategy(forecaster, apiSource)
        
        val history = generateHistory(70)
        val marketData = mapOf("TEST.NS" to history)
        val cursors = mapOf("TEST.NS" to history.lastIndex)
        
        val allocations = strategy.calculateallocation(listOf("TEST.NS"), marketData, cursors)
        
        assertTrue("NaN predictions should result in empty allocations", allocations.isEmpty())
    }

    @Test
    fun `insufficient data guard rejects stocks with less than 61 days`() = runBlocking {
        val forecaster = createMockForecaster(0.01f)
        val apiSource = createMockApiSource()
        val strategy = MultiFactorMLStrategy(forecaster, apiSource)
        
        // Only 50 days — need at least 61 for 60 log returns
        val shortHistory = generateHistory(50)
        val marketData = mapOf("TEST.NS" to shortHistory)
        val cursors = mapOf("TEST.NS" to shortHistory.lastIndex)
        
        val allocations = strategy.calculateallocation(listOf("TEST.NS"), marketData, cursors)
        
        assertTrue("Stocks with <61 data points should be excluded", allocations.isEmpty())
    }

    @Test
    fun `empty candidates returns empty map`() = runBlocking {
        val forecaster = createMockForecaster(0.01f)
        val apiSource = createMockApiSource()
        val strategy = MultiFactorMLStrategy(forecaster, apiSource)
        
        val allocations = strategy.calculateallocation(emptyList(), emptyMap(), emptyMap())
        
        assertTrue("No candidates should return empty allocations", allocations.isEmpty())
    }
}
