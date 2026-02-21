package com.example.stockmarketsim.domain.usecase

import com.example.stockmarketsim.data.local.entity.TransactionEntity
import com.example.stockmarketsim.data.repository.SimulationRepositoryImpl
import com.example.stockmarketsim.domain.model.SimulationStatus
import com.example.stockmarketsim.domain.model.TimeFrame
import com.example.stockmarketsim.domain.repository.StockRepository
import com.example.stockmarketsim.domain.strategy.StrategyProvider
import com.example.stockmarketsim.data.local.dao.TransactionDao
import com.example.stockmarketsim.domain.analysis.PortfolioRebalancer
import javax.inject.Inject

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

import com.example.stockmarketsim.domain.analysis.RiskEngine
import com.example.stockmarketsim.domain.analysis.RegimeFilter
import com.example.stockmarketsim.domain.analysis.RegimeSignal
import com.example.stockmarketsim.domain.model.StockUniverse
import com.example.stockmarketsim.data.manager.AppNotificationManager
import com.example.stockmarketsim.domain.model.PortfolioItem

class RunDailySimulationUseCase @Inject constructor(
    private val simulationRepository: SimulationRepositoryImpl,
    private val stockRepository: StockRepository,
    private val strategyProvider: StrategyProvider,
    private val transactionDao: TransactionDao,
    private val logManager: com.example.stockmarketsim.data.manager.SimulationLogManager,
    private val notificationManager: AppNotificationManager,
    private val runStrategyTournamentUseCase: RunStrategyTournamentUseCase,
    private val universeProvider: com.example.stockmarketsim.domain.provider.StockUniverseProvider,
    private val zerodhaBrokerSource: com.example.stockmarketsim.data.remote.ZerodhaBrokerSource,
    private val qualityFilter: com.example.stockmarketsim.domain.analysis.QualityFilter
) {
    suspend operator fun invoke(systemMessage: String? = null) {
        val simulations = simulationRepository.getSimulations().first()
        
        for (sim in simulations) {
            if (sim.status != SimulationStatus.ACTIVE) continue

            logManager.log(sim.id, "🌅 Starting Daily Market Analysis...")
            
            // 1. Fetch Market Data (Throttled for Battery Optimization)
            val universe = universeProvider.getUniverse()
            val marketData = mutableMapOf<String, List<com.example.stockmarketsim.domain.model.StockQuote>>()
            
            // BATTERY OPTIMIZATION: Limit concurrency to 5 threads (was unbounded)
            // This prevents "heating up" the phone by firing 200 network requests at once.
            val semaphore = kotlinx.coroutines.sync.Semaphore(5)
            
            coroutineScope {
                universe.map { symbol ->
                    async<Pair<String, List<com.example.stockmarketsim.domain.model.StockQuote>>> {
                        semaphore.acquire()
                        try {
                             symbol to stockRepository.getStockHistory(symbol, TimeFrame.DAILY, 365)
                        } finally {
                             semaphore.release()
                        }
                    }
                }.awaitAll().forEach { (symbol, history) ->
                    if (history.isNotEmpty()) {
                        marketData[symbol] = history
                    }
                }
            }
            
            val benchmarkSymbol = StockUniverse.BENCHMARK_INDEX // "^NSEI"
            val benchmarkHistory = stockRepository.getStockHistory(benchmarkSymbol, TimeFrame.DAILY, 365)
            
            // 3. Regime Filter Check
            val regimeSignal = RegimeFilter.detectRegime(benchmarkHistory, 0.0)
            val isBearMarket = regimeSignal == RegimeSignal.BEARISH
            
            // Fix: Declare initial strategyId for logging (before potential update)
            var strategyId = sim.strategyId

            if (isBearMarket && strategyId != "SAFE_HAVEN") {
                logManager.log(sim.id, "🐻 Bear Market Detected! Switching to safety mode (selling risky assets).")
            }

            // 3b. Weekly Rebalancing Gate: Only compute new allocations on Mondays OR Fast Start
            //     Risk management (stops, regime filter) still runs DAILY
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
            val isRebalanceDay = cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.MONDAY

            // 4. Calculate Target Allocations (matching Backtester approach)
            val portfolio = simulationRepository.getPortfolio(sim.id)
            val isFastStart = portfolio.isEmpty() && sim.currentAmount > 1000.0 // New simulation or all-cash: deploy immediately

            // 3c. Quality Filter: Remove stocks with poor fundamentals (ROE < 12%, D/E > 1.0)
            //     Only fetch fundamentals on rebalance days to minimize API calls
            val qualityUniverse = if ((isRebalanceDay || isFastStart) && !isBearMarket) {
                // OPTIMIZATION: Only run the Heavy Strategy Tournament when we are actually going to rebalance!
                // This saves ~6 minutes of processing on Tue-Fri.
                logManager.log(sim.id, "🏎️ Auto-Pilot: Running Strategy Tournament (Backtesting 40+ Strategies)...")
                val tournamentResult = runStrategyTournamentUseCase(marketData, benchmarkHistory, sim.currentAmount, 0.20)
                var bestStrategyId = tournamentResult.candidates.firstOrNull()?.strategyId ?: "SAFE_HAVEN"
                
                // QUANT VERDICT: Sticky ML Model (Anchor)
                // If the current strategy is the AI, require the challenger to beat it by a significant margin (1.5x Alpha)
                if (sim.strategyId == "MULTI_FACTOR_DNN" && bestStrategyId != "MULTI_FACTOR_DNN" && bestStrategyId != "SAFE_HAVEN") {
                    val currentMlResult = tournamentResult.candidates.find { it.strategyId == "MULTI_FACTOR_DNN" }
                    val topChallenger = tournamentResult.candidates.firstOrNull()
                    
                    if (currentMlResult != null && topChallenger != null) {
                        val marginRequired = if (currentMlResult.alpha > 0) currentMlResult.alpha * 1.5 else currentMlResult.alpha + 2.0
                        
                        if (topChallenger.alpha < marginRequired) {
                            logManager.log(sim.id, "🛡️ Quant Guard: Retaining ML Model. Challenger '${topChallenger.strategyId}' Alpha (${"%.2f".format(topChallenger.alpha)}%) didn't beat ML (${"%.2f".format(currentMlResult.alpha)}%) by required margin.")
                            bestStrategyId = "MULTI_FACTOR_DNN"
                        } else {
                            logManager.log(sim.id, "⚠️ Regime Shift: Challenger '${topChallenger.strategyId}' Alpha (${"%.2f".format(topChallenger.alpha)}%) significantly outperformed ML Model (${"%.2f".format(currentMlResult.alpha)}%). Switching strategy.")
                        }
                    }
                }
                
                // Reload simulation after strategy switch
                // NOTE: The tournament updates the DB, so we must reload the simulation object to get the new strategyId
                val updatedSim = simulationRepository.getSimulationById(sim.id)?.copy(strategyId = bestStrategyId) 
                if (updatedSim != null) {
                    simulationRepository.updateSimulation(updatedSim)
                    strategyId = updatedSim.strategyId // Update local variable
                }

                val fundamentals = stockRepository.getBatchFundamentals(universe) { msg ->
                    logManager.log(sim.id, msg)
                }
                val (passed, rejected) = qualityFilter.filterWithReasons(universe, fundamentals)
                if (rejected.isNotEmpty()) {
                    logManager.log(sim.id, "🧹 Filtered out ${rejected.size} low-quality stocks (Low ROE / High Debt).")
                }
                passed
            } else {
                universe // Non-rebalance days: use full universe (positions held anyway)
            }
            
            // Reload strategy (it might have changed if we ran the tournament)
            val currentSim = simulationRepository.getSimulationById(sim.id) ?: continue // Reload to get fresh state for rebalancing
            // Update strategyId one last time to be sure
            strategyId = currentSim.strategyId
            val strategy = strategyProvider.getStrategy(strategyId)
            
            // Log for Skipped Tournament
            if (!isRebalanceDay && !isFastStart) {
                 logManager.log(sim.id, "⏩ Daily Update: Skipping Strategy Tournament (Runs on Mondays). checking stops/regime...")
            }
            
            // Log for Skipped Tournament
            if (!isRebalanceDay && !isFastStart) {
                 logManager.log(sim.id, "⏩ Daily Update: Skipping Strategy Tournament (Runs on Mondays). checking stops/regime...")
            }

            val currentPortfolioSnapshot = portfolio.associate { item ->
                val quote = stockRepository.getStockQuote(item.symbol)
                item.symbol to (quote ?: com.example.stockmarketsim.domain.model.StockQuote(
                    symbol = item.symbol,
                    date = System.currentTimeMillis(),
                    open = item.averagePrice,
                    high = item.averagePrice,
                    low = item.averagePrice,
                    close = item.averagePrice,
                    volume = 0
                ))
            }
            
            var targetAllocations = if (isBearMarket) {
                logManager.log(sim.id, "🐻 Bear Market Detected. Selling all positions.")
                emptyMap() // Defensive: sell all in bear market regardless of day
            } else if (isRebalanceDay || isFastStart) {
                // Monday: Compute fresh allocations from strategy (using quality-filtered universe)
                if (isFastStart && !isRebalanceDay) {
                     logManager.log(sim.id, "🚀 FAST START: Immediate deployment triggered (Empty Portfolio).")
                } else {
                     logManager.log(sim.id, "🗓️ Weekly Strategy Update: Computing new stock picks from ${qualityUniverse.size} candidates...")
                }
                
                val cursors = marketData.mapValues { (_, history) -> history.lastIndex }
                val allocs = strategy.calculateallocation(qualityUniverse, marketData, cursors)
                if (allocs.isEmpty()) {
                    logManager.log(sim.id, "⚠️ Strategy returned 0 allocations. (Check Strategy Logic)")
                } else {
                    logManager.log(sim.id, "✅ Strategy selected ${allocs.size} stocks. Top: ${allocs.keys.take(3)}")
                }
                allocs
            } else {
                // Non-Monday: Hold current positions (only risk management can force sells)
                logManager.log(sim.id, "⏳ Mid-week logic: Holding positions (waiting for Monday rebalance).")
                val totalValue = portfolio.sumOf { it.quantity * (stockRepository.getStockQuote(it.symbol)?.close ?: it.averagePrice) } + sim.currentAmount
                if (totalValue > 0) {
                    portfolio.associate { item ->
                        val price = stockRepository.getStockQuote(item.symbol)?.close ?: item.averagePrice
                        item.symbol to ((item.quantity * price) / totalValue)
                    }
                } else emptyMap()
            }

            // --- RISK MANAGEMENT (mirroring Backtester.kt) ---
            
            // 4a. Min Price Filter: Remove stocks below ₹50 (avoid penny stocks)
            // BUG FIX: On holidays/weekends, getStockQuote might return 0.0 or null.
            // We MUST fallback to the average price or last known price to avoid accidentally selling everything.
            targetAllocations = targetAllocations.filter { (sym, _) ->
                val livePrice = stockRepository.getStockQuote(sym)?.close
                val effectivePrice = if (livePrice != null && livePrice > 0) livePrice else {
                     // Fallback: Check if we own it, use avg price. If not, check history.
                     val owned = portfolio.find { it.symbol == sym }
                     if (owned != null) owned.averagePrice else {
                         marketData[sym]?.lastOrNull()?.close ?: 0.0
                     }
                }
                
                effectivePrice > 50.0
            }
            
            // 4b. ATR Trailing Stop-Loss: Force sell positions that hit stop
            val newPortfolioMap = portfolio.associateBy { it.symbol }.toMutableMap()
            val symbolsToCut = mutableListOf<String>()
            for ((sym, item) in newPortfolioMap) {
                val symbolHistory = marketData[sym] ?: continue
                val currentPrice = stockRepository.getStockQuote(sym)?.close ?: continue
                if (currentPrice <= 0) continue
                
                // Track highest price for trailing stop
                val peakPrice = maxOf(item.highestPrice, currentPrice)
                if (currentPrice > item.highestPrice) {
                    // Update highestPrice in portfolio for next run
                    newPortfolioMap[sym] = item.copy(highestPrice = currentPrice)
                }
                
                val atr = RiskEngine.calculateATR(symbolHistory, 14)
                val isVolatile = RiskEngine.isVolatile(symbolHistory)
                val stopPrice = RiskEngine.calculateATRStopPrice(peakPrice, atr, 2.0, isVolatile)
                
                if (currentPrice < stopPrice) {
                    symbolsToCut.add(sym)
                    logManager.log(sim.id, "🛑 Stop-Loss Hit: Selling $sym @ ₹${"%,.2f".format(currentPrice)} (Fell below stop price ₹${"%,.2f".format(stopPrice)})")
                }
            }
            // Remove stopped-out positions from target allocations (force sell)
            if (symbolsToCut.isNotEmpty()) {
                targetAllocations = targetAllocations.filterKeys { it !in symbolsToCut }
            }
            
            // 4c. Sector Exposure Cap: Max 30% per sector
            val sectorGroups = targetAllocations.entries.groupBy { StockUniverse.sectorMap[it.key] ?: "OTHER" }
            val cappedAllocations = mutableMapOf<String, Double>()
            for ((_, entries) in sectorGroups) {
                val totalWeight = entries.sumOf { it.value }
                if (totalWeight > 0.30) {
                    val factor = 0.30 / totalWeight
                    for (e in entries) cappedAllocations[e.key] = e.value * factor
                } else {
                    for (e in entries) cappedAllocations[e.key] = e.value
                }
            }
            targetAllocations = cappedAllocations
            
            logManager.log(sim.id, "📊 Analysis Complete: Target ${targetAllocations.size} stocks. Stops triggered: ${symbolsToCut.size}. Market Mode: $regimeSignal")

            // 5. Rebalance
            var cash = sim.currentAmount
            
            val currentEquity = newPortfolioMap.values.sumOf { item ->
                val price = stockRepository.getStockQuote(item.symbol)?.close ?: item.averagePrice
                item.quantity * price 
            } + cash
            
            val transactions = mutableListOf<TransactionEntity>()
            val rebalanceReason = "Daily Strategy Execution ($strategyId)"
            val symbolReasons = targetAllocations.keys.associateWith { "Strategy Allocation" }

            val updatedCash = executeRebalancing(
                simId = sim.id,
                isLiveTradingEnabled = currentSim.isLiveTradingEnabled,
                targetAllocations = targetAllocations,
                newPortfolio = newPortfolioMap,
                initialCash = cash,
                totalPortfolioValue = currentEquity,
                transactions = transactions,
                reason = rebalanceReason,
                symbolReasons = symbolReasons
            )
            
            // 5. Update Database
            simulationRepository.updatePortfolio(sim.id, newPortfolioMap.values.toList())
            
            val finalPortfolioValue = newPortfolioMap.values.sumOf { item ->
                val price = stockRepository.getStockQuote(item.symbol)?.close ?: item.averagePrice
                item.quantity * price 
            }
            val totalEquity = updatedCash + finalPortfolioValue
            
            val updatedSim = currentSim.copy(
                currentAmount = updatedCash,
                totalEquity = totalEquity
            )
            simulationRepository.updateSimulation(updatedSim)
            
            transactions.forEach { transactionDao.insertTransaction(it) }
            
            simulationRepository.insertHistory(sim.id, System.currentTimeMillis(), totalEquity)
            
            logManager.log(sim.id, "🌙 Market Closed. Portfolio Value: ₹${"%,.2f".format(totalEquity)}")
            
            if (currentSim.isLiveTradingEnabled) {
                 notificationManager.sendNotification(
                    "Live Trading Executed", 
                    "Executed ${transactions.size} trades for ${currentSim.name}."
                )
            }
        }
    }

    private suspend fun executeRebalancing(
        simId: Int,
        isLiveTradingEnabled: Boolean,
        targetAllocations: Map<String, Double>,
        newPortfolio: MutableMap<String, PortfolioItem>,
        initialCash: Double,
        totalPortfolioValue: Double,
        transactions: MutableList<TransactionEntity>,
        reason: String,
        symbolReasons: Map<String, String>
    ): Double {
        val currentHoldings = newPortfolio.mapValues { it.value.quantity }
        val currentPrices = (targetAllocations.keys + currentHoldings.keys).distinct().associateWith { sym ->
            stockRepository.getStockQuote(sym)?.close ?: 0.0
        }.filterValues { it > 0.0 }

        val rebalancer = PortfolioRebalancer()
        val result: PortfolioRebalancer.RebalanceResult = rebalancer.calculateTrades(
            currentCash = initialCash,
            currentHoldings = currentHoldings,
            targetAllocations = targetAllocations,
            totalPortfolioValue = totalPortfolioValue,
            currentPrices = currentPrices,
            reason = reason,
            symbolReasons = symbolReasons
        )
        
        for (trade in result.trades) {
            val symbol = trade.symbol
            val type = trade.type
            val price = trade.executedPrice
            val qty = trade.quantity
            val amount = trade.amount
            val reason = trade.reason ?: "Rebalance"

            // --- LIVE EXECUTION LOGIC ---
            var brokerOrderId: String? = null
            if (isLiveTradingEnabled) {
                // Market Hours Check (NSE: 9:15 AM - 3:30 PM IST)
                val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
                val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                val minute = cal.get(java.util.Calendar.MINUTE)
                val isMarketOpen = (hour > 9 || (hour == 9 && minute >= 15)) && (hour < 15 || (hour == 15 && minute <= 30))

                if (!isMarketOpen) {
                    logManager.log(simId, "⚡ Market closed ($hour:${"%02d".format(minute)} IST). Simulation-only mode.")
                } else {
                    try {
                        if (zerodhaBrokerSource.isConnected()) {
                            val tag = "SIM_${simId}_${System.currentTimeMillis()}"
                            val isBuy = type == "BUY"
                            
                            // SAFETY: Floor to whole shares (exchanges don't accept fractional)
                            val intQty = kotlin.math.floor(qty).toInt()
                            
                            // SAFETY: Max order value cap (₹50,000)
                            val orderValue = intQty * price
                            val MAX_ORDER_VALUE = 50000.0
                            if (orderValue > MAX_ORDER_VALUE) {
                                logManager.log(simId, "⚡ ⚠️ Order value ₹${"%,.0f".format(orderValue)} exceeds ₹${"%,.0f".format(MAX_ORDER_VALUE)} cap. Skipped $type $symbol.")
                            } else if (intQty > 0) {
                                // Execute on Broker
                                brokerOrderId = if (isBuy) {
                                    zerodhaBrokerSource.placeBuyOrder(symbol, intQty, price, tag)
                                } else {
                                    zerodhaBrokerSource.placeSellOrder(symbol, intQty, price, tag)
                                }
                                
                                val icon = if (isBuy) "🟢" else "🔴"
                                logManager.log(simId, "⚡ $icon $type $symbol | Qty: $intQty | Price: $price | OrderID: $brokerOrderId")
                            } else {
                                logManager.log(simId, "⚡ ⚠️ Skipped $type $symbol (Qty rounds to 0 shares).")
                            }
                        } else {
                            logManager.log(simId, "⚡ ⚠️ Skipped $type $symbol (Broker Not Connected). Simulation Only.")
                        }
                    } catch (e: Exception) {
                         logManager.log(simId, "⚡ ❌ FAILED $type $symbol: ${e.message}. Reverting to Simulation.")
                    }
                }
            }

            if (type == "SELL") {
                val currentItem = newPortfolio[symbol]
                val currentQty: Double = currentItem?.quantity ?: 0.0
                val newQty: Double = currentQty - (qty as Double)
                if (newQty <= 0.001) {
                    newPortfolio.remove(symbol)
                } else {
                    newPortfolio[symbol] = currentItem!!.copy(quantity = newQty)
                }
            } else {
                val currentItem = newPortfolio[symbol]
                val currentQty: Double = currentItem?.quantity ?: 0.0
                val newQty: Double = currentQty + (qty as Double)
                val oldAvg = currentItem?.averagePrice ?: 0.0
                val newAvg = ((currentQty * oldAvg) + (qty * price)) / newQty

                newPortfolio[symbol] = PortfolioItem(
                    id = currentItem?.id ?: 0,
                    symbol = symbol,
                    quantity = newQty,
                    averagePrice = newAvg,
                    highestPrice = currentItem?.highestPrice ?: price
                )
            }

            // Add brokerOrderId to Transaction
            transactions.add(TransactionEntity(
                simulationId = simId,
                symbol = symbol,
                type = type,
                amount = amount,
                price = trade.executedPrice,
                quantity = qty,
                date = System.currentTimeMillis(),
                reason = reason,
                brokerOrderId = brokerOrderId
            ))
            
            val icon = if (type == "BUY") "🟢" else "🔴"
            logManager.log(simId, "$icon $type $symbol @ ₹${"%.2f".format(trade.executedPrice)} | Qty: ${"%.0f".format(qty)} | Value: ₹${"%.0f".format(amount)} ($reason)")
        }

        return result.newCash
    }
}
