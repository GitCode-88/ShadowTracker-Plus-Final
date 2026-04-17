package com.shadow.tracker.plus.data.remote

import com.shadow.tracker.plus.data.remote.model.HeliusAssetResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

// Helius DAS API (Digital Asset Standard)
interface HeliusApi {
    
    @POST("v1/assets")
    suspend fun searchAssets(
        @Query("api-key") apiKey: String,
        @Body request: Map<String, Any>
    ): HeliusAssetResponse
}
