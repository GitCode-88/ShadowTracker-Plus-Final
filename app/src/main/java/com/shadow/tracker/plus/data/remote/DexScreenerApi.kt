package com.shadow.tracker.plus.data.remote

import com.shadow.tracker.plus.data.remote.model.DexScreenerResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface DexScreenerApi {
    @GET("token-profiles/latest/v1")
    suspend fun getLatestTokenProfiles(): List<DexScreenerTokenProfile>
    
    @GET("latest/dex/search")
    suspend fun searchPairs(@retrofit2.http.Query("q") query: String): DexScreenerResponse

    @GET("latest/dex/tokens/{tokenAddresses}")
    suspend fun getTokens(
        @Path("tokenAddresses") tokenAddresses: String
    ): DexScreenerResponse
}

data class DexScreenerTokenProfile(
    val url: String?,
    val chainId: String?,
    val tokenAddress: String?,
    val icon: String?,
    val header: String?,
    val description: String?,
    val links: List<DexScreenerLink>?
)

data class DexScreenerLink(
    val type: String?,
    val label: String?,
    val url: String?
)
