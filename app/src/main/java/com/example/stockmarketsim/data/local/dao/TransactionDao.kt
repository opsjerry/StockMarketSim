package com.example.stockmarketsim.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.stockmarketsim.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity)
    
    @Query("SELECT * FROM transactions WHERE simulationId = :simulationId ORDER BY date DESC")
    fun getTransactions(simulationId: Int): Flow<List<TransactionEntity>>
}
