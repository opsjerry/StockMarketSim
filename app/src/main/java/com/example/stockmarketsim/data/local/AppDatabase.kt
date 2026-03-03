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
    version = 14,
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

        /**
         * MIGRATION 10 → 11: Fix duplicate stock price candles.
         *
         * Root cause: stock_prices used @PrimaryKey(autoGenerate = true) with no unique
         * constraint on (symbol, date). OnConflictStrategy.REPLACE only deduplicates by PK,
         * so every remote fetch inserted new rows for identical candles — polluting the
         * LSTM's 60-step input window with repeated data.
         *
         * Fix steps:
         *  1. Delete duplicate rows, keeping the row with the highest id (latest insert)
         *     for each (symbol, date) pair.
         *  2. Recreate the table with a UNIQUE index on (symbol, date).
         *  3. Copy clean data back.
         */
        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Step 1: Remove duplicates — keep the row with the highest id per (symbol, date)
                database.execSQL("""
                    DELETE FROM stock_prices
                    WHERE id NOT IN (
                        SELECT MAX(id) FROM stock_prices GROUP BY symbol, date
                    )
                """.trimIndent())

                // Step 2: Create new table with unique index
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `stock_prices_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `symbol` TEXT NOT NULL,
                        `date` INTEGER NOT NULL,
                        `open` REAL NOT NULL,
                        `high` REAL NOT NULL,
                        `low` REAL NOT NULL,
                        `close` REAL NOT NULL,
                        `volume` INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_stock_prices_symbol_date`
                    ON `stock_prices_new` (`symbol`, `date`)
                """.trimIndent())

                // Step 3: Copy deduplicated data
                database.execSQL("""
                    INSERT INTO stock_prices_new (id, symbol, date, open, high, low, close, volume)
                    SELECT id, symbol, date, open, high, low, close, volume FROM stock_prices
                """.trimIndent())

                // Step 4: Swap tables
                database.execSQL("DROP TABLE stock_prices")
                database.execSQL("ALTER TABLE stock_prices_new RENAME TO stock_prices")
            }
        }
        /**
         * MIGRATION 11 → 12: Add purchaseDate to portfolio_items.
         *
         * Required for Phase 2 stop-loss honeymoon logic:
         * positions opened < 3 trading days ago use a wider 3.5× ATR stop
         * to prevent premature stop-outs on normal day-1 price discovery.
         *
         * SQLite allows additing a column with a DEFAULT value directly.
         * Existing rows get purchaseDate = 0, which the honeymoon logic treats
         * as "very old position" — normal 2.0× stop applies. Safe upgrade path.
         */
        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE portfolio_items ADD COLUMN purchaseDate INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
        /**
         * MIGRATION 12 → 13: Add promoterHolding to fundamentals_cache.
         *
         * Phase 3 governance risk filter: stocks with promoter holding > 72%
         * (Adani Power, Vedanta, etc.) are excluded from the quality universe
         * regardless of ROE/D/E, because concentrated promoter + pledge risk
         * causes 15-20% single-day drawdowns that break ATR stop assumptions.
         *
         * Existing rows get 0.0 (no data) — treated as pass by the filter.
         */
        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE fundamentals_cache ADD COLUMN promoterHolding REAL NOT NULL DEFAULT 0.0"
                )
            }
        }

        /**
         * MIGRATION 13 → 14: Fix portfolio_items schema mismatch.
         *
         * Two issues introduced by MIGRATION_11_12:
         *  1. `purchaseDate` was added via ALTER TABLE with DEFAULT 0, but Room's
         *     schema validator expects no default value ('undefined') because the
         *     entity uses `val purchaseDate: Long = 0L` without @ColumnInfo(defaultValue).
         *  2. The `index_portfolio_items_simulationId` index declared in the @Entity
         *     annotation was never created by any prior migration — it only exists
         *     on fresh installs, not on upgraded devices.
         *
         * Fix: recreate the table with the exact schema Room generates, then copy data.
         */
        val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create corrected table (no DEFAULT on purchaseDate)
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `portfolio_items_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `simulationId` INTEGER NOT NULL,
                        `symbol` TEXT NOT NULL,
                        `quantity` REAL NOT NULL,
                        `averagePrice` REAL NOT NULL,
                        `highestPrice` REAL NOT NULL,
                        `purchaseDate` INTEGER NOT NULL,
                        FOREIGN KEY(`simulationId`) REFERENCES `simulations`(`id`) ON DELETE CASCADE ON UPDATE NO ACTION
                    )
                """.trimIndent())

                // Create the missing index
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS `index_portfolio_items_simulationId`
                    ON `portfolio_items_new` (`simulationId`)
                """.trimIndent())

                // Copy all existing data (existing rows with purchaseDate=0 are fine)
                database.execSQL("""
                    INSERT INTO `portfolio_items_new`
                        (`id`, `simulationId`, `symbol`, `quantity`, `averagePrice`, `highestPrice`, `purchaseDate`)
                    SELECT `id`, `simulationId`, `symbol`, `quantity`, `averagePrice`, `highestPrice`, `purchaseDate`
                    FROM `portfolio_items`
                """.trimIndent())

                // Swap tables
                database.execSQL("DROP TABLE `portfolio_items`")
                database.execSQL("ALTER TABLE `portfolio_items_new` RENAME TO `portfolio_items`")
            }
        }
    }
}
