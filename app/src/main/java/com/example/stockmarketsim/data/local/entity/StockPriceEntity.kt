package com.example.stockmarketsim.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.stockmarketsim.domain.model.StockQuote

@Entity(tableName = "stock_prices")
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
