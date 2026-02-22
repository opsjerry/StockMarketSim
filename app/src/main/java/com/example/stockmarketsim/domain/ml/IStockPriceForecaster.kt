package com.example.stockmarketsim.domain.ml

interface IStockPriceForecaster {
    fun initialize()
    fun predict(features: DoubleArray, symbol: String? = null, date: Long? = null): Float
    fun getModelVersion(): Int
    /** Returns the number of features the loaded model expects (e.g. 60 or 64). */
    fun getExpectedFeatureCount(): Int = 60
}
