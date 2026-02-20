package com.example.stockmarketsim.data.remote

import com.example.stockmarketsim.domain.model.StockQuote
import com.zerodhatech.kiteconnect.KiteConnect
import com.zerodhatech.models.Quote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ZerodhaSource @Inject constructor(
    private val zerodhaClient: ZerodhaClient
) {
    suspend fun getQuote(symbol: String): StockQuote? = withContext(Dispatchers.IO) {
        try {
            val kite = zerodhaClient.getKiteConnect() ?: return@withContext null
            
            // Zerodha requires exchange prefix, usually "NSE:" for Nifty 50
            // My app uses "RELIANCE.NS" or "RELIANCE"
            // I need to convert "RELIANCE.NS" -> "NSE:RELIANCE"
            val zerodhaSymbol = convertToZerodhaSymbol(symbol)
            val quotes: Map<String, Quote> = kite.getQuote(arrayOf(zerodhaSymbol))
            val quoteData = quotes[zerodhaSymbol] ?: return@withContext null
            
            val timestamp = quoteData.timestamp?.time ?: System.currentTimeMillis()
            
            StockQuote(
                symbol = symbol,
                date = timestamp,
                open = quoteData.ohlc.open,
                high = quoteData.ohlc.high,
                low = quoteData.ohlc.low,
                close = quoteData.lastPrice,
                volume = quoteData.volumeTradedToday.toLong()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // Log?
            null
        }
    }
    
    private fun convertToZerodhaSymbol(appSymbol: String): String {
        // Simple heuristic: 
        // If ends with .NS -> NSE:Prefix
        // If ends with .BO -> BSE:Prefix
        // Else defaults to NSE
        return when {
            appSymbol.endsWith(".NS") -> "NSE:${appSymbol.removeSuffix(".NS")}"
            appSymbol.endsWith(".BO") -> "BSE:${appSymbol.removeSuffix(".BO")}"
            else -> "NSE:$appSymbol"
        }
    }
    
    fun isSessionActive(): Boolean {
        return zerodhaClient.isSessionActive()
    }

    /**
     * Zerodha fundamentals stub — ready for paid API integration.
     * When Zerodha's paid API provides fundamental data (ROE, D/E, etc.),
     * implement this to use kite.getQuote() or kite.getInstruments() with
     * additional fundamental fields.
     * 
     * TODO: Implement when Zerodha paid API subscription is active.
     */
    suspend fun getFundamentals(symbol: String): com.example.stockmarketsim.domain.model.FundamentalData? {
        if (!isSessionActive()) return null
        // Zerodha's free API does not provide fundamental ratios.
        // When paid API is enabled, fetch ROE, D/E from:
        //   kite.getQuote(arrayOf("NSE:RELIANCE")) -> quoteData.financials
        // For now, return null to fall through to Yahoo Finance.
        return null
    }
}
