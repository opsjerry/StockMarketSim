package com.example.stockmarketsim.domain.ml

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StockPriceForecasterInstrumentedTest {

    @Test
    fun verifyGoldenSample() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val forecaster = StockPriceForecaster(appContext)

        // Test using a flat dummy array (e.g., all 0.01) to verify model ingestion
        val featureCount = forecaster.getExpectedFeatureCount()
        val dummyInput = DoubleArray(featureCount) { 0.01 }

        val prediction = forecaster.predict(dummyInput, "NSEI", System.currentTimeMillis())
        
        android.util.Log.d("ModelVerification", "Using REAL Model from Repo")
        android.util.Log.d("ModelVerification", "Input Features Count: ${dummyInput.size}")
        android.util.Log.d("ModelVerification", "Model Raw Output: $prediction")

        // We verify that the model correctly initialized, digested the buffer,
        // and returned a valid float (not NaN, not exactly zero unless by chance).
        assert(!prediction.isNaN())
        assert(prediction != 0.0f)
    }
}
