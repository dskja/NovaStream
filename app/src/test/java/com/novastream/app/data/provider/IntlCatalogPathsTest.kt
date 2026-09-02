package com.novastream.app.data.provider

import com.novastream.app.data.scraper.InternationalSiteProfiles
import com.novastream.app.data.scraper.SiteProfiles
import org.junit.Assert.assertTrue
import org.junit.Test

class IntlCatalogPathsTest {

    @Test
    fun catalogPaths_includesProfileHomeAndMoviePaths() {
        val paths = IntlCatalogPaths.catalogPaths("cinezo", SiteProfiles.cinezo)
        assertTrue(paths.contains("/"))
        assertTrue(paths.contains("/movie/"))
        assertTrue(paths.contains("/tv/"))
    }

    @Test
    fun catalogPaths_coversIntlProviders() {
        val paths = IntlCatalogPaths.catalogPaths("lookmovie2", InternationalSiteProfiles.lookmovie2)
        assertTrue(paths.any { it.contains("movie", ignoreCase = true) })
    }

    @Test
    fun catalogPaths_defaultsToRootWhenEmpty() {
        val profile = SiteProfiles.cinezo.copy(homePath = "", moviePath = "")
        val paths = IntlCatalogPaths.catalogPaths("unknown_provider", profile)
        assertTrue(paths.contains("/"))
    }
}
