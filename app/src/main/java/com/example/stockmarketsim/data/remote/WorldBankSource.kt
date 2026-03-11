package com.example.stockmarketsim.data.remote

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * World Bank Open Data API — no API key required.
 *
 * Provides India CPI (Consumer Price Index, annual % change) via:
 *   https://api.worldbank.org/v2/country/IN/indicator/FP.CPI.TOTL.ZG?format=json&mrv=2
 *
 * - Series: FP.CPI.TOTL.ZG — India CPI, Annual % (World Bank / IMF)
 * - Updates: ~quarterly, 3-month lag from the reference period
 * - Accuracy: RBI-aligned — India-specific inflation, not US CPI
 * - Fallback: 4.5% (RBI midpoint target) if the fetch fails
 *
 * Replaces AlphaVantage getInflationRate() which returned US BLS CPI — wrong for Indian market regime detection.
 */
@Singleton
class WorldBankSource @Inject constructor(
    private val httpClient: OkHttpClient
) {
    /** Result wrapper so callers can distinguish live data from fallback in simulation logs. */
    data class CpiResult(val value: Double, val isFallback: Boolean, val year: String = "?")

    companion object {
        private const val TAG = "WorldBankSource"
        private const val INDIA_CPI_URL =
            "https://api.worldbank.org/v2/country/IN/indicator/FP.CPI.TOTL.ZG?format=json&mrv=2"
        /** RBI tolerance band: 2–6%. Use midpoint as fallback if API unavailable. */
        const val INDIA_CPI_FALLBACK = 4.5
    }

    /**
     * Returns the latest India CPI annual % change.
     * Falls back to [INDIA_CPI_FALLBACK] (4.5%) on any error.
     * The [CpiResult.isFallback] flag lets callers log a warning in simulation output.
     */
    suspend fun getIndianInflationRate(): CpiResult {
        return try {
            val request = Request.Builder().url(INDIA_CPI_URL).build()
            val responseBody = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                httpClient.newCall(request).execute().use { it.body?.string() }
            } ?: return CpiResult(INDIA_CPI_FALLBACK, isFallback = true)

            // Response is a JSON array: [metadata, [dataPoint0, dataPoint1]]
            val root = JSONArray(responseBody)
            if (root.length() < 2) return CpiResult(INDIA_CPI_FALLBACK, isFallback = true)

            val dataArray = root.getJSONArray(1)
            // Find the most recent non-null value
            for (i in 0 until dataArray.length()) {
                val point = dataArray.getJSONObject(i)
                if (!point.isNull("value")) {
                    val cpi = point.getDouble("value")
                    val year = point.optString("date", "?")
                    Log.d(TAG, "India CPI ($year): $cpi%")
                    return CpiResult(cpi, isFallback = false, year = year)
                }
            }
            Log.w(TAG, "No non-null India CPI value found, using fallback.")
            CpiResult(INDIA_CPI_FALLBACK, isFallback = true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch India CPI from World Bank: ${e.message}")
            CpiResult(INDIA_CPI_FALLBACK, isFallback = true)
        }
    }
}
