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

    // Reusable buffers to avoid allocation churn
    // Input for XGBoost/MultiFactor: [1, 6] floats = 6 * 4 bytes = 24 bytes
    // Features: [RSI_14, SMA_Ratio, ATR_Pct, Relative_Volume, PE_Ratio, Sentiment_Score]
    private val inputBuffer = ByteBuffer.allocateDirect(4 * 6).order(ByteOrder.nativeOrder())
    // Output: [1, 1] float = 4 bytes
    private val outputBuffer = ByteBuffer.allocateDirect(4 * 1).order(ByteOrder.nativeOrder())

    override fun predict(features: DoubleArray, symbol: String?, date: Long?): Float {
        // Guard: Need exactly 6 features for the orthogonal XGBoost model
        if (features.size != 6) return Float.NaN
        if (interpreter == null) initialize()
        if (interpreter == null) return Float.NaN

        try {
            // 1. Direct Buffer Write (Zero Allocation)
            inputBuffer.rewind()
            for (i in 0 until 6) {
                inputBuffer.putFloat(features[i].toFloat())
            }

            // 2. Prepare Output Buffer
            outputBuffer.rewind()
            
            // Fix: Rewind Input Buffer to position 0 before inference!
            inputBuffer.rewind()

            // 3. Run Inference
            interpreter?.run(inputBuffer, outputBuffer)
            
            // 4. Post-processing: XGBoost/Classification model outputs a probability (0.0 to 1.0)
            outputBuffer.rewind()
            val predictedProbability = outputBuffer.getFloat()
            
            if (predictedProbability.isNaN()) return Float.NaN

            return predictedProbability
            
        } catch (e: Exception) {
            e.printStackTrace()
            return Float.NaN
        }
    }
}
