package com.example.stockmarketsim.proof

import com.example.stockmarketsim.domain.analysis.Backtester
import com.example.stockmarketsim.domain.model.StockQuote
import com.example.stockmarketsim.domain.strategy.Strategy
import com.example.stockmarketsim.domain.strategy.TradeSignal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TournamentDataContinuityTest {

    @Test
    fun `verify strategy has access to preceding history when running windowed backtest`() = runBlocking {
        val backtester = Backtester()
        
        // 300 days of data
        val dates = (1..300).map { 1704067200000L + it * 86400000L }
        val testData = mapOf(
            "TEST" to dates.map { date -> 
                StockQuote("TEST", date, 100.0, 100.0, 100.0, 100.0, 1000) 
            }
        )

        // Tournament Split: Test window starts at day 240
        val splitDate = dates[240]

        val continuityStrategy = object : Strategy {
            override val id = "CONTINUITY"
            override val name = "Continuity"
            override val description = "Checks history depth"
            
            override suspend fun calculateallocation(
                symbols: List<String>,
                history: Map<String, List<StockQuote>>,
                cursors: Map<String, Int>
            ): Map<String, Double> {
                val idx = cursors["TEST"] ?: return emptyMap()
                
                // On the first day of the window (i=240), cursor should occur at 239.
                // So effective history depth is 240 (0..239).
                
                if (firstCall) {
                    historySizeAtWindowStart = idx + 1
                    firstCall = false
                }
                return mapOf("TEST" to 1.0)
            }
            private var firstCall = true

            override suspend fun getSignal(symbol: String, history: List<StockQuote>, currentIdx: Int): TradeSignal = TradeSignal.HOLD
        }

        backtester.runBacktest(
            strategy = continuityStrategy, 
            marketData = testData, 
            windowStart = splitDate
        )
        
        // Should be exactly 240 (days of data BEFORE the window start)
        assertEquals(240, historySizeAtWindowStart)
    }

    private var historySizeAtWindowStart = 0
}
