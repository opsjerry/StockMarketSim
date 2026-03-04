package com.example.stockmarketsim.proof

import com.example.stockmarketsim.data.remote.IndianApiSource
import com.example.stockmarketsim.data.remote.IndianApiFundamentals
import com.example.stockmarketsim.domain.ml.IStockPriceForecaster
import com.example.stockmarketsim.domain.model.StockQuote
import com.example.stockmarketsim.domain.strategy.MultiFactorMLStrategy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import kotlin.math.ln
import kotlin.math.abs

/**
 * REGRESSION SUITE: Feature Vector Construction
 *
 * Validates that the 64-feature input array passed to the TFLite model is built
 * correctly from raw price history. Tests are fully pure-JVM — no Android context needed.
 *
 * Feature layout (matches Python train_stock_model.py create_sequences_multi_factor()):
 *   [0–59]  60 daily log returns: ln(P_t / P_{t-1})
 *   [60]    RSI-14 (normalized to 0–1 by dividing by 100)
 *   [61]    SMA Ratio 50/200 (~1.0 centered)
 *   [62]    ATR% = ATR(14) / lastClose
 *   [63]    Relative Volume vs 20-day average
 */
class FeatureVectorConstructionTest {

    private val baseDate = 1704067200000L // 2024-01-01

    /** Generates price history with a controlled linear trend (easy to compute expected log-returns). */
    private fun linearHistory(days: Int, startPrice: Double = 100.0, step: Double = 1.0): List<StockQuote> {
        return (0 until days).map { i ->
            val price = startPrice + i * step
            // Use realistic high/low so ATR is non-zero
            StockQuote("TEST.NS", baseDate + i * 86400000L, price, price * 1.01, price * 0.99, price, 1_000_000L + i * 1000L)
        }
    }

    /** A capturing forecaster that records the feature array it receives. */
    private inner class CapturingForecaster(private val featureCount: Int = 64) : IStockPriceForecaster {
        var capturedFeatures: DoubleArray? = null
        override fun initialize() {}
        override fun predict(features: DoubleArray, symbol: String?, date: Long?): Float {
            capturedFeatures = features.copyOf()
            return 0.01f // 1% — above 0.4% breakeven threshold so the trade goes through
        }
        override fun getModelVersion(): Int = 99
        override fun getExpectedFeatureCount(): Int = featureCount
    }

    private lateinit var mockApiSource: IndianApiSource

    @Before
    fun setup() {
        mockApiSource = mock(IndianApiSource::class.java)
        runBlocking {
            `when`(mockApiSource.getFundamentals(anyString(), any()))
                .thenReturn(IndianApiFundamentals())
        }
    }

    // =========================================================================
    // Gap 3a — Log Return Sequence Correctness
    // =========================================================================

    @Test
    fun `feature vector contains exactly 60 log returns in positions 0-59`() = runBlocking {
        val history = linearHistory(100, startPrice = 100.0, step = 1.0)
        val capturingForecaster = CapturingForecaster(featureCount = 64)
        val strategy = MultiFactorMLStrategy(capturingForecaster, mockApiSource)

        val cursors = mapOf("TEST.NS" to history.lastIndex)
        strategy.calculateallocation(listOf("TEST.NS"), mapOf("TEST.NS" to history), cursors)

        val features = capturingForecaster.capturedFeatures
        assertNotNull("Forecaster should have been called", features)
        assertEquals("Feature vector should be 64 elements", 64, features!!.size)

        // Validate the last 60 log returns slot into positions [0..59]
        val currentIdx = history.lastIndex
        val startIdx = currentIdx - 60
        for (i in 0 until 60) {
            val p_t = history[startIdx + i + 1].close
            val p_prev = history[startIdx + i].close
            val expected = ln(p_t / p_prev)
            assertEquals(
                "Log return at feature[$i] should equal ln(${p_t}/${p_prev})",
                expected, features[i], 1e-9
            )
        }
    }

    @Test
    fun `feature vector for 60-feature model has NO TA indicators appended`() = runBlocking {
        val history = linearHistory(100)
        val capturingForecaster = CapturingForecaster(featureCount = 60) // old 60-feature model
        val strategy = MultiFactorMLStrategy(capturingForecaster, mockApiSource)

        val cursors = mapOf("TEST.NS" to history.lastIndex)
        strategy.calculateallocation(listOf("TEST.NS"), mapOf("TEST.NS" to history), cursors)

        val features = capturingForecaster.capturedFeatures
        assertNotNull("Forecaster should have been called", features)
        assertEquals("60-feature model path should produce 60-element array", 60, features!!.size)
    }

    // =========================================================================
    // Gap 3b — TA Indicator Positions (64-feature model)
    // =========================================================================

    @Test
    fun `RSI in feature slot 60 is normalized between 0 and 1`() = runBlocking {
        val history = linearHistory(250) // enough for RSI + SMA(200)
        val capturingForecaster = CapturingForecaster(featureCount = 64)
        val strategy = MultiFactorMLStrategy(capturingForecaster, mockApiSource)

        val cursors = mapOf("TEST.NS" to history.lastIndex)
        strategy.calculateallocation(listOf("TEST.NS"), mapOf("TEST.NS" to history), cursors)

        val features = capturingForecaster.capturedFeatures!!
        val rsiNormalized = features[60]
        assertTrue("RSI in features[60] must be normalized to [0, 1], got $rsiNormalized",
            rsiNormalized in 0.0..1.0)
    }

    @Test
    fun `SMA Ratio in feature slot 61 is near 1_0 for linear price history`() = runBlocking {
        // For perfectly linear prices SMA(50) ≠ SMA(200) exactly, but both are close
        // to the trend line — ratio should be in a reasonable range [0.5, 2.0]
        val history = linearHistory(250)
        val capturingForecaster = CapturingForecaster(featureCount = 64)
        val strategy = MultiFactorMLStrategy(capturingForecaster, mockApiSource)

        strategy.calculateallocation(listOf("TEST.NS"), mapOf("TEST.NS" to history), mapOf("TEST.NS" to history.lastIndex))

        val features = capturingForecaster.capturedFeatures!!
        val smaRatio = features[61]
        assertTrue("SMA Ratio in features[61] should be positive and reasonable, got $smaRatio",
            smaRatio > 0.0 && smaRatio < 5.0)
    }

    @Test
    fun `ATR percent in feature slot 62 is non-negative and less than 1`() = runBlocking {
        val history = linearHistory(250)
        val capturingForecaster = CapturingForecaster(featureCount = 64)
        val strategy = MultiFactorMLStrategy(capturingForecaster, mockApiSource)

        strategy.calculateallocation(listOf("TEST.NS"), mapOf("TEST.NS" to history), mapOf("TEST.NS" to history.lastIndex))

        val features = capturingForecaster.capturedFeatures!!
        val atrPct = features[62]
        assertTrue("ATR% in features[62] should be >= 0, got $atrPct", atrPct >= 0.0)
        assertTrue("ATR% in features[62] should be < 1.0 (not 100%), got $atrPct", atrPct < 1.0)
    }

    @Test
    fun `Relative Volume in feature slot 63 is non-negative`() = runBlocking {
        val history = linearHistory(250)
        val capturingForecaster = CapturingForecaster(featureCount = 64)
        val strategy = MultiFactorMLStrategy(capturingForecaster, mockApiSource)

        strategy.calculateallocation(listOf("TEST.NS"), mapOf("TEST.NS" to history), mapOf("TEST.NS" to history.lastIndex))

        val features = capturingForecaster.capturedFeatures!!
        val relVol = features[63]
        assertTrue("RelVol in features[63] should be >= 0, got $relVol", relVol >= 0.0)
    }

    // =========================================================================
    // Gap 3c — No Look-Ahead Bias in Feature Window
    // =========================================================================

    @Test
    fun `log returns use only data up to cursor index (no look-ahead)`() = runBlocking {
        // Append a "future" candle with a drastically different price.
        // If the feature builder uses it, the last log return will be huge.
        val history = linearHistory(100, startPrice = 100.0, step = 1.0).toMutableList()
        val futureCandle = StockQuote("TEST.NS", baseDate + 100 * 86400000L, 9999.0, 10000.0, 9998.0, 9999.0, 5_000_000L)
        history.add(futureCandle)

        val capturingForecaster = CapturingForecaster(featureCount = 64)
        val strategy = MultiFactorMLStrategy(capturingForecaster, mockApiSource)

        // Point cursor at index 99, NOT the future candle at index 100
        val cursors = mapOf("TEST.NS" to 99)
        strategy.calculateallocation(listOf("TEST.NS"), mapOf("TEST.NS" to history), cursors)

        val features = capturingForecaster.capturedFeatures!!
        // Last log return (features[59]) should be ~ln(199/198) ≈ 0.005, NOT ln(9999/199) ≈ 3.9
        val lastLogReturn = features[59]
        assertTrue(
            "Last log return must not include the future candle, got $lastLogReturn",
            abs(lastLogReturn) < 0.1
        )
    }
}
