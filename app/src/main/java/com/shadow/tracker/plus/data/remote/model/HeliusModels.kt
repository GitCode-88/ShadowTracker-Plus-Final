package com.shadow.tracker.plus.data.remote.model

import com.google.gson.annotations.SerializedName

data class HeliusAssetResponse(
    @SerializedName("items") val items: List<HeliusAssetItem>?
)

data class HeliusAssetItem(
    @SerializedName("id") val id: String?,
    @SerializedName("content") val content: HeliusContent?,
    @SerializedName("token_info") val tokenInfo: HeliusTokenInfo?,
    @SerializedName("mutable") val mutable: Boolean?,
    @SerializedName("authorities") val authorities: List<HeliusAuthority>?
)

data class HeliusAuthority(
    @SerializedName("address") val address: String?,
    @SerializedName("scopes") val scopes: List<String>?
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
