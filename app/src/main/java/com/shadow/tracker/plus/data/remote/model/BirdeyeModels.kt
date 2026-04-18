package com.shadow.tracker.plus.data.remote.model

import com.google.gson.annotations.SerializedName

data class BirdeyeOverviewResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: BirdeyeData?
)

data class BirdeyeData(
    @SerializedName("address") val address: String?,
    @SerializedName("liquidity") val liquidity: Double?,
    @SerializedName("price") val price: Double?,
    @SerializedName("v24hUSD") val v24hUSD: Double?,
    @SerializedName("uniqueWallet24h") val uniqueWallet24h: Int?
)

data class BirdeyeTokenListResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: BirdeyeTokenListData?
)

data class BirdeyeTokenListData(
    @SerializedName("tokens") val tokens: List<BirdeyeTokenItem>?
)

data class BirdeyeTokenItem(
    @SerializedName("address") val address: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("symbol") val symbol: String?
)
