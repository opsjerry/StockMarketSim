package com.example.stockmarketsim.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

data class IndianApiFundamentals(
    val peRatio: Double = 25.0,
    val roe: Double = 15.0,
    val debtToEquity: Double = 0.5,
    val marketCap: Double = 100000.0,
    val sentimentScore: Double = 0.5 // Mapped from Analyst reviews: -1.0 to 1.0
)

/**
 * Stub/Mock implementation of IndianAPI.in remote source.
 * In production, this uses Retrofit/OkHttp to query /v1/stock/{symbol}/fundamentals.
 */
class IndianApiSource @javax.inject.Inject constructor(
    private val settingsManager: com.example.stockmarketsim.data.manager.SettingsManager
) {
    private val TAG = "IndianApiSource"
    
    // In-memory cache to prevent redundant API calls during the tournament
    private val fundamentalCache = mutableMapOf<String, IndianApiFundamentals>()

    suspend fun getFundamentals(symbol: String): IndianApiFundamentals = withContext(Dispatchers.IO) {
        if (fundamentalCache.containsKey(symbol)) {
            return@withContext fundamentalCache[symbol]!!
        }
        
        Log.d(TAG, "Fetching fundamentals from IndianApi for $symbol...")
        
        // MOCK NETWORK DELAY (Remove in prod)
        // Thread.sleep(50) 
        
        // MOCK DATA based on symbol for deterministic testing.
        // In a real application, the API key is passed in the header:
        // val headers = mapOf("Authorization" to "Bearer ${settingsManager.indianApiKey}")
        val mockData = when {
            symbol.startsWith("RELIANCE") -> IndianApiFundamentals(28.4, 18.2, 0.4, 2000000.0, 0.8)
            symbol.startsWith("HDFCBANK") -> IndianApiFundamentals(18.5, 16.0, 0.8, 1200000.0, 0.6)
            symbol.startsWith("INFY") -> IndianApiFundamentals(30.1, 31.0, 0.0, 600000.0, -0.2)
            symbol.startsWith("TCS") -> IndianApiFundamentals(32.0, 45.0, 0.0, 1400000.0, 0.5)
            // Bearish mock data for failing fundamental tests: high debt, low score
            symbol.startsWith("IDEA") -> IndianApiFundamentals(0.0, -50.0, 5.0, 20000.0, -0.9)
            // Neutral default
            else -> IndianApiFundamentals()
        }
        
        fundamentalCache[symbol] = mockData
        return@withContext mockData
    }
    
    fun clearCache() {
        fundamentalCache.clear()
    }
}
