package com.shadow.tracker.plus.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface BirdeyeApi {
    
    // Platzhalter für Birdeye API (Preis & Liquidität)
    @GET("defi/token_overview")
    suspend fun getTokenOverview(
        @Query("address") tokenAddress: String
    ): Any // To be implemented
}
