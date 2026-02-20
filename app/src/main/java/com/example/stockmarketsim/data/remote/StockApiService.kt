package com.example.stockmarketsim.data.remote

import com.example.stockmarketsim.domain.model.StockQuote
import com.example.stockmarketsim.domain.model.TimeFrame
import retrofit2.http.GET
import retrofit2.http.Query

interface StockApiService {
    
    // We will start with a placeholder structure.
    // Since we are targeting Yahoo Finance (Unofficial) or Alpha Vantage,
    // the actual endpoint depends on the implementation choice.
    // For now, let's assume a generic interface that our "DataSource" will map to.
    
    @GET("query")
    suspend fun getGlobalQuote(
        @Query("function") function: String,
        @Query("symbol") symbol: String,
        @Query("apikey") apiKey: String
    ): StockQuote // Simplified return type for now, usually needs a Wrapper
}
