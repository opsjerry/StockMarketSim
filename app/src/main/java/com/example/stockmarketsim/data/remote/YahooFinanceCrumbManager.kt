package com.example.stockmarketsim.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request

class YahooFinanceCrumbManager(private val client: OkHttpClient) {
    var crumb: String? = null
    var cookie: String? = null

    private val mutex = kotlinx.coroutines.sync.Mutex()
    private var crumbTimestamp: Long = 0
    private val CRUMB_TTL_MS = 25 * 60 * 1000L // 25 minutes (Yahoo expires ~30 min)

    suspend fun getCrumb(): String? {
        val now = System.currentTimeMillis()
        if (crumb != null && (now - crumbTimestamp) < CRUMB_TTL_MS) return crumb

        return mutex.withLock {
            // Double-check inside lock
            val nowInLock = System.currentTimeMillis()
            if (crumb != null && (nowInLock - crumbTimestamp) < CRUMB_TTL_MS) return@withLock crumb
            // Expired or missing — reset and re-fetch
            crumb = null
            cookie = null

            withContext(Dispatchers.IO) {
                try {
            // 1. Get Cookie from a real page (more robust than fc.yahoo.com)
            android.util.Log.d("YahooAuthentificator", "Fetching cookies from finance.yahoo.com...")
            val request1 = Request.Builder()
                .url("https://finance.yahoo.com/quote/SPY") 
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .build()
                
            val response1 = client.newCall(request1).execute()
            val cookies = response1.headers("set-cookie")
            response1.close()
            
            if (cookies.isNotEmpty()) {
                // Keep only the key-value pairs
                cookie = cookies.joinToString("; ") { it.split(";")[0] }
                android.util.Log.d("YahooAuthentificator", "Cookies captured: $cookie")
            } else {
                android.util.Log.w("YahooAuthentificator", "No cookies received from Yahoo!")
            }

            // 2. Get Crumb
            if (cookie != null) {
                android.util.Log.d("YahooAuthentificator", "Fetching crumb...")
                val request2 = Request.Builder()
                    .url("https://query1.finance.yahoo.com/v1/test/getcrumb")
                    .header("Cookie", cookie!!)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build()
                    
                val response2 = client.newCall(request2).execute()
                if (response2.isSuccessful) {
                    crumb = response2.body?.string()
                    crumbTimestamp = System.currentTimeMillis()
                    android.util.Log.d("YahooAuthentificator", "Crumb obtained: $crumb")
                } else {
                     android.util.Log.e("YahooAuthentificator", "Failed to get crumb: ${response2.code} ${response2.message}")
                }
                response2.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return@withContext crumb
            }
        }
    }
}
