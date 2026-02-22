package com.example.stockmarketsim.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.stockmarketsim.data.local.dao.PortfolioDao
import com.example.stockmarketsim.data.local.dao.SimulationDao
import com.example.stockmarketsim.data.local.dao.StockDao
import com.example.stockmarketsim.data.local.dao.TransactionDao
import com.example.stockmarketsim.data.local.entity.PortfolioItemEntity
import com.example.stockmarketsim.data.local.entity.SimulationEntity
import com.example.stockmarketsim.data.local.entity.StockPriceEntity
import com.example.stockmarketsim.data.local.entity.TransactionEntity

@Database(
    entities = [SimulationEntity::class, StockPriceEntity::class, PortfolioItemEntity::class, TransactionEntity::class, com.example.stockmarketsim.data.local.entity.SimulationHistoryEntity::class, com.example.stockmarketsim.data.local.entity.StockUniverseEntity::class, com.example.stockmarketsim.data.local.entity.PredictionEntity::class, com.example.stockmarketsim.data.local.entity.FundamentalsCacheEntity::class],
    version = 10,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun simulationDao(): SimulationDao
    abstract fun stockDao(): StockDao
    abstract fun portfolioDao(): PortfolioDao
    abstract fun transactionDao(): TransactionDao
    abstract fun simulationHistoryDao(): com.example.stockmarketsim.data.local.dao.SimulationHistoryDao
    abstract fun predictionDao(): com.example.stockmarketsim.data.local.dao.PredictionDao
    abstract fun fundamentalsCacheDao(): com.example.stockmarketsim.data.local.dao.FundamentalsCacheDao

    companion object {
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE transactions ADD COLUMN reason TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add 'lastSwitchDate' column to simulations table
                database.execSQL("ALTER TABLE simulations ADD COLUMN lastSwitchDate INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create stock_universe table
                database.execSQL("CREATE TABLE IF NOT EXISTS `stock_universe` (`symbol` TEXT NOT NULL, `name` TEXT NOT NULL, `isActive` INTEGER NOT NULL, PRIMARY KEY(`symbol`))")
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add isLiveTradingEnabled to simulations
                database.execSQL("ALTER TABLE simulations ADD COLUMN isLiveTradingEnabled INTEGER NOT NULL DEFAULT 0")
                // Add brokerOrderId to transactions
                database.execSQL("ALTER TABLE transactions ADD COLUMN brokerOrderId TEXT")
            }
        }

        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create predictions table
                database.execSQL("CREATE TABLE IF NOT EXISTS `predictions` (`symbol` TEXT NOT NULL, `date` INTEGER NOT NULL, `predictedReturn` REAL NOT NULL, `modelVersion` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`symbol`, `date`, `modelVersion`))")
            }
        }

        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create fundamentals_cache table
                database.execSQL("CREATE TABLE IF NOT EXISTS `fundamentals_cache` (`symbol` TEXT NOT NULL, `peRatio` REAL NOT NULL, `roe` REAL NOT NULL, `debtToEquity` REAL NOT NULL, `marketCap` REAL NOT NULL, `sentimentScore` REAL NOT NULL, `source` TEXT NOT NULL, `fetchTimestamp` INTEGER NOT NULL, PRIMARY KEY(`symbol`))")
            }
        }
    }
}
