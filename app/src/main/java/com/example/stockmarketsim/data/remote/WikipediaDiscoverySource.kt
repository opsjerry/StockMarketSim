package com.example.stockmarketsim.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WikipediaDiscoverySource @Inject constructor() {

    private val nifty50Url = "https://en.wikipedia.org/wiki/NIFTY_50"
    private val niftyNext50Url = "https://en.wikipedia.org/wiki/NIFTY_Next_50"

    suspend fun discoverStocks(): List<String> = withContext(Dispatchers.IO) {
        val discoveredSymbols = mutableSetOf<String>()
        val debugLogs = mutableListOf<String>()

        try {
            // 1. Scrape Nifty 50
            debugLogs.add("Scanning Wikipedia (Nifty 50)...")
            val doc50 = Jsoup.connect(nifty50Url).get()
            // The main table is usually the first table with class 'wikitable' or 'sortable'
            val tables50 = doc50.select("table.wikitable")
            val table50 = tables50.firstOrNull { it.text().contains("Symbol", ignoreCase = true) }
            
            if (table50 != null) {
                val rows = table50.select("tr")
                for (row in rows) {
                    val cols = row.select("td")
                    if (cols.isNotEmpty()) {
                        // Usually Symbol is the second column (index 1) in Nifty 50 wiki
                        // But let's look for known patterns. 
                        // Col 1: Symbol (e.g. ADANIENT)
                        val symbolText = cols[1].text().trim()
                        if (symbolText.isNotEmpty() && !symbolText.contains("Symbol", true)) {
                            val nseSymbol = "$symbolText.NS"
                            discoveredSymbols.add(nseSymbol)
                        }
                    }
                }
                debugLogs.add("Found ${discoveredSymbols.size} Nifty 50 stocks.")
            } else {
                debugLogs.add("ERROR: Nifty 50 table structure changed.")
            }

            // 2. Scrape Nifty Next 50
            debugLogs.add("Scanning Wikipedia (Nifty Next 50)...")
            val docNext = Jsoup.connect(niftyNext50Url).get()
            val tablesNext = docNext.select("table.wikitable")
            val tableNext = tablesNext.firstOrNull { it.text().contains("Symbol", ignoreCase = true) }
            
            if (tableNext != null) {
                val rows = tableNext.select("tr")
                var count = 0
                for (row in rows) {
                    val cols = row.select("td")
                    if (cols.isNotEmpty()) {
                        val symbolText = cols[1].text().trim()
                        if (symbolText.isNotEmpty()) {
                            val nseSymbol = "$symbolText.NS"
                            if (discoveredSymbols.add(nseSymbol)) {
                                count++
                            }
                        }
                    }
                }
                debugLogs.add("Added $count Nifty Next 50 stocks.")
            }

        } catch (e: Exception) {
            android.util.Log.e("WikiDiscovery", "Failed to scrape Wikipedia", e)
            debugLogs.add("Discovery Failed: ${e.message}")
        }
        
        android.util.Log.d("WikiDiscovery", debugLogs.joinToString("\n"))
        return@withContext discoveredSymbols.toList().sorted()
    }
}
