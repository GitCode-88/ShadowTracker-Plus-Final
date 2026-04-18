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
    val marketCapUsd: Double,
    
    // Volume & Trade Dynamics (DexScreener)
    val volume1hUsd: Double,
    val volume24hUsd: Double,
    val txns1h: Int,
    val txns24h: Int,
    
    // Historical Analysis (Local Aggregation)
    val pairCreatedAt: Long, // from DexScreener initially
    val genesisBlockTime: Long?, // from Helius (True Age)
    val atlUsd: Double,
    val athUsd: Double,
    
    // Algorithm Metrics
    val ageInDays: Int?,
    val rvol: Double?, // Relative Volume
    val proximityToAtl: Double?, // Percentage above ATL
    val choppinessIndex: Double?, // Consolidation metric
    val dormancyDays: Int?, // Days under volume threshold
    
    // Security & Hard Constraints (RugCheck)
    val isRugCheckComplete: Boolean,
    val isSafe: Boolean, // Aggregated safety flag
    val mintAuthorityRevoked: Boolean?,
    val freezeAuthorityRevoked: Boolean?,
    val isMutable: Boolean?,
    val narrativeTags: String?,// Sektor/Narrativ (z.B. AI, DePIN)
    val isSmartMoneyAccumulating: Boolean?, // Smart Money Accumulation
    val lpLockedPct: Double?,
    val top10HolderPct: Double?,
    val riskScore: Int,
    
    // Internal Tracking
    val lastDiscoveredAt: Long,
    val lastAnalysisAt: Long,
    val lastRugCheckAt: Long
)
