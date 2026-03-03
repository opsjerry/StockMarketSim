package com.example.stockmarketsim.presentation.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stockmarketsim.data.local.entity.TransactionEntity
import com.example.stockmarketsim.presentation.ui.components.PortfolioTrendChart
import com.example.stockmarketsim.presentation.ui.components.PortfolioValueChart
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulationDetailScreen(
    simulationId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToLogs: (Int) -> Unit,
    viewModel: SimulationDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(simulationId) {
        viewModel.loadSimulation(simulationId)
    }

    val simulation by viewModel.simulation.collectAsState()
    val holdings by viewModel.holdings.collectAsState()
    val holdingsValue by viewModel.holdingsValue.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val history by viewModel.history.collectAsState()
    val recommendations by viewModel.recommendations.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val isSwitching by viewModel.isSwitching.collectAsState()
    val message by viewModel.message.collectAsState()
    val sharpeRatio by viewModel.sharpeRatio.collectAsState()
    val alpha by viewModel.alpha.collectAsState()
    val insufficientData by viewModel.insufficientData.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(simulation?.name ?: "Simulation Detail") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header stats
            item {
                simulation?.let { sim ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Current Equity", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                                Text(
                                    text = currencyFormat.format(sim.totalEquity),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                val roi = if (sim.initialAmount > 0) {
                                    ((sim.totalEquity - sim.initialAmount) / sim.initialAmount) * 100
                                } else 0.0
                                Text(
                                    text = "Total Return: ${if (roi >= 0) "+" else ""}${"%.2f".format(roi)}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (roi >= 0) Color(0xFF10B981) else Color(0xFFEF4444),
                                    fontWeight = FontWeight.Bold
                                )
                                
                                val cash = sim.currentAmount
                                val invested = sim.totalEquity - cash
                                val investedPct = if (sim.totalEquity > 0) (invested / sim.totalEquity).toFloat() else 0f
                                
                                // FIX 4: Clarify equity/cash bar — blue fill = invested, green track = cash
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Portfolio Allocation",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = investedPct,
                                    modifier = Modifier.fillMaxWidth(0.9f).height(6.dp),
                                    color = MaterialTheme.colorScheme.primary,   // blue = invested
                                    trackColor = Color(0xFF10B981)               // green = cash
                                )
                                Row(modifier = Modifier.fillMaxWidth(0.9f).padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        Box(Modifier.size(6.dp).then(Modifier).also {}, contentAlignment = androidx.compose.ui.Alignment.Center) {
                                            androidx.compose.foundation.Canvas(modifier = Modifier.size(6.dp)) {
                                                drawCircle(androidx.compose.ui.graphics.Color(0xFF3B82F6))
                                            }
                                        }
                                        Spacer(Modifier.width(3.dp))
                                        Text("Invested ${"%.0f".format(investedPct * 100)}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        androidx.compose.foundation.Canvas(modifier = Modifier.size(6.dp)) {
                                            drawCircle(androidx.compose.ui.graphics.Color(0xFF10B981))
                                        }
                                        Spacer(Modifier.width(3.dp))
                                        Text("Cash ${"%.0f".format((1 - investedPct) * 100)}%", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981))
                                    }
                                }
                            }
                            
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                Text("Active Strategy", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                                val prettyStrategy = sim.strategyId.replace("_", " ").lowercase()
                                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                                    .replace("Ema", "EMA")
                                    .replace("Rsi", "RSI")
                                    .replace("Macd", "MACD")
                                    .replace("Sma", "SMA")
                                    .replace("Dnn", "DNN")
                                    .replace("Ml", "ML")
                                    .replace("Bb", "BB")
                                
                                Text(prettyStrategy, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text("Target Goal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                                Text(
                                    text = "${"%.2f".format(sim.targetReturnPercentage)}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text("Live Trading", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Switch(
                                        checked = sim.isLiveTradingEnabled,
                                        onCheckedChange = { viewModel.toggleLiveTrading(it) },
                                        modifier = Modifier.scale(0.8f) // Compact switch
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Quant Metrics Layer
            item {
                if (insufficientData) {
                    // Phase 1 Fix: Don't show statistically meaningless metrics
                    // With < 20 data points, the 95% CI on Sharpe spans ±2.0 — displaying it misleads.
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Text("α vs Nifty", style = MaterialTheme.typography.labelMedium)
                                Text("< 20 days", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Insufficient data", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Text("Sharpe Ratio", style = MaterialTheme.typography.labelMedium)
                                Text("< 20 days", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Insufficient data", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Text("α vs Nifty", style = MaterialTheme.typography.labelMedium)
                                Text("${if (alpha >= 0) "+" else ""}${"%.2f".format(alpha)}%", style = MaterialTheme.typography.titleMedium, color = if (alpha >= 0) Color(0xFF10B981) else Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                            }
                        }
                        Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Text("Sharpe Ratio", style = MaterialTheme.typography.labelMedium)
                                Text("${"%.2f".format(sharpeRatio)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Alpha warning chip — only meaningful when data is sufficient
                    if (alpha < -3.0 && alpha >= -5.0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text("⚠️", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Strategy may auto-switch soon — Alpha (${"%.2f".format(alpha)}%) approaching -5% threshold.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFF59E0B)
                                )
                            }
                        }
                    } else if (alpha < -5.0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text("🚨", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Auto-Pilot switching strategy now — Alpha breached -5% floor.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Performance Chart + Goal Progress
            item {
                if (history.isNotEmpty() && simulation != null) {
                    val sim = simulation!!
                    val roi = if (sim.initialAmount > 0) ((sim.totalEquity - sim.initialAmount) / sim.initialAmount) * 100 else 0.0
                    val goalProgress = (roi / sim.targetReturnPercentage).coerceIn(0.0, 1.0).toFloat()

                    // FIX 2: Chart header with legend
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("Performance vs Goal", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            androidx.compose.foundation.Canvas(modifier = Modifier.size(8.dp)) {
                                drawCircle(androidx.compose.ui.graphics.Color(0xFF3B82F6))
                            }
                            Spacer(Modifier.width(3.dp))
                            Text("Portfolio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            androidx.compose.foundation.Canvas(modifier = Modifier.size(8.dp)) {
                                drawCircle(androidx.compose.ui.graphics.Color(0xFF94A3B8))
                            }
                            Spacer(Modifier.width(3.dp))
                            Text("Target", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    PortfolioTrendChart(
                        history = history,
                        initialAmount = sim.initialAmount,
                        targetReturn = sim.targetReturnPercentage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )

                    // FIX 3: Goal progress bar — shows how far along the return target we are
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Progress to Goal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${"%.2f".format(roi)}% of ${"%.1f".format(sim.targetReturnPercentage)}% target",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (roi >= sim.targetReturnPercentage) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = goalProgress,
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = if (roi >= sim.targetReturnPercentage) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            
            // Auto-Pilot Status Section
            item {
                if (simulation != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)) // Slate-800 for stealth/premium look
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Settings, // Using Settings icon as proxy for "Gears/Checking"
                                    contentDescription = "Auto-Pilot",
                                    tint = Color(0xFF10B981) // Green
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Auto-Pilot: Active", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "System monitors strategy Alpha daily. If performance drops (Alpha < -5%), it automatically switches to a better performing strategy.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = Color(0xFF334155)) // Dark divider
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // EXPERT REVIEW UX: Market Status Sensor
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "MARKET SENSOR ACTIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF10B981),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Protects capital during Bear Markets (200 SMA)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            // PHASE 5: XGBoost Forecast Visualization
                            if (simulation?.strategyId == "MULTI_FACTOR_DNN") {
                                Spacer(modifier = Modifier.height(12.dp))
                                Divider(color = Color(0xFF334155))
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Text("Multi-Factor Intelligence (XGBoost)", style = MaterialTheme.typography.titleSmall, color = Color(0xFFA855F7)) // Purple
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Analysis: Probability of Uptrend > 60%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                                Text(
                                    "Features: Momentum (RSI), Value (P/E), Sentiment",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { onNavigateToLogs(simulation!!.id) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Text("View Detailed System Logs")
                    }
                }
            }

            // Charts
            item {
                Text("Holdings Allocation", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                
                if (holdingsValue.isNotEmpty()) {
                    PortfolioValueChart(
                        holdingsData = holdingsValue,
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    )
                } else {
                    Text("No holdings yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Top Holdings
            item {
                Text("Top Holdings", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                for (hold in holdings.take(5)) {
                    Text("- ${hold.symbol}: ${"%.2f".format(hold.quantity)} units", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Transaction Log", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
            }

            // Transactions List
            items(
                items = transactions,
                key = { it.id }
            ) { txn ->
                TransactionItem(txn, currencyFormat)
            }
        }
    }
}

@Composable
fun TransactionItem(txn: TransactionEntity, formatter: NumberFormat) {
    val dateFormatter = java.text.SimpleDateFormat("dd MMM, HH:mm", java.util.Locale.getDefault())
    val dateStr = dateFormatter.format(java.util.Date(txn.date))

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${txn.type} ${txn.symbol} (${"%.2f".format(txn.quantity)} units)",
                    fontWeight = FontWeight.Bold,
                    color = if (txn.type == "BUY") Color(0xFF10B981) else Color(0xFFEF4444)
                )
                Row {
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = txn.reason,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            Text(
                text = formatter.format(txn.amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
