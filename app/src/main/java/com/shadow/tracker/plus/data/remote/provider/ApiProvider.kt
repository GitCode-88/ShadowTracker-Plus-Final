package com.shadow.tracker.plus.data.remote.provider

import com.shadow.tracker.plus.data.remote.BirdeyeApi
import com.shadow.tracker.plus.data.remote.CryptoRankApi
import com.shadow.tracker.plus.data.remote.HeliusApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiProvider {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    // Helius Base URL (DAS API)
    private const val HELIUS_BASE_URL = "https://mainnet.helius-rpc.com/"

    // Birdeye Base URL
    private const val BIRDEYE_BASE_URL = "https://public-api.birdeye.so/"

    // CryptoRank Base URL
    private const val CRYPTORANK_BASE_URL = "https://api.cryptorank.io/v1/"

    val heliusApi: HeliusApi by lazy {
        Retrofit.Builder()
            .baseUrl(HELIUS_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HeliusApi::class.java)
    }

    val birdeyeApi: BirdeyeApi by lazy {
        Retrofit.Builder()
            .baseUrl(BIRDEYE_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BirdeyeApi::class.java)
    }

    val cryptoRankApi: CryptoRankApi by lazy {
        Retrofit.Builder()
            .baseUrl(CRYPTORANK_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CryptoRankApi::class.java)
    }
}
