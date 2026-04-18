package com.shadow.tracker.plus.data.remote

import com.shadow.tracker.plus.data.remote.model.DexScreenerResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface DexScreenerApi {
    @GET("tokens/{tokenAddresses}")
    suspend fun getTokens(
        @Path("tokenAddresses") tokenAddresses: String
    ): DexScreenerResponse
}
