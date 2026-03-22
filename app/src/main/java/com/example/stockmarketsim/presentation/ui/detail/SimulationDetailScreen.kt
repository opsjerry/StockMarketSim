package com.example.stockmarketsim.presentation.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stockmarketsim.data.local.entity.TransactionEntity
import com.example.stockmarketsim.domain.analysis.RegimeSignal
import com.example.stockmarketsim.presentation.ui.components.*
import com.example.stockmarketsim.presentation.ui.theme.*
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
    LaunchedEffect(simulationId) { viewModel.loadSimulation(simulationId) }

    val simulation    by viewModel.simulation.collectAsState()
    val holdings      by viewModel.holdings.collectAsState()
    val holdingsValue by viewModel.holdingsValue.collectAsState()
    val transactions  by viewModel.transactions.collectAsState()
    val history       by viewModel.history.collectAsState()
    val sharpeRatio   by viewModel.sharpeRatio.collectAsState()
    val alpha         by viewModel.alpha.collectAsState()
    val insufficientData by viewModel.insufficientData.collectAsState()
    val message       by viewModel.message.collectAsState()
    val macroSnapshot by viewModel.macroSnapshot.collectAsState()
    val livePrices    by viewModel.livePrices.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() } }

    val fmt = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Navy900,
        topBar = {
            TopAppBar(
                title = { Text(simulation?.name ?: "Simulation", style = MaterialTheme.typography.titleLarge, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy950)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
        ) {

            // ── 1. Hero header card ───────────────────────────────────────────
            item {
                simulation?.let { sim ->
                    val roi = if (sim.initialAmount > 0) ((sim.totalEquity - sim.initialAmount) / sim.initialAmount) * 100 else 0.0
                    val isPositive = roi >= 0
                    val goalProgress = (roi / sim.targetReturnPercentage).coerceIn(0.0, 1.0).toFloat()

                    GlassCard(modifier = Modifier.fillMaxWidth(), glowColor = if (isPositive) BullGreen else BearRed) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Current Equity", style = MaterialTheme.typography.labelMedium, color = NeutralSlate)
                                    Text(
                                        fmt.format(sim.totalEquity),
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val prefix = if (roi > 0) "+" else ""
                                    Text(
                                        "${prefix}${"%.2f".format(roi)}% total return",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isPositive) BullGreen else BearRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                // Circular goal arc
                                CircularGoalArc(
                                    progress = goalProgress,
                                    label = "${"%.0f".format(goalProgress * 100)}%",
                                    sublabel = "of goal",
                                    color = if (isPositive) BullGreen else BearRed
                                )
                            }

                            Spacer(Modifier.height(14.dp))
                            // Portfolio allocation segmented bar
                            val cash = sim.currentAmount
                            val invested = sim.totalEquity - cash
                            val investedPct = if (sim.totalEquity > 0) (invested / sim.totalEquity).toFloat() else 0f

                            GradientProgressBar(
                                progress = investedPct,
                                startColor = ElectricBlue,
                                endColor = CyanAccent,
                                trackColor = BullGreen.copy(alpha = 0.3f),
                                height = 7.dp,
                                label = "Invested ${"%.0f".format(investedPct * 100)}%",
                                trailingLabel = "Cash ${"%.0f".format((1 - investedPct) * 100)}%"
                            )

                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Active Strategy", style = MaterialTheme.typography.labelSmall, color = NeutralSlate)
                                    Text(
                                        sim.strategyId.replace("_", " ").lowercase()
                                            .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.titlecase(Locale.getDefault()) } }
                                            .replace("Ml","ML").replace("Ema","EMA")
                                            .replace("Rsi","RSI").replace("Dnn","DNN")
                                            .replace("Sma","SMA").replace("Macd","MACD").replace("Bb","BB"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = CyanAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Target Return", style = MaterialTheme.typography.labelSmall, color = NeutralSlate)
                                    Text(
                                        "${"%.1f".format(sim.targetReturnPercentage)}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = ElectricBlue,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Live", style = MaterialTheme.typography.labelSmall, color = NeutralSlate)
                                    Spacer(Modifier.width(4.dp))
                                    Switch(
                                        checked = sim.isLiveTradingEnabled,
                                        onCheckedChange = { viewModel.toggleLiveTrading(it) },
                                        modifier = Modifier.scale(0.75f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 2. Quant metrics ──────────────────────────────────────────────
            item {
                val sharpeLabel = when {
                    insufficientData   -> null
                    sharpeRatio >= 2.0 -> "Excellent"
                    sharpeRatio >= 1.0 -> "Good"
                    sharpeRatio >= 0.5 -> "Acceptable"
                    else               -> "Poor"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatChip(
                        label = "α vs Nifty",
                        value = if (insufficientData) "< 20 days" else "${if (alpha >= 0) "+" else ""}${"%.2f".format(alpha)}%",
                        valueColor = when {
                            insufficientData -> NeutralSlate
                            alpha >= 0       -> BullGreen
                            else             -> BearRed
                        },
                        badge = if (insufficientData) "Insufficient data" else null,
                        modifier = Modifier.weight(1f)
                    )
                    StatChip(
                        label = "Sharpe Ratio",
                        value = if (insufficientData) "< 20 days" else "${"%.2f".format(sharpeRatio)}",
                        valueColor = if (insufficientData) NeutralSlate else ElectricBlue,
                        badge = if (insufficientData) "Insufficient data" else sharpeLabel,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Alpha warning banners
                if (!insufficientData) {
                    Spacer(Modifier.height(8.dp))
                    when {
                        alpha < -5.0 -> WarnBanner(
                            "🚨 Auto-Pilot switching strategy — Alpha breached -5% floor.",
                            BearRed
                        )
                        alpha < -3.0 -> WarnBanner(
                            "⚠️ Strategy may auto-switch soon — Alpha (${"%,.2f".format(alpha)}%) approaching -5%.",
                            AmberWarning
                        )
                        else -> {}
                    }
                }
            }

            // ── 3. Performance chart ──────────────────────────────────────────
            item {
                if (history.isNotEmpty() && simulation != null) {
                    val sim = simulation!!
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Performance vs Goal", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LegendDot(ElectricBlue); Spacer(Modifier.width(3.dp)); Text("Portfolio", style = MaterialTheme.typography.labelSmall, color = NeutralSlate)
                                Spacer(Modifier.width(10.dp))
                                LegendDot(AmberWarning); Spacer(Modifier.width(3.dp)); Text("Target", style = MaterialTheme.typography.labelSmall, color = NeutralSlate)
                            }
                        }

                        com.example.stockmarketsim.presentation.ui.components.PortfolioTrendChart(
                            history = history,
                            initialAmount = sim.initialAmount,
                            targetReturn = sim.targetReturnPercentage,
                            modifier = Modifier.fillMaxWidth().height(220.dp)
                        )

                        Spacer(Modifier.height(8.dp))
                        val roi = if (sim.initialAmount > 0) ((sim.totalEquity - sim.initialAmount) / sim.initialAmount) * 100 else 0.0
                        val goalProgress = (roi / sim.targetReturnPercentage).coerceIn(0.0, 1.0).toFloat()
                        GradientProgressBar(
                            progress = goalProgress,
                            startColor = ElectricBlue,
                            endColor = if (goalProgress >= 1f) BullGreen else CyanAccent,
                            height = 5.dp,
                            label = "Goal Progress",
                            trailingLabel = "${"%.2f".format(roi)}% of ${"%.1f".format(sim.targetReturnPercentage)}% target"
                        )
                    }
                }
            }

            // ── 4. Auto-Pilot status ──────────────────────────────────────────
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = BullGreen.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Settings, null, tint = BullGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Auto-Pilot: Active", style = MaterialTheme.typography.titleSmall, color = Color.White)
                        }
                        Spacer(Modifier.height(10.dp))
                        Divider(color = Navy600)
                        Spacer(Modifier.height(10.dp))

                        val snap = macroSnapshot
                        if (snap != null) {
                            val (regimeLabel, regimeColor) = when (snap.regime) {
                                RegimeSignal.BULLISH -> "🐂 BULLISH" to BullGreen
                                RegimeSignal.BEARISH -> "🐻 BEARISH" to BearRed
                                RegimeSignal.NEUTRAL -> "⚖️ NEUTRAL" to NeutralSlate
                            }
                            val smaSign  = if (snap.smaDistancePct >= 0) "+" else ""
                            val smaColor = if (snap.smaDistancePct >= 0) BullGreen else BearRed
                            val volColor = if (snap.volatilityPct > 20.0) BearRed else BullGreen
                            val cpiColor = if (snap.cpiPct > 6.0) BearRed else BullGreen

                            Text("Live Macro Snapshot", style = MaterialTheme.typography.labelSmall, color = NeutralSlate)
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MacroChip("Regime", regimeLabel, regimeColor, Modifier.weight(1f))
                                MacroChip(
                                    "SMA(200)",
                                    "$smaSign${"%.1f".format(snap.smaDistancePct)}%",
                                    smaColor,
                                    Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MacroChip(
                                    "1Y Vol",
                                    "${"%.1f".format(snap.volatilityPct)}%  ❘ 20%",
                                    volColor,
                                    Modifier.weight(1f)
                                )
                                MacroChip(
                                    "India CPI",
                                    "${"%.1f".format(snap.cpiPct)}%  ❘ 6%",
                                    cpiColor,
                                    Modifier.weight(1f)
                                )
                            }
                        } else {
                            Text(
                                "SMA(200) + 20-day fast-bear tripwire active",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeutralSlate
                            )
                        }
                    }
                }
            }

            // ── 5. Logs button ────────────────────────────────────────────────
            item {
                simulation?.let { sim ->
                    OutlinedButton(
                        onClick = { onNavigateToLogs(sim.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("View Intelligence Feed")
                    }
                }
            }

            // ── 6. Holdings ───────────────────────────────────────────────────
            item {
                Text("Holdings", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
            if (holdings.isEmpty()) {
                item {
                    Text("No open positions", style = MaterialTheme.typography.bodySmall, color = NeutralSlate, modifier = Modifier.padding(vertical = 4.dp))
                }
            } else {
                items(holdings.take(7)) { hold ->
                    val livePrice = livePrices[hold.symbol] ?: hold.averagePrice
                    val pnlPct = if (hold.averagePrice > 0) ((livePrice - hold.averagePrice) / hold.averagePrice) * 100.0 else 0.0
                    val pnlColor = if (pnlPct >= 0) BullGreen else BearRed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Navy700)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = ElectricBlue.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                Text(hold.symbol.take(6), style = MaterialTheme.typography.labelMedium, color = ElectricBlue, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("${"%.0f".format(hold.quantity)} units", style = MaterialTheme.typography.bodySmall, color = NeutralSlate)
                                val pnlSign = if (pnlPct >= 0) "+" else ""
                                Text(
                                    "$pnlSign${"%.1f".format(pnlPct)}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = pnlColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(fmt.format(hold.quantity * livePrice), style = MaterialTheme.typography.bodySmall, color = Color.White)
                    }
                }
            }

            // ── 7. Transactions ───────────────────────────────────────────────
            item {
                Spacer(Modifier.height(4.dp))
                Text("Transaction History", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
            items(transactions, key = { it.id }) { txn ->
                TransactionItem(txn, fmt)
            }
        }
    }
}

// ── Circular progress arc ────────────────────────────────────────────────────
@Composable
private fun CircularGoalArc(
    progress: Float,
    label: String,
    sublabel: String,
    color: Color,
    size: androidx.compose.ui.unit.Dp = 70.dp
) {
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = this.size.width * 0.12f
            val startAngle = 135f
            val sweepAngle = 270f
            drawArc(color = Navy600, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round))
            if (progress > 0f) {
                drawArc(color = color, startAngle = startAngle, sweepAngle = sweepAngle * progress.coerceIn(0f,1f), useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold)
            Text(sublabel, style = MaterialTheme.typography.labelSmall, color = NeutralSlate)
        }
    }
}

@Composable
private fun WarnBanner(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(10.dp))
    }
}

@Composable
private fun LegendDot(color: Color) {
    Canvas(modifier = Modifier.size(8.dp)) { drawCircle(color) }
}

// ── Transaction row ───────────────────────────────────────────────────────────
@Composable
fun TransactionItem(txn: TransactionEntity, formatter: NumberFormat) {
    val isBuy = txn.type == "BUY"
    val accentColor = if (isBuy) BullGreen else BearRed
    val dateStr = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(txn.date))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Navy700.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Direction dot
        Box(
            modifier = Modifier.size(8.dp).clip(CircleShape).background(accentColor)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${txn.type} ${txn.symbol} · ${"%.0f".format(txn.quantity)} units",
                style = MaterialTheme.typography.bodySmall,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = ElectricBlue)
                Spacer(Modifier.width(7.dp))
                Surface(
                    color = Navy600,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(txn.reason, style = MaterialTheme.typography.labelSmall, color = NeutralSlate, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                }
            }
        }
        Text(formatter.format(txn.amount), style = MaterialTheme.typography.bodySmall, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

/** A small titled info chip used inside the Live Macro Snapshot grid. */
@Composable
private fun MacroChip(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Surface(
        color = Navy700,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = NeutralSlate)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.labelMedium, color = valueColor, fontWeight = FontWeight.Bold)
        }
    }
}
