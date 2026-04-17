package com.shadow.tracker.plus.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tokens")
data class TokenEntity(
    @PrimaryKey val mintAddress: String,
    val name: String,
    val symbol: String,
    val liquidityUsd: Double,
    val priceUsd: Double,
    val atlUsd: Double,
    val atlLastUpdated: Long, // Timestamp in milliseconds
    val isSafe: Boolean,
    val riskScore: Int
)
