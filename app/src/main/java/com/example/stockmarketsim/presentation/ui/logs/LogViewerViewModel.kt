package com.example.stockmarketsim.presentation.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.stockmarketsim.data.manager.SimulationLogManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltViewModel
class LogViewerViewModel @Inject constructor(
    private val logManager: SimulationLogManager,
    private val application: Application
) : ViewModel() {

    private val _events = MutableStateFlow<List<IntelligenceEvent>>(emptyList())
    val events = _events.asStateFlow()
    
    private val _rawLogs = MutableStateFlow("")
    val rawLogs = _rawLogs.asStateFlow()

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus = _exportStatus.asStateFlow()

    fun loadLogs(simulationId: Int) {
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) {
                logManager.getLogs(simulationId)
            }
            _rawLogs.value = content
            _events.value = parseLogs(content)
        }
    }

    fun exportHtmlReport(simulationId: Int, simName: String = "Simulation #$simulationId") {
        viewModelScope.launch {
            _exportStatus.value = "Generating report…"
            try {
                val htmlFile = withContext(Dispatchers.IO) {
                    logManager.generateHtmlReport(simulationId, simName)
                }
                // Share the file via a FileProvider so Chrome/any browser can open it
                val uri: Uri = FileProvider.getUriForFile(
                    application,
                    "${application.packageName}.fileprovider",
                    htmlFile
                )
                val shareIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "text/html")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                application.startActivity(Intent.createChooser(shareIntent, "Open Intelligence Report").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                _exportStatus.value = null
            } catch (e: Exception) {
                _exportStatus.value = "Export failed: ${e.message}"
            }
        }
    }

    private fun parseLogs(logContent: String): List<IntelligenceEvent> {
        val lines = logContent.split("\n")
        return lines.mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            
            // Structured tags
            val stopLossRegex = Regex("\\[STOP_LOSS\\] (.*)")
            val regimeRegex = Regex("\\[REGIME\\] (.*)")
            val tradeRegex = Regex("""\[TRADE\] (BUY|SELL|BOUGHT|SOLD) (.*?) @ ([\d\.,]+) \| Qty: ([\d\.,]+) \| Value: ([\d\.,]+) \| Reason: (.+)""")
            val analysisRegex = Regex("""\[ANALYSIS\] (.+)""")
            val warnRegex = Regex("""\[WARN\] (.+)""")
            val errorRegex = Regex("""\[ERROR\] (.+)""")
            val infoTagRegex = Regex("""\[INFO\] (.+)""")
            
            // 1. Check Stop Loss
            stopLossRegex.find(line)?.let {
                return@mapNotNull IntelligenceEvent.Error("Capital Protection: ${it.groupValues[1]}")
            }

            // 2. Check Regime
            regimeRegex.find(line)?.let {
                return@mapNotNull IntelligenceEvent.Warning("Intelligence Alert: ${it.groupValues[1]}")
            }

            // 3. Trades
            val tradeMatch = tradeRegex.find(line)
            if (tradeMatch != null) {
                val (rawType, symbol, price, qty, value, reason) = tradeMatch.destructured
                val type = if (rawType.uppercase().contains("BUY") || rawType.uppercase().contains("BOUGHT")) "BUY" else "SELL"
                return@mapNotNull IntelligenceEvent.Trade(type, symbol, price, qty, value, reason)
            }
            
            // 4. Other Tags
            analysisRegex.find(line)?.let { return@mapNotNull IntelligenceEvent.Analysis(it.groupValues[1]) }
            warnRegex.find(line)?.let { return@mapNotNull IntelligenceEvent.Warning(it.groupValues[1]) }
            errorRegex.find(line)?.let { return@mapNotNull IntelligenceEvent.Error(it.groupValues[1]) }
            infoTagRegex.find(line)?.let { return@mapNotNull IntelligenceEvent.Analysis(it.groupValues[1]) }
            
            // Filter noise
            if (line.contains("Fetching data") || line.contains("Market Data fetched") || line.contains("Cookies captured")) return@mapNotNull null
            
            IntelligenceEvent.Info(line)
        }
    }
}

sealed class IntelligenceEvent {
    data class Trade(val type: String, val symbol: String, val price: String, val qty: String, val value: String, val reason: String) : IntelligenceEvent()
    data class Analysis(val message: String) : IntelligenceEvent()
    data class Warning(val message: String) : IntelligenceEvent()
    data class Error(val message: String) : IntelligenceEvent()
    data class Info(val message: String) : IntelligenceEvent()
}
