package com.example.stockmarketsim.domain.strategy

import javax.inject.Inject

open class StrategyProvider @Inject constructor(
    private val momentumStrategy: MomentumStrategy,
    private val safeHavenStrategy: SafeHavenStrategy,
    private val forecaster: com.example.stockmarketsim.domain.ml.IStockPriceForecaster,
    private val stockRepository: com.example.stockmarketsim.domain.repository.StockRepository,
    private val indianApiSource: com.example.stockmarketsim.data.remote.IndianApiSource
) {
    open fun getStrategy(id: String): Strategy {
        return when {
            id == "NEWS_SENTIMENT_MOMENTUM" -> com.example.stockmarketsim.domain.strategy.NewsSentimentStrategy(stockRepository)
            id == "MULTI_FACTOR_DNN" -> com.example.stockmarketsim.domain.strategy.MultiFactorMLStrategy(forecaster, indianApiSource)
            id.startsWith("MOMENTUM_SMA_") -> {
                val period = id.substringAfter("MOMENTUM_SMA_").toIntOrNull() ?: 20
                com.example.stockmarketsim.domain.strategy.ConfigurableMomentumStrategy(period)
            }
            id.startsWith("EMA_MOMENTUM_") -> {
                val period = id.substringAfter("EMA_MOMENTUM_").toIntOrNull() ?: 20
                com.example.stockmarketsim.domain.strategy.EmaMomentumStrategy(period)
            }
            id.startsWith("BOLLINGER_BREAKOUT_") -> {
                val parts = id.substringAfter("BOLLINGER_BREAKOUT_").split("_")
                val period = parts.getOrNull(0)?.toIntOrNull() ?: 20
                val mult = parts.getOrNull(1)?.toDoubleOrNull()?.let { it / 10.0 } ?: 2.0
                com.example.stockmarketsim.domain.strategy.BollingerBreakoutStrategy(period, mult)
            }
            id.startsWith("BB_MEAN_REVERSION_") -> {
                val parts = id.substringAfter("BB_MEAN_REVERSION_").split("_")
                val period = parts.getOrNull(0)?.toIntOrNull() ?: 20
                val mult = parts.getOrNull(1)?.toDoubleOrNull()?.let { it / 10.0 } ?: 2.0
                com.example.stockmarketsim.domain.strategy.BollingerMeanReversionStrategy(period, mult)
            }
            id.startsWith("RSI_") -> {
                val period = id.substringAfterLast("_").toIntOrNull() ?: 14
                com.example.stockmarketsim.domain.strategy.RsiStrategy(period)
            }
            id.startsWith("HYBRID_MOM_RSI") -> {
                val period = id.substringAfter("HYBRID_MOM_RSI_").toIntOrNull() ?: 50
                com.example.stockmarketsim.domain.strategy.HybridMomentumRsiStrategy(smaPeriod = period)
            }
            id == "MACD_BASIC" -> com.example.stockmarketsim.domain.strategy.MacdStrategy()
            id == "YEARLY_HIGH_BREAKOUT" -> com.example.stockmarketsim.domain.strategy.YearlyHighBreakoutStrategy()
            id.startsWith("VPT_") -> {
                val period = id.substringAfter("VPT_").toIntOrNull() ?: 20
                com.example.stockmarketsim.domain.strategy.VptStrategy(period)
            }
            id.startsWith("REL_VOL_") -> {
                val parts = id.substringAfter("REL_VOL_").split("_")
                val period = parts.getOrNull(0)?.toIntOrNull() ?: 20
                val mult = parts.getOrNull(1)?.toDoubleOrNull()?.let { it / 10.0 } ?: 2.0
                com.example.stockmarketsim.domain.strategy.RelativeVolumeStrategy(period, mult)
            }
            id == "SAFE_HAVEN" -> safeHavenStrategy
            id == "MOMENTUM" -> momentumStrategy
            else -> momentumStrategy 
        }
    }
    
    open fun getAllStrategies(): List<Strategy> {
        return listOf(
            com.example.stockmarketsim.domain.strategy.MultiFactorMLStrategy(forecaster, indianApiSource)
        )
    }
}

