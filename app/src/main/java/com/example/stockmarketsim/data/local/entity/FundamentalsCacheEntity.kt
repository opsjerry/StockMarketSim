package com.example.stockmarketsim.data.local.entity

import androidx.room.Entity
import com.example.stockmarketsim.domain.model.FundamentalData

/**
 * Room entity for caching IndianAPI.in / Yahoo fundamentals data.
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
    val source: String,                       // "INDIAN_API" or "YAHOO_FINANCE"
    // Phase 3: Governance risk field. 0.0 = not fetched → passes filter (benefit of the doubt)
    val promoterHolding: Double = 0.0,        // 0.0–1.0
    val fetchTimestamp: Long = System.currentTimeMillis()
)

fun FundamentalsCacheEntity.toDomain(): FundamentalData {
    return FundamentalData(
        symbol = symbol,
        returnOnEquity = if (roe > 0) roe else null,
        debtToEquity = if (debtToEquity > 0) debtToEquity else null,
        marketCap = marketCap.toLong(),
        trailingPE = if (peRatio > 0) peRatio else null,
        bookValue = null,
        promoterHolding = if (promoterHolding > 0) promoterHolding else null
    )
}
