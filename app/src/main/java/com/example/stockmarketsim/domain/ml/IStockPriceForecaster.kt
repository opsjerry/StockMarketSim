package com.example.stockmarketsim.domain.ml

interface IStockPriceForecaster {
    fun initialize()
    fun predict(features: DoubleArray, symbol: String? = null, date: Long? = null): Float
}
