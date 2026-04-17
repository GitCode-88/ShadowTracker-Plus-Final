package com.shadow.tracker.plus.data.remote

import com.shadow.tracker.plus.data.remote.model.BirdeyeOverviewResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface BirdeyeApi {
    
    @GET("defi/token_overview")
    suspend fun getTokenOverview(
        @Header("X-API-KEY") apiKey: String,
        @Query("address") tokenAddress: String
    ): BirdeyeOverviewResponse
}
