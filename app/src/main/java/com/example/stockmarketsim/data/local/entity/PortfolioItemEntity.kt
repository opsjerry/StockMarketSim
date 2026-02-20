package com.example.stockmarketsim.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.example.stockmarketsim.domain.model.PortfolioItem

@Entity(
    tableName = "portfolio_items",
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
    val highestPrice: Double // Track peak for trailing stops
)

fun PortfolioItemEntity.toDomain(): PortfolioItem {
    return PortfolioItem(
        id = id,
        symbol = symbol,
        quantity = quantity,
        averagePrice = averagePrice,
        highestPrice = highestPrice
    )
}

fun PortfolioItem.toEntity(simulationId: Int): PortfolioItemEntity {
    return PortfolioItemEntity(
        id = id,
        simulationId = simulationId,
        symbol = symbol,
        quantity = quantity,
        averagePrice = averagePrice,
        highestPrice = highestPrice
    )
}
