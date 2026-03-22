package com.example.stockmarketsim.proof

import com.example.stockmarketsim.data.remote.YahooFinanceSource
import com.example.stockmarketsim.domain.analysis.Backtester
import com.example.stockmarketsim.domain.analysis.BacktestResult
import com.example.stockmarketsim.domain.strategy.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.TimeZone

class HistoricalDownturnAnalysisTest {

    @Test
    fun testCovid19CrashAndRecovery() = runBlocking {
        val client = OkHttpClient()
        val yahooSource = YahooFinanceSource(client)

        val symbols = listOf("RELIANCE.NS", "TCS.NS", "HDFCBANK.NS", "INFY.NS", "HINDUNILVR.NS")
        val benchmark = "^NSEI"

        val format = SimpleDateFormat("yyyy-MM-dd")
        format.timeZone = TimeZone.getTimeZone("UTC")
        val startSecs = format.parse("2022-01-01").time / 1000

        println("Fetching real historical data from Yahoo Finance...")
        val requests = symbols.associateWith { startSecs }
        val marketData = yahooSource.getHistories(requests)
        val benchData = yahooSource.getHistory(benchmark, startSecs)
        
        val backtester = Backtester()
        
        // Window evaluates Sep 2024 - Feb 2025
        val windowStart = format.parse("2024-09-27").time
        val windowEnd = format.parse("2025-02-28").time
        
        println("Running Simulation Engine over Sep 2024 - Feb 2025...\n")
        
        val strategiesToTest = listOf(
            ConfigurableMomentumStrategy(50),      // The one that failed in chop
            RsiStrategy(14),                       // Mean Reversion 
            BollingerMeanReversionStrategy(),      // Volatility Mean Reversion
            SafeHavenStrategy(),                   // Risk-Parity Low Volatility
            MacdStrategy()                         // Cycle crossing
        )
        
        val results = mutableListOf<BacktestResult>()
        
        for (strategy in strategiesToTest) {
            println("Evaluating Strategy: ${strategy.name}...")
            val result = backtester.runBacktest(
                strategy = strategy,
                marketData = marketData,
                benchmarkData = benchData,
                initialCash = 100000.0,
                windowStart = windowStart,
                windowEnd = windowEnd,
                useDeterministicSlippage = true,
                logger = { /* Silence per-strategy logs for clean output */ }
            )
            results.add(result)
        }

        println("\n==========================================================================")
        println("         RECENT MARKET (SEP 2024 - FEB 2025) COMPARATIVE ANALYSIS         ")
        println("==========================================================================")
        println("Universe: Top 5 NIFTY Bluechips")
        println("Period  : Sep 27, 2024 - Feb 28, 2025 (5 Months)")
        println("NIFTY 50 Benchmark Return: ${"%.2f".format(results.first().benchmarkReturn)}%")
        println("--------------------------------------------------------------------------")
        println(String.format("%-30s | %-12s | %-12s | %-10s", "Strategy Name", "Net Return", "Max Drawdown", "Win Rate"))
        println("--------------------------------------------------------------------------")
        
        for (res in results.sortedByDescending { it.alpha }) {
            val retStr = "${"%.2f".format(res.returnPct)}%"
            val ddStr = "${"%.2f".format(res.maxDrawdown)}%"
            val winStr = "${"%.2f".format(res.winRate * 100)}%"
            
            println(String.format("%-30s | %-12s | %-12s | %-10s", res.strategyName, retStr, ddStr, winStr))
        }
        println("==========================================================================")
    }
}
