package com.mahi.assistant.api

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * ULTIMATE Web Search Service — Multi-source fallback chain.
 * NO API KEY NEEDED! Works even when Gemini/Grok APIs fail.
 *
 * Search Chain:
 * 1. Wikipedia API (FREE, multilingual, high quality)
 * 2. DuckDuckGo Instant Answers (FREE, good for definitions)
 * 3. Google search URL generation (always works, opens in browser)
 *
 * This ensures MAHI ALWAYS responds — never shows "I'm having trouble".
 */
object WebSearchService {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Detect if text contains Hindi/Devanagari characters.
     * Used to route to appropriate Wikipedia language.
     */
    fun isHindiText(text: String): Boolean {
        val devanagariRange = '\u0900'..'\u097F'
        return text.any { it in devanagariRange }
    }

    /**
     * Transliterate common Hindi Devanagari words to Hinglish.
     * This helps when searching English-language sources.
     */
    fun devanagariToHinglish(text: String): String {
        // Common Hindi word mappings for better search results
        val mappings = mapOf(
            "मौसम" to "mausam weather",
            "खबर" to "khabar news",
            "समाचार" to "samachar news",
            "आज" to "aaj today",
            "कल" to "kal tomorrow yesterday",
            "बिहार" to "Bihar",
            "दिल्ली" to "Delhi",
            "मुंबई" to "Mumbai",
            "कोलकाता" to "Kolkata",
            "चेन्नई" to "Chennai",
            "बैंगलोर" to "Bangalore",
            "हैदराबाद" to "Hyderabad",
            "जयपुर" to "Jaipur",
            "लखनऊ" to "Lucknow",
            "पटना" to "Patna",
            "फिल्म" to "film movie",
            "सिनेमा" to "cinema movie",
            "गाना" to "gaana song",
            "खेल" to "khel sport game",
            "क्रिकेट" to "cricket",
            "अभी" to "abhi now",
            "क्या" to "kya what",
            "कैसे" to "kaise how",
            "कहां" to "kahan where",
            "कब" to "kab when",
            "क्यों" to "kyon why",
            "कौन" to "kaun who",
            "कितना" to "kitna how much",
            "मदद" to "madad help",
            "जरूर" to "zaroor must",
            "शेयर" to "share stock market",
            "बाजार" to "bazaar market",
            "दिल्ली" to "Delhi",
            "भारत" to "India",
            "देश" to "desh country",
            "वीडियो" to "video",
            "यूट्यूब" to "YouTube",
            "मूवी" to "movie",
            "कॉल" to "call",
            "मैसेज" to "message",
            "फोन" to "phone",
            "कैमरा" to "camera",
            "बैटरी" to "battery",
            "इंटरनेट" to "internet",
            "वाईफाई" to "wifi",
            "ब्लूटूथ" to "bluetooth",
            "एप्प" to "app",
            "गाड़ी" to "gaadi car vehicle",
            "ट्रेन" to "train",
            "बस" to "bus",
            "उड़ान" to "flight",
            "पैसा" to "paisa money",
            "रुपया" to "rupee",
            "डॉलर" to "dollar",
            "कीमत" to "keemat price",
            "राजनीति" to "politics",
            "चुनाव" to "election",
            "सरकार" to "sarkar government",
            "मंत्री" to "minister",
            "प्रधानमंत्री" to "prime minister",
            "मुख्यमंत्री" to "chief minister",
            "शिक्षा" to "shiksha education",
            "अस्पताल" to "hospital",
            "डॉक्टर" to "doctor",
            "दवा" to "dawa medicine",
            "स्वास्थ्य" to "swasthya health",
            "त्योहार" to "tyohar festival",
            "मंदिर" to "mandir temple",
            "खाना" to "khana food",
            "रेस्टोरेंट" to "restaurant",
            "होटल" to "hotel",
            "घर" to "ghar home house",
            "स्कूल" to "school",
            "कॉलेज" to "college",
            "नौकरी" to "naukri job",
            "परीक्षा" to "pariksha exam"
        )

        var result = text
        for ((hindi, hinglish) in mappings) {
            result = result.replace(hindi, hinglish)
        }

        // If still mostly Devanagari, just return original
        // (Wikipedia Hindi will handle it)
        return result
    }

    /**
     * MAIN search method — tries multiple sources in order.
     * NEVER returns empty/useless response.
     */
    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        val isHindi = isHindiText(query)
        val results = mutableListOf<String>()

        // ── SOURCE 1: Wikipedia (BEST for factual queries) ──────────────
        try {
            val wikiResult = searchWikipedia(query, if (isHindi) "hi" else "en")
            if (wikiResult.isNotBlank() && wikiResult.length > 30) {
                results.add(wikiResult)
            }
        } catch (_: Exception) { }

        // ── SOURCE 2: DuckDuckGo Instant Answers ────────────────────────
        try {
            val ddgResult = searchDuckDuckGo(query)
            if (ddgResult.isNotBlank() && !ddgResult.contains("couldn't find") && ddgResult.length > 20) {
                results.add(ddgResult)
            }
        } catch (_: Exception) { }

        // If Hindi query failed, try English version too
        if (isHindi && results.isEmpty()) {
            try {
                val engQuery = devanagariToHinglish(query)
                if (engQuery != query) {
                    val wikiEngResult = searchWikipedia(engQuery, "en")
                    if (wikiEngResult.isNotBlank() && wikiEngResult.length > 30) {
                        results.add(wikiEngResult)
                    }
                }
            } catch (_: Exception) { }
        }

        // ── RETURN BEST RESULT ──────────────────────────────────────────
        when {
            results.isNotEmpty() -> results.first()
            else -> generateHelpfulResponse(query)
        }
    }

    /**
     * Search Wikipedia API — FREE, multilingual, high quality.
     * Supports Hindi (hi), English (en), and 300+ languages.
     */
    private fun searchWikipedia(query: String, language: String = "en"): String {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://$language.wikipedia.org/w/api.php?" +
                    "action=query&list=search&srsearch=$encodedQuery&" +
                    "format=json&srlimit=3&utf8=1"

            val searchRequest = Request.Builder().url(searchUrl).build()
            val searchResponse = client.newCall(searchRequest).execute()
            val searchBody = searchResponse.body?.string() ?: return ""

            val searchResult = gson.fromJson(searchBody, WikiSearchResult::class.java)
            val firstResult = searchResult.query?.search?.firstOrNull() ?: return ""

            // Get the full page extract for better answers
            val pageId = firstResult.pageid
            val extractUrl = "https://$language.wikipedia.org/w/api.php?" +
                    "action=query&pageids=$pageId&prop=extracts&exintro=1&" +
                    "explaintext=1&format=json&utf8=1"

            val extractRequest = Request.Builder().url(extractUrl).build()
            val extractResponse = client.newCall(extractRequest).execute()
            val extractBody = extractResponse.body?.string() ?: return ""

            val extractResult = gson.fromJson(extractBody, WikiExtractResult::class.java)
            val extract = extractResult.query?.pages?.get(pageId.toString())?.extract ?: return ""

            if (extract.isNotBlank()) {
                // Clean up and truncate
                val cleaned = extract
                    .replace(Regex("\\n+"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(800)

                val title = firstResult.title ?: ""
                if (language == "hi") {
                    "$title: $cleaned"
                } else {
                    "$title: $cleaned"
                }
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Search DuckDuckGo for instant answers.
     * Good for definitions, calculations, and quick facts.
     */
    private fun searchDuckDuckGo(query: String): String {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return ""

            val result = gson.fromJson(body, DuckDuckGoResult::class.java)

            buildString {
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
            }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Generate a helpful response when all search sources fail.
     * NEVER returns "I'm having trouble" — always provides something useful.
     */
    private fun generateHelpfulResponse(query: String): String {
        val isHindi = isHindiText(query)

        // Try to provide contextual help based on the query
        val q = query.lowercase()

        // Common queries with built-in answers
        when {
            q.contains("time") || q.contains("samay") || q.contains("waqt") || q.contains("समय") -> {
                val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                return if (isHindi) "Abhi ka time $time hai." else "Current time is $time."
            }
            q.contains("date") || q.contains("tarikh") || q.contains("din") || q.contains("तारीख") -> {
                val date = SimpleDateFormat("dd MMMM yyyy, EEEE", Locale.getDefault()).format(Date())
                return if (isHindi) "Aaj ki date $date hai." else "Today's date is $date."
            }
            q.contains("weather") || q.contains("mausam") || q.contains("मौसम") -> {
                return if (isHindi) "Mausam jaanne ke liye bolo: 'mausam batao' ya specific city ka naam lo." else "Say 'weather' or a city name to check the weather."
            }
            q.contains("news") || q.contains("khabar") || q.contains("खबर") || q.contains("समाचार") -> {
                return if (isHindi) "Latest khabar jaanne ke liye bolo: 'aaj ki khabar' ya koi topic bolo." else "Say 'news' or a topic to get latest news."
            }
        }

        // General helpful response that doesn't feel like an error
        return if (isHindi) {
            "Main is baare me abhi exact info nahi de pa raha, lekin aap Google par search kar sakte hain. Kya main aapki kisi aur cheez me madad kar sakta hoon?"
        } else {
            "I don't have specific info on that right now, but you can search Google for more details. Is there anything else I can help you with?"
        }
    }

    /**
     * Search for news about a specific topic.
     * Uses Google News RSS as the source.
     */
    suspend fun searchNews(topic: String, maxResults: Int = 5): List<NewsItem> = withContext(Dispatchers.IO) {
        try {
            val isHindi = isHindiText(topic)
            val searchTopic = if (isHindi) devanagariToHinglish(topic) else topic
            val encodedTopic = URLEncoder.encode(searchTopic, "UTF-8")
            val langParams = if (isHindi) "hl=hi-IN&gl=IN&ceid=IN:hi" else "hl=en-IN&gl=IN&ceid=IN:en"
            val url = "https://news.google.com/rss/search?q=$encodedTopic&$langParams"

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

    /**
     * Get a Google search URL for any query.
     * Used as the final fallback to open in browser.
     */
    fun getGoogleSearchUrl(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return "https://www.google.com/search?q=$encoded"
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

    // Wikipedia search result models
    data class WikiSearchResult(
        val query: WikiSearchQuery? = null
    )

    data class WikiSearchQuery(
        val search: List<WikiSearchItem>? = null
    )

    data class WikiSearchItem(
        val ns: Int = 0,
        val title: String? = null,
        val pageid: Int = 0,
        val snippet: String? = null
    )

    data class WikiExtractResult(
        val query: WikiExtractQuery? = null
    )

    data class WikiExtractQuery(
        val pages: Map<String, WikiPage>? = null
    )

    data class WikiPage(
        val pageid: Int = 0,
        val title: String? = null,
        val extract: String? = null
    )
}


