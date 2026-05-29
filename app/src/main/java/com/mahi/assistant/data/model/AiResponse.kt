package com.mahi.assistant.data.model

import com.google.gson.annotations.SerializedName

data class AiResponse(
    val candidates: List<Candidate>? = null,
    val promptFeedback: PromptFeedback? = null
)

data class Candidate(
    val content: ResponseContent? = null,
    @SerializedName("finish_reason")
    val finishReason: String? = null,
    val index: Int? = null,
    @SerializedName("safety_ratings")
    val safetyRatings: List<SafetyRating>? = null
)

data class ResponseContent(
    val parts: List<ResponsePart>? = null,
    val role: String? = null
)

data class ResponsePart(
    val text: String? = null
)

data class SafetyRating(
    val category: String? = null,
    val probability: String? = null
)

data class PromptFeedback(
    @SerializedName("block_reason")
    val blockReason: String? = null,
    val safetyRatings: List<SafetyRating>? = null
)
