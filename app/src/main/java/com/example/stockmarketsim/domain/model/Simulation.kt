package com.example.stockmarketsim.domain.model

data class Simulation(
    val id: Int = 0,
    val name: String,
    val initialAmount: Double,
    val currentAmount: Double,
    val totalEquity: Double = currentAmount,
    val durationMonths: Int,
    val startDate: Long,
    val targetReturnPercentage: Double,
    val status: SimulationStatus = SimulationStatus.CREATED,
    val strategyId: String = "", // e.g., "MOMENTUM", "SAFE_HAVEN"
    val lastSwitchDate: Long = 0L,
    val isLiveTradingEnabled: Boolean = false
)

enum class SimulationStatus {
    CREATED,
    ANALYZING,
    ANALYSIS_COMPLETE,
    ACTIVE,
    COMPLETED,
    FAILED
}
