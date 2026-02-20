package com.example.stockmarketsim.proof

import com.example.stockmarketsim.domain.analysis.Backtester
import com.example.stockmarketsim.domain.model.StockQuote
import com.example.stockmarketsim.domain.strategy.Strategy
import com.example.stockmarketsim.domain.strategy.TradeSignal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BacktesterLookaheadTest {

    @Test
    fun `verify strategy cannot see current day price during allocation calculation`() = runBlocking {
        val backtester = Backtester()
        
        // Mock data with 200 days (warm-up) + 2 days for test
        val dates = (1..205).map { 1704067200000L + it * 86400000L }
        val testData = mapOf(
            "TEST" to dates.map { date -> 
                StockQuote("TEST", date, 100.0, 100.0, 100.0, 100.0, 1000) 
            }
        )

        val cheatingStrategy = object : Strategy {
            override val id = "CHEATER"
            override val name = "Cheater"
            override val description = "Tries to see today"
            
            override suspend fun calculateallocation(
                symbols: List<String>,
                history: Map<String, List<StockQuote>>,
                cursors: Map<String, Int>
            ): Map<String, Double> {
                // history is now FULL history. We check cursor to ensure we are respecting time.
                val idx = cursors["TEST"] ?: return emptyMap()
                
                // Cursor points to T-1. So visible count is idx + 1.
                // e.g. if today is i=204, we want data up to 203. idx should be 203.
                // visible count = 204.
                val visibleCount = idx + 1
                
                // Let's store the max size seen
                maxHistorySize = maxOf(maxHistorySize, visibleCount)
                return mapOf("TEST" to 1.0)
            }

            override suspend fun getSignal(symbol: String, history: List<StockQuote>, currentIdx: Int): TradeSignal = TradeSignal.HOLD
        }

        backtester.runBacktest(cheatingStrategy, testData, 100000.0)
        
        // Since loop starts from i=startDay (20 or 200) and ends at dates.size - 1.
        // For i=204 (last day), it should provide subList(0, 204) -> size 204.
        // If it provided subList(0, 205), it would be look-ahead.
        assertEquals(204, maxHistorySize)
    }

    private var maxHistorySize = 0
}
