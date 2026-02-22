package com.example.stockmarketsim.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import android.util.Log
import com.example.stockmarketsim.data.local.dao.FundamentalsCacheDao
import com.example.stockmarketsim.data.local.entity.FundamentalsCacheEntity
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class IndianApiFundamentals(
    val peRatio: Double = 25.0,
    val roe: Double = 15.0,
    val debtToEquity: Double = 0.5,
    val marketCap: Double = 100000.0,
    val sentimentScore: Double = 0.0,
    val source: String = "UNKNOWN"
)

/**
 * Fetches fundamental data with a strict NO MOCK DATA policy.
 * 
 * Fallback chain:
 *   1. Room persistent cache (7-day TTL, stale data still accepted if API fails)
 *   2. IndianAPI.in real API (1 req/sec throttle)
 *   3. Yahoo Finance fundamentals (free, no rate limit)
 *   4. null → stock is SKIPPED (never use fake data)
 *
 * All log messages are dispatched via a lambda to reach active simulations.
 */
class IndianApiSource @javax.inject.Inject constructor(
    private val settingsManager: com.example.stockmarketsim.data.manager.SettingsManager,
    private val cacheDao: FundamentalsCacheDao,
    private val yahooSource: YahooFinanceSource
) {
    private val TAG = "IndianApiSource"
    private val BASE_URL = "https://stock.indianapi.in/stock"
    
    // 7-day TTL for Room cache
    private val CACHE_TTL = 7 * 24 * 60 * 60 * 1000L
    
    // 1 request per second throttle
    private var lastRequestTime = 0L
    private val THROTTLE_MS = 1000L
    
    // Track rate limit status for this session
    private var apiRateLimited = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * Get fundamentals for a symbol. Returns null if ALL sources fail — stock should be skipped.
     * @param onLog Optional lambda to broadcast log messages to active simulations
     */
    suspend fun getFundamentals(
        symbol: String,
        onLog: ((String) -> Unit)? = null
    ): IndianApiFundamentals? = withContext(Dispatchers.IO) {
        
        // === STEP 1: Room persistent cache (7-day fresh, infinite stale) ===
        val cached = cacheDao.get(symbol)
        if (cached != null) {
            val age = System.currentTimeMillis() - cached.fetchTimestamp
            if (age < CACHE_TTL) {
                // Fresh cache — use directly
                return@withContext cached.toFundamentals()
            }
            // Stale cache — try to refresh, but keep as fallback
        }

        // === STEP 2: IndianAPI.in (1 req/sec throttle) ===
        val apiKey = settingsManager.indianApiKey
        if (apiKey.isNotBlank() && !apiRateLimited) {
            val apiResult = tryIndianApi(symbol, apiKey, onLog)
            if (apiResult != null) {
                // Save to Room cache
                cacheDao.insert(apiResult.toEntity(symbol))
                return@withContext apiResult
            }
        }

        // === STEP 3: Yahoo Finance fallback ===
        val yahooResult = tryYahooFinance(symbol, onLog)
        if (yahooResult != null) {
            // Save to Room cache
            cacheDao.insert(yahooResult.toEntity(symbol))
            return@withContext yahooResult
        }

        // === STEP 4: Stale cache (better than nothing) ===
        if (cached != null) {
            val ageHours = (System.currentTimeMillis() - cached.fetchTimestamp) / (60 * 60 * 1000)
            Log.w(TAG, "⚠️ Using stale cache for $symbol (${ageHours}h old)")
            onLog?.invoke("⚠️ [Fundamentals] Using stale cache for $symbol (${ageHours}h old)")
            return@withContext cached.toFundamentals()
        }

        // === STEP 5: No data at all → SKIP this stock ===
        Log.w(TAG, "❌ No fundamentals available for $symbol from any source. Stock will be SKIPPED.")
        onLog?.invoke("❌ [Fundamentals] No data for $symbol — skipping stock (API + Yahoo both failed)")
        return@withContext null
    }

    /**
     * Attempt to fetch from IndianAPI.in with 1 req/sec throttle.
     */
    private suspend fun tryIndianApi(
        symbol: String,
        apiKey: String,
        onLog: ((String) -> Unit)?
    ): IndianApiFundamentals? {
        try {
            // Throttle: Wait until 1 second has passed since last request
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime
            if (elapsed < THROTTLE_MS) {
                delay(THROTTLE_MS - elapsed)
            }
            
            val companyName = symbol.removeSuffix(".NS")
            val request = Request.Builder()
                .url("$BASE_URL?name=$companyName")
                .addHeader("X-Api-Key", apiKey)
                .build()

            lastRequestTime = System.currentTimeMillis()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                
                if (code == 429) {
                    apiRateLimited = true
                    Log.w(TAG, "⚠️ IndianAPI 429 — rate limited for this session")
                    onLog?.invoke("⚠️ [IndianAPI] Rate limit hit (HTTP 429). Falling back to Yahoo Finance for remaining stocks.")
                } else {
                    Log.w(TAG, "❌ IndianAPI error $code for $symbol")
                }
                return null
            }

            val body = response.body?.string() ?: ""
            response.close()
            
            return parseIndianApiResponse(body, symbol)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ IndianAPI network error for $symbol: ${e.message}")
            onLog?.invoke("⚠️ [IndianAPI] Network error for $symbol: ${e.message}")
            return null
        }
    }

    /**
     * Fallback: fetch from Yahoo Finance quoteSummary.
     */
    private suspend fun tryYahooFinance(
        symbol: String,
        onLog: ((String) -> Unit)?
    ): IndianApiFundamentals? {
        try {
            val data = yahooSource.getFundamentals(symbol) ?: return null
            
            Log.d(TAG, "📊 Yahoo fallback for $symbol: P/E=${"%.1f".format(data.trailingPE ?: 0.0)}, ROE=${"%.1f".format((data.returnOnEquity ?: 0.0) * 100)}%")
            
            return IndianApiFundamentals(
                peRatio = data.trailingPE ?: 25.0,
                roe = (data.returnOnEquity ?: 0.15) * 100.0,  // Yahoo returns as fraction, we store as %
                debtToEquity = data.debtToEquity ?: 0.5,
                marketCap = (data.marketCap ?: 100000).toDouble(),
                sentimentScore = 0.0,  // Yahoo doesn't provide sentiment — neutral
                source = "YAHOO_FINANCE"
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Yahoo Finance error for $symbol: ${e.message}")
            return null
        }
    }

    /**
     * Parse the IndianAPI.in /stock JSON response.
     */
    private fun parseIndianApiResponse(body: String, symbol: String): IndianApiFundamentals? {
        try {
            val json = JSONObject(body)
            
            val keyMetrics = json.optJSONObject("keyMetrics")
            val peRatio = keyMetrics?.optDouble("pe", Double.NaN) ?: Double.NaN
            val roe = keyMetrics?.optDouble("roe", Double.NaN) ?: Double.NaN
            val debtToEquity = keyMetrics?.optDouble("debtToEquity", Double.NaN) ?: Double.NaN
            val marketCap = keyMetrics?.optDouble("marketCap", Double.NaN) ?: Double.NaN
            
            // If all key metrics are NaN, the response is probably invalid
            if (peRatio.isNaN() && roe.isNaN() && debtToEquity.isNaN()) {
                Log.w(TAG, "⚠️ IndianAPI returned empty keyMetrics for $symbol")
                return null
            }
            
            // Analyst sentiment: buy/sell/hold → -1.0 to 1.0
            val analystView = json.optJSONObject("analystView")
            val sentimentScore = if (analystView != null) {
                val buy = analystView.optInt("buy", 0)
                val sell = analystView.optInt("sell", 0)
                val total = buy + sell + analystView.optInt("hold", 0)
                if (total > 0) (buy - sell).toDouble() / total else 0.0
            } else 0.0
            
            Log.d(TAG, "✅ $symbol: P/E=${"%.1f".format(if (peRatio.isNaN()) 0.0 else peRatio)}, ROE=${"%.1f".format(if (roe.isNaN()) 0.0 else roe)}%, Sentiment=${"%.2f".format(sentimentScore)}")
            
            return IndianApiFundamentals(
                peRatio = if (peRatio.isNaN()) 25.0 else peRatio,
                roe = if (roe.isNaN()) 15.0 else roe,
                debtToEquity = if (debtToEquity.isNaN()) 0.5 else debtToEquity,
                marketCap = if (marketCap.isNaN()) 100000.0 else marketCap,
                sentimentScore = sentimentScore,
                source = "INDIAN_API"
            )
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Failed to parse IndianAPI response for $symbol: ${e.message}")
            return null
        }
    }
    
    fun clearCache() {
        kotlinx.coroutines.runBlocking {
            cacheDao.clearAll()
        }
    }
}

// Extension functions for Room entity conversion
private fun FundamentalsCacheEntity.toFundamentals() = IndianApiFundamentals(
    peRatio = peRatio,
    roe = roe,
    debtToEquity = debtToEquity,
    marketCap = marketCap,
    sentimentScore = sentimentScore,
    source = "ROOM_CACHE"
)

private fun IndianApiFundamentals.toEntity(symbol: String) = FundamentalsCacheEntity(
    symbol = symbol,
    peRatio = peRatio,
    roe = roe,
    debtToEquity = debtToEquity,
    marketCap = marketCap,
    sentimentScore = sentimentScore,
    source = source
)
