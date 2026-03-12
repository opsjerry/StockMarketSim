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
        val activeSimulations = simulations.filter { it.status == SimulationStatus.ACTIVE }
        if (activeSimulations.isEmpty()) return

        val activeIds = activeSimulations.map { it.id }

        // ── GLOBAL PIPELINE (runs once, shared by all simulations) ────────────────

        logManager.logToAll(activeIds, "🌅 Starting Daily Market Analysis...")

        // 1. Fetch Market Data (Throttled for Battery Optimization)
        val universe = universeProvider.getUniverse()
        val marketData = mutableMapOf<String, List<com.example.stockmarketsim.domain.model.StockQuote>>()

        // BATTERY OPTIMIZATION: Limit concurrency to 5 threads (was unbounded)
        val semaphore = kotlinx.coroutines.sync.Semaphore(5)

        coroutineScope {
            universe.map { symbol ->
                async<Pair<String, List<com.example.stockmarketsim.domain.model.StockQuote>>> {
                    semaphore.acquire()
                    try {
                         symbol to stockRepository.getStockHistory(symbol, TimeFrame.DAILY, 600)
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

        // Data coverage report — surfaces history failures to the sim log
        val failedHistorySymbols = universe.filter { it !in marketData }
        if (failedHistorySymbols.isNotEmpty()) {
            val preview = failedHistorySymbols.take(5).joinToString()
            val more = if (failedHistorySymbols.size > 5) " …and ${failedHistorySymbols.size - 5} more" else ""
            logManager.logToAll(activeIds,
                "⚠️ No price history for ${failedHistorySymbols.size} symbols: $preview$more — excluded from analysis.")
        }
        val coveragePct = if (universe.isNotEmpty()) marketData.size * 100 / universe.size else 0
        logManager.logToAll(activeIds,
            "📈 Market data ready: ${marketData.size}/${universe.size} symbols ($coveragePct% coverage).")

        val benchmarkSymbol = StockUniverse.BENCHMARK_INDEX // "^NSEI"
        val benchmarkHistory = stockRepository.getStockHistory(benchmarkSymbol, TimeFrame.DAILY, 365)

        // 2. Regime Filter Check (global — same market for all sims)
        val regimeSignal = RegimeFilter.detectRegime(benchmarkHistory, 0.0)
        val isBearMarket = regimeSignal == RegimeSignal.BEARISH

        if (isBearMarket) {
            logManager.logToAll(activeIds, "🐻 Bear Market Detected! Switching to safety mode (Targeting 0% equity exposure).")
        }

        // 3. Scheduling — compute once
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
        val isRebalanceDay = cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.MONDAY

        // 4. Quality Filter universe (global — computed once on rebalance days, shared across all sims)
        var qualityPassedUniverse: List<String> = universe

        // Determine if at least one sim will need rebalancing (Monday or empty portfolio)
        val anyNeedsRebalance = isRebalanceDay || activeSimulations.any { sim ->
            val portfolio = simulationRepository.getPortfolio(sim.id)
            portfolio.isEmpty() && sim.currentAmount > 1000.0
        }

        // FIX 1: Fundamentals + quality filter run once globally (shared across all sims).
        // The dead globalTournamentResult has been removed — the per-sim loop below
        // runs its own tournament with the correct per-sim parameters.
        if (anyNeedsRebalance && !isBearMarket) {
            val fundamentals = stockRepository.getBatchFundamentals(universe) { msg ->
                logManager.logToAll(activeIds, msg)
            }
            val (passed, rejected) = qualityFilter.filterWithReasons(universe, fundamentals)
            if (rejected.isNotEmpty()) {
                logManager.logToAll(activeIds, "🧹 Filtered out ${rejected.size} low-quality stocks (Low ROE / High Debt).")
            }
            qualityPassedUniverse = passed
        }

        // ── PER-SIMULATION SECTION ────────────────────────────────────────────────

        for (sim in activeSimulations) {
          try {

            // Broadcast system message to this sim if app was updated
            if (systemMessage != null) {
                logManager.log(sim.id, systemMessage)
            }

            var strategyId = sim.strategyId

            // 5a. Per-sim rebalancing gate
            val portfolio = simulationRepository.getPortfolio(sim.id)
            // FIX: FastStart is only valid for a GENUINELY new simulation (one that has never
            // completed a trade cycle). A simulation whose portfolio was emptied by an incomplete
            // prior run (e.g., app killed mid-rebalance) should wait for Monday, not re-trigger
            // the full tournament every day. We detect "never traded" by checking if totalEquity
            // equals currentAmount — a sim that has traded will have history entries that diverge.
            val hasNeverTraded = sim.totalEquity <= sim.currentAmount + 1.0 // within ₹1 rounding
            val isFastStart = portfolio.isEmpty() && sim.currentAmount > 1000.0 && (isRebalanceDay || hasNeverTraded)

            val qualityUniverse = if ((isRebalanceDay || isFastStart) && !isBearMarket) {
                // Run the sim-specific tournament with the correct per-sim target return AND duration
                logManager.log(sim.id, "🏎️ Auto-Pilot: Running Strategy Tournament (Backtesting 40+ Strategies)...")
                val simDurationDays = sim.durationMonths * 30
                val simTournamentResult = runStrategyTournamentUseCase(
                    marketData = marketData,
                    benchmarkData = benchmarkHistory,
                    initialCash = sim.currentAmount,
                    targetReturn = sim.targetReturnPercentage / 100.0,
                    simDurationDays = simDurationDays,
                    onProgress = { msg -> logManager.log(sim.id, msg) }
                )
                var bestStrategyId = simTournamentResult.candidates.firstOrNull()?.strategyId ?: "SAFE_HAVEN"

                // QUANT VERDICT: Sticky ML Model (Anchor)
                if (sim.strategyId == "MULTI_FACTOR_DNN" && bestStrategyId != "MULTI_FACTOR_DNN" && bestStrategyId != "SAFE_HAVEN") {
                    val currentMlResult = simTournamentResult.candidates.find { it.strategyId == "MULTI_FACTOR_DNN" }
                    val topChallenger = simTournamentResult.candidates.firstOrNull()

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

                // Save winning strategy for this sim
                val updatedSim = simulationRepository.getSimulationById(sim.id)?.copy(strategyId = bestStrategyId)
                if (updatedSim != null) {
                    simulationRepository.updateSimulation(updatedSim)
                    strategyId = updatedSim.strategyId
                }

                qualityPassedUniverse
            } else {
                universe
            }

            // Reload strategy (it might have changed if we ran the tournament)
            val currentSim = simulationRepository.getSimulationById(sim.id) ?: continue
            strategyId = currentSim.strategyId
            val strategy = strategyProvider.getStrategy(strategyId)

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
                if (portfolio.isEmpty()) {
                    logManager.log(sim.id, "🐻 Bear Market: Holding cash (portfolio is already empty).")
                } else {
                    logManager.log(sim.id, "🐻 Bear Market: Selling all positions to protect capital.")
                }
                emptyMap()
            } else if (isRebalanceDay || isFastStart) {
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
            targetAllocations = targetAllocations.filter { (sym, _) ->
                val livePrice = stockRepository.getStockQuote(sym)?.close
                val effectivePrice = if (livePrice != null && livePrice > 0) livePrice else {
                     val owned = portfolio.find { it.symbol == sym }
                     if (owned != null) owned.averagePrice else {
                         marketData[sym]?.lastOrNull()?.close ?: 0.0
                     }
                }
                effectivePrice > 50.0
            }

            // 4b. ATR Trailing Stop-Loss with Honeymoon Period (Phase 2 Fix)
            // Positions < 3 trading days old use a wider stop (3.5x ATR) to allow for
            // normal day-1 price discovery. Firing a 2.0x ATR stop on day 1 is "stop hunting".
            val now = System.currentTimeMillis()
            val THREE_TRADING_DAYS_MS = 3 * 24 * 60 * 60 * 1000L
            val newPortfolioMap = portfolio.associateBy { it.symbol }.toMutableMap()
            val symbolsToCut = mutableListOf<String>()
            for ((sym, item) in newPortfolioMap) {
                val symbolHistory = marketData[sym] ?: continue
                val currentPrice = stockRepository.getStockQuote(sym)?.close ?: continue
                if (currentPrice <= 0) continue

                val peakPrice = maxOf(item.highestPrice, currentPrice)
                if (currentPrice > item.highestPrice) {
                    newPortfolioMap[sym] = item.copy(highestPrice = currentPrice)
                }

                val atr = RiskEngine.calculateATR(symbolHistory, 14)
                val isVolatile = RiskEngine.isVolatile(symbolHistory)

                // Phase 2: Use wider stop for fresh positions (honeymoon)
                val daysSincePurchase = now - item.purchaseDate
                val stopMultiplier = if (daysSincePurchase < THREE_TRADING_DAYS_MS) 3.5 else 2.0
                val stopPrice = RiskEngine.calculateATRStopPrice(peakPrice, atr, stopMultiplier, isVolatile)

                if (currentPrice < stopPrice) {
                    val honeymoonTag = if (daysSincePurchase < THREE_TRADING_DAYS_MS) " [Honeymoon-widened stop]" else ""
                    symbolsToCut.add(sym)
                    logManager.log(sim.id, "🛑 Stop-Loss Hit: Selling $sym @ ₹${"%,.2f".format(currentPrice)} (stop ₹${"%,.2f".format(stopPrice)}, ${stopMultiplier}×ATR$honeymoonTag)")
                }
            }
            if (symbolsToCut.isNotEmpty()) {
                targetAllocations = targetAllocations.filterKeys { it !in symbolsToCut }
            }

            // ── FIX 2: MID-WEEK HOLD PATH ─────────────────────────────────────────────
            // App Bible §2F: "Non-Monday: Existing positions are held. Only risk-triggered
            // exits occur." Bypass the rebalancer entirely — execute stop-loss sells
            // directly, update equity, and continue to the next simulation.
            if (!isRebalanceDay && !isFastStart) {
                logManager.log(sim.id, "📊 Analysis Complete: Target ${newPortfolioMap.size - symbolsToCut.size} stocks. Stops triggered: ${symbolsToCut.size}. Market Mode: $regimeSignal")
                var holdCash = sim.currentAmount
                val holdTxList = mutableListOf<TransactionEntity>()
                for (sym in symbolsToCut) {
                    val item = newPortfolioMap.remove(sym) ?: continue
                    val sellPrice = stockRepository.getStockQuote(sym)?.close ?: item.averagePrice
                    if (sellPrice <= 0) continue
                    val gross = item.quantity * sellPrice
                    val commission = gross * 0.001
                    holdCash += gross - commission
                    holdTxList.add(TransactionEntity(
                        simulationId = sim.id, symbol = sym, type = "SELL",
                        amount = gross, price = sellPrice, quantity = item.quantity,
                        date = System.currentTimeMillis(),
                        reason = "Daily Strategy Execution ($strategyId)", brokerOrderId = null
                    ))
                    logManager.log(sim.id, "🔴 SELL $sym @ ₹${"%.2f".format(sellPrice)} | Qty: ${"%.0f".format(item.quantity)} | Value: ₹${"%.0f".format(gross)} (Daily Strategy Execution ($strategyId))")
                }
                val holdEquity = newPortfolioMap.values.sumOf { item ->
                    val p = stockRepository.getStockQuote(item.symbol)?.close ?: item.averagePrice
                    item.quantity * p
                }
                val holdTotal = holdCash + holdEquity
                simulationRepository.updatePortfolio(sim.id, newPortfolioMap.values.toList())
                simulationRepository.updateSimulation(currentSim.copy(currentAmount = holdCash, totalEquity = holdTotal))
                holdTxList.forEach { transactionDao.insertTransaction(it) }
                simulationRepository.insertHistory(sim.id, System.currentTimeMillis(), holdTotal)
                if (currentSim.isLiveTradingEnabled && holdTxList.isNotEmpty()) {
                    notificationManager.sendNotification("Live Trading Executed", "Executed ${holdTxList.size} stop-loss trades for ${currentSim.name}.")
                }
                logManager.log(sim.id, "🌙 Market Closed. Portfolio Value: ₹${"%,.2f".format(holdTotal)}")
                continue
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

            // 6. Update Database
            simulationRepository.updatePortfolio(sim.id, newPortfolioMap.values.toList())

            val finalPortfolioValue = newPortfolioMap.values.sumOf { item ->
                val price = stockRepository.getStockQuote(item.symbol)?.close ?: item.averagePrice
                item.quantity * price
            }
            val totalEquity = updatedCash + finalPortfolioValue

            val updatedSimFinal = currentSim.copy(
                currentAmount = updatedCash,
                totalEquity = totalEquity
            )
            simulationRepository.updateSimulation(updatedSimFinal)

            transactions.forEach { transactionDao.insertTransaction(it) }

            simulationRepository.insertHistory(sim.id, System.currentTimeMillis(), totalEquity)

            logManager.log(sim.id, "🌙 Market Closed. Portfolio Value: ₹${"%,.2f".format(totalEquity)}")

            if (currentSim.isLiveTradingEnabled) {
                 notificationManager.sendNotification(
                    "Live Trading Executed",
                    "Executed ${transactions.size} trades for ${currentSim.name}."
                )
            }

          } catch (e: kotlinx.coroutines.CancellationException) {
            // OS killed the coroutine (battery optimisation, WorkManager timeout, app backgrounded).
            // Use NonCancellable so the log write survives the cancelled scope.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                logManager.log(sim.id,
                    "⚠️ Simulation interrupted mid-run (system killed the job — low memory / battery saver). " +
                    "Portfolio unchanged. Will retry in ~5 minutes.")
                android.util.Log.w("RunDailySimulationUseCase",
                    "Coroutine cancelled for sim ${sim.id} — ${e.message}")
            }
            throw e  // Re-throw so the coroutine cancels normally
          } catch (e: Exception) {
            logManager.log(sim.id,
                "❌ Simulation error: ${e.javaClass.simpleName}: ${e.message?.take(120) ?: "Unknown error"}. " +
                "Retrying automatically.")
            android.util.Log.e("RunDailySimulationUseCase",
                "Error in sim ${sim.id}", e)
            throw e  // Re-throw so DailySimulationWorker triggers retry
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

                // FIX 4: Preserve purchaseDate for top-up BUYs (avoids resetting honeymoon
                // period). Only set to now() for brand-new positions (currentItem == null).
                // Positions migrated from DB v11 with purchaseDate=0 get refreshed here.
                val purchaseDate = if (currentItem != null && currentItem.purchaseDate > 0L)
                    currentItem.purchaseDate
                else
                    System.currentTimeMillis()

                newPortfolio[symbol] = PortfolioItem(
                    id = currentItem?.id ?: 0,
                    symbol = symbol,
                    quantity = newQty,
                    averagePrice = newAvg,
                    highestPrice = currentItem?.highestPrice ?: price,
                    purchaseDate = purchaseDate
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
