package com.mahi.assistant.data.remote

import com.mahi.assistant.data.model.AiRequest
import com.mahi.assistant.data.model.AiResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface GeminiApiService {
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: AiRequest
    ): AiResponse
}
