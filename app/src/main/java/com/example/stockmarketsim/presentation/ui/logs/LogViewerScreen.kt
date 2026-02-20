package com.example.stockmarketsim.presentation.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

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

    LaunchedEffect(simulationId) {
        viewModel.loadLogs(simulationId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Intelligence Feed") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Intelligence Logs", rawLogs)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Logs copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Copy to Clipboard")
                    }
                    IconButton(onClick = { viewModel.loadLogs(simulationId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (events.isEmpty()) {
                item { 
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(events.size) { index ->
                    IntelligenceLogItem(events[index])
                }
            }
        }
    }
}

@Composable
fun IntelligenceLogItem(event: IntelligenceEvent) {
    when (event) {
        is IntelligenceEvent.Trade -> TradeCard(event)
        is IntelligenceEvent.Analysis -> AnalysisCard(event)
        is IntelligenceEvent.Warning -> WarningCard(event)
        is IntelligenceEvent.Error -> ErrorCard(event)
        is IntelligenceEvent.Info -> InfoCard(event)
    }
}

@Composable
fun TradeCard(trade: IntelligenceEvent.Trade) {
    val isBuy = trade.type == "BUY"
    val color = if (isBuy) androidx.compose.ui.graphics.Color(0xFFE8F5E9) else androidx.compose.ui.graphics.Color(0xFFFFEBEE)
    val icon = if (isBuy) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown
    val textColor = if (isBuy) androidx.compose.ui.graphics.Color(0xFF1B5E20) else androidx.compose.ui.graphics.Color(0xFFB71C1C)

    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = textColor.copy(alpha = 0.1f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.padding(4.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${if (isBuy) "BUY" else "SELL"} ${trade.symbol}", 
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, color = textColor)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "₹${trade.value}", 
                    style = MaterialTheme.typography.titleMedium.copy(color = textColor)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = "${trade.qty} units @ ₹${trade.price}", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = trade.reason, 
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = textColor.copy(alpha = 0.7f)
                    )
                )
            }
        }
    }
}

@Composable
fun AnalysisCard(analysis: IntelligenceEvent.Analysis) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
             Icon(
                 Icons.Filled.Info, 
                 contentDescription = null, 
                 tint = MaterialTheme.colorScheme.primary,
                 modifier = Modifier.size(20.dp).padding(top = 2.dp)
             )
             Spacer(modifier = Modifier.width(12.dp))
             Text(
                 text = analysis.message, 
                 style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                 color = MaterialTheme.colorScheme.onSurfaceVariant
             )
        }
    }
}

@Composable
fun WarningCard(warning: IntelligenceEvent.Warning) {
    Card(
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFFFF3E0)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFFFB74D))
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
             Icon(Icons.Filled.Warning, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFFE65100))
             Spacer(modifier = Modifier.width(12.dp))
             Text(
                 text = warning.message, 
                 style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                 color = androidx.compose.ui.graphics.Color(0xFFBF360C)
             )
        }
    }
}

@Composable
fun ErrorCard(error: IntelligenceEvent.Error) {
    Card(
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFFFEBEE)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFEF9A9A))
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
             Icon(Icons.Filled.Warning, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFFC62828))
             Spacer(modifier = Modifier.width(12.dp))
             Text(
                 text = error.message, 
                 style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
                 color = androidx.compose.ui.graphics.Color(0xFFB71C1C)
             )
        }
    }
}

@Composable
fun InfoCard(info: IntelligenceEvent.Info) {
    Text(
        text = info.message, 
        style = MaterialTheme.typography.bodySmall.copy(color = androidx.compose.ui.graphics.Color.Gray),
        modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp)
    )
}
