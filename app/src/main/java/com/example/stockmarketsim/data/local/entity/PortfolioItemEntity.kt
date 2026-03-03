package com.example.stockmarketsim.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.example.stockmarketsim.domain.model.PortfolioItem

import androidx.room.Index

@Entity(
    tableName = "portfolio_items",
    indices = [Index(value = ["simulationId"])],   // Avoids full-scan on FK cascade
    foreignKeys = [
        ForeignKey(
            entity = SimulationEntity::class,
            parentColumns = ["id"],
            childColumns = ["simulationId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PortfolioItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val simulationId: Int,
    val symbol: String,
    val quantity: Double,
    val averagePrice: Double,
    val highestPrice: Double,               // Track peak for trailing stops
    val purchaseDate: Long = 0L             // Epoch ms when position was first opened
)

fun PortfolioItemEntity.toDomain(): PortfolioItem {
    return PortfolioItem(
        id = id,
        symbol = symbol,
        quantity = quantity,
        averagePrice = averagePrice,
        highestPrice = highestPrice,
        purchaseDate = purchaseDate
    )
}

fun PortfolioItem.toEntity(simulationId: Int): PortfolioItemEntity {
    return PortfolioItemEntity(
        id = id,
        simulationId = simulationId,
        symbol = symbol,
        quantity = quantity,
        averagePrice = averagePrice,
        highestPrice = highestPrice,
        purchaseDate = purchaseDate
    )
}
