package com.example.stockmarketsim.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

import kotlinx.coroutines.flow.first

@HiltWorker
class ModelUpdaterWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val client: OkHttpClient,
    private val simulationRepository: com.example.stockmarketsim.domain.repository.SimulationRepository,
    private val logManager: com.example.stockmarketsim.data.manager.SimulationLogManager
) : CoroutineWorker(context, params) {

    private val TAG = "ModelUpdaterWorker"
    private val MODEL_FILE_NAME = "stock_model.tflite"
    
    // In production, this would be an S3 bucket or Firebase Storage URL
    // We are pointing this to the latest metadata release on the GitHub 'main' branch
    private val OTA_METADATA_URL = "https://raw.githubusercontent.com/opsjerry/StockMarketSim/main/model_metadata.json"

    private suspend fun broadcastLog(msg: String) {
        try {
            val simulations = simulationRepository.getSimulations().first()
            val activeSims = simulations.filter { it.status == com.example.stockmarketsim.domain.model.SimulationStatus.ACTIVE }
            activeSims.forEach { sim ->
                logManager.log(sim.id, msg)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking for Multi-Factor ML Model OTA updates...")
            broadcastLog("[INFO] 🤖 Auto-Pilot checking GitHub for new AI models...")

            // 1. Fetch Metadata
            val request = Request.Builder().url(OTA_METADATA_URL).build()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to fetch model metadata: ${response.code}")
                return@withContext Result.retry()
            }

            val jsonOutput = response.body?.string() ?: return@withContext Result.failure()
            val metadata = JSONObject(jsonOutput)
            
            val latestVersion = metadata.getString("version")   // e.g. "20260301.13" — CI emits as unquoted Double
            val downloadUrl = metadata.getString("download_url")
            val expectedSha256 = metadata.getString("sha256")
            
            Log.d(TAG, "Found latest model version: $latestVersion")

            // Migration-safe version read:
            // Existing installs stored version as Int via putInt(). Calling getString() on an
            // Int-typed key throws ClassCastException on most Android versions. We try getString()
            // first (new format), fall back to getInt() for legacy devices, and migrate in-place.
            val prefs = context.getSharedPreferences("ml_ops_prefs", Context.MODE_PRIVATE)
            val currentVersion: String = resolveCurrentVersion(
                getString = { prefs.getString("current_model_version", null) },
                getInt    = { prefs.getInt("current_model_version", 0) },
                migrate   = { migrated ->
                    prefs.edit().remove("current_model_version")
                        .putString("current_model_version", migrated).apply()
                    Log.d(TAG, "Migrated model version pref from Int to String($migrated)")
                }
            )

            if (latestVersion == currentVersion) {
                Log.d(TAG, "App is already using the latest model engine (v$currentVersion).")
                broadcastLog("[INFO] ✨ Deep Neural Net (v$currentVersion) is up-to-date.")
                return@withContext Result.success()
            }


            // 2. Download Model file securely to a temporary file
            Log.d(TAG, "Downloading newer model (v$latestVersion)...")
            broadcastLog("[INFO] 📥 Downloading new Deep Neural Net (v$latestVersion)...")
            val modelRequest = Request.Builder().url(downloadUrl).build()
            val modelResponse = client.newCall(modelRequest).execute()
            
            if (!modelResponse.isSuccessful) {
                return@withContext Result.retry()
            }

            val tempFile = File(context.cacheDir, "temp_stock_model.tflite")
            val inputStream = modelResponse.body?.byteStream()
            val outputStream = FileOutputStream(tempFile)
            
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            // 3. Phase 8 Safety Check: Verify SHA-256 Checksum
            val downloadedSha256 = calculateSha256(tempFile)
            if (downloadedSha256 != expectedSha256) {
                Log.e(TAG, "CRITICAL: Model Checksum mismatch! Corrupted download or MITM attack.")
                Log.e(TAG, "Expected: $expectedSha256 | Got: $downloadedSha256")
                broadcastLog("[ERROR] ⚠️ OTA Download corrupted! Falling back to bundled model.")
                tempFile.delete()
                
                // Discard the corrupted file. The StockPriceForecaster will safely
                // fall back to the bundled assets/stock_model.tflite
                return@withContext Result.failure()
            }

            // 4. Atomically Replace Old Model in filesDir
            val finalTarget = File(context.filesDir, MODEL_FILE_NAME)
            if (finalTarget.exists()) {
                finalTarget.delete()
            }
            tempFile.copyTo(finalTarget, overwrite = true)
            tempFile.delete()

            // 5. Update Version Preference
            prefs.edit().putString("current_model_version", latestVersion).apply()
            
            Log.i(TAG, "✅ Multi-Factor ML Model OTA Update Successful! (v$currentVersion -> v$latestVersion)")
            broadcastLog("[INFO] ✅ New AI Brain activated! Updated to v$latestVersion.")
            return@withContext Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.retry()
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val inputStream = file.inputStream()
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        val bytes = digest.digest()
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        /**
         * Resolves the currently stored model version string in a migration-safe way.
         *
         * Legacy installs stored the version as an Int via putInt(). Calling getString()
         * on an Int-typed SharedPreferences key throws ClassCastException on most Android
         * versions. This function handles all three states:
         *   1. String key already exists (new format) → returned directly.
         *   2. Key missing or null (fresh install) → fallback to getInt(); "0" if absent.
         *   3. ClassCastException (key exists as Int, legacy install) → read int, migrate,
         *      invoke [migrate] callback so the caller can persist the new String value.
         *
         * Exposed as a companion function so it can be unit-tested without Android Context.
         */
        fun resolveCurrentVersion(
            getString: () -> String?,
            getInt: () -> Int,
            migrate: (String) -> Unit
        ): String {
            return try {
                getString() ?: run {
                    // Key missing — fresh install or wiped prefs; check for legacy Int
                    val legacyInt = getInt()
                    if (legacyInt != 0) legacyInt.toString() else "0"
                }
            } catch (e: ClassCastException) {
                // Key exists but typed as Int — legacy install. Read, convert, migrate.
                val legacyInt = getInt()
                val migrated = if (legacyInt != 0) legacyInt.toString() else "0"
                migrate(migrated)
                migrated
            }
        }
    }
}
