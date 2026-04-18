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
    val marketCapUsd: Double, // fdv
    val volume24hUsd: Double,
    val txns24h: Int,
    val pairCreatedAt: Long, // timestamp
    val atlUsd: Double,
    val athUsd: Double,
    val atlLastUpdated: Long, // timestamp
    val isSafe: Boolean,
    val isMutable: Boolean,
    val riskScore: Int
)
