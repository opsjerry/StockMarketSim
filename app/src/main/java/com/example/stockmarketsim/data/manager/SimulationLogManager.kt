package com.example.stockmarketsim.data.manager

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimulationLogManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun log(simulationId: Int, message: String) {
        // EXPERT REVIEW FIX: Security Obfuscation
        // In a real app, we would use BuildConfig.DEBUG.
        // For this improvement, we strip specific "Strategy Logic" details if they look sensitive.
        val safeMessage = maskSensitiveInfo(message)
        
        val file = File(context.filesDir, "sim_logs_$simulationId.txt")
        val timestamp = dateFormat.format(Date())
        val logLine = "[$timestamp] $safeMessage\n"
        
        try {
            file.appendText(logLine)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun maskSensitiveInfo(msg: String): String {
        // Simple obfuscation: If it contains specific keywords, redact the specific values
        // This is a placeholder for a more robust ProGuard/R8 mapping.
        if (msg.contains("API_KEY") || msg.contains("Auth")) return "[REDACTED SECURITY]"
        return msg
    }

    fun getLogs(simulationId: Int): String {
        // Limit log viewing to last 200KB to prevent OOM
        return getLogsTail(simulationId, 200 * 1024) 
    }

    private fun getLogsTail(simulationId: Int, limitBytes: Long): String {
        val file = File(context.filesDir, "sim_logs_$simulationId.txt")
        if (!file.exists()) return "No logs found for Simulation #$simulationId"
        
        return try {
            if (file.length() > limitBytes) {
                java.io.RandomAccessFile(file, "r").use { raf ->
                    raf.seek(file.length() - limitBytes)
                    val bytes = ByteArray(limitBytes.toInt())
                    raf.readFully(bytes)
                    
                    // Skip the first partial line
                    val text = String(bytes)
                    val firstNewline = text.indexOf('\n')
                    val cleanText = if (firstNewline != -1) text.substring(firstNewline + 1) else text
                    
                    "⚠️ LOGS TRUNCATED (Showing last ${limitBytes/1024}KB) ...\n\n$cleanText"
                }
            } else {
                file.readText()
            }
        } catch (e: Exception) {
            "Error reading logs: ${e.message}"
        }
    }
    
    fun clearLogs(simulationId: Int) {
        val file = File(context.filesDir, "sim_logs_$simulationId.txt")
        if (file.exists()) file.delete()
    }
}
