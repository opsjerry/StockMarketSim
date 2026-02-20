package com.example.stockmarketsim.domain.analysis

import com.example.stockmarketsim.domain.model.StockQuote
import com.example.stockmarketsim.domain.strategy.MomentumStrategy
import com.example.stockmarketsim.domain.strategy.SafeHavenStrategy
import com.example.stockmarketsim.domain.strategy.Strategy
import com.example.stockmarketsim.domain.strategy.TradeSignal
import javax.inject.Inject

data class BacktestResult(
    val strategyId: String,
    val strategyName: String,
    val description: String,
    val returnPct: Double,
    val winRate: Double,
    val finalValue: Double,
    val benchmarkReturn: Double,
    val alpha: Double,
    val maxDrawdown: Double = 0.0,
    val sharpeRatio: Double = 0.0,
    val totalTrades: Int = 0  // For fee-adjusted tournament scoring
)

class Backtester @Inject constructor() {
    


    // Run a simulation on historical data
    suspend fun runBacktest(
        strategy: Strategy,
        marketData: Map<String, List<StockQuote>>, // Symbol -> Daily History (Sorted oldest to newest)
        initialCash: Double = 100000.0,
        benchmarkData: List<StockQuote>? = null, // e.g., NIFTY 50 History
        windowStart: Long? = null, // Optional limit: Only rebalance within this window
        windowEnd: Long? = null,   // Optional limit
        useDeterministicSlippage: Boolean = true, // Use fixed 0.2% for tournament comparison
        logger: (String) -> Unit = {}
    ): BacktestResult {
        val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
        fun fmt(date: Long) = dateFormat.format(java.util.Date(date))
        
        logger("Initializing Backtest for strategy: ${strategy.name}")
        var cash = initialCash
        val holdings = mutableMapOf<String, Double>() // Symbol -> Qty
        
        // Drive calendar using the first stock's history
        val dates = marketData.values.firstOrNull()?.map { it.date } ?: return BacktestResult(strategy.id, strategy.name, strategy.description, 0.0, 0.0, initialCash, 0.0, 0.0)
        
        if (dates.size < 20) return BacktestResult(strategy.id, strategy.name, strategy.description, 0.0, 0.0, initialCash, 0.0, 0.0)
        
        var successfulTrades = 0
        var totalTrades = 0
        
        // Start from day 200 (to allow Benchmark SMA 200 to warm up if present, otherwise 20)
        val startDay = if (benchmarkData != null) 200 else 20
        if (dates.size <= startDay) return BacktestResult(strategy.id, strategy.name, strategy.description, 0.0, 0.0, initialCash, 0.0, 0.0)

        // Trailing Stop Tracker
        val holdingHighs = mutableMapOf<String, Double>()

        // Metrics tracking
        var maxDrawdown = 0.0
        var peakEquity = initialCash
        val dailyReturns = mutableListOf<Double>()
        
        // For windowed evaluation, we track equity at window boundaries
        var equityAtWindowStart = initialCash
        var prevEquity = initialCash
        var windowEntryCaptured = false

        // Pre-allocate maps
        val symbols = marketData.keys.sorted()
        val rebalancer = PortfolioRebalancer() // Hoisted: avoid re-creating every day
        
        // Track entry prices for accurate win rate
        val entryPrices = mutableMapOf<String, Double>() // Symbol -> Avg Buy Price
        
        // Zero-Allocation: Cursor map to track current index for each stock
        // Initialize to 0 or -1
        val symbolCursors = symbols.associateWith { 0 }.toMutableMap()
        var benchCursor = 0

        for (i in startDay until dates.size) {
            val date = dates[i]
            
            // --- 1. Window Filter ---
            val isInsideWindow = (windowStart == null || date >= windowStart) && 
                                (windowEnd == null || date <= windowEnd)
            
            if (isInsideWindow && !windowEntryCaptured) {
                 equityAtWindowStart = prevEquity
                 windowEntryCaptured = true
            }

            // --- 2. Synchronize Cursors (Point to T-1) ---
            // We want cursor to point to the latest candle strictly BEFORE 'date' (or exactly 'date' if we trade on Close)
            // Existing logic assumes trading on Next Open based on Prior Close (T-1).
            // So for 'date', we want history ending at date[i-1].
            
            // Efficient Cursor Update (O(1) amortized)
            for (sym in symbols) {
                val history = marketData[sym] ?: continue
                var idx = symbolCursors[sym] ?: 0
                
                // Advance cursor: find largest idx where history[idx].date < date
                // Note: We use < date because we want T-1 data for the strategy
                while (idx + 1 < history.size && history[idx + 1].date < date) {
                    idx++
                }
                symbolCursors[sym] = idx
            }
            
            // Regime Filter (Benchmark uses simple index as it drives the dates usually, but safer to align too)
            var isBullMarket = true
            if (benchmarkData != null) {
                while (benchCursor + 1 < benchmarkData.size && benchmarkData[benchCursor + 1].date < date) {
                    benchCursor++
                }
                // T-1 Slice for Regime
                val benchHistoryT1 = if (benchCursor > 0 || benchmarkData[benchCursor].date < date) 
                                       benchmarkData.subList(0, benchCursor + 1) else emptyList()
                
                if (benchHistoryT1.isNotEmpty()) {
                    val regimeSignal = RegimeFilter.detectRegime(benchHistoryT1, 0.0)
                    isBullMarket = regimeSignal == RegimeSignal.BULLISH
                }
            }

            // --- 3. Get Allocations & Rebalance ---
            if (isInsideWindow) {
                var allocations: Map<String, Double> = emptyMap()
                
                if (isBullMarket) {
                    // ZERO-ALLOCATION STRATEGY CALL
                    allocations = strategy.calculateallocation(symbols, marketData, symbolCursors)
                }

                // --- RISK MANAGEMENT ---
                // Helper to safely get Price at T (Today)
                fun getPriceT(sym: String): Double {
                    val history = marketData[sym] ?: return 0.0
                    // We need price at 'date'. 
                    // Our cursor points to T-1 (< date). 
                    // So T should be potentially cursor + 1
                    val cursor = symbolCursors[sym] ?: 0
                    if (cursor + 1 < history.size && history[cursor + 1].date == date) {
                        return history[cursor + 1].close
                    }
                    return 0.0
                }
                
                // DEBUG LOG: Show Pre-Risk Allocations on first valid day
                if (totalTrades == 0 && allocations.isNotEmpty()) {
                     logger("🔍 First Allocation Signal: ${allocations.keys.take(3)}...")
                }

                allocations = allocations.filter { (sym, _) -> getPriceT(sym) > 50.0 }

                val symbolsToCut = mutableListOf<String>()
                for (sym in holdings.keys) {
                    val cursor = symbolCursors[sym] ?: 0
                    val symbolHistory = marketData[sym] ?: continue
                    
                    // RiskEngine needs history up to T-1 (cursor)
                    // Create view (Zero-copy)
                    val historyView = symbolHistory.subList(0, cursor + 1)
                    
                    val priceT = getPriceT(sym)
                    
                    if (priceT > 0) {
                        val high = holdingHighs[sym] ?: priceT
                        if (priceT > high) holdingHighs[sym] = priceT
                        
                        val atr = RiskEngine.calculateATR(historyView, 14)
                        val stopPrice = RiskEngine.calculateATRStopPrice(high, atr) 
                        
                        if (priceT < stopPrice) { 
                            symbolsToCut.add(sym)
                        }
                    }
                }
                
                if (symbolsToCut.isNotEmpty()) {
                    allocations = allocations.filterKeys { !symbolsToCut.contains(it) }
                    for (sym in symbolsToCut) holdingHighs.remove(sym)
                }

                val sectorGroups = allocations.entries.groupBy { com.example.stockmarketsim.domain.model.StockUniverse.sectorMap[it.key] ?: "OTHER" }
                val cappedAllocations = mutableMapOf<String, Double>()
                for (entry in sectorGroups) {
                    val entries = entry.value
                    val totalWeight = entries.sumOf { it.value }
                    if (totalWeight > 0.30) {
                        val factor = 0.30 / totalWeight
                        for (e in entries) cappedAllocations[e.key] = e.value * factor
                    } else {
                        for (e in entries) cappedAllocations[e.key] = e.value
                    }
                }
                allocations = cappedAllocations
                
                // --- Rebalance at Current Prices (T) ---
                val currentPricesT = symbols.associateWith { sym -> getPriceT(sym) }
                
                val holdingsValue = holdings.entries.sumOf { (sym, qty) -> 
                    qty * (currentPricesT[sym] ?: 0.0) 
                }
                val totalValue = cash + holdingsValue

                val costPct = if (useDeterministicSlippage) 0.002 else 0.001
                
                val rebalancerResult = rebalancer.calculateTrades(
                    cash, 
                    holdings, 
                    allocations, 
                    totalValue, 
                    currentPricesT, 
                    transactionCostPct = costPct,
                    useFixedSlippage = useDeterministicSlippage
                )

                cash = rebalancerResult.newCash
                holdings.clear()
                holdings.putAll(rebalancerResult.updatedHoldings)
                
                for (trade in rebalancerResult.trades) {
                    totalTrades++
                    if (trade.type == "BUY") {
                        // Track entry price for win rate
                        entryPrices[trade.symbol] = trade.executedPrice
                        val existingHigh = holdingHighs[trade.symbol] ?: 0.0
                        if (trade.executedPrice > existingHigh) holdingHighs[trade.symbol] = trade.executedPrice
                    }
                    if (trade.type == "SELL") {
                        // Only count as "successful" if sold above entry price
                        val entry = entryPrices[trade.symbol] ?: trade.executedPrice
                        if (trade.executedPrice >= entry) successfulTrades++
                        entryPrices.remove(trade.symbol)
                    }
                }
            }
            
            // --- 4. Metric Tracking ---
            // Re-use safe price lookup logic
            val eodPrices = symbols.associateWith { sym -> 
                val history = marketData[sym] ?: emptyList()
                val cursor = symbolCursors[sym] ?: 0
                // We want price at T (date)
                // Cursor points to T-1 (< date)
                if (cursor + 1 < history.size && history[cursor + 1].date == date) {
                     history[cursor + 1].close
                } else {
                     0.0
                }
            }
            val currentEquity = cash + holdings.entries.sumOf { (sym, qty) -> qty * (eodPrices[sym] ?: 0.0) }

            if (isInsideWindow) {
                if (currentEquity > peakEquity) peakEquity = currentEquity
                val currentDrawdown = (peakEquity - currentEquity) / peakEquity
                if (currentDrawdown > maxDrawdown) maxDrawdown = currentDrawdown
                
                val dailyRet = (currentEquity - prevEquity) / prevEquity
                dailyReturns.add(dailyRet)
            }
            
            prevEquity = currentEquity

            if (i % 30 == 0 && isInsideWindow) {
                logger("[INFO] Day ${fmt(date)}: ₹${"%.2f".format(currentEquity)}")
            }
            
            kotlinx.coroutines.yield()
        }
        
        // --- 5. Final Metrics (Window Adjusted) ---
        val totalEquity = prevEquity
        val returnPct = ((totalEquity - equityAtWindowStart) / equityAtWindowStart) * 100
        val winRate = if (totalTrades > 0) (successfulTrades.toDouble() / totalTrades) else 0.0
        
        val validReturns = dailyReturns.filter { !it.isNaN() }
        val avgDailyRet = if (validReturns.isNotEmpty()) validReturns.average() else 0.0
        val variance = if (validReturns.isNotEmpty()) validReturns.map { Math.pow(it - avgDailyRet, 2.0) }.average() else 0.0
        val stdDevDailyRet = Math.sqrt(variance)
        val sharpeRatio = if (stdDevDailyRet > 0.0001) (avgDailyRet / stdDevDailyRet) * Math.sqrt(252.0) else 0.0

        var benchReturn = 0.0
        var alpha = 0.0
        if (benchmarkData != null && benchmarkData.isNotEmpty()) {
            val startWindowDate = windowStart ?: dates[startDay]
            val endWindowDate = windowEnd ?: dates.last()
            
            val startPrice = benchmarkData.firstOrNull { it.date >= startWindowDate }?.close ?: benchmarkData.first().close
            val endPrice = benchmarkData.lastOrNull { it.date <= endWindowDate }?.close ?: benchmarkData.last().close
            
            benchReturn = ((endPrice - startPrice) / startPrice) * 100
            alpha = returnPct - benchReturn
        }

        return BacktestResult(
            strategyId = strategy.id,
            strategyName = strategy.name,
            description = strategy.description,
            returnPct = returnPct,
            winRate = winRate,
            finalValue = totalEquity,
            benchmarkReturn = benchReturn,
            alpha = alpha,
            maxDrawdown = maxDrawdown * 100, // as percentage
            sharpeRatio = sharpeRatio,
            totalTrades = totalTrades
        )
    }
}
