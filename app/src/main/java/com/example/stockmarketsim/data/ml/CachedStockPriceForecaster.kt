package com.example.stockmarketsim.data.ml

import com.example.stockmarketsim.data.local.dao.PredictionDao
import com.example.stockmarketsim.data.local.entity.PredictionEntity
import com.example.stockmarketsim.domain.ml.IStockPriceForecaster
import javax.inject.Inject
import javax.inject.Named

class CachedStockPriceForecaster @Inject constructor(
    @Named("RealForecaster") private val realForecaster: IStockPriceForecaster,
    private val predictionDao: PredictionDao
) : IStockPriceForecaster {

    // HARDCODED VERSION: Update this string when model.tflite is replaced!
    private val CURRENT_MODEL_VERSION = "v2_xgboost_multifactor"

    override fun initialize() {
        realForecaster.initialize()
    }

    override fun predict(features: DoubleArray, symbol: String?, date: Long?): Float {
        // 1. If context is missing, skip cache and use real (e.g. ad-hoc inputs)
        if (symbol == null || date == null) {
            return realForecaster.predict(features, symbol, date)
        }

        // 2. Check Cache
        // Note: Room is suspend-only usually, but here we might need runBlocking if interface is blocking.
        // Ideally, domain interface should be suspend, but let's assume it's blocking for now to avoid massive refactor.
        // WE MUST USE runBlocking safely or CoroutineScope.
        // Assuming this runs inside a coroutine (Backtester does), we can use runBlocking for DB access if DAO is suspend.
        // PERFORMANCE NOTE: blocking DB call is vastly faster than TFLite inference (ms vs seconds).
        
        val cached = kotlinx.coroutines.runBlocking {
            predictionDao.getPrediction(symbol, date, CURRENT_MODEL_VERSION)
        }

        if (cached != null) {
            // Log.d("StockML", "Cache Hit for $symbol on $date")
            return cached.predictedReturn
        }

        // 3. Cache Miss: Run Inference
        val prediction = realForecaster.predict(features, symbol, date)

        if (!prediction.isNaN()) {
            // 4. Save to Cache
            val entity = PredictionEntity(
                symbol = symbol,
                date = date,
                predictedReturn = prediction,
                modelVersion = CURRENT_MODEL_VERSION
            )
            kotlinx.coroutines.runBlocking {
                predictionDao.insertPrediction(entity)
            }
        }

        return prediction
    }
}
