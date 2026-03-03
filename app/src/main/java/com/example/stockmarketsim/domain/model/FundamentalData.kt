package com.example.stockmarketsim.domain.model

/**
 * Fundamental financial ratios for quality screening.
 * Sourced from Yahoo Finance quoteSummary or Zerodha (when paid).
 */
data class FundamentalData(
    val symbol: String,
    val returnOnEquity: Double?,       // ROE (e.g., 0.15 = 15%)
    val debtToEquity: Double?,         // D/E ratio (e.g., 0.5)
    val marketCap: Long?,              // Market Cap in INR
    val trailingPE: Double?,           // Trailing P/E ratio
    val bookValue: Double?,            // Book Value per share
    // Phase 3: Governance risk proxy — Yahoo's insidersPercentHeld ≈ NSE promoter holding
    // Stocks with > 72% concentrated holding + pledge risk (Adani, Vedanta, etc.) are flagged.
    val promoterHolding: Double? = null,  // 0.0–1.0 (e.g., 0.75 = 75%)
    val fetchTimestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val MIN_ROE = 0.12
        const val MAX_DEBT_EQUITY = 1.0
        /** Stocks above this threshold carry concentrated promoter / governance risk */
        const val MAX_PROMOTER_HOLDING = 0.72   // 72%
    }

    /**
     * Quality check: ROE > 12%, D/E < 1.0, and promoter holding < 72%.
     * If data is unavailable for any field, that check passes (benefit of the doubt).
     */
    fun meetsQualityThreshold(
        minROE: Double = MIN_ROE,
        maxDebtEquity: Double = MAX_DEBT_EQUITY,
        maxPromoterHolding: Double = MAX_PROMOTER_HOLDING
    ): Boolean {
        val roeOk = returnOnEquity == null || returnOnEquity >= minROE
        val deOk  = debtToEquity == null || debtToEquity <= maxDebtEquity
        // Phase 3: Block concentrated-promoter stocks regardless of ROE/D/E
        val promoterOk = promoterHolding == null || promoterHolding <= maxPromoterHolding
        return roeOk && deOk && promoterOk
    }
}
