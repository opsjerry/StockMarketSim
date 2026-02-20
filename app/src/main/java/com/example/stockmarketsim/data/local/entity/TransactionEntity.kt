package com.example.stockmarketsim.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = SimulationEntity::class,
            parentColumns = ["id"],
            childColumns = ["simulationId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val simulationId: Int,
    val date: Long,
    val type: String, // BUY, SELL
    val symbol: String,
    val quantity: Double,
    val price: Double,
    val amount: Double, // total value
    val reason: String, // The "Why"
    val brokerOrderId: String? = null // For Live Trading
)
