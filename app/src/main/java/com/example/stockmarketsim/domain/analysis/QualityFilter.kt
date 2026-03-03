package com.example.stockmarketsim.domain.analysis

import com.example.stockmarketsim.domain.model.FundamentalData
import javax.inject.Inject

/**
 * Filters out stocks that fail fundamental quality checks.
 *
 * Criteria:
 *   - ROE >= 12%             (profitability)
 *   - Debt/Equity <= 1.0     (not over-leveraged)
 *   - Promoter holding < 72% (Phase 3: governance risk gate)
 *
 * The promoter cap catches stocks like Adani Power (74.97%) that pass ROE/D/E
 * on standalone financials but carry concentrated promoter + pledge risk, which
 * causes 15–20% single-day drawdowns that break ATR stop assumptions.
 *
 * Stocks with missing data PASS (benefit of the doubt — don't exclude because API failed).
 */
class QualityFilter @Inject constructor() {

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
            data == null || data.meetsQualityThreshold()
        }
    }

    /**
     * Return which stocks were removed and why (for logging).
     */
    fun filterWithReasons(
        candidates: List<String>,
        fundamentals: Map<String, FundamentalData>
    ): Pair<List<String>, List<String>> {
        val passed   = mutableListOf<String>()
        val rejected = mutableListOf<String>()

        for (symbol in candidates) {
            val data = fundamentals[symbol]
            if (data == null || data.meetsQualityThreshold()) {
                passed.add(symbol)
            } else {
                val reason = buildString {
                    data.returnOnEquity?.let {
                        if (it < FundamentalData.MIN_ROE)
                            append("ROE=${(it * 100).toInt()}% ")
                    }
                    data.debtToEquity?.let {
                        if (it > FundamentalData.MAX_DEBT_EQUITY)
                            append("D/E=${"%.1f".format(it)} ")
                    }
                    // Phase 3: Log promoter concentration rejections distinctly
                    data.promoterHolding?.let {
                        if (it > FundamentalData.MAX_PROMOTER_HOLDING)
                            append("Promoter=${"%.1f".format(it * 100)}% ")
                    }
                }
                rejected.add("$symbol (${reason.trim()})")
            }
        }
        return passed to rejected
    }
}
