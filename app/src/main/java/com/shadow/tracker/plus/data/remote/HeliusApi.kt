package com.shadow.tracker.plus.data.remote

import com.shadow.tracker.plus.data.remote.model.HeliusRpcRequest
import com.shadow.tracker.plus.data.remote.model.HeliusRpcResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

// Helius DAS API (Digital Asset Standard) via JSON-RPC
interface HeliusApi {
    
    @POST("/")
    suspend fun searchAssets(
        @Query("api-key") apiKey: String,
        @Body request: HeliusRpcRequest
    ): HeliusRpcResponse
}
