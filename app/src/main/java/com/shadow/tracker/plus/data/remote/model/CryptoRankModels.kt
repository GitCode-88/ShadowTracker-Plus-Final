package com.shadow.tracker.plus.data.remote.model

import com.google.gson.annotations.SerializedName

data class CryptoRankCoinResponse(
    @SerializedName("data") val data: List<CryptoRankCoinData>?
)

data class CryptoRankCoinData(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String?,
    @SerializedName("symbol") val symbol: String?,
    @SerializedName("tokens") val tokens: List<CryptoRankToken>?
)

data class CryptoRankToken(
    @SerializedName("tokenAddress") val tokenAddress: String?,
    @SerializedName("platform") val platform: CryptoRankPlatform?
)

data class CryptoRankPlatform(
    @SerializedName("name") val name: String?
)

data class CryptoRankPriceResponse(
    @SerializedName("data") val data: Map<String, CryptoRankPriceData>?
)

data class CryptoRankPriceData(
    @SerializedName("price") val price: Double?,
    @SerializedName("atlPrice") val atlPrice: CryptoRankAtlData?,
    @SerializedName("athPrice") val athPrice: CryptoRankAthData?
)

data class CryptoRankAtlData(
    @SerializedName("USD") val usd: Double?
)

data class CryptoRankAthData(
    @SerializedName("USD") val usd: Double?
)
