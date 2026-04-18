package com.shadow.tracker.plus.data.remote

import com.shadow.tracker.plus.data.remote.model.HeliusRpcRequest
import com.shadow.tracker.plus.data.remote.model.HeliusRpcResponse
import com.shadow.tracker.plus.data.remote.model.HeliusBlockTimeResponse
import com.shadow.tracker.plus.data.remote.model.HeliusSignaturesResponse
import com.shadow.tracker.plus.data.remote.model.HeliusTokenAccountsResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

// Helius DAS API and RPC API via JSON-RPC
interface HeliusApi {
    
    @POST("/")
    suspend fun getAsset(
        @Query("api-key") apiKey: String,
        @Body request: HeliusRpcRequest
    ): HeliusRpcResponse

    @POST("/")
    suspend fun getSignaturesForAddress(
        @Query("api-key") apiKey: String,
        @Body request: HeliusRpcRequest
    ): HeliusSignaturesResponse

    @POST("/")
    suspend fun getBlockTime(
        @Query("api-key") apiKey: String,
        @Body request: HeliusRpcRequest
    ): HeliusBlockTimeResponse
    
    @POST("/")
    suspend fun getTokenLargestAccounts(
        @Query("api-key") apiKey: String,
        @Body request: HeliusRpcRequest
    ): HeliusTokenAccountsResponse
}
