package com.example.stockmarketsim.data.remote

import com.example.stockmarketsim.data.manager.SettingsManager
import com.example.stockmarketsim.domain.model.StockQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlphaVantageSource @Inject constructor(
    private val client: OkHttpClient,
    private val settingsManager: SettingsManager
) {
    private val baseUrl = "https://www.alphavantage.co/query"

    private fun getApiKey(): String {
        return settingsManager.alphaVantageApiKey
    }

    suspend fun getHistory(symbol: String): List<StockQuote> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) return@withContext emptyList()

        val url = "$baseUrl?function=TIME_SERIES_DAILY&symbol=$symbol&outputsize=full&apikey=$apiKey"
        val request = Request.Builder().url(url).build()

        try {
            client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val timeSeries = json.optJSONObject("Time Series (Daily)") ?: return@withContext emptyList()
                
                val quotes = mutableListOf<StockQuote>()
                val keys = timeSeries.keys()
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)

                while (keys.hasNext()) {
                    val dateStr = keys.next()
                    val data = timeSeries.getJSONObject(dateStr)
                    val date = dateFormat.parse(dateStr)?.time ?: continue
                    
                    quotes.add(StockQuote(
                        symbol = symbol,
                        date = date,
                        open = data.getString("1. open").toDouble(),
                        high = data.getString("2. high").toDouble(),
                        low = data.getString("3. low").toDouble(),
                        close = data.getString("4. close").toDouble(),
                        volume = data.getString("5. volume").toLong()
                    ))
                }
                return@withContext quotes.sortedBy { it.date }
            }
            Unit
            } // .use
        } catch (e: Exception) {
            e.printStackTrace()
        }
        emptyList()
    }

    suspend fun getSentimentScore(symbol: String): Double = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) return@withContext 0.0

        val url = "$baseUrl?function=NEWS_SENTIMENT&tickers=$symbol&apikey=$apiKey"
        val request = Request.Builder().url(url).build()

        try {
            client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext 0.0
                val json = JSONObject(body)
                val feed = json.optJSONArray("feed") ?: return@withContext 0.0
                
                var totalScore = 0.0
                var count = 0
                
                for (i in 0 until feed.length()) {
                    val article = feed.getJSONObject(i)
                    val tickerSentiment = article.optJSONArray("ticker_sentiment") ?: continue
                    for (j in 0 until tickerSentiment.length()) {
                        val ts = tickerSentiment.getJSONObject(j)
                        if (ts.getString("ticker") == symbol) {
                            totalScore += ts.getDouble("ticker_sentiment_score")
                            count++
                        }
                    }
                }
                
                if (count > 0) return@withContext totalScore / count
            }
            Unit
            } // .use
        } catch (e: Exception) {
            e.printStackTrace()
        }
        0.0
    }

    suspend fun getInflationRate(): Double = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) return@withContext 0.0

        val url = "$baseUrl?function=CPI&interval=monthly&apikey=$apiKey"
        val request = Request.Builder().url(url).build()

        try {
            client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext 0.0
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: return@withContext 0.0
                if (data.length() >= 13) {
                    val currentCpi = data.getJSONObject(0).getDouble("value")
                    val prevYearCpi = data.getJSONObject(12).getDouble("value")
                    if (prevYearCpi > 0) {
                        return@withContext ((currentCpi - prevYearCpi) / prevYearCpi) * 100.0
                    }
                } else if (data.length() > 0) {
                    // Fallback: If less than a year, return current value (though it's not YoY%)
                    return@withContext 0.0 
                }
            }
            Unit
            } // .use
        } catch (e: Exception) {
            e.printStackTrace()
        }
        0.0
    }
}
