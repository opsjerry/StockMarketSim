package com.example.stockmarketsim.proof

import com.example.stockmarketsim.domain.model.FundamentalData
import com.example.stockmarketsim.domain.analysis.QualityFilter
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the Fundamental Quality Filter (P2 Item #1).
 * 
 * Tests verify:
 * 1. FundamentalData.meetsQualityThreshold() logic
 * 2. QualityFilter.filter() inclusion/exclusion behavior
 * 3. Null-safety (missing data should PASS, not exclude)
 * 4. Edge cases (exact thresholds, partial data)
 */
class QualityFilterTest {

    private val qualityFilter = QualityFilter()

    // --- FundamentalData.meetsQualityThreshold() Tests ---

    @Test
    fun `high quality stock passes threshold`() {
        val data = FundamentalData(
            symbol = "RELIANCE.NS",
            returnOnEquity = 0.20,  // 20% ROE - excellent
            debtToEquity = 0.5,     // 0.5 D/E - conservative
            marketCap = 1500000000000L,
            trailingPE = 25.0,
            bookValue = 1200.0
        )
        assertTrue("High quality stock should pass", data.meetsQualityThreshold())
    }

    @Test
    fun `low ROE stock fails threshold`() {
        val data = FundamentalData(
            symbol = "POORROE.NS",
            returnOnEquity = 0.05,  // 5% ROE - below 12% threshold
            debtToEquity = 0.3,     // Fine D/E
            marketCap = 50000000000L,
            trailingPE = 40.0,
            bookValue = 100.0
        )
        assertFalse("Low ROE stock should fail", data.meetsQualityThreshold())
    }

    @Test
    fun `high debt stock fails threshold`() {
        val data = FundamentalData(
            symbol = "HIGHDEBT.NS",
            returnOnEquity = 0.18,  // Good ROE
            debtToEquity = 2.5,     // 2.5 D/E - over-leveraged
            marketCap = 80000000000L,
            trailingPE = 15.0,
            bookValue = 200.0
        )
        assertFalse("High debt stock should fail", data.meetsQualityThreshold())
    }

    @Test
    fun `null ROE passes - benefit of the doubt`() {
        val data = FundamentalData(
            symbol = "NODATA.NS",
            returnOnEquity = null,  // Missing ROE data
            debtToEquity = 0.5,
            marketCap = null,
            trailingPE = null,
            bookValue = null
        )
        assertTrue("Null ROE should pass (benefit of doubt)", data.meetsQualityThreshold())
    }

    @Test
    fun `null debt equity passes - benefit of the doubt`() {
        val data = FundamentalData(
            symbol = "NODATA2.NS",
            returnOnEquity = 0.15,
            debtToEquity = null,  // Missing D/E data
            marketCap = null,
            trailingPE = null,
            bookValue = null
        )
        assertTrue("Null D/E should pass (benefit of doubt)", data.meetsQualityThreshold())
    }

    @Test
    fun `all null data passes - benefit of the doubt`() {
        val data = FundamentalData(
            symbol = "EMPTY.NS",
            returnOnEquity = null,
            debtToEquity = null,
            marketCap = null,
            trailingPE = null,
            bookValue = null
        )
        assertTrue("All null data should pass", data.meetsQualityThreshold())
    }

    @Test
    fun `exact ROE threshold passes`() {
        val data = FundamentalData(
            symbol = "EDGE.NS",
            returnOnEquity = 0.12,  // Exactly 12% - should pass
            debtToEquity = 1.0,     // Exactly 1.0 - should pass
            marketCap = null,
            trailingPE = null,
            bookValue = null
        )
        assertTrue("Exact threshold values should pass", data.meetsQualityThreshold())
    }

    @Test
    fun `just below ROE threshold fails`() {
        val data = FundamentalData(
            symbol = "JUSTBELOW.NS",
            returnOnEquity = 0.119,  // Just below 12%
            debtToEquity = 0.5,
            marketCap = null,
            trailingPE = null,
            bookValue = null
        )
        assertFalse("ROE just below 12% should fail", data.meetsQualityThreshold())
    }

    // --- QualityFilter.filter() Tests ---

    @Test
    fun `filter removes bad stocks and keeps good ones`() {
        val candidates = listOf("GOOD.NS", "BAD_ROE.NS", "BAD_DEBT.NS", "NO_DATA.NS")
        val fundamentals = mapOf(
            "GOOD.NS" to FundamentalData("GOOD.NS", 0.20, 0.5, null, null, null),
            "BAD_ROE.NS" to FundamentalData("BAD_ROE.NS", 0.05, 0.3, null, null, null),
            "BAD_DEBT.NS" to FundamentalData("BAD_DEBT.NS", 0.15, 1.5, null, null, null)
            // NO_DATA.NS intentionally missing from map
        )

        val result = qualityFilter.filter(candidates, fundamentals)

        assertTrue("GOOD.NS should pass", "GOOD.NS" in result)
        assertFalse("BAD_ROE.NS should be removed", "BAD_ROE.NS" in result)
        assertFalse("BAD_DEBT.NS should be removed", "BAD_DEBT.NS" in result)
        assertTrue("NO_DATA.NS should pass (no data = pass)", "NO_DATA.NS" in result)
        assertEquals("Should have 2 passing stocks", 2, result.size)
    }

    @Test
    fun `filter with empty fundamentals passes everything`() {
        val candidates = listOf("A.NS", "B.NS", "C.NS")
        val fundamentals = emptyMap<String, FundamentalData>()

        val result = qualityFilter.filter(candidates, fundamentals)

        assertEquals("All stocks pass when no fundamentals available", 3, result.size)
    }

    @Test
    fun `filterWithReasons provides rejection explanations`() {
        val candidates = listOf("GOOD.NS", "BAD.NS")
        val fundamentals = mapOf(
            "GOOD.NS" to FundamentalData("GOOD.NS", 0.20, 0.5, null, null, null),
            "BAD.NS" to FundamentalData("BAD.NS", 0.05, 2.0, null, null, null)
        )

        val (passed, rejected) = qualityFilter.filterWithReasons(candidates, fundamentals)

        assertEquals(1, passed.size)
        assertEquals(1, rejected.size)
        assertEquals("GOOD.NS", passed[0])
        assertTrue("Rejection should mention the stock", rejected[0].startsWith("BAD.NS"))
    }
}
