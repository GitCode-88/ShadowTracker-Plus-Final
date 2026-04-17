package com.shadow.tracker.plus.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface HeliusApi {
    
    // Platzhalter für Helius API (RPC / Yellowstone / Asset Data)
    @GET("v0/tokens/{mint}")
    suspend fun getTokenMetadata(
        @Path("mint") mintAddress: String,
        @Query("api-key") apiKey: String
    ): Any // To be implemented
}
