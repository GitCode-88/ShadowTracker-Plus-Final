package com.shadow.tracker.plus.data.remote.model

import com.google.gson.annotations.SerializedName

data class DexScreenerResponse(
    @SerializedName("pairs") val pairs: List<DexScreenerPair>?
)

data class DexScreenerPair(
    @SerializedName("pairAddress") val pairAddress: String?,
    @SerializedName("baseToken") val baseToken: DexScreenerToken?,
    @SerializedName("priceUsd") val priceUsd: String?,
    @SerializedName("liquidity") val liquidity: DexScreenerLiquidity?,
    @SerializedName("volume") val volume: DexScreenerVolume?,
    @SerializedName("txns") val txns: DexScreenerTxns?,
    @SerializedName("fdv") val fdv: Double?,
    @SerializedName("pairCreatedAt") val pairCreatedAt: Long?
)

data class DexScreenerToken(
    @SerializedName("address") val address: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("symbol") val symbol: String?
)

data class DexScreenerLiquidity(
    @SerializedName("usd") val usd: Double?
)

data class DexScreenerVolume(
    @SerializedName("h1") val h1: Double?,
    @SerializedName("h6") val h6: Double?,
    @SerializedName("h24") val h24: Double?
)

data class DexScreenerTxns(
    @SerializedName("h1") val h1: DexScreenerTxnData?,
    @SerializedName("h6") val h6: DexScreenerTxnData?,
    @SerializedName("h24") val h24: DexScreenerTxnData?
)

data class DexScreenerTxnData(
    @SerializedName("buys") val buys: Int?,
    @SerializedName("sells") val sells: Int?
)
