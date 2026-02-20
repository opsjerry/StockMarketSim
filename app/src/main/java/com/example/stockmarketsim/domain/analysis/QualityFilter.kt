package com.example.stockmarketsim.domain.analysis

import com.example.stockmarketsim.domain.model.FundamentalData
import javax.inject.Inject

/**
 * Filters out stocks that fail fundamental quality checks.
 * 
 * Criteria:
 *   - ROE >= 12% (Return on Equity — profitability)
 *   - Debt/Equity <= 1.0 (Leverage — not over-leveraged)
 * 
 * Stocks with missing data PASS (benefit of the doubt).
 * This prevents RSI and Bollinger Mean Reversion from catching "falling knives"
 * — stocks that are cheap for a reason (deteriorating fundamentals).
 */
class QualityFilter @Inject constructor() {

    companion object {
        const val MIN_ROE = 0.12       // 12%
        const val MAX_DEBT_EQUITY = 1.0
    }

    /**
     * Filter a candidate list to only quality stocks.
     * @param candidates List of stock symbols
     * @param fundamentals Map of symbol -> FundamentalData (may be partial)
     * @return Filtered list of symbols that pass quality checks
     */
    fun filter(
        candidates: List<String>,
        fundamentals: Map<String, FundamentalData>
    ): List<String> {
        return candidates.filter { symbol ->
            val data = fundamentals[symbol]
            // No data = pass (don't exclude stocks just because API failed)
            data == null || data.meetsQualityThreshold(MIN_ROE, MAX_DEBT_EQUITY)
        }
    }

    /**
     * Return which stocks were removed and why (for logging).
     */
    fun filterWithReasons(
        candidates: List<String>,
        fundamentals: Map<String, FundamentalData>
    ): Pair<List<String>, List<String>> {
        val passed = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        
        for (symbol in candidates) {
            val data = fundamentals[symbol]
            if (data == null || data.meetsQualityThreshold(MIN_ROE, MAX_DEBT_EQUITY)) {
                passed.add(symbol)
            } else {
                val reason = buildString {
                    data.returnOnEquity?.let { if (it < MIN_ROE) append("ROE=${(it * 100).toInt()}% ") }
                    data.debtToEquity?.let { if (it > MAX_DEBT_EQUITY) append("D/E=${"%.1f".format(it)} ") }
                }
                rejected.add("$symbol ($reason)")
            }
        }
        return passed to rejected
    }
}
