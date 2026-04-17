package com.shadow.tracker.plus.data.remote.model

import com.google.gson.annotations.SerializedName

data class HeliusAssetResponse(
    @SerializedName("items") val items: List<HeliusAssetItem>?
)

data class HeliusAssetItem(
    @SerializedName("id") val id: String?,
    @SerializedName("content") val content: HeliusContent?,
    @SerializedName("token_info") val tokenInfo: HeliusTokenInfo?
)

data class HeliusContent(
    @SerializedName("metadata") val metadata: HeliusMetadata?
)

data class HeliusMetadata(
    @SerializedName("name") val name: String?,
    @SerializedName("symbol") val symbol: String?
)

data class HeliusTokenInfo(
    @SerializedName("supply") val supply: Long?,
    @SerializedName("decimals") val decimals: Int?
)
