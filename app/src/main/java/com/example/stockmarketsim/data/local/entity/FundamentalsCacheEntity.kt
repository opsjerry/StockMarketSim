package com.example.stockmarketsim.data.local.entity

import androidx.room.Entity

/**
 * Room entity for caching IndianAPI.in fundamentals data.
 * 7-day TTL — fundamentals change quarterly, so stale data is still valid.
 */
@Entity(tableName = "fundamentals_cache", primaryKeys = ["symbol"])
data class FundamentalsCacheEntity(
    val symbol: String,
    val peRatio: Double,
    val roe: Double,
    val debtToEquity: Double,
    val marketCap: Double,
    val sentimentScore: Double,
    val source: String,           // "INDIAN_API" or "YAHOO_FINANCE"
    val fetchTimestamp: Long = System.currentTimeMillis()
)
