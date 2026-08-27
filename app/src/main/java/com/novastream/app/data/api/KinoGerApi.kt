package com.novastream.app.data.api

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit-Interface für KinoGer.to (DLE-basiertes CMS).
 * URL-Schema (Stand 2025/2026):
 *   Startseite:     /
 *   Filme:          /stream/
 *   Genre:          /stream/{genre}/page/{n}
 *   Suche:          /?do=search&subaction=search&titleonly=3&story={query}
 *   Detail:         /stream/{id}-{slug}.html
 *   Serie Detail:   /series/{id}-{slug}.html
 */
interface KinoGerApi {

    @GET("/")
    suspend fun home(): String

    @GET("stream/serie")
    suspend fun seriesHome(): String

    @GET("stream/tv-shows")
    suspend fun tvShows(): String

    @GET("stream/filme")
    suspend fun movies(): String

    @GET("stream/serie/page/{page}")
    suspend fun seriesPage(@Path("page") page: Int = 1): String

    @GET("stream/tv-shows/page/{page}")
    suspend fun tvShowsPage(@Path("page") page: Int = 1): String

    @GET("stream/filme/page/{page}")
    suspend fun moviesPage(@Path("page") page: Int = 1): String

    @GET("stream/{genre}/page/{page}")
    suspend fun genrePage(
        @Path("genre") genre: String,
        @Path("page") page: Int = 1
    ): String

    @GET("/")
    suspend fun search(
        @Query("do") doSearch: String = "search",
        @Query("subaction") subaction: String = "search",
        @Query("titleonly") titleonly: Int = 3,
        @Query("story") query: String
    ): String

    @GET("{path}")
    suspend fun raw(@Path("path") path: String): String
}
