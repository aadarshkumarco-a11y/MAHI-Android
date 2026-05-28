package com.mahi.assistant.ai

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit service interface for the Google Gemini generative AI API.
 *
 * Base URL: https://generativelanguage.googleapis.com/v1beta/
 *
 * Usage example with Retrofit builder:
 * ```
 * val retrofit = Retrofit.Builder()
 *     .baseUrl("https://generativelanguage.googleapis.com/v1beta/")
 *     .addConverterFactory(GsonConverterFactory.create())
 *     .build()
 *
 * val service = retrofit.create(GeminiApiService::class.java)
 * val response = service.generateContent(apiKey = "YOUR_KEY", request = geminiRequest)
 * ```
 */
interface GeminiApiService {

    /**
     * Generate content using the Gemini 1.5 Flash model.
     *
     * @param apiKey The Google AI API key (passed as query parameter).
     * @param request The GeminiRequest body containing conversation contents and system instructions.
     * @return GeminiResponse containing the model's generated content.
     */
    @POST("models/gemini-1.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}
