package com.novastream.app.data.api

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit-Interface für NovaStream.to (liefert rohes HTML).
 * URL-Schema (Stand 2025/2026):
 *   Startseite:     /
 *   Suche:          /suche?term=...
 *   Serie Detail:   /serie/{slug}           (redirectet auf /serie/{slug}/staffel-1)
 *   Staffel:        /serie/{slug}/staffel-{n}
 *   Episode:        /serie/{slug}/staffel-{n}/episode-{m}
 */
interface NovaStreamApi {

    @GET("/")
    suspend fun home(): String

    @GET("/suche")
    suspend fun search(@Query("term") query: String): String

    @GET("/serie/{slug}")
    suspend fun seriesDetail(@Path("slug") slug: String): String

    @GET("/serie/{slug}/staffel-{season}")
    suspend fun season(@Path("slug") slug: String, @Path("season") season: Int): String

    @GET("/serie/{slug}/staffel-{season}/episode-{episode}")
    suspend fun episode(
        @Path("slug") slug: String,
        @Path("season") season: Int,
        @Path("episode") episode: Int
    ): String

    /** Lädt eine beliebige relative URL als HTML (für Redirect-Auflösung). */
    @GET("{path}")
    suspend fun raw(@Path("path") path: String): String
}
