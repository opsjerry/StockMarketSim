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
    val fetchTimestamp: Long = System.currentTimeMillis()
) {
    /** Quality check: ROE > 12% and Debt/Equity < 1.0 */
    fun meetsQualityThreshold(minROE: Double = 0.12, maxDebtEquity: Double = 1.0): Boolean {
        // If data unavailable, pass (benefit of the doubt — don't exclude just because API failed)
        val roeOk = returnOnEquity == null || returnOnEquity >= minROE
        val deOk = debtToEquity == null || debtToEquity <= maxDebtEquity
        return roeOk && deOk
    }
}
