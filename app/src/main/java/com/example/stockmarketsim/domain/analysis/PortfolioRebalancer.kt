package com.example.stockmarketsim.domain.analysis

/**
 * Shared logic for executing trades and rebalancing a portfolio.
 * Used by both Backtester (for speed) and RunDailySimulationUseCase (for persistence).
 */
class PortfolioRebalancer(
    private val commissionPct: Double = 0.001, // 0.1% Commission/STT
    private val minTradeValue: Double = 500.0,
    private val minAllocationChange: Double = 0.005 // 0.5% minimum change to justify a trade
) {

    data class RebalanceResult(
        val newCash: Double,
        val updatedHoldings: Map<String, Double>, // Symbol -> New Qty
        val trades: List<TradeAction>
    )

    data class TradeAction(
        val symbol: String,
        val type: String, // "BUY" or "SELL"
        val quantity: Double,
        val marketPrice: Double, // The theoretical 'Close' price
        val executedPrice: Double, // The price after slippage
        val amount: Double, // ExecutedPrice * Qty (Gross)
        val commission: Double,   // Brokerage/STT
        val netAmount: Double, // Amount +/- commission
        val reason: String
    )

    /**
     * Calculates required trades to reach target allocations.
     * @param currentCash Current available liquidity
     * @param currentHoldings Current symbol/quantity map
     * @param targetAllocations Desired symbol/percentage map
     * @param totalPortfolioValue Cash + Current Holdings Value at currentPrices
     * @param currentPrices Map of current symbol prices
     */
    fun calculateTrades(
        currentCash: Double,
        currentHoldings: Map<String, Double>,
        targetAllocations: Map<String, Double>,
        totalPortfolioValue: Double,
        currentPrices: Map<String, Double>,
        reason: String = "Rebalance",
        symbolReasons: Map<String, String> = emptyMap(),
        transactionCostPct: Double? = null, // Allow override for commission only now
        useFixedSlippage: Boolean = false
    ): RebalanceResult {
        // Use override if provided, else use class default (0.1%)
        val appliedCommission = transactionCostPct ?: commissionPct
        
        var tempCash = currentCash
        val tempHoldings = currentHoldings.toMutableMap()
        val trades = mutableListOf<TradeAction>()

        // 1. Pass: SELLS (Freex up cash first)
        val allSymbols = (targetAllocations.keys + currentHoldings.keys).distinct().sorted()
        
        for (symbol in allSymbols) {
            val targetPct = targetAllocations[symbol] ?: 0.0
            val marketPrice = currentPrices[symbol] ?: continue
            if (marketPrice <= 0) continue

            val targetVal = totalPortfolioValue * targetPct
            val currentQty = tempHoldings[symbol] ?: 0.0
            val currentVal = currentQty * marketPrice
            val currentPct = if (totalPortfolioValue > 0) currentVal / totalPortfolioValue else 0.0
            
            val diffVal = targetVal - currentVal
            val diffPct = Math.abs(targetPct - currentPct)

            // SELL if underweight or removed (Negative diff)
            if (diffVal < -minTradeValue && diffPct >= minAllocationChange) {
                val sellValRaw = -diffVal
                val sellQty = sellValRaw / marketPrice
                
                // Ensure we don't sell more than we have
                val finalSellQty = if (sellQty > currentQty) currentQty else sellQty
                
                // --- APPLY SLIPPAGE ---
                val executedPrice = if (useFixedSlippage) {
                    SlippageModel.applyFixedSlippage(marketPrice, isBuy = false)
                } else {
                    SlippageModel.applySellSlippage(marketPrice)
                }
                val grossSellAmount = finalSellQty * executedPrice
                
                val commission = grossSellAmount * appliedCommission
                val netProceeds = grossSellAmount - commission

                tempCash += netProceeds
                
                val newQty = currentQty - finalSellQty
                if (newQty <= 0.001) {
                    tempHoldings.remove(symbol)
                } else {
                    tempHoldings[symbol] = newQty
                }

                trades.add(TradeAction(
                    symbol = symbol,
                    type = "SELL",
                    quantity = finalSellQty,
                    marketPrice = marketPrice,
                    executedPrice = executedPrice,
                    amount = grossSellAmount,
                    commission = commission,
                    netAmount = netProceeds,
                    reason = symbolReasons[symbol] ?: reason
                ))
            }
        }

        // 2. Pass: BUYS
        for (symbol in allSymbols) {
            val targetPct = targetAllocations[symbol] ?: 0.0
            val marketPrice = currentPrices[symbol] ?: continue
            if (marketPrice <= 0) continue

            val targetVal = totalPortfolioValue * targetPct
            val currentQty = tempHoldings[symbol] ?: 0.0
            val currentVal = currentQty * marketPrice
            val currentPct = if (totalPortfolioValue > 0) currentVal / totalPortfolioValue else 0.0
            
            val diffVal = targetVal - currentVal
            val diffPct = Math.abs(targetPct - currentPct)

            // BUY if underweight (Positive diff)
            if (diffVal > minTradeValue && diffPct >= minAllocationChange) {
                // --- APPLY SLIPPAGE FOR ESTIMATION ---
                val executedPrice = if (useFixedSlippage) {
                    SlippageModel.applyFixedSlippage(marketPrice, isBuy = true)
                } else {
                    SlippageModel.applyBuySlippage(marketPrice)
                }
                
                val buyValRaw = diffVal
                val commissionEstimate = buyValRaw * appliedCommission
                val totalRequired = buyValRaw + commissionEstimate
                
                // Check cash availability
                val actualBuyVal = if (totalRequired > tempCash) {
                   // Scale down to available cash
                   val availableForGross = tempCash / (1 + appliedCommission)
                   availableForGross
                } else {
                    buyValRaw
                }

                if (actualBuyVal > minTradeValue) {
                    val buyQty = actualBuyVal / executedPrice
                    val grossBuyAmount = buyQty * executedPrice
                    val actualCommission = grossBuyAmount * appliedCommission
                    val totalDeduction = grossBuyAmount + actualCommission

                    tempCash -= totalDeduction
                    tempHoldings[symbol] = (tempHoldings[symbol] ?: 0.0) + buyQty

                    trades.add(TradeAction(
                        symbol = symbol,
                        type = "BUY",
                        quantity = buyQty,
                        marketPrice = marketPrice,
                        executedPrice = executedPrice,
                        amount = grossBuyAmount,
                        commission = actualCommission,
                        netAmount = totalDeduction,
                        reason = symbolReasons[symbol] ?: reason
                    ))
                }
            }
        }

        return RebalanceResult(
            newCash = tempCash,
            updatedHoldings = tempHoldings,
            trades = trades
        )
    }
}

