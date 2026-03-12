package com.example.stockmarketsim.presentation.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stockmarketsim.presentation.ui.components.ShimmerBox
import com.example.stockmarketsim.presentation.ui.theme.*
import kotlinx.coroutines.launch

// ── Filter tabs ───────────────────────────────────────────────────────────────
private enum class LogFilter(val label: String) {
    ALL("All"), TRADES("Trades"), ALERTS("Alerts"), ANALYSIS("Analysis")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    simulationId: Int,
    onNavigateBack: () -> Unit,
    viewModel: LogViewerViewModel = hiltViewModel()
) {
    val events by viewModel.events.collectAsState()
    val rawLogs by viewModel.rawLogs.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(LogFilter.ALL) }

    LaunchedEffect(simulationId) { viewModel.loadLogs(simulationId) }

    // Apply filter
    val filtered = remember(events, filter) {
        when (filter) {
            LogFilter.ALL      -> events
            LogFilter.TRADES   -> events.filterIsInstance<IntelligenceEvent.Trade>()
            LogFilter.ALERTS   -> events.filter { it is IntelligenceEvent.Warning || it is IntelligenceEvent.Error }
            LogFilter.ANALYSIS -> events.filterIsInstance<IntelligenceEvent.Analysis>()
        }
    }

    Scaffold(
        containerColor = Navy900,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Intelligence Feed", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text("Real-time simulation logs", style = MaterialTheme.typography.labelSmall, color = NeutralSlate)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy950),
                actions = {
                    IconButton(onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("Logs", rawLogs))
                        Toast.makeText(context, "Logs copied!", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.Share, null, tint = NeutralSlate) }
                    IconButton(onClick = { viewModel.loadLogs(simulationId) }) {
                        Icon(Icons.Default.Refresh, null, tint = NeutralSlate)
                    }
                }
            )
        },
        floatingActionButton = {
            if (filtered.size > 5) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    containerColor = Navy700,
                    contentColor = ElectricBlue,
                    shape = RoundedCornerShape(12.dp)
                ) { Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(18.dp)) }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter pills
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Navy950)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(LogFilter.entries) { tab ->
                    val selected = tab == filter
                    Surface(
                        onClick = { filter = tab },
                        color = if (selected) ElectricBlue else Navy700,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) Color.White else NeutralSlate,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // Log list
            if (filtered.isEmpty() && events.isEmpty()) {
                // Loading
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(6) { ShimmerBox(modifier = Modifier.fillMaxWidth().height(72.dp)) }
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No ${filter.label.lowercase()} events yet", color = NeutralSlate)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered.size) { index ->
                        IntelligenceLogItem(filtered[index])
                    }
                }
            }
        }
    }
}

@Composable
fun IntelligenceLogItem(event: IntelligenceEvent) {
    when (event) {
        is IntelligenceEvent.Trade    -> TradeCard(event)
        is IntelligenceEvent.Analysis -> AnalysisCard(event)
        is IntelligenceEvent.Warning  -> WarningCard(event)
        is IntelligenceEvent.Error    -> ErrorCard(event)
        is IntelligenceEvent.Info     -> InfoCard(event)
    }
}

// ── Dark-native Trade Card ────────────────────────────────────────────────────
@Composable
fun TradeCard(trade: IntelligenceEvent.Trade) {
    val isBuy = trade.type == "BUY"
    val accentColor = if (isBuy) BullGreen else BearRed
    val dimBg       = if (isBuy) BullGreenDim else BearRedDim
    val dirIcon     = if (isBuy) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown
    val typeLabel   = if (isBuy) "BUY" else "SELL"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(dimBg.copy(alpha = 0.4f))
            .border(
                0.8.dp,
                accentColor.copy(alpha = 0.4f),
                RoundedCornerShape(14.dp)
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.2f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(dirIcon, null, tint = accentColor, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "$typeLabel ${trade.symbol}",
                        style = MaterialTheme.typography.titleMedium,
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "${trade.qty} units @ ₹${trade.price}",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeutralSlate
                    )
                }
                Text(
                    "₹${trade.value}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            if (trade.reason.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    trade.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = accentColor.copy(alpha = 0.8f),
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

// ── Dark-native Analysis Card ─────────────────────────────────────────────────
@Composable
fun AnalysisCard(analysis: IntelligenceEvent.Analysis) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Navy700.copy(alpha = 0.7f))
            .border(0.5.dp, Navy600, RoundedCornerShape(12.dp))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Info,
                null,
                tint = ElectricBlue,
                modifier = Modifier.size(18.dp).padding(top = 1.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                analysis.message,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                color = NeutralSlate
            )
        }
    }
}

// ── Dark-native Warning Card ──────────────────────────────────────────────────
@Composable
fun WarningCard(warning: IntelligenceEvent.Warning) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AmberWarning.copy(alpha = 0.10f))
            .border(0.8.dp, AmberWarning.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = AmberWarning, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                warning.message,
                style = MaterialTheme.typography.bodySmall,
                color = AmberWarning,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ── Dark-native Error Card ────────────────────────────────────────────────────
@Composable
fun ErrorCard(error: IntelligenceEvent.Error) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BearRed.copy(alpha = 0.12f))
            .border(0.8.dp, BearRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = BearRed, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                error.message,
                style = MaterialTheme.typography.bodySmall,
                color = BearRed,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Info Card (minimal) ───────────────────────────────────────────────────────
@Composable
fun InfoCard(info: IntelligenceEvent.Info) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(NeutralSlate.copy(alpha = 0.4f))
        )
        Spacer(Modifier.width(10.dp))
        Text(
            info.message,
            style = MaterialTheme.typography.bodySmall,
            color = NeutralSlate.copy(alpha = 0.8f)
        )
    }
}
