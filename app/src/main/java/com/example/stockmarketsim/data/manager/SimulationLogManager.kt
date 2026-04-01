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

    private val lock = Any()

    fun log(simulationId: Int, message: String) {
        val safeMessage = maskSensitiveInfo(message)
        val file = File(context.filesDir, "sim_logs_$simulationId.txt")
        val timestamp = dateFormat.format(Date())
        val logLine = "[$timestamp] $safeMessage\n"
        synchronized(lock) {
            try { file.appendText(logLine) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun logToAll(simulationIds: List<Int>, message: String) {
        val safeMessage = maskSensitiveInfo(message)
        val timestamp = dateFormat.format(Date())
        val logLine = "[$timestamp] $safeMessage\n"
        synchronized(lock) {
            for (id in simulationIds) {
                try { File(context.filesDir, "sim_logs_$id.txt").appendText(logLine) }
                catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    private fun maskSensitiveInfo(msg: String): String {
        if (msg.contains("API_KEY") || msg.contains("Auth")) return "[REDACTED SECURITY]"
        return msg
    }

    fun getLogs(simulationId: Int): String {
        // Raised from 200 KB → 2 MB so full KPI tables are never truncated
        return getLogsTail(simulationId, 2 * 1024 * 1024L)
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
                    val text = String(bytes)
                    val firstNewline = text.indexOf('\n')
                    val cleanText = if (firstNewline != -1) text.substring(firstNewline + 1) else text
                    "⚠️ LOGS TRUNCATED (Showing last ${limitBytes / 1024}KB) ...\n\n$cleanText"
                }
            } else {
                file.readText()
            }
        } catch (e: Exception) { "Error reading logs: ${e.message}" }
    }

    fun clearLogs(simulationId: Int) {
        val file = File(context.filesDir, "sim_logs_$simulationId.txt")
        if (file.exists()) file.delete()
    }

    /**
     * Generates a styled HTML report from the plain-text log.
     * Saved to the app's cache dir under "sim_report_{id}.html".
     * The caller should share this file via a FileProvider intent so the user
     * can open it in Chrome / any browser without WebView integration.
     *
     * Line colouring rules:
     *  🟢 BUY lines     → green
     *  🔴 SELL/Stop     → red
     *  ⚠️ WARN lines    → amber
     *  ❌ ERROR lines   → red-intense
     *  🏆/📊/🔬/💼     → electric blue (Analysis blocks)
     *  all others        → neutral grey
     */
    fun generateHtmlReport(simulationId: Int, simName: String = "Simulation #$simulationId"): File {
        val rawLogs = getLogs(simulationId)
        val lines = rawLogs.split("\n")
        val reportDate = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())

        val sb = StringBuilder()
        sb.append("""
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Apex Trader — $simName</title>
<style>
  :root{--bg:#0a0f1e;--surface:#111827;--border:#1e2d45;--text:#c9d6e8;--dim:#6b7f9e;
        --green:#22c55e;--red:#ef4444;--amber:#f59e0b;--blue:#38bdf8;--purple:#a78bfa;
        --green-dim:rgba(34,197,94,0.12);--red-dim:rgba(239,68,68,0.12);
        --amber-dim:rgba(245,158,11,0.12);--blue-dim:rgba(56,189,248,0.10);}
  *{box-sizing:border-box;margin:0;padding:0;}
  body{background:var(--bg);color:var(--text);font-family:'Courier New',monospace;font-size:13px;line-height:1.65;}
  header{background:var(--surface);border-bottom:1px solid var(--border);padding:20px 28px;}
  header h1{font-size:20px;font-weight:700;color:#fff;letter-spacing:0.5px;}
  header p{color:var(--dim);font-size:12px;margin-top:4px;}
  .log-wrap{padding:16px 24px 80px;}
  .line{padding:5px 12px;border-radius:6px;margin-bottom:2px;word-break:break-word;white-space:pre-wrap;}
  .buy{background:var(--green-dim);color:var(--green);font-weight:600;}
  .sell{background:var(--red-dim);color:var(--red);font-weight:600;}
  .stop{background:var(--red-dim);color:var(--red);font-weight:600;}
  .warn{background:var(--amber-dim);color:var(--amber);}
  .error{background:rgba(239,68,68,0.2);color:#fca5a5;font-weight:700;}
  .analysis{background:var(--blue-dim);color:var(--blue);}
  .section{background:rgba(167,139,250,0.10);color:var(--purple);font-weight:600;}
  .info{color:var(--text);}
  .dim{color:var(--dim);}
  .ts{color:var(--dim);font-size:11px;margin-right:8px;}
  .divider{border-top:1px solid var(--border);margin:10px 0;}
  table{width:100%;border-collapse:collapse;margin:6px 0;}
  th{background:#1e2d45;color:var(--blue);text-align:left;padding:6px 10px;font-size:12px;}
  td{padding:5px 10px;border-bottom:1px solid #1a2640;font-size:12px;}
  tr:hover td{background:#111f33;}
</style>
</head>
<body>
<header>
  <h1>📊 Apex Trader — Intelligence Report</h1>
  <p><strong>$simName</strong> &nbsp;·&nbsp; Generated: $reportDate &nbsp;·&nbsp; ${lines.size} log entries</p>
</header>
<div class="log-wrap">
""")

        for (line in lines) {
            if (line.isBlank()) { sb.append("<div style='height:4px'></div>"); continue }

            // Extract timestamp + message
            val tsMatch = Regex("^\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\] (.*)$").find(line)
            val ts = tsMatch?.groupValues?.getOrNull(1) ?: ""
            val msg = tsMatch?.groupValues?.getOrNull(2) ?: line

            val escapedMsg = msg
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

            val tsSpan = if (ts.isNotBlank()) "<span class='ts'>[$ts]</span>" else ""

            val cssClass = when {
                msg.contains("🟢") && (msg.contains("BUY") || msg.contains("BOUGHT")) -> "buy"
                msg.contains("🔴") || msg.contains("SELL") || msg.startsWith("🛑") -> "sell"
                msg.startsWith("⚠️") || msg.contains("[WARN]") -> "warn"
                msg.startsWith("❌") || msg.contains("[ERROR]") -> "error"
                msg.startsWith("🏆") || msg.startsWith("📊") || msg.startsWith("🔬") ||
                msg.startsWith("💼") || msg.startsWith("📈") || msg.startsWith("🧹") ||
                msg.startsWith("📅") || msg.startsWith("🏅") -> "analysis"
                msg.startsWith("🌅") || msg.startsWith("🏁") || msg.startsWith("🗓️") ||
                msg.startsWith("🚀") || msg.startsWith("🎯") || msg.startsWith("🛡️") ||
                msg.startsWith("🐻") || msg.startsWith("✅") -> "section"
                msg.startsWith("─") || msg.startsWith("══") -> "divider"
                ts.isBlank() -> "dim"
                else -> "info"
            }

            if (cssClass == "divider") {
                sb.append("<div class='divider'></div>")
            } else {
                sb.append("<div class='line $cssClass'>$tsSpan$escapedMsg</div>\n")
            }
        }

        sb.append("</div></body></html>")

        val outFile = File(context.cacheDir, "sim_report_$simulationId.html")
        outFile.writeText(sb.toString())
        return outFile
    }
}



