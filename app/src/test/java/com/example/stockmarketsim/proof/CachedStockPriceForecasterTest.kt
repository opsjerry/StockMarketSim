package com.example.stockmarketsim.proof

import com.example.stockmarketsim.data.local.dao.PredictionDao
import com.example.stockmarketsim.data.local.entity.PredictionEntity
import com.example.stockmarketsim.data.ml.CachedStockPriceForecaster
import com.example.stockmarketsim.domain.ml.IStockPriceForecaster
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * REGRESSION SUITE: CachedStockPriceForecaster
 *
 * Tests cache hit/miss logic, NaN exclusion from cache, and null context bypass.
 */
class CachedStockPriceForecasterTest {

    private lateinit var mockDao: PredictionDao
    private lateinit var mockRealForecaster: IStockPriceForecaster
    private lateinit var cachedForecaster: CachedStockPriceForecaster

    private val testSymbol = "TEST.NS"
    private val testDate = 1704067200000L
    private val modelVersion = "v4_multifactor_seq64"

    @Before
    fun setup() {
        mockDao = mock(PredictionDao::class.java)
        mockRealForecaster = mock(IStockPriceForecaster::class.java)
        cachedForecaster = CachedStockPriceForecaster(mockRealForecaster, mockDao)
    }

    // =====================================================================
    // Cache Miss → Inference → Cache Write
    // =====================================================================

    @Test
    fun `cache miss triggers real inference and saves result`() {
        val features = DoubleArray(60) { 0.001 }
        val expectedReturn = 0.005f

        // Setup: No cache entry
        runBlocking {
            `when`(mockDao.getPrediction(testSymbol, testDate, modelVersion)).thenReturn(null)
        }
        `when`(mockRealForecaster.predict(features, testSymbol, testDate)).thenReturn(expectedReturn)

        val result = cachedForecaster.predict(features, testSymbol, testDate)

        // Verify: Real inference was called
        verify(mockRealForecaster).predict(features, testSymbol, testDate)

        // Verify: Result was saved to cache
        runBlocking {
            verify(mockDao).insertPrediction(any(PredictionEntity::class.java) ?: PredictionEntity(testSymbol, testDate, expectedReturn, modelVersion))
        }

        assertEquals("Should return predicted value", expectedReturn, result, 0.0001f)
    }

    // =====================================================================
    // Cache Hit → Skip Inference
    // =====================================================================

    @Test
    fun `cache hit returns cached value without calling real forecaster`() {
        val features = DoubleArray(60) { 0.001 }
        val cachedReturn = 0.007f

        // Setup: Cache has an entry
        runBlocking {
            `when`(mockDao.getPrediction(testSymbol, testDate, modelVersion)).thenReturn(
                PredictionEntity(testSymbol, testDate, cachedReturn, modelVersion)
            )
        }

        val result = cachedForecaster.predict(features, testSymbol, testDate)

        // Verify: Real inference was NOT called
        verify(mockRealForecaster, never()).predict(any(DoubleArray::class.java) ?: features, anyString(), anyLong())

        assertEquals("Should return cached value", cachedReturn, result, 0.0001f)
    }

    // =====================================================================
    // NaN Predictions Are Not Cached
    // =====================================================================

    @Test
    fun `NaN predictions are not saved to cache`() {
        val features = DoubleArray(60) { 0.001 }

        // Setup: No cache, inference returns NaN
        runBlocking {
            `when`(mockDao.getPrediction(testSymbol, testDate, modelVersion)).thenReturn(null)
        }
        `when`(mockRealForecaster.predict(features, testSymbol, testDate)).thenReturn(Float.NaN)

        val result = cachedForecaster.predict(features, testSymbol, testDate)

        // Verify: NaN result was NOT saved to cache
        runBlocking {
            verify(mockDao, never()).insertPrediction(any(PredictionEntity::class.java) ?: PredictionEntity(testSymbol, testDate, 0f, modelVersion))
        }

        assertTrue("Should return NaN", result.isNaN())
    }

    // =====================================================================
    // Null Context Bypasses Cache
    // =====================================================================

    @Test
    fun `null symbol bypasses cache and calls real forecaster directly`() {
        val features = DoubleArray(60) { 0.001 }
        val expectedReturn = 0.003f

        `when`(mockRealForecaster.predict(features, null, testDate)).thenReturn(expectedReturn)

        val result = cachedForecaster.predict(features, null, testDate)

        // Verify: Direct call to real forecaster (no cache interaction)
        verify(mockRealForecaster).predict(features, null, testDate)

        // Verify: No cache interaction at all
        runBlocking {
            verify(mockDao, never()).getPrediction(anyString(), anyLong(), anyString())
        }

        assertEquals("Should return real inference value", expectedReturn, result, 0.0001f)
    }

    @Test
    fun `null date bypasses cache and calls real forecaster directly`() {
        val features = DoubleArray(60) { 0.001 }
        val expectedReturn = 0.004f

        `when`(mockRealForecaster.predict(features, testSymbol, null)).thenReturn(expectedReturn)

        val result = cachedForecaster.predict(features, testSymbol, null)

        verify(mockRealForecaster).predict(features, testSymbol, null)

        assertEquals("Should return real inference value", expectedReturn, result, 0.0001f)
    }

    // =====================================================================
    // Initialize Delegation
    // =====================================================================

    @Test
    fun `initialize delegates to real forecaster`() {
        cachedForecaster.initialize()
        verify(mockRealForecaster).initialize()
    }

    @Test
    fun `getModelVersion delegates to real forecaster`() {
        `when`(mockRealForecaster.getModelVersion()).thenReturn(3)
        val version = cachedForecaster.getModelVersion()
        assertEquals(3, version)
        verify(mockRealForecaster).getModelVersion()
    }
}
