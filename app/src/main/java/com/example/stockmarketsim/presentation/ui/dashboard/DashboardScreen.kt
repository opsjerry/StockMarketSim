package com.example.stockmarketsim.presentation.ui.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stockmarketsim.domain.model.Simulation
import com.example.stockmarketsim.domain.model.SimulationStatus
import com.example.stockmarketsim.presentation.ui.components.GlassCard
import com.example.stockmarketsim.presentation.ui.components.GradientProgressBar
import com.example.stockmarketsim.presentation.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToDetail: (Int) -> Unit,
    onNavigateToAnalysis: (Int) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val simulations by viewModel.simulations.collectAsState()

    Scaffold(
        containerColor = Navy900,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Auto-Pilot",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Intelligent Trading Simulator",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeutralSlate
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy950),
                actions = {
                    // SIM ONLY chip
                    Surface(
                        color = AmberWarning.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = "SIM ONLY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AmberWarning,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = NeutralSlate)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCreate,
                containerColor = ElectricBlue,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Simulation")
            }
        }
    ) { paddingValues ->
        if (simulations.isEmpty()) {
            EmptyDashboard(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onCreate = onNavigateToCreate
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
            ) {
                items(simulations) { simulation ->
                    SimulationCard(
                        simulation = simulation,
                        onClick = {
                            when (simulation.status) {
                                SimulationStatus.ANALYZING,
                                SimulationStatus.CREATED,
                                SimulationStatus.ANALYSIS_COMPLETE -> onNavigateToAnalysis(simulation.id)
                                else -> onNavigateToDetail(simulation.id)
                            }
                        },
                        onDelete = { viewModel.deleteSimulation(simulation) },
                        onRetry  = { viewModel.retrySimulation(simulation.id) }
                    )
                }

                // Ghost "Add" card
                item {
                    OutlinedCard(
                        onClick = onNavigateToCreate,
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Brush.horizontalGradient(listOf(Navy600, ElectricBlue.copy(alpha = 0.4f), Navy600))
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Add, null, tint = ElectricBlue, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("New Simulation", style = MaterialTheme.typography.bodyMedium, color = ElectricBlue)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDashboard(modifier: Modifier = Modifier, onCreate: () -> Unit) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📊", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text("No simulations yet", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(
            "Create your first Auto-Pilot simulation\nto start intelligent trading.",
            style = MaterialTheme.typography.bodyMedium,
            color = NeutralSlate,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onCreate,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Create Simulation", fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulationCard(
    simulation: Simulation,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit
) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    val roi = if (simulation.initialAmount > 0)
        ((simulation.totalEquity - simulation.initialAmount) / simulation.initialAmount) * 100
    else 0.0

    val isPositive = roi >= 0
    val glowColor = when {
        simulation.status == SimulationStatus.FAILED   -> BearRed
        simulation.status == SimulationStatus.ANALYZING-> ElectricBlue
        isPositive                                     -> BullGreen
        else                                            -> BearRed
    }

    val elapsedDays = ((System.currentTimeMillis() - simulation.startDate) / 86_400_000L).toInt().coerceAtLeast(0)
    val totalDays   = simulation.durationMonths * 30
    val timeProgress = (elapsedDays.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)
    val targetEquity = simulation.initialAmount * (1 + simulation.targetReturnPercentage / 100)

    // Strategy display name
    val strategyName = simulation.strategyId
        .replace("_", " ").lowercase()
        .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.titlecase(Locale.getDefault()) } }
        .replace("Bb","BB").replace("Ema","EMA").replace("Rsi","RSI")
        .replace("Macd","MACD").replace("Sma","SMA").replace("Dnn","DNN").replace("Ml","ML")
        .ifEmpty { "Pending" }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        glowColor = glowColor
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Row 1: Name + status badge + delete ──────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = simulation.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    if (simulation.strategyId.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text("⚙️", fontSize = 10.sp)
                            Spacer(Modifier.width(3.dp))
                            Text(
                                strategyName,
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanAccent
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(simulation.status)
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = BearRed.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Divider(color = Navy600.copy(alpha = 0.6f), thickness = 0.5.dp)
            Spacer(Modifier.height(12.dp))

            // ── Row 2: Equity + ROI | Target ─────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Current Equity", style = MaterialTheme.typography.labelSmall, color = NeutralSlate)
                    Text(
                        currencyFormat.format(simulation.totalEquity),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    if (simulation.status == SimulationStatus.ACTIVE || simulation.status == SimulationStatus.COMPLETED) {
                        val roiPrefix = if (roi > 0) "+" else ""
                        Text(
                            "$roiPrefix${"%.2f".format(roi)}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isPositive) BullGreen else BearRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                when (simulation.status) {
                    SimulationStatus.ACTIVE, SimulationStatus.COMPLETED -> {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Target", style = MaterialTheme.typography.labelSmall, color = NeutralSlate)
                            Text(
                                currencyFormat.format(targetEquity),
                                style = MaterialTheme.typography.titleSmall,
                                color = ElectricBlue,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Goal: ${"%.1f".format(simulation.targetReturnPercentage)}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeutralSlate
                            )
                        }
                    }
                    SimulationStatus.ANALYSIS_COMPLETE -> {
                        Button(
                            onClick = onClick,
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("View Results", fontSize = 12.sp) }
                    }
                    SimulationStatus.FAILED -> {
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BearRed),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Retry", fontSize = 12.sp) }
                    }
                    else -> {}
                }
            }

            // ── Row 3: Progress bar (active sims only) ───────────────────────
            if (simulation.status == SimulationStatus.ACTIVE) {
                Spacer(Modifier.height(14.dp))
                GradientProgressBar(
                    progress = timeProgress,
                    startColor = ElectricBlue,
                    endColor = if (timeProgress > 0.8f) BullGreen else CyanAccent,
                    height = 5.dp,
                    label = "Day $elapsedDays",
                    trailingLabel = "$totalDays days"
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: SimulationStatus) {
    val (label, bg, fg) = when (status) {
        SimulationStatus.ACTIVE           -> Triple("● ACTIVE",    BullGreenDim,     BullGreen)
        SimulationStatus.ANALYZING        -> Triple("◌ ANALYZING", NavyGlow,          ElectricBlue)
        SimulationStatus.ANALYSIS_COMPLETE-> Triple("✓ READY",     NavyGlow,          CyanAccent)
        SimulationStatus.COMPLETED        -> Triple("✓ DONE",      BullGreenDim,     BullGreen)
        SimulationStatus.FAILED           -> Triple("✕ FAILED",    BearRedDim,       BearRed)
        else                              -> Triple(status.name,   Navy700,           NeutralSlate)
    }
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
