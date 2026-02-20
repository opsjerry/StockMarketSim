package com.example.stockmarketsim.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.stockmarketsim.domain.model.Simulation
import com.example.stockmarketsim.domain.model.SimulationStatus

@Entity(tableName = "simulations")
data class SimulationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val initialAmount: Double,
    val currentAmount: Double,
    val totalEquity: Double,
    val durationMonths: Int,
    val startDate: Long,
    val targetReturnPercentage: Double,
    val status: String, // Stored as String for simplicity
    val strategyId: String,
    val lastSwitchDate: Long = 0L,
    val isLiveTradingEnabled: Boolean = false
)

fun SimulationEntity.toDomain(): Simulation {
    return Simulation(
        id = id,
        name = name,
        initialAmount = initialAmount,
        currentAmount = currentAmount,
        totalEquity = totalEquity,
        durationMonths = durationMonths,
        startDate = startDate,
        targetReturnPercentage = targetReturnPercentage,
        status = SimulationStatus.valueOf(status),
        strategyId = strategyId,
        lastSwitchDate = lastSwitchDate,
        isLiveTradingEnabled = isLiveTradingEnabled
    )
}

fun Simulation.toEntity(): SimulationEntity {
    return SimulationEntity(
        id = id,
        name = name,
        initialAmount = initialAmount,
        currentAmount = currentAmount,
        totalEquity = totalEquity,
        durationMonths = durationMonths,
        startDate = startDate,
        targetReturnPercentage = targetReturnPercentage,
        status = status.name,
        strategyId = strategyId,
        lastSwitchDate = lastSwitchDate,
        isLiveTradingEnabled = isLiveTradingEnabled
    )
}
