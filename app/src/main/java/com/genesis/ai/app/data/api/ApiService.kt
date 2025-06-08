package com.genesis.ai.app.data.api

import com.genesis.ai.app.data.model.AskResponse
import com.genesis.ai.app.data.model.ImportResponse
import com.genesis.ai.app.data.model.MessageRequest
import com.genesis.ai.app.data.model.MessageResponse
import com.genesis.ai.app.data.model.RootToggleRequest
import com.genesis.ai.app.data.model.RootToggleResponse
import com.genesis.ai.app.data.model.LSPosedModuleRequest // ADDED IMPORT
import com.genesis.ai.app.data.model.LSPosedModuleResponse // ADDED IMPORT
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    @POST("sendMessage")
    fun sendMessage(@Body request: MessageRequest): Call<MessageResponse>

    @Multipart
    @POST("importFile")
    fun importFile(@Part file: MultipartBody.Part): Call<ImportResponse>

    @POST("toggleRoot")
    fun toggleRoot(@Body request: RootToggleRequest): Call<RootToggleResponse>

    @GET("getAiQuestions")
    fun getAiQuestions(): Call<AskResponse>

    @POST("toggleLSPosedModule") // NEW ENDPOINT
    fun toggleLSPosedModule(@Body request: LSPosedModuleRequest): Call<LSPosedModuleResponse>
}