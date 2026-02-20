package com.example.stockmarketsim.data.remote

import com.example.stockmarketsim.domain.model.StockQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

class YahooFinanceSource @Inject constructor(
    private val client: OkHttpClient
) {
    // Yahoo Finance CSV URL logic
    // URL format: https://query1.finance.yahoo.com/v7/finance/download/SYMBOL?period1=START&period2=END&interval=1d&events=history
    
    private val crumbManager = YahooFinanceCrumbManager(client)

    // EXPERT REVIEW FIX: User-Agent Rotation
    // Reduces risk of IP block by mimicking different browsers.
    private val userAgents = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.1 Safari/605.1.15",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:89.0) Gecko/20100101 Firefox/89.0",
        "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 14_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Mobile/15E148 Safari/604.1"
    )

    private fun getRandomUserAgent(): String = userAgents.random()

    suspend fun getHistory(symbol: String, startDateSeconds: Long? = null): List<StockQuote> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis() / 1000
            val start = startDateSeconds ?: (now - (365 * 5 * 24 * 60 * 60)) // Custom start or 5 years ago
            
            // Ensure we have a crumb
            val crumb = crumbManager.getCrumb()
            val cookie = crumbManager.cookie
            
            if (crumb == null) {
                android.util.Log.w("YahooFinanceSource", "Warning: No Crumb found for $symbol")
            }
            
            // Use v8 Chart API (JSON) instead of v7 Download (CSV)
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/$symbol?period1=$start&period2=$now&interval=1d&includePrePost=false&events=div|split&crumb=$crumb"
            
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", getRandomUserAgent()) // ROTATION
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Origin", "https://finance.yahoo.com")
                .header("Referer", "https://finance.yahoo.com/quote/$symbol")
                
            if (cookie != null) {
                requestBuilder.header("Cookie", cookie)
            }
                
            client.newCall(requestBuilder.build()).execute().use { response ->
            
            if (response.isSuccessful) {
                val jsonString = response.body?.string()
                if (jsonString != null) {
                    val root = org.json.JSONObject(jsonString)
                    val chart = root.getJSONObject("chart")
                    val result = chart.getJSONArray("result").getJSONObject(0)
                    
                    val timestamps = result.getJSONArray("timestamp")
                    val indicators = result.getJSONObject("indicators")
                    val quote = indicators.getJSONArray("quote").getJSONObject(0)
                    
                    val opens = quote.getJSONArray("open")
                    val highs = quote.getJSONArray("high")
                    val lows = quote.getJSONArray("low")
                    val closes = quote.getJSONArray("close")
                    val volumes = quote.getJSONArray("volume")
                    
                    // Try to get default adjclose
                    val adjCloseArr = indicators.optJSONArray("adjclose")?.optJSONObject(0)?.optJSONArray("adjclose")
                    
                    // EXPERT FIX: Parse Splits Manually for robustness
                    val events = result.optJSONObject("events")
                    val splits = events?.optJSONObject("splits")
                    val splitMap = HashMap<Long, Double>()

                    if (splits != null) {
                        val keys = splits.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val splitObj = splits.getJSONObject(key)
                            // "date" (long), "numerator" (double), "denominator" (double)
                            val num = splitObj.optDouble("numerator", 1.0)
                            val denom = splitObj.optDouble("denominator", 1.0)
                            if (num > 0 && denom > 0) {
                                val splitDateSeconds = splitObj.optLong("date", 0L)
                                // Normalize Key to unique Day Index (Unix Seconds / 86400)
                                // This handles time mismatches (e.g., Midnight UTC vs 09:15 IST)
                                val dayKey = splitDateSeconds / 86400
                                val factor = denom / num 
                                splitMap[dayKey] = factor
                                android.util.Log.d("YahooSplits", "Found split for $symbol on Day $dayKey: Ratio $num:$denom -> Factor $factor")
                            }
                        }
                    }
                    
                    val quotesList = java.util.LinkedList<StockQuote>()
                    var cumulativeSplitFactor = 1.0

                    // Iterate BACKWARDS to apply cumulative split factors
                    for (i in timestamps.length() - 1 downTo 0) {
                        // Handle potential nulls
                        if (closes.isNull(i) || opens.isNull(i)) continue
                        
                        val dateSeconds = timestamps.getLong(i)
                        val dayKey = dateSeconds / 86400
                        
                        val open = opens.optDouble(i, 0.0)
                        val high = highs.optDouble(i, 0.0)
                        val low = lows.optDouble(i, 0.0)
                        val close = closes.optDouble(i, 0.0)
                        val volume = volumes.optLong(i, 0)
                        
                        // Yahoo's pre-calculated adjClose (includes Dividends + Splits)
                        val jsonAdjClose = if (adjCloseArr != null && !adjCloseArr.isNull(i)) adjCloseArr.optDouble(i, close) else close
                        
                        // Manual Split-Only Adjustment
                        val manualAdjClose = close * cumulativeSplitFactor
                        
                        // DECISION LOGIC: 
                        // If Manual Split Factor is active, trust it primarily (Backtest needs continuity).
                        val isManualAdjusted = kotlin.math.abs(cumulativeSplitFactor - 1.0) > 0.001
                        
                        val finalAdjClose: Double
                        val finalAdjustmentFactor: Double

                        if (isManualAdjusted) {
                             finalAdjClose = manualAdjClose
                             finalAdjustmentFactor = cumulativeSplitFactor
                        } else {
                             // Use Yahoo's provided adjClose (Dividends)
                             finalAdjClose = jsonAdjClose
                             finalAdjustmentFactor = if (close > 0) jsonAdjClose / close else 1.0
                        }

                        if (close > 0 && finalAdjClose > 0) {
                             quotesList.addFirst(StockQuote(
                                symbol = symbol,
                                date = dateSeconds * 1000L,
                                open = open * finalAdjustmentFactor,
                                high = high * finalAdjustmentFactor,
                                low = low * finalAdjustmentFactor,
                                close = finalAdjClose,
                                volume = volume
                            ))
                        }
                        
                        // Apply Split Factor for previous days (next iteration backwards)
                        // If split happens ON this day (dayKey), all PREVIOUS records need adjustment.
                        if (splitMap.containsKey(dayKey)) {
                            val factor = splitMap[dayKey] ?: 1.0
                            cumulativeSplitFactor *= factor
                            android.util.Log.d("YahooSplits", "Applying Split Factor x$factor for $symbol (<= Day $dayKey). Cumulative: $cumulativeSplitFactor")
                        }
                    }
                    if (quotesList.isNotEmpty()) return@withContext quotesList
                }
            } else {
                val errorBody = response.body?.string() ?: "No body"
                if (response.code == 429) {
                     android.util.Log.e("YahooFinanceSource", "[CRITICAL] Rate Limited (429) for $symbol. Pausing.")
                     kotlinx.coroutines.delay(2000) 
                }
                android.util.Log.e("YahooFinanceSource", "HTTP Error for $symbol: ${response.code} ${response.message} | Body: $errorBody")
            }
            Unit
            } // .use

        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("YahooFinanceSource", "Exception fetching data for $symbol: ${e.message}", e)
        }

        // FAIL: We do NOT generate mock data anymore as per strict requirements.
        // android.util.Log.e("YahooFinanceSource", "Failed to fetch real data for $symbol (Fallthrough)")
        emptyList()
    }

    suspend fun getHistories(symbols: List<String>): Map<String, List<StockQuote>> {
        return getHistories(symbols.associateWith { null })
    }

    suspend fun getHistories(requests: Map<String, Long?>): Map<String, List<StockQuote>> = withContext(Dispatchers.IO) {
        val semaphore = kotlinx.coroutines.sync.Semaphore(10) // Limit to 10 concurrent requests
        
        requests.map { (symbol, start) ->
            async {
                semaphore.acquire()
                try {
                    val history = getHistory(symbol, start)
                    symbol to history
                } finally {
                    semaphore.release()
                }
            }
        }.awaitAll().toMap()
    }

    /**
     * Fetch fundamental financial data from Yahoo Finance v10/quoteSummary.
     * Returns ROE, Debt/Equity, Market Cap, P/E, and Book Value.
     */
    suspend fun getFundamentals(symbol: String): com.example.stockmarketsim.domain.model.FundamentalData? = withContext(Dispatchers.IO) {
        try {
            val crumb = crumbManager.getCrumb()
            val cookie = crumbManager.cookie

            val url = "https://query1.finance.yahoo.com/v10/finance/quoteSummary/$symbol" +
                    "?modules=financialData,defaultKeyStatistics&crumb=$crumb"

            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", getRandomUserAgent())
                .header("Accept", "application/json")
                .header("Origin", "https://finance.yahoo.com")
                .header("Referer", "https://finance.yahoo.com/quote/$symbol")

            if (cookie != null) {
                requestBuilder.header("Cookie", cookie)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w("YahooFundamentals", "HTTP ${response.code} for $symbol")
                    return@withContext null
                }

                val json = response.body?.string() ?: return@withContext null
                val root = org.json.JSONObject(json)
                val result = root.getJSONObject("quoteSummary")
                    .getJSONArray("result")
                    .getJSONObject(0)

                // Parse financialData module
                val financialData = result.optJSONObject("financialData")
                val roe = financialData?.optJSONObject("returnOnEquity")?.optDouble("raw")
                val debtToEquity = financialData?.optJSONObject("debtToEquity")?.optDouble("raw")
                    ?.let { it / 100.0 } // Yahoo returns as percentage (e.g., 50 = 0.5 ratio)

                // Parse defaultKeyStatistics module
                val keyStats = result.optJSONObject("defaultKeyStatistics")
                val marketCap = keyStats?.optJSONObject("enterpriseValue")?.optLong("raw")
                val trailingPE = keyStats?.optJSONObject("trailingPE")?.optDouble("raw")
                val bookValue = keyStats?.optJSONObject("bookValue")?.optDouble("raw")

                return@withContext com.example.stockmarketsim.domain.model.FundamentalData(
                    symbol = symbol,
                    returnOnEquity = if (roe?.isNaN() == true) null else roe,
                    debtToEquity = if (debtToEquity?.isNaN() == true) null else debtToEquity,
                    marketCap = marketCap,
                    trailingPE = if (trailingPE?.isNaN() == true) null else trailingPE,
                    bookValue = if (bookValue?.isNaN() == true) null else bookValue
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("YahooFundamentals", "Error fetching fundamentals for $symbol: ${e.message}")
            null
        }
    }

    /**
     * Batch fetch fundamentals for multiple symbols with rate limiting.
     */
    suspend fun getBatchFundamentals(symbols: List<String>): Map<String, com.example.stockmarketsim.domain.model.FundamentalData> = withContext(Dispatchers.IO) {
        val semaphore = kotlinx.coroutines.sync.Semaphore(5) // Conservative: 5 concurrent
        val results = symbols.map { symbol ->
            async {
                semaphore.acquire()
                try {
                    val data = getFundamentals(symbol)
                    if (data != null) symbol to data else null
                } finally {
                    semaphore.release()
                }
            }
        }.awaitAll().filterNotNull().toMap()
        results
    }
}
