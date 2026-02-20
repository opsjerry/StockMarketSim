package com.example.stockmarketsim.domain.analysis

/**
 * Simulates real-world execution imperfections (Slippage).
 * Real trades rarely execute at the exact 'Close' price due to bid/ask spreads
 * and order book depth.
 */
object SlippageModel {

    /**
     * Buy orders typically execute slightly HIGHER than the last close.
     * Range: 0.1% to 0.3% slippage.
     */
    fun applyBuySlippage(price: Double): Double {
        val slip = 0.001 + kotlin.random.Random.nextDouble() * 0.002 // 0.1% to 0.3%
        return price * (1.0 + slip)
    }

    /**
     * Sell orders typically execute slightly LOWER than the last close.
     * Range: 0.1% to 0.3% slippage.
     */
    fun applySellSlippage(price: Double): Double {
        val slip = 0.001 + kotlin.random.Random.nextDouble() * 0.002 // 0.1% to 0.3%
        return price * (1.0 - slip)
    }

    /**
     * Professional Upgrade: Deterministic Slippage for Backtesting.
     * Uses a fixed 0.2% slippage to eliminate "Luck" from tournament winners.
     */
    fun applyFixedSlippage(price: Double, isBuy: Boolean): Double {
        val slip = 0.002 // Fixed 0.2%
        return if (isBuy) price * (1.0 + slip) else price * (1.0 - slip)
    }

    /**
     * Generates a random slippage percentage for manual calculations.
     */
    fun getRandomSlippageFactor(): Double {
        return 0.001 + kotlin.random.Random.nextDouble() * 0.002
    }
}
