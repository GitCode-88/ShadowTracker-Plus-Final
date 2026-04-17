package com.shadow.tracker.plus.data.remote.model

import com.google.gson.annotations.SerializedName

data class HeliusRpcRequest(
    @SerializedName("jsonrpc") val jsonrpc: String = "2.0",
    @SerializedName("id") val id: String = "1",
    @SerializedName("method") val method: String,
    @SerializedName("params") val params: Map<String, Any>
)

data class HeliusRpcResponse(
    @SerializedName("jsonrpc") val jsonrpc: String?,
    @SerializedName("id") val id: String?,
    @SerializedName("result") val result: HeliusAssetResult?
)

data class HeliusAssetResult(
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
