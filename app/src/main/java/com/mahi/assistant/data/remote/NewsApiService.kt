package com.mahi.assistant.data.remote

import com.mahi.assistant.data.model.NewsResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface NewsApiService {

    @GET("api/v4/top-headlines")
    suspend fun getTopHeadlines(
        @Query("token") apiKey: String,
        @Query("lang") language: String = "en",
        @Query("max") maxArticles: Int = 10,
        @Query("category") category: String? = null
    ): NewsResponse

    @GET("api/v4/search")
    suspend fun searchNews(
        @Query("q") query: String,
        @Query("token") apiKey: String,
        @Query("lang") language: String = "en",
        @Query("max") maxArticles: Int = 10
    ): NewsResponse
}
