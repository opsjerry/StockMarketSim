package com.example.stockmarketsim.proof

import com.example.stockmarketsim.domain.analysis.SlippageModel
import org.junit.Assert.*
import org.junit.Test

/**
 * REGRESSION SUITE: Slippage Model
 *
 * Tests deterministic and random slippage for buy/sell trades,
 * boundary validation, and symmetry properties.
 */
class SlippageModelRegressionTest {

    // =====================================================================
    // Buy Slippage
    // =====================================================================

    @Test
    fun `buy slippage always increases price`() {
        repeat(100) {
            val price = 500.0
            val slipped = SlippageModel.applyBuySlippage(price)
            assertTrue("Buy slippage should increase price (iteration $it)", slipped > price)
        }
    }

    @Test
    fun `buy slippage within 0_1 to 0_3 percent range`() {
        repeat(100) {
            val price = 1000.0
            val slipped = SlippageModel.applyBuySlippage(price)
            val slipPct = (slipped - price) / price

            assertTrue("Buy slip should be >= 0.1% (got ${slipPct * 100}%)", slipPct >= 0.001)
            assertTrue("Buy slip should be <= 0.3% (got ${slipPct * 100}%)", slipPct <= 0.003)
        }
    }

    // =====================================================================
    // Sell Slippage
    // =====================================================================

    @Test
    fun `sell slippage always decreases price`() {
        repeat(100) {
            val price = 500.0
            val slipped = SlippageModel.applySellSlippage(price)
            assertTrue("Sell slippage should decrease price (iteration $it)", slipped < price)
        }
    }

    @Test
    fun `sell slippage within 0_1 to 0_3 percent range`() {
        repeat(100) {
            val price = 1000.0
            val slipped = SlippageModel.applySellSlippage(price)
            val slipPct = (price - slipped) / price

            assertTrue("Sell slip should be >= 0.1% (got ${slipPct * 100}%)", slipPct >= 0.001)
            assertTrue("Sell slip should be <= 0.3% (got ${slipPct * 100}%)", slipPct <= 0.003)
        }
    }

    // =====================================================================
    // Fixed (Deterministic) Slippage
    // =====================================================================

    @Test
    fun `fixed buy slippage is exactly 0_2 percent`() {
        val price = 1000.0
        val slipped = SlippageModel.applyFixedSlippage(price, isBuy = true)
        assertEquals("Fixed buy should be +0.2%", 1002.0, slipped, 0.001)
    }

    @Test
    fun `fixed sell slippage is exactly 0_2 percent`() {
        val price = 1000.0
        val slipped = SlippageModel.applyFixedSlippage(price, isBuy = false)
        assertEquals("Fixed sell should be -0.2%", 998.0, slipped, 0.001)
    }

    @Test
    fun `fixed slippage is deterministic across multiple calls`() {
        val price = 500.0
        val results = (1..10).map { SlippageModel.applyFixedSlippage(price, isBuy = true) }
        assertTrue("All fixed slippage calls should produce identical results",
            results.all { it == results[0] })
    }

    @Test
    fun `fixed slippage buy and sell are symmetric`() {
        val price = 1000.0
        val buySlip = SlippageModel.applyFixedSlippage(price, isBuy = true) - price
        val sellSlip = price - SlippageModel.applyFixedSlippage(price, isBuy = false)
        assertEquals("Buy and sell slippage should be equal magnitude", buySlip, sellSlip, 0.001)
    }

    // =====================================================================
    // Random Slippage Factor
    // =====================================================================

    @Test
    fun `random slippage factor within expected range`() {
        repeat(100) {
            val factor = SlippageModel.getRandomSlippageFactor()
            assertTrue("Factor should be >= 0.001 (got $factor)", factor >= 0.001)
            assertTrue("Factor should be <= 0.003 (got $factor)", factor <= 0.003)
        }
    }

    // =====================================================================
    // Edge Cases
    // =====================================================================

    @Test
    fun `slippage with very small price`() {
        val price = 1.0
        val buySlipped = SlippageModel.applyFixedSlippage(price, isBuy = true)
        val sellSlipped = SlippageModel.applyFixedSlippage(price, isBuy = false)

        assertTrue("Small price buy should still increase", buySlipped > price)
        assertTrue("Small price sell should still decrease", sellSlipped < price)
    }

    @Test
    fun `slippage with large price`() {
        val price = 100000.0
        val buySlipped = SlippageModel.applyFixedSlippage(price, isBuy = true)
        assertEquals("Large price: +0.2%", 100200.0, buySlipped, 0.01)
    }

    @Test
    fun `round trip cost is approximately 0_4 percent`() {
        val price = 10000.0
        val buyPrice = SlippageModel.applyFixedSlippage(price, isBuy = true)
        val sellPrice = SlippageModel.applyFixedSlippage(price, isBuy = false) // Use same base price for spread
        val spread = buyPrice - sellPrice
        val roundTripCost = spread / price * 100

        // Spread should be ~0.4% (0.2% buy slip + 0.2% sell slip)
        assertTrue("Round trip cost should be ~0.4%", roundTripCost > 0.35 && roundTripCost < 0.45)
    }
}
