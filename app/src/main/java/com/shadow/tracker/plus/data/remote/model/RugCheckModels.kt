package com.shadow.tracker.plus.data.remote.model

import com.google.gson.annotations.SerializedName

data class RugCheckResponse(
    @SerializedName("score") val score: Int?,
    @SerializedName("risks") val risks: List<RugCheckRisk>?
)

data class RugCheckRisk(
    @SerializedName("name") val name: String?,
    @SerializedName("value") val value: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("score") val score: Int?,
    @SerializedName("level") val level: String? // "danger", "warn"
)
