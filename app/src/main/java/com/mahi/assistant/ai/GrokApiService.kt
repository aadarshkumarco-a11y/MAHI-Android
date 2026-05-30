package com.mahi.assistant.ai

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Grok (xAI) API Service — AUTOMATIC FALLBACK when Gemini fails.
 *
 * Uses xAI's OpenAI-compatible chat completions endpoint.
 * When Gemini returns 401/403 or is unavailable, MAHI automatically
 * switches to Grok as backup AI.
 *
 * API Docs: https://docs.x.ai/docs
 */
interface GrokApiService {

    @POST("v1/chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") authorization: String,
        @Body request: GrokRequest
    ): GrokResponse
}

// ── Grok Request/Response Models (OpenAI-compatible format) ──────────────────

data class GrokRequest(
    val model: String = "grok-3-mini",
    val messages: List<GrokMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 1024,
    val top_p: Double = 0.95
)

data class GrokMessage(
    val role: String,  // "system", "user", "assistant"
    val content: String
)

data class GrokResponse(
    val id: String? = null,
    val choices: List<GrokChoice>? = null,
    val usage: GrokUsage? = null
)

data class GrokChoice(
    val index: Int? = null,
    val message: GrokMessage? = null,
    @SerializedName("finish_reason")
    val finishReason: String? = null
)

data class GrokUsage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerializedName("completion_tokens")
    val completionTokens: Int? = null,
    @SerializedName("total_tokens")
    val totalTokens: Int? = null
)

/**
 * Helper to extract text from Grok response.
 */
fun GrokResponse.extractText(): String? {
    return this.choices?.firstOrNull()?.message?.content
}

/**
 * Grok Client — manages the Retrofit instance and provides high-level API calls.
 */
object GrokClient {

    private const val BASE_URL = "https://api.x.ai/"

    private val gson: Gson = GsonBuilder()
        .setLenient()
        .setPrettyPrinting()
        .create()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    val apiService: GrokApiService by lazy {
        retrofit.create(GrokApiService::class.java)
    }
}
