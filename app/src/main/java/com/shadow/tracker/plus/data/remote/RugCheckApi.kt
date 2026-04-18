package com.shadow.tracker.plus.data.remote

import com.shadow.tracker.plus.data.remote.model.RugCheckResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface RugCheckApi {
    @GET("tokens/{mint}/report/summary")
    suspend fun getReportSummary(
        @Path("mint") mint: String
    ): RugCheckResponse
}
