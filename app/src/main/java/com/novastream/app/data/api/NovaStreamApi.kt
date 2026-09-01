package com.novastream.app.data.api

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit-Interface für SerienStream.to / .cx (liefert rohes HTML).
 *
 * Getestete Endpunkte (2026):
 *   /                      – Startseite
 *   /suche?term=...        – Suche
 *   /serie/{slug}          – Detail (redirect → staffel-1)
 *   /serie/{slug}/staffel-{n}
 *   /serie/{slug}/staffel-{n}/episode-{m}
 *   /genre/{genre}         – Genre-Liste (+ /page/{n})
 *   /neue-episoden         – Frisch hinzugefügte Episoden
 *   /beliebte-serien       – Beliebte Serien (show-card)
 *   /serien                – Katalog
 *   /serienkalender        – Kalender
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

    @GET("/genre/{genre}")
    suspend fun genre(@Path("genre") genre: String): String

    @GET("/genre/{genre}/page/{page}")
    suspend fun genrePaged(
        @Path("genre") genre: String,
        @Path("page") page: Int
    ): String

    /** Früher /neuesten (404) → korrekter Pfad /neue-episoden. */
    @GET("/neue-episoden")
    suspend fun newest(): String

    /** Alias für Kompatibilität. */
    @GET("/neue-episoden")
    suspend fun neueEpisoden(): String

    /** Früher /beliebte (404) → korrekter Pfad /beliebte-serien. */
    @GET("/beliebte-serien")
    suspend fun popular(): String

    @GET("/beliebte-serien")
    suspend fun beliebteSerien(): String

    @GET("/serien")
    suspend fun catalog(): String

    @GET("/serien/page/{page}")
    suspend fun catalogPaged(@Path("page") page: Int): String

    @GET("/serienkalender")
    suspend fun calendar(): String

    @GET("{path}")
    suspend fun raw(@Path(value = "path", encoded = true) path: String): String
}
