package com.example.stockmarketsim.data.repository

import com.example.stockmarketsim.data.local.dao.StockDao
import com.example.stockmarketsim.data.local.entity.toDomain
import com.example.stockmarketsim.data.local.entity.toEntity
import com.example.stockmarketsim.data.remote.YahooFinanceSource
import com.example.stockmarketsim.domain.model.StockQuote
import com.example.stockmarketsim.domain.model.TimeFrame
import com.example.stockmarketsim.domain.repository.StockRepository
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

class StockRepositoryImpl @Inject constructor(
    private val remoteSource: YahooFinanceSource,
    private val alphaVantageSource: com.example.stockmarketsim.data.remote.AlphaVantageSource,
    private val zerodhaSource: com.example.stockmarketsim.data.remote.ZerodhaSource,
    private val discoverySource: com.example.stockmarketsim.data.remote.WikipediaDiscoverySource,
    private val dao: StockDao,
    private val settingsManager: com.example.stockmarketsim.data.manager.SettingsManager
) : StockRepository {

    /**
     * Alpha Vantage uses .NSE and .BSE for Indian stocks, whereas Yahoo uses .NS and .BO.
     */
    private fun convertToAvTicker(symbol: String): String {
        return when {
            symbol.endsWith(".NS") -> symbol.removeSuffix(".NS") + ".NSE"
            symbol.endsWith(".BO") -> symbol.removeSuffix(".BO") + ".BSE"
            symbol == "^NSEI" -> "NSE:NIFTY" // Example for benchmark
            else -> symbol
        }
    }

    override suspend fun getStockQuote(symbol: String): StockQuote? {
        val latest = dao.getLatestPrice(symbol)
        
        if (latest != null) {
            val domain = latest.toDomain()
            val now = System.currentTimeMillis()
            // If data is less than 12 hours old, trust the cache.
            if ((now - domain.date) <= 12 * 60 * 60 * 1000L) {
                return domain
            }
        }
        
        // Fetch fresh if missing or stale (> 12h)
        return try {
            // PRIORITY 1: Zerodha (Real-Time Paid API)
            // If user has paid & connected, use this for ZERO latency.
            if (zerodhaSource.isSessionActive()) {
                 val zQuote = zerodhaSource.getQuote(symbol)
                 if (zQuote != null) {
                     dao.insertPrices(listOf(zQuote.toEntity()))
                     return zQuote
                 }
            }

            // PRIORITY 2: Alpha Vantage if enabled and key present
            val avKey = settingsManager.alphaVantageApiKey
            if (avKey.isNotEmpty()) {
                val avTicker = convertToAvTicker(symbol)
                val avHistory = alphaVantageSource.getHistory(avTicker)
                if (avHistory.isNotEmpty()) {
                    dao.insertPrices(avHistory.map { it.toEntity().copy(symbol = symbol) }) // Save back with original symbol
                    return avHistory.last()
                }
            }

            // FALLBACK / DEFAULT: Yahoo Finance
            // OPTIMIZATION: Use incremental fetch if we have some history
            val startDate = latest?.let { (it.date / 1000) } 
            
            val history = remoteSource.getHistory(symbol, startDate)
            
            if (history.isNotEmpty()) {
                dao.insertPrices(history.map { it.toEntity() })
                history.last()
            } else {
                latest?.toDomain() // Fallback to stale data if remote fails
            }
        } catch (e: Exception) {
            latest?.toDomain()
        }
    }

    override suspend fun getStockHistory(
        symbol: String,
        timeFrame: TimeFrame,
        limit: Int
    ): List<StockQuote> {
        val cached = dao.getHistory(symbol, 0) // fetch all
        if (cached.isNotEmpty()) {
            return cached.map { it.toDomain() }
        }
        
        // Fetch remote
        // Try Alpha Vantage first if key exists
        val avKey = settingsManager.alphaVantageApiKey
        if (avKey.isNotEmpty()) {
            val avTicker = convertToAvTicker(symbol)
            val avHistory = alphaVantageSource.getHistory(avTicker)
            if (avHistory.isNotEmpty()) {
                dao.insertPrices(avHistory.map { it.toEntity().copy(symbol = symbol) })
                return avHistory
            }
        }

        val remote = remoteSource.getHistory(symbol)
        dao.insertPrices(remote.map { it.toEntity() })
        return remote
    }

    override suspend fun getBatchStockHistory(
        symbols: List<String>,
        timeFrame: TimeFrame,
        limit: Int,
        onLog: (String) -> Unit
    ): Map<String, List<StockQuote>> {
        val resultMap = mutableMapOf<String, List<StockQuote>>()
        val requests = mutableMapOf<String, Long?>()
        
        // 5 Years in millis (approx)
        val fiveYearsMillis = 5L * 365 * 24 * 60 * 60 * 1000
        val cutoffDate = System.currentTimeMillis() - fiveYearsMillis
        
        // 1. Check Cache
        val allCached = dao.getBatchHistory(symbols, cutoffDate)
            .groupBy { it.symbol }

        var newStocksCount = 0
        for (symbol in symbols) {
            val cachedEntities = allCached[symbol] ?: emptyList()
            if (cachedEntities.isNotEmpty()) {
                val cachedQuotes = cachedEntities.map { it.toDomain() }
                resultMap[symbol] = cachedQuotes
                
                val lastDate = cachedQuotes.last().date
                val now = System.currentTimeMillis()
                
                // If last candle is < 12 hours old, it's fresh enough.
                val isStale = (now - lastDate) > 12 * 60 * 60 * 1000L
                
                if (isStale) {
                    requests[symbol] = (lastDate / 1000) + 1
                }
            } else {
                requests[symbol] = null
                newStocksCount++
            }
        }
        
        if (newStocksCount > 0) {
            onLog("📥 Downloading full history for $newStocksCount new symbols...")
        }
        
        // 2. Fetch Remote (Hybrid: Alpha Vantage Priority -> Yahoo Fallback)
        val validRemoteData = mutableMapOf<String, List<StockQuote>>()
        val yahooRequests = mutableMapOf<String, Long?>()
        val avKey = settingsManager.alphaVantageApiKey
        
        if (requests.isNotEmpty()) {
            // CIRCUIT BREAKER for Alpha Vantage
            // If we hit rate limit multiple times, stop trying to save time.
            var avCircuitOpen = avKey.isEmpty() // Open = Don't use AV
            var avConsecutiveFailures = 0
            val AV_FAILURE_THRESHOLD = 3
            
            // Limit AV calls in this batch to avoid immediate blocking if user has Free Tier (5/min)
            // We'll try to fetch top 5 important stocks or just first 5.
            var avCallsMade = 0
            val AV_BATCH_LIMIT = 5 

            val semaphore = kotlinx.coroutines.sync.Semaphore(3) // BATTERY SAVER: Max 3 concurrent network requests
            
            // Chunking requests to further reduce load (Process 5 symbols at a time)
            val requestChunks = requests.toList().chunked(5)

            for (chunk in requestChunks) {
                // Parallelize within the small chunk
                coroutineScope {
                    chunk.map { (symbol, date) ->
                        async {
                            semaphore.acquire()
                            try {
                                var fetched = false
                                // Try Alpha Vantage
                                if (!avCircuitOpen && avCallsMade < AV_BATCH_LIMIT) {
                                    try {
                                        val avTicker = convertToAvTicker(symbol)
                                        val avHistory = alphaVantageSource.getHistory(avTicker)
                                        
                                        if (avHistory.isNotEmpty()) {
                                            val mappedHistory = avHistory.map { it.toEntity().copy(symbol = symbol).toDomain() }
                                            synchronized(validRemoteData) {
                                                validRemoteData[symbol] = mappedHistory
                                            }
                                            fetched = true
                                            // Atomically update shared counters
                                            synchronized(this@StockRepositoryImpl) {
                                                avCallsMade++
                                                avConsecutiveFailures = 0
                                            }
                                            onLog("⬇️ Fetched $symbol from Alpha Vantage.")
                                        } else {
                                            synchronized(this@StockRepositoryImpl) {
                                                avConsecutiveFailures++
                                                if (avConsecutiveFailures >= AV_FAILURE_THRESHOLD) {
                                                    avCircuitOpen = true
                                                    onLog("⚠️ Alpha Vantage Limit Reached. Switching to Yahoo Finance.")
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        synchronized(this@StockRepositoryImpl) { avConsecutiveFailures++ }
                                    }
                                }
                                
                                if (!fetched) {
                                    synchronized(yahooRequests) {
                                        yahooRequests[symbol] = date
                                    }
                                }
                            } finally {
                                semaphore.release()
                            }
                        }
                    }.awaitAll()
                }
                // Small delay between chunks to let CPU cool down
                kotlinx.coroutines.delay(100) 
            }
            
            // 3. Fallback Batch Fetch (Yahoo)
            if (yahooRequests.isNotEmpty()) {
                if (avCallsMade > 0) {
                     onLog("🔄 Fallback to Yahoo Finance for remaining ${yahooRequests.size} symbols.")
                }
                
                try {
                    val yahooData = remoteSource.getHistories(yahooRequests)
                    // Merge Yahoo data
                    for ((sym, quotes) in yahooData) {
                        if (quotes.isNotEmpty()) {
                            validRemoteData[sym] = quotes
                        }
                    }
                } catch (e: Exception) {
                    onLog("❌ Yahoo Fallback Failed: ${e.message}")
                }
            }
            
            // 4. Save & Merge
            try {
                // DATA INTEGRITY: Filter out invalid quotes
                 val finalValidData = validRemoteData.filterValues { quotes -> 
                     quotes.isNotEmpty() && quotes.all { it.close > 0.0 }
                 }
                
                // STALE DATA STORM PROTECTION
                var freshCount = 0
                var totalChecked = 0
                val now = System.currentTimeMillis()
                // Relaxed to 96 hours (4 days) to handle Weekends + Market Holidays
                val staleThreshold = 96 * 60 * 60 * 1000L 
                
                for (quotes in finalValidData.values) {
                     val lastDate = quotes.last().date
                     if ((now - lastDate) < staleThreshold) {
                         freshCount++
                     }
                     totalChecked++
                }
                
                if (totalChecked > 10 && (freshCount.toDouble() / totalChecked) < 0.5) {
                    onLog("⚠️ Market Data might be stale (> 4 days old). Only $freshCount/$totalChecked symbols are fresh.")
                }

                // Save to DB
                val allNewEntities = finalValidData.values.flatten().map { it.toEntity() }
                if (allNewEntities.isNotEmpty()) {
                    dao.insertPrices(allNewEntities)
                }
                
                // Update Result Map
                var totalNewCandles = 0
                for ((symbol, newQuotes) in finalValidData) {
                    totalNewCandles += newQuotes.size
                    val existing = resultMap[symbol]
                    if (existing != null) {
                        resultMap[symbol] = (existing + newQuotes)
                            .distinctBy { it.date }
                            .sortedBy { it.date }
                    } else {
                        resultMap[symbol] = newQuotes
                    }
                }
                
                if (totalNewCandles > 0) {
                    onLog("✅ Synced $totalNewCandles new candles across ${finalValidData.size} symbols.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onLog("❌ Data Save Failed: ${e.message}")
            }
        }
        
        return resultMap
    }

    override suspend fun cleanupOldData(onLog: (String) -> Unit) {
        // Keep 10 Years
        val tenYearsMillis = 10L * 365 * 24 * 60 * 60 * 1000
        val cutoff = System.currentTimeMillis() - tenYearsMillis
        val count = dao.deleteOldData(cutoff)
        if (count > 0) {
            onLog("🧹 Pruned $count old records (Age > 10 years).")
        }
    }

    override suspend fun searchStock(query: String): List<String> {
        return com.example.stockmarketsim.domain.model.StockUniverse.AllMarket.filter { 
            it.contains(query, ignoreCase = true) 
        }
    }

    override suspend fun getSentimentScore(symbol: String): Double {
        val avTicker = convertToAvTicker(symbol)
        return alphaVantageSource.getSentimentScore(avTicker)
    }

    override suspend fun getInflationRate(): Double {
        return alphaVantageSource.getInflationRate()
    }

    // --- Dynamic Universe Implementation ---

    override fun getActiveUniverse(): kotlinx.coroutines.flow.Flow<List<String>> {
        return dao.getActiveUniverse()
    }

    override suspend fun getActiveUniverseSnapshot(): List<String> {
        return dao.getActiveUniverseSnapshot()
    }

    override suspend fun syncUniverseFromDiscovery(onLog: (String) -> Unit): Int {
        return try {
            val discovered = discoverySource.discoverStocks()
            if (discovered.isNotEmpty()) {
                val entities = discovered.map { symbol ->
                    com.example.stockmarketsim.data.local.entity.StockUniverseEntity(
                        symbol = symbol,
                        isActive = true
                    )
                }
                
                // Add hardcoded backups if not present (Safety Net)
                // We trust Wikipedia, but let's ensure we don't lose key stocks if Wiki is sparse
                // For now, simple replace.
                dao.insertUniverse(entities)
                onLog("Synced ${entities.size} stocks from Wikipedia.")
                return entities.size
            } else {
                onLog("Discovery Source returned 0 stocks.")
                0
            }
        } catch (e: Exception) {
            onLog("Sync Failed: ${e.message}")
            0
        }
    }
    
    override suspend fun addStockToUniverse(symbol: String) {
        val entity = com.example.stockmarketsim.data.local.entity.StockUniverseEntity(
            symbol = symbol,
            isActive = true
        )
        dao.insertUniverse(listOf(entity))
    }

    override suspend fun removeStockFromUniverse(symbol: String) {
        dao.removeFromUniverse(symbol)
    }

    // --- Fundamentals (Quality Filter) ---
    // In-memory cache to avoid redundant API calls (refreshes every hour)
    private val fundamentalsCache = mutableMapOf<String, Pair<com.example.stockmarketsim.domain.model.FundamentalData, Long>>()
    private val FUNDAMENTALS_CACHE_TTL = 60 * 60 * 1000L // 1 hour

    override suspend fun getFundamentals(symbol: String): com.example.stockmarketsim.domain.model.FundamentalData? {
        // Check cache first
        val cached = fundamentalsCache[symbol]
        if (cached != null && System.currentTimeMillis() - cached.second < FUNDAMENTALS_CACHE_TTL) {
            return cached.first
        }

        // Priority 1: Zerodha (paid API, when available)
        if (zerodhaSource.isSessionActive()) {
            val zData = zerodhaSource.getFundamentals(symbol)
            if (zData != null) {
                fundamentalsCache[symbol] = zData to System.currentTimeMillis()
                return zData
            }
        }

        // Priority 2: Yahoo Finance (free)
        val yahooData = remoteSource.getFundamentals(symbol)
        if (yahooData != null) {
            fundamentalsCache[symbol] = yahooData to System.currentTimeMillis()
        }
        return yahooData
    }

    override suspend fun getBatchFundamentals(symbols: List<String>, onLog: (String) -> Unit): Map<String, com.example.stockmarketsim.domain.model.FundamentalData> {
        val results = mutableMapOf<String, com.example.stockmarketsim.domain.model.FundamentalData>()
        val uncachedSymbols = mutableListOf<String>()

        // Collect from cache first
        for (symbol in symbols) {
            val cached = fundamentalsCache[symbol]
            if (cached != null && System.currentTimeMillis() - cached.second < FUNDAMENTALS_CACHE_TTL) {
                results[symbol] = cached.first
            } else {
                uncachedSymbols.add(symbol)
            }
        }

        if (uncachedSymbols.isNotEmpty()) {
            onLog("📑 Fetching fundamentals for ${uncachedSymbols.size} symbols (${results.size} cached)...")
            val freshData = remoteSource.getBatchFundamentals(uncachedSymbols)
            for ((sym, data) in freshData) {
                fundamentalsCache[sym] = data to System.currentTimeMillis()
                results[sym] = data
            }
            onLog("📑 Fetched ${freshData.size}/${uncachedSymbols.size} fundamentals. Quality filter ready.")
        }

        return results
    }
}
