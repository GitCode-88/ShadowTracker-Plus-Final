package com.shadow.tracker.plus.data.remote

import com.shadow.tracker.plus.data.remote.model.CryptoRankCoinResponse
import com.shadow.tracker.plus.data.remote.model.CryptoRankPriceResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface CryptoRankApi {
    @GET("coins")
    suspend fun getCoins(
        @Query("api_key") apiKey: String,
        @Query("symbol") symbol: String
    ): CryptoRankCoinResponse

    @GET("coins/price")
    suspend fun getPrice(
        @Query("api_key") apiKey: String,
        @Query("ids") ids: String
    ): CryptoRankPriceResponse
}
