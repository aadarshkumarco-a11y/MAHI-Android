package com.mahi.assistant.data.model

import com.google.gson.annotations.SerializedName

data class NewsResponse(
    val totalArticles: Int? = null,
    val articles: List<NewsArticle>? = null
)

data class NewsArticle(
    val title: String? = null,
    val description: String? = null,
    val content: String? = null,
    val url: String? = null,
    val image: String? = null,
    val publishedAt: String? = null,
    val source: NewsSource? = null
)

data class NewsSource(
    val name: String? = null,
    val url: String? = null
)
