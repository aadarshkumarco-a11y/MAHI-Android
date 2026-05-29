package com.mahi.assistant.data.model

import com.google.gson.annotations.SerializedName

data class AiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val safetySettings: List<SafetySetting>? = null
) {
    data class Content(
        val parts: List<Part>,
        val role: String? = null
    )

    data class Part(
        val text: String? = null,
        @SerializedName("inline_data")
        val inlineData: InlineData? = null
    )

    data class InlineData(
        val mimeType: String,
        val data: String
    )

    data class GenerationConfig(
        val temperature: Double = 0.7,
        val topK: Int = 40,
        val topP: Double = 0.95,
        val maxOutputTokens: Int = 1024,
        val candidateCount: Int = 1,
        val stopSequences: List<String>? = null
    )

    data class SafetySetting(
        val category: String,
        val threshold: String
    )

    companion object {
        fun createSimpleRequest(
            userMessage: String,
            systemInstruction: String? = null,
            temperature: Double = 0.7,
            maxTokens: Int = 1024
        ): AiRequest {
            val contents = mutableListOf<Content>()

            if (systemInstruction != null) {
                contents.add(
                    Content(
                        parts = listOf(Part(text = systemInstruction)),
                        role = "user"
                    )
                )
                contents.add(
                    Content(
                        parts = listOf(Part(text = "Understood. I will follow these instructions.")),
                        role = "model"
                    )
                )
            }

            contents.add(
                Content(
                    parts = listOf(Part(text = userMessage)),
                    role = "user"
                )
            )

            return AiRequest(
                contents = contents,
                generationConfig = GenerationConfig(
                    temperature = temperature,
                    maxOutputTokens = maxTokens
                ),
                safetySettings = listOf(
                    SafetySetting(
                        category = "HARM_CATEGORY_HARASSMENT",
                        threshold = "BLOCK_MEDIUM_AND_ABOVE"
                    ),
                    SafetySetting(
                        category = "HARM_CATEGORY_HATE_SPEECH",
                        threshold = "BLOCK_MEDIUM_AND_ABOVE"
                    ),
                    SafetySetting(
                        category = "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                        threshold = "BLOCK_MEDIUM_AND_ABOVE"
                    ),
                    SafetySetting(
                        category = "HARM_CATEGORY_DANGEROUS_CONTENT",
                        threshold = "BLOCK_MEDIUM_AND_ABOVE"
                    )
                )
            )
        }
    }
}
