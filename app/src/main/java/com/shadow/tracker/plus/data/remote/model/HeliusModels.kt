package com.shadow.tracker.plus.data.remote.model

import com.google.gson.annotations.SerializedName

data class HeliusRpcRequest(
    @SerializedName("jsonrpc") val jsonrpc: String = "2.0",
    @SerializedName("id") val id: String = "1",
    @SerializedName("method") val method: String,
    @SerializedName("params") val params: Any
)

data class HeliusRpcResponse(
    @SerializedName("jsonrpc") val jsonrpc: String?,
    @SerializedName("id") val id: String?,
    @SerializedName("result") val result: HeliusAssetResult?
)

data class HeliusBlockTimeResponse(
    @SerializedName("jsonrpc") val jsonrpc: String?,
    @SerializedName("id") val id: String?,
    @SerializedName("result") val result: Long? // timestamp in seconds
)

data class HeliusSignaturesResponse(
    @SerializedName("jsonrpc") val jsonrpc: String?,
    @SerializedName("id") val id: String?,
    @SerializedName("result") val result: List<HeliusSignature>?
)

data class HeliusSignature(
    @SerializedName("signature") val signature: String?,
    @SerializedName("slot") val slot: Long?,
    @SerializedName("err") val err: Any?,
    @SerializedName("memo") val memo: String?,
    @SerializedName("blockTime") val blockTime: Long?
)

data class HeliusTokenAccountsResponse(
    @SerializedName("jsonrpc") val jsonrpc: String?,
    @SerializedName("id") val id: String?,
    @SerializedName("result") val result: HeliusTokenAccountsResult?
)

data class HeliusTokenAccountsResult(
    @SerializedName("value") val value: List<HeliusTokenAccountValue>?
)

data class HeliusTokenAccountValue(
    @SerializedName("pubkey") val pubkey: String?,
    @SerializedName("account") val account: HeliusTokenAccountData?
)

data class HeliusTokenAccountData(
    @SerializedName("data") val data: HeliusTokenAccountParsedData?
)

data class HeliusTokenAccountParsedData(
    @SerializedName("parsed") val parsed: HeliusTokenAccountParsedInfo?
)

data class HeliusTokenAccountParsedInfo(
    @SerializedName("info") val info: HeliusTokenAccountInfoDetails?
)

data class HeliusTokenAccountInfoDetails(
    @SerializedName("tokenAmount") val tokenAmount: HeliusTokenAmount?
)

data class HeliusTokenAmount(
    @SerializedName("uiAmount") val uiAmount: Double?
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
