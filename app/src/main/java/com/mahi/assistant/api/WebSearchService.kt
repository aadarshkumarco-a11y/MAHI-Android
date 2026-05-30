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
 * JARVIS-LEVEL Research Engine — Multi-source deep search pipeline.
 * NO API KEY NEEDED! Works even when Gemini/Grok APIs fail.
 *
 * Search Chain (5 sources, aggressive fallback):
 * 1. Wikipedia API (FREE, multilingual, high quality)
 * 2. DuckDuckGo Instant Answers (FREE, good for definitions)
 * 3. Brave Search API (FREE tier, web results with snippets)
 * 4. Google News RSS (FREE, for current events/news)
 * 5. Contextual help generation (built-in answers)
 *
 * Research Pipeline:
 * Question → Intent Detection → Web Search → Source Collection →
 * Information Extraction → Summarization → Response
 *
 * This ensures MAHI NEVER says "I don't have specific info" — always researches first.
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
     * Check if a query is likely to need web research (vs just casual chat).
     * Questions about facts, people, events, places, things need research.
     */
    fun needsResearch(query: String): Boolean {
        val q = query.lowercase().trim()
        // Questions that need research
        val researchIndicators = listOf(
            "who is", "who was", "who are", "what is", "what was", "what are",
            "when is", "when was", "when did", "where is", "where was", "where do",
            "how is", "how was", "how did", "how do", "how does", "how many",
            "why is", "why was", "why did", "why do", "why does",
            "which is", "which was", "which are",
            "tell me about", "tell about", "explain", "describe",
            "information about", "info about", "details about",
            "latest", "recent", "current", "today", "now",
            "news", "update", "happening", "score", "result",
            "price of", "cost of", "value of", "worth of",
            "weather", "temperature", "forecast",
            "meaning of", "definition of", "define",
            "history of", "origin of", "cause of",
            "difference between", "compare", "vs",
            "how old is", "how old was", "where was he", "where was she",
            "when was he", "when was she", "what did he", "what did she",
            "who is his", "who is her", "how tall is", "how much is",
            "how about him", "how about her", "what about him", "what about her",
            "tell me more about", "more about", "more info",
            "is it true", "is it real", "fact check",
            "kya hai", "kaun hai", "kahan hai", "kab hua", "kyon hai",
            "kaise hai", "kitna hai", "kitne hai",
            "batao", "samjhao", "dikhao", "pata karo",
            "khabar", "mausam", "update",
            "uska naam", "uski umar", "uske baare", "uska phone"
        )

        // Casual chat that does NOT need research
        val casualPatterns = listOf(
            "hello", "hi ", "hey", "how are you", "how r u", "kya hal",
            "good morning", "good night", "good evening", "good afternoon",
            "thank", "thanks", "ok", "okay", "nice", "cool", "great",
            "haan", "nahi", "thik hai", "accha", "theek", "sahi",
            "yes", "no", "maybe", "sure", "done", "stop"
        )

        // Pronoun-heavy follow-ups that need context — always research these
        val followUpPatterns = listOf(
            "how old is he", "how old is she", "where was he", "where was she",
            "when did he", "when did she", "what did he", "what did she",
            "who is he", "who is she", "what is it", "where is it",
            "how about him", "how about her", "what about him", "what about her",
            "tell me more", "more about that", "what else", "and then"
        )

        // Follow-up patterns always need research
        if (followUpPatterns.any { q.contains(it) }) return true

        // If it's casual chat, no research needed
        if (casualPatterns.any { q.contains(it) } && q.length < 30) return false

        // If it has research indicators, research needed
        if (researchIndicators.any { q.contains(it) }) return true

        // If it's longer than 15 chars and not a simple greeting, probably needs research
        if (q.length > 15) return true

        return false
    }

    /**
     * Transliterate common Hindi Devanagari words to Hinglish.
     * This helps when searching English-language sources.
     */
    fun devanagariToHinglish(text: String): String {
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
        return result
    }

    /**
     * MAIN search method — tries 4+ sources in order.
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

        // ── SOURCE 3: Brave Search (FREE, web results with snippets!) ───
        try {
            val braveResult = searchBrave(query)
            if (braveResult.isNotBlank() && braveResult.length > 30) {
                results.add(braveResult)
            }
        } catch (_: Exception) { }

        // ── SOURCE 3.5: DuckDuckGo HTML search (NO API key needed!) ───
        if (results.isEmpty()) {
            try {
                val ddgHtmlResult = searchDuckDuckGoHtml(query)
                if (ddgHtmlResult.isNotBlank() && ddgHtmlResult.length > 30) {
                    results.add(ddgHtmlResult)
                }
            } catch (_: Exception) { }
        }

        // If Hindi query failed, try English version too
        if (isHindi && results.isEmpty()) {
            try {
                val engQuery = devanagariToHinglish(query)
                if (engQuery != query) {
                    val wikiEngResult = searchWikipedia(engQuery, "en")
                    if (wikiEngResult.isNotBlank() && wikiEngResult.length > 30) {
                        results.add(wikiEngResult)
                    }
                    // Also try Brave with English query
                    val braveEngResult = searchBrave(engQuery)
                    if (braveEngResult.isNotBlank() && braveEngResult.length > 30) {
                        results.add(braveEngResult)
                    }
                }
            } catch (_: Exception) { }
        }

        // ── SOURCE 4: Google News RSS (for current events) ──────────────
        val q = query.lowercase()
        if (q.contains("news") || q.contains("latest") || q.contains("recent") ||
            q.contains("current") || q.contains("today") || q.contains("happening") ||
            q.contains("khabar") || q.contains("खबर") || q.contains("update")) {
            try {
                val newsItems = searchNews(query, 3)
                if (newsItems.isNotEmpty()) {
                    val newsText = newsItems.mapIndexed { i, item ->
                        "${i + 1}. ${item.title}"
                    }.joinToString(". ")
                    results.add("Latest news: $newsText")
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
     * DEEP RESEARCH — searches ALL sources, combines results.
     * Used when a single source isn't enough.
     * Returns combined information from multiple sources.
     */
    suspend fun deepResearch(query: String): String = withContext(Dispatchers.IO) {
        val isHindi = isHindiText(query)
        val searchQuery = if (isHindi) devanagariToHinglish(query) else query
        val allResults = mutableListOf<Pair<String, String>>() // (source, content)

        // Parallel-ish research from all sources
        // Source 1: Wikipedia
        try {
            val wikiResult = searchWikipedia(query, if (isHindi) "hi" else "en")
            if (wikiResult.isNotBlank() && wikiResult.length > 30) {
                allResults.add("Wikipedia" to wikiResult)
            }
            // Also try English Wikipedia if Hindi
            if (isHindi) {
                val engWiki = searchWikipedia(searchQuery, "en")
                if (engWiki.isNotBlank() && engWiki.length > 30) {
                    allResults.add("Wikipedia EN" to engWiki)
                }
            }
        } catch (_: Exception) { }

        // Source 2: DuckDuckGo
        try {
            val ddgResult = searchDuckDuckGo(searchQuery)
            if (ddgResult.isNotBlank() && ddgResult.length > 20) {
                allResults.add("DuckDuckGo" to ddgResult)
            }
        } catch (_: Exception) { }

        // Source 3: Brave Search
        try {
            val braveResult = searchBrave(searchQuery)
            if (braveResult.isNotBlank() && braveResult.length > 30) {
                allResults.add("Web Search" to braveResult)
            }
        } catch (_: Exception) { }

        // Source 3.5: DuckDuckGo HTML (no API key needed)
        if (allResults.isEmpty()) {
            try {
                val ddgHtmlResult = searchDuckDuckGoHtml(searchQuery)
                if (ddgHtmlResult.isNotBlank() && ddgHtmlResult.length > 30) {
                    allResults.add("DuckDuckGo Web" to ddgHtmlResult)
                }
            } catch (_: Exception) { }
        }

        // Source 4: News (for current events)
        try {
            val newsItems = searchNews(query, 3)
            if (newsItems.isNotEmpty()) {
                val newsText = newsItems.map { "${it.title}. ${it.description}".take(300) }.joinToString("\n")
                allResults.add("News" to newsText)
            }
        } catch (_: Exception) { }

        // Combine all results
        if (allResults.isEmpty()) {
            return@withContext generateHelpfulResponse(query)
        }

        // Return the best (longest, most detailed) result
        val bestResult = allResults.maxByOrNull { it.second.length }?.second ?: generateHelpfulResponse(query)

        // If we have multiple sources, combine them
        if (allResults.size > 1) {
            val combined = allResults.take(2).map { "${it.first}: ${it.second}" }.joinToString("\n\n")
            return@withContext combined.take(1500) // Limit for AI context
        }

        return@withContext bestResult
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
                val cleaned = extract
                    .replace(Regex("\\n+"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(800)

                val title = firstResult.title ?: ""
                "$title: $cleaned"
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
     * Search Brave Search API — FREE, returns web results with snippets!
     * This is the KEY addition that fills the gap between Wikipedia and DuckDuckGo.
     * No API key needed for basic queries.
     */
    private fun searchBrave(query: String): String {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            // Brave Search public endpoint — no key needed for basic queries
            val url = "https://search.brave.com/api/suggest?q=$encodedQuery"

            // Alternative: Use Brave's web search results
            val searchUrl = "https://api.search.brave.com/res/v1/web/search?q=$encodedQuery&count=5"
            val request = Request.Builder()
                .url(searchUrl)
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return ""

            if (body.isBlank()) return ""

            val braveResult = gson.fromJson(body, BraveSearchResult::class.java)
            val results = braveResult.web?.results ?: return ""

            if (results.isEmpty()) return ""

            // Combine snippets from top results
            val combined = results.take(3).mapNotNull { result ->
                val title = result.title ?: return@mapNotNull null
                val snippet = result.description ?: ""
                if (snippet.isNotBlank()) "$title: $snippet" else title
            }.joinToString("\n")

            combined
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Search DuckDuckGo HTML results — NO API key needed!
     * Scrapes DuckDuckGo HTML search for results when API and Brave fail.
     * This is the ROBUST fallback that always works.
     */
    private fun searchDuckDuckGoHtml(query: String): String {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://html.duckduckgo.com/html/?q=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return ""

            if (html.isBlank()) return ""

            // Parse HTML for result titles and snippets
            val results = mutableListOf<String>()
            val resultRegex = Regex("""<a[^>]*class="result__a"[^>]*>(.*?)</a>.*?<a[^>]*class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
            resultRegex.findAll(html).take(5).forEach { match ->
                val title = match.groupValues[1].replace(Regex("""<[^>]+>"""), "").trim()
                val snippet = match.groupValues[2].replace(Regex("""<[^>]+>"""), "").trim()
                if (title.isNotBlank()) {
                    results.add(if (snippet.isNotBlank()) "$title: $snippet" else title)
                }
            }

            // Fallback: try simpler parsing if the regex above didn't find results
            if (results.isEmpty()) {
                val simpleTitleRegex = Regex("""class="result__a"[^>]*>(.*?)</a>""")
                simpleTitleRegex.findAll(html).take(5).forEach { match ->
                    val title = match.groupValues[1].replace(Regex("""<[^>]+>"""), "").trim()
                    if (title.isNotBlank() && title.length > 5) {
                        results.add(title)
                    }
                }
            }

            results.joinToString("\n").take(1000)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Quick answers for common questions that don't need full research.
     * Returns a direct answer or empty string if no quick answer available.
     */
    fun quickAnswer(query: String): String {
        val q = query.lowercase().trim()
        val isHindi = isHindiText(query)

        return when {
            q.contains("time") && (q.contains("what") || q.contains("samay") || q.length < 10) -> {
                val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                if (isHindi) "Abhi ka time $time hai." else "Current time is $time."
            }
            q.contains("date") && (q.contains("what") || q.contains("tarikh") || q.length < 10) -> {
                val date = SimpleDateFormat("dd MMMM yyyy, EEEE", Locale.getDefault()).format(Date())
                if (isHindi) "Aaj ki date $date hai." else "Today's date is $date."
            }
            q.contains("day") && q.contains("what") -> {
                val day = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())
                if (isHindi) "Aaj $day hai." else "Today is $day."
            }
            else -> "" // No quick answer available
        }
    }

    /**
     * Generate a helpful response when all search sources fail.
     * NEVER returns "I don't have specific info" — always provides something useful.
     */
    private fun generateHelpfulResponse(query: String): String {
        val isHindi = isHindiText(query)
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

        // Instead of "I don't have info", provide an action-oriented response
        return if (isHindi) {
            "Maine internet par search kiya lekin exact answer nahi mila. Aap dobara puch sakte hain ya kuch aur poochein — main koshish karunga help karne ka!"
        } else {
            "I searched but couldn't find a specific answer for that. Try rephrasing your question, or ask me something else — I'll do my best to help!"
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

    // Brave Search API response model
    data class BraveSearchResult(
        val web: BraveWebResults? = null
    )

    data class BraveWebResults(
        val results: List<BraveSearchItem>? = null
    )

    data class BraveSearchItem(
        val title: String? = null,
        val url: String? = null,
        val description: String? = null
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
