package com.example.stockmarketsim.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_universe")
data class StockUniverseEntity(
    @PrimaryKey val symbol: String,
    val name: String = "",
    val isActive: Boolean = true
)
