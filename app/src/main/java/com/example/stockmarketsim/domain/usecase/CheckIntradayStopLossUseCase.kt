package com.example.stockmarketsim.domain.usecase

import com.example.stockmarketsim.data.local.dao.PortfolioDao
import com.example.stockmarketsim.data.local.dao.TransactionDao
import com.example.stockmarketsim.data.local.entity.TransactionEntity
import com.example.stockmarketsim.data.manager.SimulationLogManager
import com.example.stockmarketsim.data.remote.ZerodhaSource
import com.example.stockmarketsim.data.repository.SimulationRepositoryImpl
import com.example.stockmarketsim.domain.analysis.RiskEngine
import com.example.stockmarketsim.domain.model.SimulationStatus
import com.example.stockmarketsim.domain.repository.StockRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intra-day stop-loss checker — runs every 30 minutes during market hours.
 *
 * Design notes (App Bible §9):
 * - Uses Zerodha real-time lastPrice as primary price source.
 * - Falls back to Room-cached close price IF the cache is fresh (<12 h, i.e. same trading session).
 * - If neither source can provide same-session data, the check is SKIPPED for that symbol.
 *   Never trigger on yesterday's close — a stale price produces false "safe" readings.
 * - ATR parameters (14-period, 2.0× standard / 3.5× honeymoon) are unchanged from the daily runner.
 *   Whipsaw protection comes from the 2-consecutive-check confirmation filter, not from a wider multiplier.
 * - Singleton scope: firstBreachMap persists across 30-min worker invocations to implement the filter.
 *
 * Quant concern resolved (Issue 1 from review): a stop is only executed if the price has been
 * below the stop for TWO consecutive 30-min checks (≈ 60 minutes of sustained breach).
 */
@Singleton
class CheckIntradayStopLossUseCase @Inject constructor(
    private val simulationRepository: SimulationRepositoryImpl,
    private val stockRepository: StockRepository,
    private val zerodhaSource: ZerodhaSource,
    private val logManager: SimulationLogManager,
    private val transactionDao: TransactionDao,
    private val portfolioDao: PortfolioDao,
    private val notificationManager: com.example.stockmarketsim.data.manager.AppNotificationManager
) {
    // 2-check confirmation filter: "simId:symbol" → timestamp of first breach.
    // In-memory only — resets on app restart (acceptable; RestartResilience §7H).
    private val firstBreachMap = mutableMapOf<String, Long>()

    // Minimum sustain window (≈ one 30-min polling interval).
    private val BREACH_CONFIRMATION_MS = 25 * 60 * 1000L // 25 min (slightly less than 30 to allow timing jitter)

    // Price cache freshness threshold: only use cached price if it's from the same trading session.
    private val SAME_SESSION_MAX_AGE_MS = 12 * 60 * 60 * 1000L

    suspend operator fun invoke() {
        val simulations = simulationRepository.getSimulations().first()
        val activeSimulations = simulations.filter { it.status == SimulationStatus.ACTIVE }
        if (activeSimulations.isEmpty()) return

        val zerodhaActive = zerodhaSource.isSessionActive()
        var totalDeferred = 0
        var consecutiveAllDeferredCycles = 0 // tracked in worker, not here

        for (sim in activeSimulations) {
            val portfolio = simulationRepository.getPortfolio(sim.id)
            if (portfolio.isEmpty()) continue

            var updatedCash = sim.currentAmount
            val updatedPortfolio = portfolio.associateBy { it.symbol }.toMutableMap()
            val stopTriggers = mutableListOf<TransactionEntity>()

            for (item in portfolio) {
                val sym = item.symbol
                val breachKey = "${sim.id}:$sym"

                // ── Price Resolution (Zerodha → fresh Room cache → skip) ─────────────
                val currentPrice: Double? = if (zerodhaActive) {
                    zerodhaSource.getQuote(sym)?.close
                } else {
                    // Zerodha unavailable — check Room cache freshness
                    val cached = stockRepository.getStockQuote(sym)
                    val ageMs = System.currentTimeMillis() - (cached?.date ?: 0L)
                    if (ageMs < SAME_SESSION_MAX_AGE_MS) cached?.close else null
                }

                if (currentPrice == null || currentPrice <= 0.0) {
                    totalDeferred++
                    android.util.Log.d("IntradayStopLoss",
                        "⚠️ No live price for $sym (sim ${sim.id}) — stop check deferred this cycle.")
                    firstBreachMap.remove(breachKey) // reset any pending breach if price lost
                    continue
                }

                // ── Update trailing highestPrice ──────────────────────────────────────
                // Issue 3 from quant review: highestPrice must be updated intra-day too.
                if (currentPrice > item.highestPrice) {
                    portfolioDao.updateHighestPrice(sim.id, sym, currentPrice)
                    updatedPortfolio[sym] = item.copy(highestPrice = currentPrice)
                    firstBreachMap.remove(breachKey) // price recovered → reset breach
                }

                // ── ATR Stop Calculation (same parameters as daily runner) ──────────
                val symbolHistory = try {
                    stockRepository.getStockHistory(sym,
                        com.example.stockmarketsim.domain.model.TimeFrame.DAILY, 600)
                } catch (e: Exception) { emptyList() }

                if (symbolHistory.size < 15) continue // not enough data for ATR(14)

                val peakPrice = maxOf(item.highestPrice, currentPrice)
                val atr = RiskEngine.calculateATR(symbolHistory, 14)
                val isVolatile = RiskEngine.isVolatile(symbolHistory)

                val now = System.currentTimeMillis()
                val THREE_TRADING_DAYS_MS = 3 * 24 * 60 * 60 * 1000L
                val daysSincePurchase = now - item.purchaseDate
                val stopMultiplier = if (daysSincePurchase < THREE_TRADING_DAYS_MS) 3.5 else 2.0

                val stopPrice = RiskEngine.calculateATRStopPrice(peakPrice, atr, stopMultiplier, isVolatile)

                // ── 2-Check Confirmation Filter (Quant Issue 1) ───────────────────
                if (currentPrice < stopPrice) {
                    val firstBreach = firstBreachMap.getOrPut(breachKey) { now }
                    val sustainedMs = now - firstBreach

                    if (sustainedMs < BREACH_CONFIRMATION_MS) {
                        // First breach detected — wait for next cycle to confirm
                        android.util.Log.d("IntradayStopLoss",
                            "⚡ First breach detected for $sym @ ₹${"%.2f".format(currentPrice)} " +
                            "(stop ₹${"%.2f".format(stopPrice)}). Awaiting confirmation.")
                        continue
                    }

                    // ── Confirmed Breach → Execute Stop ──────────────────────────
                    firstBreachMap.remove(breachKey)
                    val honeymoonTag = if (daysSincePurchase < THREE_TRADING_DAYS_MS) " [Honeymoon]" else ""
                    val gross = item.quantity * currentPrice
                    val commission = gross * 0.001
                    val netCash = gross - commission

                    updatedCash += netCash
                    updatedPortfolio.remove(sym)

                    stopTriggers.add(TransactionEntity(
                        simulationId = sim.id,
                        symbol = sym,
                        type = "SELL",
                        amount = gross,
                        price = currentPrice,
                        quantity = item.quantity,
                        date = now,
                        reason = "Intra-Day Stop-Loss (${stopMultiplier}×ATR$honeymoonTag)",
                        brokerOrderId = null
                    ))

                    logManager.log(sim.id,
                        "⚡ INTRADAY STOP: $sym breached ATR stop ₹${"%.2f".format(stopPrice)} " +
                        "(price ₹${"%.2f".format(currentPrice)}, ${stopMultiplier}×ATR, confirmed over 30 min)$honeymoonTag")
                    logManager.log(sim.id,
                        "🔴 SELL $sym @ ₹${"%.2f".format(currentPrice)} | " +
                        "Qty: ${"%.0f".format(item.quantity)} | Value: ₹${"%.0f".format(gross)} (Intra-Day Stop-Loss)")

                } else {
                    // Price above stop — reset any pending breach
                    firstBreachMap.remove(breachKey)
                }
            }

            // ── Commit DB writes (Issue 5: cash update must be last) ─────────────
            if (stopTriggers.isNotEmpty()) {
                simulationRepository.updatePortfolio(sim.id, updatedPortfolio.values.toList())
                stopTriggers.forEach { transactionDao.insertTransaction(it) }

                val finalPortfolioValue = updatedPortfolio.values.sumOf { portItem ->
                    stockRepository.getStockQuote(portItem.symbol)?.close ?: portItem.averagePrice * portItem.quantity
                }
                val newTotalEquity = updatedCash + finalPortfolioValue
                // updateSimulation() is the final write — Room's SQLite serialization
                // ensures the daily runner reads the committed cash after this point.
                simulationRepository.updateSimulation(
                    sim.copy(currentAmount = updatedCash, totalEquity = newTotalEquity)
                )
                simulationRepository.insertHistory(sim.id, System.currentTimeMillis(), newTotalEquity)

                val title = if (sim.isLiveTradingEnabled) "Live Intraday Stop Hit" else "Paper Intraday Stop Hit"
                val mode = if (sim.isLiveTradingEnabled) "via Zerodha" else "(Virtual - Manual Action Required)"
                notificationManager.sendNotification(
                    title,
                    "Executed ${stopTriggers.size} intra-day stop-loss sell(s) for ${sim.name} $mode."
                )
            }
        }

        if (totalDeferred > 0) {
            android.util.Log.w("IntradayStopLoss",
                "⚠️ $totalDeferred symbol checks deferred this cycle — Zerodha session inactive or stale cache.")
        }
    }
}
