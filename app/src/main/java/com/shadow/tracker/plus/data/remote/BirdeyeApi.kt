package com.shadow.tracker.plus.data.remote

import com.shadow.tracker.plus.data.remote.model.BirdeyeOverviewResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

import com.shadow.tracker.plus.data.remote.model.BirdeyeTokenListResponse

interface BirdeyeApi {
    
    @GET("defi/token_overview")
    suspend fun getTokenOverview(
        @Header("X-API-KEY") apiKey: String,
        @Query("address") tokenAddress: String
    ): BirdeyeOverviewResponse

    @GET("defi/tokenlist")
    suspend fun getTokenList(
        @Header("X-API-KEY") apiKey: String,
        @Query("sort_by") sortBy: String = "v24hUSD",
        @Query("sort_type") sortType: String = "desc",
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 50
    ): BirdeyeTokenListResponse
}
