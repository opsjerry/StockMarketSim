package com.example.stockmarketsim.domain.ml

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ensures the zero-allocation Kotlin math identically matches the Python
 * Pandas rolling window logic from Phase 1 Feature Engineering.
 */
class TechnicalIndicatorsTest {

    @Test
    fun `test RSI 14 calculation matches Python Pandas logic`() {
        // Mock data from a known Pandas DataFrame slice
        val closingPrices = doubleArrayOf(
            100.0, 101.0, 102.0, 101.5, 100.5,
            103.0, 104.0, 103.5, 105.0, 106.0,
            105.5, 107.0, 108.0, 109.0, 108.5
        )
        // 14 periods of difference require 15 total data points.
        // RSI Calculation:
        // Gains: 1, 1, 0, 0, 2.5, 1, 0, 1.5, 1, 0, 1.5, 1, 1, 0
        // Losses: 0, 0, -0.5, -1, 0, 0, -0.5, 0, 0, -0.5, 0, 0, 0, -0.5
        
        val rsi = TechnicalIndicators.calculateRsiZeroAlloc(closingPrices, closingPrices.size, 14)
        
        
        // Assert RSI is calculated correctly (Simple Moving Average RSI to match Python)
        // Gain sum = 11.5, Loss sum = 3.0, RS = 3.833, RSI = 79.31
        assertEquals(79.31, rsi, 0.1)
    }

    @Test
    fun `test SMA Ratio 50-200 calculation with short dataset returns neutral 1_0`() {
        val prices = DoubleArray(150) { 100.0 } // Only 150 days of data
        val ratio = TechnicalIndicators.calculateSmaRatioZeroAlloc(prices, prices.size, 50, 200)
        
        // Should return 1.0 (neutral) because 200 days are required
        assertEquals(1.0, ratio, 0.0)
    }

    @Test
    fun `test SMA Ratio 50-200 calculation with valid data`() {
        val prices = DoubleArray(200)
        
        // Slow SMA (200) average will be 150
        // Fast SMA (50) is the last 50 elements, average will be 190
        for (i in 0 until 200) {
            prices[i] = i.toDouble() + 50.0 
        }
        
        val ratio = TechnicalIndicators.calculateSmaRatioZeroAlloc(prices, prices.size, 50, 200)
        val expectedRatio = 224.5 / 149.5 // Fast SMA (avg of 200 to 249) / Slow SMA (avg of 50 to 249)
        
        assertEquals(expectedRatio, ratio, 0.01)
    }
    
    @Test
    fun `test ATR Proxy percentage calculation`() {
        val highs = doubleArrayOf(105.0, 108.0, 104.0) // 108 makes TR1 = 10
        val lows = doubleArrayOf(95.0, 98.0, 99.0)
        val closes = doubleArrayOf(100.0, 102.0, 101.0)
        
        // Window size 2
        // TR1: max(10, 8, 2) = 10
        // TR2: max(5, 4, 3) = 5
        // ATR = (10+5)/2 = 7.5
        // ATR Pct = 7.5 / 101.0 (last close) = 0.074...
        
        val atrPct = TechnicalIndicators.calculateAtrPctZeroAlloc(highs, lows, closes, closes.size, 2)
        assertEquals(0.0742, atrPct, 0.001)
    }
    
    @Test
    fun `test Relative Volume calculation`() {
        val volumes = doubleArrayOf(
            100.0, 100.0, 100.0, 100.0, 100.0, // 5 days
            200.0 // Today
        )
        
        // Avg of past 5 days = 100.0
        // Today = 200.0
        // Relative Volume = 2.0
        
        val relVol = TechnicalIndicators.calculateRelativeVolumeZeroAlloc(volumes, volumes.size, 5)
        assertEquals(2.0, relVol, 0.0)
    }
}
