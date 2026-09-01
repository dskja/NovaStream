package com.novastream.app.data.scraper

import com.novastream.app.data.scraper.SiteProfiles.cinezo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalHtmlScraperTest {

    private fun loadFixture(name: String): String =
        javaClass.classLoader!!.getResource("html/$name")!!.readText()

    private val serienstreamProfile = SiteProfile(
        id = "serienstream",
        displayName = "SerienStream",
        baseUrl = "https://serienstream.to",
        seriesLinkSelector = "a[href*=/serie/]",
        seriesLinkPattern = "/serie/([\\w-]+)",
        slugRegex = "/serie/([\\w-]+)",
        titleSelector = "h2, h3, span",
        coverSelector = "img[src], img[data-src]"
    )

    private val streamkisteProfile = SiteProfile(
        id = "streamkiste",
        displayName = "StreamKiste",
        baseUrl = "https://stream-kiste.de",
        seriesLinkSelector = "a[href*=/serien/], a[href*=/filme/]",
        seriesLinkPattern = "/(?:serien|filme)/([\\w-]+)",
        slugRegex = "/(?:serien|filme)/([\\w-]+)",
        titleSelector = "span.title, h3, span",
        coverSelector = "img[src]",
        supportsMovies = true
    )

    @Test
    fun parseSeriesList_serienstream_extractsSlugsAndTitles() {
        val html = loadFixture("serienstream.html")
        val series = UniversalHtmlScraper.parseSeriesList(html, serienstreamProfile)
        val ids = series.map { it.id }
        assertTrue(ids.contains("dark"))
        assertTrue(ids.contains("stranger-things"))
        assertTrue(ids.contains("breaking-bad"))
        assertEquals("Dark", series.first { it.id == "dark" }.title)
    }

    @Test
    fun parseSeriesList_streamkiste_extractsSeriesAndMovies() {
        val html = loadFixture("streamkiste.html")
        val series = UniversalHtmlScraper.parseSeriesList(html, streamkisteProfile)
        val ids = series.map { it.id }
        assertTrue(ids.contains("game-of-thrones"))
        assertTrue(ids.contains("inception"))
    }

    @Test
    fun parseSeriesList_cinezo_usesSiteProfile() {
        val html = loadFixture("cinezo.html")
        val series = UniversalHtmlScraper.parseSeriesList(html, cinezo)
        assertEquals(3, series.size)
        assertTrue(series.any { it.id == "tv-1396" && it.title.contains("Breaking", ignoreCase = true) })
        assertTrue(series.any { it.id == "movie-550" && it.isMovie })
    }

    @Test
    fun parseSeriesList_burningSeries_extractsSerieLinks() {
        val html = loadFixture("burningseries.html")
        val profile = SiteProfile(
            id = "burningseries",
            displayName = "Burning Series",
            baseUrl = "https://bs.to",
            seriesLinkSelector = "a[href^=/serie/]",
            seriesLinkPattern = "/serie/([\\w-]+)",
            slugRegex = "/serie/([\\w-]+)",
            titleSelector = "h3, h2",
            coverSelector = "img[src], img[data-src]"
        )
        val series = UniversalHtmlScraper.parseSeriesList(html, profile)
        assertTrue(series.any { it.id == "game-of-thrones" })
        assertTrue(series.any { it.id == "breaking-bad" })
    }
}
