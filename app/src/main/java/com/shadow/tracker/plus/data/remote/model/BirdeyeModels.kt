package com.shadow.tracker.plus.data.remote.model

import com.google.gson.annotations.SerializedName

data class BirdeyeOverviewResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: BirdeyeData?
)

data class BirdeyeData(
    @SerializedName("address") val address: String?,
    @SerializedName("liquidity") val liquidity: Double?,
    @SerializedName("price") val price: Double?
)
