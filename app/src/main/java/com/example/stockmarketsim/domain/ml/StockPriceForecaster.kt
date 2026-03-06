package com.example.stockmarketsim.domain.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.MappedByteBuffer

class StockPriceForecaster @Inject constructor(
    @ApplicationContext private val context: Context
) : IStockPriceForecaster {
    private var interpreter: Interpreter? = null
    private val MODEL_FILE = "stock_model.tflite"

    override fun initialize() {
        if (interpreter != null) return
        try {
            val modelBuffer = loadModelBuffer()
            interpreter = Interpreter(modelBuffer)
        } catch (e: Exception) {
            e.printStackTrace()
            // Model might be missing (not trained yet)
        }
    }

    private fun loadModelBuffer(): MappedByteBuffer {
        // Phase 8: MLOps OTA Support
        // First check if an updated model was downloaded to internal storage
        val otaFile = File(context.filesDir, MODEL_FILE)
        if (otaFile.exists() && otaFile.length() > 0) {
            try {
                val inputStream = java.io.FileInputStream(otaFile)
                val fileChannel = inputStream.channel
                return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
            } catch (e: Exception) {
                e.printStackTrace()
                // If OTA model is corrupted, fall back to assets
            }
        }
        
        // Fallback to factory bundled model in assets
        val fileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = java.io.FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    // Dynamic input buffer — lazily allocated to match actual feature count
    // Supports both 60 (LSTM log returns) and 64 (multi-factor: log returns + TA indicators)
    private var inputBuffer: ByteBuffer? = null
    private var currentFeatureCount = 0
    // Output: [1, 1] float = 4 bytes
    private val outputBuffer = ByteBuffer.allocateDirect(4 * 1).order(ByteOrder.nativeOrder())

    private fun getOrCreateInputBuffer(featureCount: Int): ByteBuffer {
        if (inputBuffer == null || currentFeatureCount != featureCount) {
            inputBuffer = ByteBuffer.allocateDirect(4 * featureCount).order(ByteOrder.nativeOrder())
            currentFeatureCount = featureCount
        }
        return inputBuffer!!
    }

    @Synchronized
    override fun predict(features: DoubleArray, symbol: String?, date: Long?): Float {
        if (features.isEmpty()) return Float.NaN
        if (interpreter == null) initialize()
        if (interpreter == null) return Float.NaN

        try {
            val buf = getOrCreateInputBuffer(features.size)
            
            // 1. Direct Buffer Write (Zero Allocation)
            buf.rewind()
            for (i in features.indices) {
                buf.putFloat(features[i].toFloat())
            }

            // 2. Prepare Output Buffer
            outputBuffer.rewind()
            
            // Fix: Rewind Input Buffer to position 0 before inference!
            buf.rewind()

            // 3. Run Inference
            interpreter?.run(buf, outputBuffer)
            
            // 4. Post-processing: model outputs a single continuous value (predicted log return)
            outputBuffer.rewind()
            val predictedReturn = outputBuffer.getFloat()
            
            if (predictedReturn.isNaN()) return Float.NaN

            return predictedReturn
            
        } catch (e: Exception) {
            e.printStackTrace()
            return Float.NaN
        }
    }

    override fun getModelVersion(): Int {
        val prefs = context.getSharedPreferences("ml_ops_prefs", Context.MODE_PRIVATE)
        // ModelUpdaterWorker stores this as a String (e.g. "20260301.13") since the OTA fix.
        // Legacy installs may still have an Int. Resolve safely to avoid ClassCastException.
        return try {
            val strVal = prefs.getString("current_model_version", null)
            // getString returns null if key is absent or stored as Integer type on old prefs.
            strVal?.toIntOrNull()
                ?: strVal?.substringBefore(".")?.toIntOrNull()  // "20260301.13" → 20260301
                ?: prefs.getInt("current_model_version", 1)
        } catch (e: ClassCastException) {
            // Key exists as Int (pre-String-migration install) — read it directly.
            try { prefs.getInt("current_model_version", 1) } catch (e2: Exception) { 1 }
        }
    }

    override fun getExpectedFeatureCount(): Int {
        if (interpreter == null) initialize()
        return try {
            val inputTensor = interpreter?.getInputTensor(0)
            val shape = inputTensor?.shape() // e.g. [1, 64, 1] or [1, 60, 1]
            shape?.getOrNull(1) ?: 60  // Second dimension is the feature count
        } catch (e: Exception) {
            60  // Default fallback for old 60-feature models
        }
    }
}
