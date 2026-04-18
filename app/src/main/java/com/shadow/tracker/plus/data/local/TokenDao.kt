package com.shadow.tracker.plus.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TokenDao {
    @Query("SELECT * FROM tokens")
    fun getAllTokens(): Flow<List<TokenEntity>>

    @Query("SELECT * FROM tokens WHERE mintAddress = :mintAddress LIMIT 1")
    suspend fun getToken(mintAddress: String): TokenEntity?

    @Query("SELECT * FROM tokens WHERE isRugCheckComplete = 0 ORDER BY lastDiscoveredAt ASC LIMIT :limit")
    suspend fun getTokensPendingRugCheck(limit: Int): List<TokenEntity>

    @Query("SELECT * FROM tokens WHERE ageInDays IS NULL ORDER BY lastDiscoveredAt ASC LIMIT :limit")
    suspend fun getTokensPendingAnalysis(limit: Int): List<TokenEntity>
    
    @Query("DELETE FROM tokens WHERE isSafe = 0 AND isRugCheckComplete = 1")
    suspend fun deleteUnsafeTokens()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToken(token: TokenEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTokens(tokens: List<TokenEntity>)
}