package com.example.stockmarketsim.di

import android.content.Context
import androidx.room.Room
import com.example.stockmarketsim.data.local.AppDatabase
import com.example.stockmarketsim.data.local.dao.SimulationDao
import com.example.stockmarketsim.data.local.dao.StockDao
import com.example.stockmarketsim.data.local.dao.PortfolioDao
import com.example.stockmarketsim.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "stock_market_sim.db"
        )
        .addMigrations(AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10)
        .fallbackToDestructiveMigration() // Enabled for robust dev builds
        .build()
    }

    @Provides
    @Singleton
    fun provideSimulationDao(
        database: AppDatabase
    ): SimulationDao {
        return database.simulationDao()
    }

    @Provides
    @Singleton
    fun provideStockDao(
        database: AppDatabase
    ): StockDao {
        return database.stockDao()
    }

    @Provides
    @Singleton
    fun providePortfolioDao(
        database: AppDatabase
    ): PortfolioDao {
        return database.portfolioDao()
    }

    @Provides
    @Singleton
    fun provideTransactionDao(
        database: AppDatabase
    ): TransactionDao {
        return database.transactionDao()
    }

    @Provides
    @Singleton
    fun provideSimulationHistoryDao(
        database: AppDatabase
    ): com.example.stockmarketsim.data.local.dao.SimulationHistoryDao {
        return database.simulationHistoryDao()
    }

    @Provides
    @Singleton
    fun providePredictionDao(
        database: AppDatabase
    ): com.example.stockmarketsim.data.local.dao.PredictionDao {
        return database.predictionDao()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): okhttp3.OkHttpClient {
        return okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideWikipediaDiscoverySource(): com.example.stockmarketsim.data.remote.WikipediaDiscoverySource {
        return com.example.stockmarketsim.data.remote.WikipediaDiscoverySource()
    }

    @Provides
    @Singleton
    fun provideFundamentalsCacheDao(
        database: AppDatabase
    ): com.example.stockmarketsim.data.local.dao.FundamentalsCacheDao {
        return database.fundamentalsCacheDao()
    }

    @Provides
    @Singleton
    fun provideIndianApiSource(
        settingsManager: com.example.stockmarketsim.data.manager.SettingsManager,
        fundamentalsCacheDao: com.example.stockmarketsim.data.local.dao.FundamentalsCacheDao,
        yahooFinanceSource: com.example.stockmarketsim.data.remote.YahooFinanceSource
    ): com.example.stockmarketsim.data.remote.IndianApiSource {
        return com.example.stockmarketsim.data.remote.IndianApiSource(settingsManager, fundamentalsCacheDao, yahooFinanceSource)
    }

    @Provides
    @Singleton
    fun provideStockUniverseProvider(
        @ApplicationContext context: Context,
        repository: com.example.stockmarketsim.domain.repository.StockRepository
    ): com.example.stockmarketsim.domain.provider.StockUniverseProvider {
        return com.example.stockmarketsim.domain.provider.StockUniverseProvider(context, repository)
    }
    @Provides
    @Singleton
    @javax.inject.Named("RealForecaster")
    fun provideRealStockPriceForecaster(
        impl: com.example.stockmarketsim.domain.ml.StockPriceForecaster
    ): com.example.stockmarketsim.domain.ml.IStockPriceForecaster {
        return impl
    }

    @Provides
    @Singleton
    fun provideCachedStockPriceForecaster(
        impl: com.example.stockmarketsim.data.ml.CachedStockPriceForecaster
    ): com.example.stockmarketsim.domain.ml.IStockPriceForecaster {
        return impl
    }
}
