package com.mahi.assistant.ai

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Retrofit service interface for the Google Gemini generative AI API.
 *
 * Base URL: https://generativelanguage.googleapis.com/v1beta/
 *
 * We use @Url to make the endpoint dynamic so we can try multiple model names
 * (gemini-2.0-flash, gemini-1.5-flash, gemini-pro) as fallbacks.
 */
interface GeminiApiService {

    /**
     * Generate content using a Gemini model.
     * Uses dynamic URL so we can try different model endpoints.
     *
     * @param url The full endpoint URL (e.g. "models/gemini-2.0-flash:generateContent")
     * @param apiKey The Google AI API key (passed as query parameter).
     * @param request The GeminiRequest body containing conversation contents and system instructions.
     * @return GeminiResponse containing the model's generated content.
     */
    @POST
    suspend fun generateContent(
        @Url url: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}
