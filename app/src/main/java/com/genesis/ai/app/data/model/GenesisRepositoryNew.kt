package com.genesis.ai.app.data.model

import com.genesis.ai.app.data.api.ApiService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object GenesisRepositoryNew {
    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://your-api-base-url.com/") // TODO: Replace with your actual API base URL
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}