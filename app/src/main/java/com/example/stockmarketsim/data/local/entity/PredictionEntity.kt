package com.example.stockmarketsim.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "predictions", primaryKeys = ["symbol", "date", "modelVersion"])
data class PredictionEntity(
    val symbol: String,
    val date: Long,
    val predictedReturn: Float,
    val modelVersion: String,
    val timestamp: Long = System.currentTimeMillis()
)
