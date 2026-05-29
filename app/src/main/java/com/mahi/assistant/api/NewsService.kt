package com.mahi.assistant.api

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

// ──────────────────────────────────────────────────────────────────────────────
// Retrofit Service Interface
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Retrofit interface for the GNews API (free tier).
 *
 * Base URL: https://gnews.io/api/v4/
 *
 * Usage:
 * ```
 * val retrofit = Retrofit.Builder()
 *     .baseUrl("https://gnews.io/api/v4/")
 *     .addConverterFactory(GsonConverterFactory.create())
 *     .build()
 * val service = retrofit.create(NewsService::class.java)
 * val news = service.getTopHeadlines(category = "general", lang = "en", token = "YOUR_API_KEY")
 * ```
 */
interface NewsService {

    @GET("top-headlines")
    suspend fun getTopHeadlines(
        @Query("category") category: String? = null,
        @Query("lang") lang: String = "en",
        @Query("country") country: String? = null,
        @Query("max") max: Int = 5,
        @Query("token") token: String
    ): NewsData

    @GET("search")
    suspend fun searchNews(
        @Query("q") query: String,
        @Query("lang") lang: String = "en",
        @Query("max") max: Int = 5,
        @Query("token") token: String
    ): NewsData
}

// ──────────────────────────────────────────────────────────────────────────────
// Response Data Classes
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Top-level response from the GNews API.
 */
data class NewsData(
    val totalArticles: Int? = null,
    val articles: List<Article>? = null
)

/**
 * A single news article from GNews.
 */
data class Article(
    val title: String? = null,
    val description: String? = null,
    val content: String? = null,
    val url: String? = null,
    val image: String? = null,
    @SerializedName("publishedAt")
    val publishedAt: String? = null,
    val source: NewsSource? = null
)

/**
 * The source of a news article.
 */
data class NewsSource(
    val name: String? = null,
    val url: String? = null
)

// ──────────────────────────────────────────────────────────────────────────────
// Formatting Helpers
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Format news data as speech-friendly text suitable for TTS output.
 * Produces a natural-sounding news briefing.
 *
 * @param maxArticles Maximum number of articles to include in the summary. Defaults to 5.
 * @return A speech-friendly news briefing string.
 */
fun NewsData.toSpeechText(maxArticles: Int = 5): String {
    val articles = this.articles

    if (articles.isNullOrEmpty()) {
        return "I couldn't find any news headlines at the moment. Please try again later."
    }

    val limitedArticles = articles.take(maxArticles)

    val parts = mutableListOf<String>()
    parts.add("Here are the top ${limitedArticles.size} news headlines.")

    limitedArticles.forEachIndexed { index, article ->
        val title = article.title ?: "Untitled"
        val sourceName = article.source?.name
        val timeAgo = article.publishedAt?.toTimeAgo()

        val entry = buildString {
            append("Number ${index + 1}: $title")
            if (!sourceName.isNullOrBlank()) {
                append(", from $sourceName")
            }
            if (!timeAgo.isNullOrBlank()) {
                append(", $timeAgo")
            }
            append(".")
        }
        parts.add(entry)
    }

    parts.add("That's all for now. Would you like me to read any of these in detail?")

    return parts.joinToString(" ")
}

/**
 * Format news data as a concise text for UI display.
 */
fun NewsData.toDisplayText(): String {
    val articles = this.articles ?: return "No news available."

    return articles.take(5).mapIndexed { index, article ->
        val title = article.title ?: "Untitled"
        val source = article.source?.name ?: ""
        "${index + 1}. $title — $source"
    }.joinToString("\n")
}

/**
 * Convert an ISO 8601 date string to a human-readable "time ago" string.
 * Used for making article timestamps speech-friendly.
 */
private fun String.toTimeAgo(): String? {
    return try {
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        )

        var parsedDate: java.util.Date? = null
        for (format in formats) {
            try {
                parsedDate = format.parse(this)
                break
            } catch (_: Exception) {
                continue
            }
        }

        if (parsedDate == null) return null

        val now = System.currentTimeMillis()
        val diff = now - parsedDate.time

        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "just now"
            diff < TimeUnit.HOURS.toMillis(1) -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
                "$minutes minute${if (minutes != 1L) "s" else ""} ago"
            }
            diff < TimeUnit.DAYS.toMillis(1) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                "$hours hour${if (hours != 1L) "s" else ""} ago"
            }
            diff < TimeUnit.DAYS.toMillis(7) -> {
                val days = TimeUnit.MILLISECONDS.toDays(diff)
                "$days day${if (days != 1L) "s" else ""} ago"
            }
            else -> {
                val outputFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)
                "on ${outputFormat.format(parsedDate)}"
            }
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Get the full content summary of an article, formatted for TTS.
 */
fun Article.toFullSpeechText(): String {
    val parts = mutableListOf<String>()

    this.title?.let { parts.add(it) }
    this.source?.name?.let { parts.add("From $it.") }

    val body = this.content ?: this.description
    if (!body.isNullOrBlank()) {
        // GNews sometimes truncates content with "[XXX chars]" suffix
        val cleaned = body.replace(Regex("\\s*\\[\\d+\\s+chars]$"), "")
        parts.add(cleaned)
    }

    return if (parts.isEmpty()) "No content available for this article."
    else parts.joinToString(" ")
}

// ──────────────────────────────────────────────────────────────────────────────
// News Client Helper
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Convenience helper to create a NewsService instance with the
 * GNews base URL pre-configured.
 */
object NewsClient {

    private const val BASE_URL = "https://gnews.io/api/v4/"

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val instance: NewsService by lazy {
        retrofit.create(NewsService::class.java)
    }

    /**
     * Supported GNews categories for filtering headlines.
     */
    enum class Category(val value: String) {
        GENERAL("general"),
        WORLD("world"),
        NATION("nation"),
        BUSINESS("business"),
        TECHNOLOGY("technology"),
        ENTERTAINMENT("entertainment"),
        SPORTS("sports"),
        SCIENCE("science"),
        HEALTH("health")
    }
}
