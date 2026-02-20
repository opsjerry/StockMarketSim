package com.example.stockmarketsim.presentation.navigation

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object CreateSimulation : Screen("create_simulation")
    object SimulationDetail : Screen("simulation_detail/{simulationId}") {
        fun createRoute(simulationId: Int) = "simulation_detail/$simulationId"
    }
    object AnalysisResult : Screen("analysis_result/{simulationId}") {
        fun createRoute(simulationId: Int) = "analysis_result/$simulationId"
    }
    object Settings : Screen("settings")
    object LogViewer : Screen("log_viewer/{simulationId}") {
        fun createRoute(simulationId: Int) = "log_viewer/$simulationId"
    }
    object ManageUniverse : Screen("manage_universe")
}
