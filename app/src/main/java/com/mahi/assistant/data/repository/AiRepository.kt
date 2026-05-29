package com.mahi.assistant.data.repository

import com.mahi.assistant.ai.AiConversationEngine
import com.mahi.assistant.data.local.SettingsManager

interface AiRepository {
    suspend fun generateConversation(apiKey: String, userMessage: String, systemPrompt: String): Result<String>
    suspend fun sendMessage(message: String, history: List<com.mahi.assistant.ai.ChatMessage>): Result<String>
}

class AiRepositoryImpl(
    private val aiEngine: AiConversationEngine,
    private val settingsManager: SettingsManager
) : AiRepository {

    override suspend fun generateConversation(
        apiKey: String,
        userMessage: String,
        systemPrompt: String
    ): Result<String> {
        return try {
            val response = aiEngine.sendMessage(userMessage)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendMessage(
        message: String,
        history: List<com.mahi.assistant.ai.ChatMessage>
    ): Result<String> {
        return try {
            val response = aiEngine.sendMessage(message, history)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
