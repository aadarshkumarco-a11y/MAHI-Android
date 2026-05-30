package com.mahi.assistant.api

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * FREE Web Search Service — Uses DuckDuckGo Instant Answer API.
 * NO API KEY NEEDED! Works even when Gemini/Grok APIs fail.
 * This is the ULTIMATE fallback to ensure MAHI ALWAYS responds.
 */
object WebSearchService {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Search DuckDuckGo for instant answers.
     * Returns a formatted text answer or summary.
     */
    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext "No results found."

            val result = gson.fromJson(body, DuckDuckGoResult::class.java)

            // Priority: Abstract > Answer > Related Topics
            val answer = buildString {
                if (!result.abstractText.isNullOrBlank()) {
                    append(result.abstractText)
                    if (!result.abstractSource.isNullOrBlank()) {
                        append("\n\nSource: ${result.abstractSource}")
                    }
                } else if (!result.answer.isNullOrBlank()) {
                    append(result.answer)
                } else if (!result.definition.isNullOrBlank()) {
                    append(result.definition)
                    if (!result.definitionSource.isNullOrBlank()) {
                        append("\n\nSource: ${result.definitionSource}")
                    }
                } else if (!result.relatedTopics.isNullOrEmpty()) {
                    val topics = result.relatedTopics.take(5).mapNotNull { topic ->
                        topic.text ?: topic.topics?.take(3)?.map { it.text }?.joinToString(". ")
                    }.filter { it.isNotBlank() }
                    if (topics.isNotEmpty()) {
                        append(topics.joinToString(".\n"))
                    }
                }

                if (isBlank()) {
                    // Fallback: try Google search via intent
                    append("I couldn't find a direct answer. Let me search the web for you.")
                }
            }

            answer.ifBlank { "I couldn't find information about that. Let me search the web for you." }
        } catch (e: Exception) {
            "I couldn't search right now. Please check your internet connection."
        }
    }

    /**
     * Search for news about a specific topic.
     * Uses Google News RSS as the source.
     */
    suspend fun searchNews(topic: String, maxResults: Int = 5): List<NewsItem> = withContext(Dispatchers.IO) {
        try {
            val encodedTopic = URLEncoder.encode(topic, "UTF-8")
            val url = "https://news.google.com/rss/search?q=$encodedTopic&hl=en-IN&gl=IN&ceid=IN:en"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val xml = response.body?.string() ?: return@withContext emptyList()

            val items = mutableListOf<NewsItem>()
            val itemRegex = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
            val titleRegex = Regex("<title>(.*?)</title>")
            val linkRegex = Regex("<link>(.*?)</link>")
            val descRegex = Regex("<description>(.*?)</description>")
            val pubDateRegex = Regex("<pubDate>(.*?)</pubDate>")

            itemRegex.findAll(xml).take(maxResults).forEach { match ->
                val itemXml = match.groupValues[1]
                val title = titleRegex.find(itemXml)?.groupValues?.get(1)?.decodeHtmlEntities() ?: ""
                val link = linkRegex.find(itemXml)?.groupValues?.get(1)?.trim() ?: ""
                val desc = descRegex.find(itemXml)?.groupValues?.get(1)?.decodeHtmlEntities()?.take(200) ?: ""
                val pubDate = pubDateRegex.find(itemXml)?.groupValues?.get(1) ?: ""

                if (title.isNotBlank()) {
                    items.add(NewsItem(title = title, link = link, description = desc, pubDate = pubDate))
                }
            }

            items
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun String.decodeHtmlEntities(): String {
        return this
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
    }

    data class NewsItem(
        val title: String,
        val link: String,
        val description: String,
        val pubDate: String
    )

    // DuckDuckGo API response model
    data class DuckDuckGoResult(
        @SerializedName("Abstract") val abstractText: String? = null,
        @SerializedName("AbstractSource") val abstractSource: String? = null,
        @SerializedName("AbstractURL") val abstractUrl: String? = null,
        @SerializedName("Answer") val answer: String? = null,
        @SerializedName("Definition") val definition: String? = null,
        @SerializedName("DefinitionSource") val definitionSource: String? = null,
        @SerializedName("RelatedTopics") val relatedTopics: List<RelatedTopic>? = null,
        @SerializedName("Results") val results: List<DuckDuckGoResultItem>? = null
    )

    data class RelatedTopic(
        @SerializedName("Text") val text: String? = null,
        @SerializedName("Topics") val topics: List<RelatedTopic>? = null
    )

    data class DuckDuckGoResultItem(
        @SerializedName("Text") val text: String? = null,
        @SerializedName("FirstURL") val url: String? = null
    )
}
