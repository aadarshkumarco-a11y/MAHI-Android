package com.mahi.assistant.data.repository

import com.mahi.assistant.data.model.AiRequest
import com.mahi.assistant.data.model.AiResponse
import com.mahi.assistant.data.remote.GeminiApiService

interface AiRepository {
    suspend fun generateContent(apiKey: String, request: AiRequest): Result<AiResponse>
    suspend fun generateConversation(apiKey: String, userMessage: String, systemPrompt: String): Result<String>
}

class AiRepositoryImpl(
    private val geminiApiService: GeminiApiService
) : AiRepository {

    override suspend fun generateContent(apiKey: String, request: AiRequest): Result<AiResponse> {
        return try {
            val response = geminiApiService.generateContent(apiKey, request)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun generateConversation(
        apiKey: String,
        userMessage: String,
        systemPrompt: String
    ): Result<String> {
        return try {
            val request = AiRequest(
                contents = listOf(
                    AiRequest.Content(
                        parts = listOf(
                            AiRequest.Part(text = systemPrompt)
                        ),
                        role = "user"
                    ),
                    AiRequest.Content(
                        parts = listOf(
                            AiRequest.Part(text = "Understood. I will follow these instructions.")
                        ),
                        role = "model"
                    ),
                    AiRequest.Content(
                        parts = listOf(
                            AiRequest.Part(text = userMessage)
                        ),
                        role = "user"
                    )
                ),
                generationConfig = AiRequest.GenerationConfig(
                    temperature = 0.7,
                    topK = 40,
                    topP = 0.95,
                    maxOutputTokens = 1024
                )
            )

            val response = geminiApiService.generateContent(apiKey, request)
            val text = response.candidates?.firstOrNull()
                ?.content?.parts?.firstOrNull()?.text
                ?: "I couldn't generate a response. Please try again."
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
