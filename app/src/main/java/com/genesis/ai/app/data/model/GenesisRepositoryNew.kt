package com.genesis.ai.app.data.model

import com.genesis.ai.app.data.api.ApiService
import com.genesis.ai.app.data.model.MessageResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object GenesisRepositoryNew {
    // Set this to true to use mock responses
    private const val USE_MOCK_RESPONSES = true
    
    // Base URL for the API - can be changed in app settings
    private var baseUrl = "https://your-api-base-url.com/"
    
    // Mock responses
    private val mockResponses = listOf(
        "I'm currently running in offline mode. The server appears to be down.",
        "This is a mock response. Please check your internet connection.",
        "The Genesis AI service is currently unavailable. Using local responses.",
        "I can still help you with basic tasks while offline."
    )
    
    val api: ApiService by lazy {
        if (USE_MOCK_RESPONSES) {
            // Return a mock implementation of the API
            object : ApiService {
                override fun sendMessage(message: MessageRequest) = 
                    object : retrofit2.Call<MessageResponse> {
                        override fun execute() = 
                            Response.success(MessageResponse(mockResponses.random()))
                        override fun enqueue(callback: Callback<MessageResponse>) {
                            callback.onResponse(this, Response.success(MessageResponse(mockResponses.random())))
                        }
                        override fun isExecuted() = false
                        override fun cancel() {}
                        override fun isCanceled() = false
                        override fun request() = null!!
                        override fun clone(): Call<MessageResponse> = this
                    }
                // Add other required API methods with mock implementations
            }
        } else {
            // Real API implementation
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
                
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }
    
    // Function to update the base URL at runtime if needed
    fun updateBaseUrl(newUrl: String) {
        baseUrl = newUrl
    }
}