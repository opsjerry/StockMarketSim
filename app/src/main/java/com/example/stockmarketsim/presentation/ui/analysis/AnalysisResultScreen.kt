package com.example.stockmarketsim.presentation.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stockmarketsim.presentation.ui.components.GlassCard
import com.example.stockmarketsim.presentation.ui.components.GradientProgressBar
import com.example.stockmarketsim.presentation.ui.components.ShimmerBox
import com.example.stockmarketsim.presentation.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisResultScreen(
    simulationId: Int,
    onNavigateBack: () -> Unit,
    onSimulationStarted: () -> Unit,
    onNavigateToLogs: () -> Unit,
    viewModel: AnalysisViewModel = hiltViewModel()
) {
    LaunchedEffect(simulationId) { viewModel.loadAnalysis(simulationId) }

    val state by viewModel.state.collectAsState()
    val fmt = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    Scaffold(
        containerColor = Navy900,
        topBar = {
            TopAppBar(
                title = { Text("Analysis Results", style = MaterialTheme.typography.titleLarge, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy950)
            )
        }
    ) { padding ->
        when (val s = state) {
            is AnalysisState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(4) { ShimmerBox(Modifier.fillMaxWidth().height(110.dp)) }
                }
            }

            is AnalysisState.Error -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(s.message, color = BearRed, style = MaterialTheme.typography.bodyMedium)
                }
            }

            is AnalysisState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── Header ──────────────────────────────────────────────
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    "🏆 Strategy Battle Complete",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    s.simulationName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NeutralSlate
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    LabelValue("Capital", fmt.format(s.amount))
                                    LabelValue("Duration", "${s.duration} mo")
                                    LabelValue("Strategies",  "${s.strategies.size}")
                                }
                            }
                        }
                    }

                    // ── Strategy label ──────────────────────────────────────
                    item {
                        Text(
                            "Select your Auto-Pilot strategy",
                            style = MaterialTheme.typography.labelLarge,
                            color = NeutralSlate
                        )
                    }

                    // ── Ranked strategy cards ───────────────────────────────
                    itemsIndexed(s.strategies) { index, strategy ->
                        RankedStrategyCard(
                            rank = index + 1,
                            strategy = strategy,
                            enabled = !s.isActivating,
                            onSelect = { viewModel.selectStrategy(simulationId, strategy.id, onSimulationStarted) }
                        )
                    }

                    // ── Activation progress ─────────────────────────────────
                    if (s.isActivating) {
                        item {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = ElectricBlue, modifier = Modifier.size(32.dp))
                                    Spacer(Modifier.height(10.dp))
                                    Text("Activating Intelligence Engine…", style = MaterialTheme.typography.bodyMedium, color = NeutralSlate)
                                }
                            }
                        }
                    }

                    // ── Actions ─────────────────────────────────────────────
                    item {
                        val context = LocalContext.current
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    viewModel.generatePdf(simulationId) { file ->
                                        android.widget.Toast.makeText(
                                            context,
                                            if (file != null) "PDF Saved: ${file.name}" else "PDF generation failed",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !s.isActivating,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Navy700)
                            ) {
                                Text("📄 Download Analysis Report", color = Color.White)
                            }

                            OutlinedButton(
                                onClick = onNavigateToLogs,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("📋 View Simulation Logs")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankedStrategyCard(
    rank: Int,
    strategy: StrategyRecommendation,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    val medal = when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#$rank" }
    val isWinner = rank == 1
    val glow = if (isWinner && enabled) ElectricBlue else Color.Transparent
    val fmt = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val alphaColor = when {
        !enabled -> NeutralSlate
        strategy.alpha >= 0 -> BullGreen
        else -> BearRed
    }
    val scoreNorm = (strategy.score.coerceIn(0.0, 1.0)).toFloat()

    GlassCard(modifier = Modifier.fillMaxWidth(), glowColor = glow) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Rank + name ───────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(medal, fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            strategy.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isWinner && enabled) Color.White else NeutralSlate,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            strategy.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = NeutralSlate
                        )
                    }
                }
                if (isWinner) {
                    Surface(
                        color = ElectricBlue.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "RECOMMENDED",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricBlue,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Score bar ─────────────────────────────────────────────────
            GradientProgressBar(
                progress = scoreNorm,
                startColor = if (isWinner) ElectricBlue else NeutralSlate,
                endColor   = if (isWinner) CyanAccent   else NeutralSlate.copy(alpha = 0.6f),
                height = 5.dp,
                label = "Score",
                trailingLabel = "${(scoreNorm * 100).toInt()}/100"
            )

            Spacer(Modifier.height(12.dp))

            // ── Quant metrics ─────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricPill("α Alpha", "${if (strategy.alpha >= 0) "+" else ""}${"%.2f".format(strategy.alpha)}%", alphaColor, Modifier.weight(1f))
                MetricPill("Sharpe", "${"%.2f".format(strategy.sharpe)}", ElectricBlue, Modifier.weight(1f))
                MetricPill("Max DD", "-${"%.1f".format(strategy.drawdown)}%", AmberWarning, Modifier.weight(1f))
                MetricPill("Value", fmt.format(strategy.finalValue).take(10), BullGreen, Modifier.weight(1.3f))
            }

            Spacer(Modifier.height(14.dp))

            // ── Select button ─────────────────────────────────────────────
            Button(
                onClick = onSelect,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isWinner) ElectricBlue else Navy700,
                    disabledContainerColor = Navy600
                )
            ) {
                Text(
                    if (enabled) "Select & Start →" else "Starting…",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Navy700)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = NeutralSlate)
        Text(value, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = NeutralSlate)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}
