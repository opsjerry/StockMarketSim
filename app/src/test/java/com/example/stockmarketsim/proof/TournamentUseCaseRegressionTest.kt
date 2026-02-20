package com.example.stockmarketsim.proof

import android.content.Context
import com.example.stockmarketsim.domain.model.StockQuote
import com.example.stockmarketsim.domain.model.StockUniverse
import com.example.stockmarketsim.domain.model.FundamentalData
import com.example.stockmarketsim.domain.ml.IStockPriceForecaster

import com.example.stockmarketsim.domain.strategy.MomentumStrategy
import com.example.stockmarketsim.domain.strategy.SafeHavenStrategy
import com.example.stockmarketsim.domain.strategy.Strategy
import com.example.stockmarketsim.domain.strategy.StrategyProvider
import com.example.stockmarketsim.domain.usecase.RunStrategyTournamentUseCase
import com.example.stockmarketsim.domain.ml.StockPriceForecaster
import com.example.stockmarketsim.data.remote.IndianApiSource
import com.example.stockmarketsim.domain.repository.StockRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Field

class TournamentUseCaseRegressionTest {

    // Dummy Repo
    private val dummyRepo = object : StockRepository {
        override suspend fun getStockHistory(symbol: String, timeFrame: com.example.stockmarketsim.domain.model.TimeFrame, limit: Int): List<StockQuote> = emptyList()
        override suspend fun getStockQuote(symbol: String): StockQuote? = null
        override suspend fun searchStock(query: String): List<String> = emptyList()
        override suspend fun getBatchFundamentals(symbols: List<String>, logger: (String) -> Unit): Map<String, com.example.stockmarketsim.domain.model.FundamentalData> = emptyMap()
        
        override suspend fun getBatchStockHistory(symbols: List<String>, timeFrame: com.example.stockmarketsim.domain.model.TimeFrame, limit: Int, onLog: (String) -> Unit): Map<String, List<StockQuote>> = emptyMap()
        override suspend fun cleanupOldData(onLog: (String) -> Unit) {}
        override suspend fun getSentimentScore(symbol: String): Double = 0.0
        override suspend fun getInflationRate(): Double = 0.0
        override suspend fun getFundamentals(symbol: String): com.example.stockmarketsim.domain.model.FundamentalData? = null
        override fun getActiveUniverse(): kotlinx.coroutines.flow.Flow<List<String>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getActiveUniverseSnapshot(): List<String> = emptyList()
        override suspend fun syncUniverseFromDiscovery(onLog: (String) -> Unit): Int = 0
        override suspend fun addStockToUniverse(symbol: String) {}
        override suspend fun removeStockFromUniverse(symbol: String) {}
    }



    private val fakeStrategyProvider = object : StrategyProvider(
        MomentumStrategy(),
        SafeHavenStrategy(),
        object : IStockPriceForecaster { // SAFE: Dummy implementation
            override fun initialize() {}
            override fun predict(features: DoubleArray, symbol: String?, date: Long?): Float = 0f
        },
        dummyRepo,
        IndianApiSource(com.example.stockmarketsim.data.manager.SettingsManager(org.mockito.Mockito.mock(Context::class.java)))
    ) {
        override fun getStrategy(id: String): Strategy {
            // Return simple Momentum strategy for ALL requested IDs to ensure test stability
            return MomentumStrategy() 
        }

        override fun getAllStrategies(): List<Strategy> {
            return listOf(MomentumStrategy())
        }
    }

    private val useCase = RunStrategyTournamentUseCase(fakeStrategyProvider)

    @Test
    fun `parallel execution produces deterministic results`() = runBlocking {
        // Generate enough data to trigger the walk-forward logic (100 days)
        val data = (0 until 100).map { i ->
            val price = 100.0 + i
            StockQuote("TEST", 1704067200000L + i * 86400000L, price, price + 5, price - 5, price, 1000)
        }
        val marketData = mapOf("TEST" to data)
        val benchmarkData = data // Benchmark is same as stock

        // Run 1
        val result1 = useCase(marketData, benchmarkData, 100000.0, 12.0)
        
        // Run 2
        val result2 = useCase(marketData, benchmarkData, 100000.0, 12.0)

        // Verifications
        assertNotNull(result1)
        assertNotNull(result2)
        
        // Check determinism
        assertEquals("Split date should be identical", result1.evaluationStartDate, result2.evaluationStartDate)
        
        // Since we return MomentumStrategy for EVERYTHING, we expect many results, all identical or similar
        // Ideally candidates list is not empty
        assertTrue("Should return candidates", result1.candidates.isNotEmpty())
        
        // Compare first candidate
        val c1 = result1.candidates.firstOrNull()
        val c2 = result2.candidates.firstOrNull()
        
        assertNotNull(c1)
        assertNotNull(c2)
        assertEquals("Strategy ID check", c1?.strategyId, c2?.strategyId)
        assertEquals("Alpha check", c1?.alpha ?: 0.0, c2?.alpha ?: 0.0, 0.001)
        assertEquals("Trades check", c1?.totalTrades, c2?.totalTrades)
    }
}
