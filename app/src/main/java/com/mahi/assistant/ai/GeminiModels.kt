package com.mahi.assistant.ai

import com.google.gson.annotations.SerializedName

// ──────────────────────────────────────────────────────────────────────────────
// Request Models
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Top-level request body sent to the Gemini generateContent endpoint.
 */
data class GeminiRequest(
    val contents: List<Content>,
    val systemInstruction: SystemInstruction? = null,
    val generationConfig: GenerationConfig? = null
)

/**
 * A single content message in the conversation.
 * role: "user" for user messages, "model" for assistant responses.
 */
data class Content(
    val role: String? = null,
    val parts: List<Part>
)

/**
 * A text part within a content message.
 */
data class Part(
    val text: String
)

/**
 * System instruction to set the behavior/persona of the model.
 */
data class SystemInstruction(
    val parts: List<Part>
)

/**
 * Optional generation configuration parameters.
 */
data class GenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val candidateCount: Int? = null,
    val maxOutputTokens: Int? = null,
    val stopSequences: List<String>? = null
)

// ──────────────────────────────────────────────────────────────────────────────
// Response Models
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Top-level response from the Gemini generateContent endpoint.
 */
data class GeminiResponse(
    val candidates: List<Candidate>? = null,
    val promptFeedback: PromptFeedback? = null
)

/**
 * A single candidate response from the model.
 */
data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null,
    val index: Int? = null,
    val safetyRatings: List<SafetyRating>? = null
)

/**
 * Safety rating for a candidate response.
 */
data class SafetyRating(
    val category: String? = null,
    val probability: String? = null
)

/**
 * Prompt feedback including any blocked reasons.
 */
data class PromptFeedback(
    val blockReason: String? = null,
    val safetyRatings: List<SafetyRating>? = null
)

// ──────────────────────────────────────────────────────────────────────────────
// Chat-level Convenience Models
// ──────────────────────────────────────────────────────────────────────────────

/**
 * A simplified chat message representation used by the conversation engine
 * to maintain conversation history independent of the Gemini wire format.
 */
data class ChatMessage(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_MODEL = "model"
    }
}

/**
 * Extension: Convert a ChatMessage to Gemini Content format.
 */
fun ChatMessage.toContent(): Content = Content(
    role = this.role,
    parts = listOf(Part(text = this.content))
)

/**
 * Extension: Convert a list of ChatMessages to Gemini Content list.
 */
fun List<ChatMessage>.toContents(): List<Content> = this.map { it.toContent() }

/**
 * Extract the text from a GeminiResponse, returning null if no valid content is available.
 */
fun GeminiResponse.extractText(): String? {
    return this.candidates
        ?.firstOrNull()
        ?.content
        ?.parts
        ?.firstOrNull()
        ?.text
}
