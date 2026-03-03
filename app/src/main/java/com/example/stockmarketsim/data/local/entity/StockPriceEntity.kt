package com.example.stockmarketsim.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.stockmarketsim.domain.model.StockQuote

@Entity(
    tableName = "stock_prices",
    indices = [
        // CRITICAL: Composite unique index on (symbol, date) ensures OnConflictStrategy.REPLACE
        // deduplicates by business key, not just the autoGenerate PK.
        // Without this, every fetch created new rows for the same candle, causing the LSTM to
        // see repeated price data in its 60-step input window — generating false patterns.
        androidx.room.Index(value = ["symbol", "date"], unique = true)
    ]
)
data class StockPriceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val symbol: String,
    val date: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)


fun StockPriceEntity.toDomain(): StockQuote {
    return StockQuote(
        symbol = symbol,
        date = date,
        open = open,
        high = high,
        low = low,
        close = close,
        volume = volume
    )
}

fun StockQuote.toEntity(): StockPriceEntity {
    return StockPriceEntity(
        symbol = symbol,
        date = date,
        open = open,
        high = high,
        low = low,
        close = close,
        volume = volume
    )
}
