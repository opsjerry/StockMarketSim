package com.example.stockmarketsim.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import com.example.stockmarketsim.domain.model.Simulation

@Entity(
    tableName = "simulation_history",
    foreignKeys = [
        ForeignKey(
            entity = SimulationEntity::class,
            parentColumns = ["id"],
            childColumns = ["simulationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        androidx.room.Index(value = ["simulationId", "date"], unique = true)
    ]
)
data class SimulationHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val simulationId: Int,
    val date: Long,
    val totalEquity: Double
)
