package com.example.stockmarketsim.domain.provider

import android.content.Context
import androidx.core.content.edit
import com.example.stockmarketsim.data.remote.WikipediaDiscoverySource
import com.example.stockmarketsim.domain.model.StockUniverse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockUniverseProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: com.example.stockmarketsim.domain.repository.StockRepository
) {
    
    // BACKWARD COMPATIBILITY:
    // If DB is empty (Migration), we populate it with the default list.
    // This ensures existing simulations don't crash or see empty markets.
    suspend fun getUniverse(): List<String> {
        val dbStocks = repository.getActiveUniverseSnapshot()
        
        if (dbStocks.isNotEmpty()) {
            return dbStocks
        } else {
            // MIGRATION: Initialize DB with Defaults
            val defaults = StockUniverse.AllMarket
            val entities = defaults.map { 
                com.example.stockmarketsim.data.local.entity.StockUniverseEntity(it, isActive = true) 
            }
            // We need a bulk insert method ideally, but loop or generic insert works.
            // StockRepository doesn't expose bulk insertEntity directly, but we added 'addStockToUniverse' one by one.
            // Actually, we can just use the Dao if we had access, but we should use Repo.
            // Let's add a `initializeUniverse` method to Repo or just iterate.
            // Iterating is fine for 100 stocks once. Or better, let's use the Dao via cast/hack or add method.
            // Wait, I added `insertUniverse` to Dao but didn't expose list insert in Repo interface?
            // Checking Repo... `addStockToUniverse` is single.
            // `syncUniverseFromDiscovery` does bulk insert inside Repo.
            
            // Let's iterate for now to be safe and simple without changing Repo interface again.
            // Or better, checking `StockRepositoryImpl`, I didn't expose `insertUniverse` public list method.
            // I will use `addStockToUniverse` in a loop. It's 100 items, negligible perf hit for one-time migration.
            
            entities.forEach { repository.addStockToUniverse(it.symbol) }
            
            // Also check SharedPreferences for any custom legacy state? 
            // The old provider used `stock_universe_prefs`. Let's pay respects to it.
            try {
                val prefs = context.getSharedPreferences("stock_universe_prefs", Context.MODE_PRIVATE)
                val savedString = prefs.getString("symbols", null)
                if (savedString != null) {
                    val legacyCustom = savedString.split(",").filter { it.isNotEmpty() }
                    legacyCustom.forEach { repository.addStockToUniverse(it) }
                }
            } catch (e: Exception) {
                // Ignore prefs error
            }

            return repository.getActiveUniverseSnapshot()
        }
    }
}
