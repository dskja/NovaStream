package com.novastream.app.data.scraper

/**
 * Konfiguration für den universellen HTML-Scraper.
 * Ermöglicht Provider-agnostisches Parsen über CSS-Selektoren.
 */
data class SiteProfile(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val homePath: String = "/",
    val searchPath: String = "/search?q={query}",
    val searchMethod: String = "GET", // GET | POST
    val searchPostField: String? = null,
    val seriesLinkSelector: String = "a[href]",
    val seriesLinkPattern: String = "",
    val titleSelector: String = "",
    val coverSelector: String = "img[data-src], img[src]",
    val detailTitleSelector: String = "h1",
    val detailDescriptionSelector: String = ".description, .plot, .overview, [itemprop=description], meta[name=description]",
    val detailCoverSelector: String = "img[data-src], img[src]",
    val seasonLinkSelector: String = "a[href*=season], a[href*=staffel], select option",
    val episodeLinkSelector: String = "a[href*=episode], a[href*=folge]",
    val episodeLinkPattern: String = "",
    val hosterSelector: String = "iframe[src], a[href*=embed], a[href*=player], [data-play-url], [data-src]",
    val genrePathTemplate: String = "/genre/{genre}",
    val supportsSeries: Boolean = true,
    val isMovieFocused: Boolean = false,
    /** Wenn gesetzt: Slugs aus URL-Gruppe 1 extrahieren. */
    val slugRegex: String = "",
    val absoluteLinkPrefix: String = ""
)

/** Vorgefertigte Profile für FMHY-/Streaming-Sites. */
object SiteProfiles {

    val hydraHd = SiteProfile(
        id = "hydrahd",
        displayName = "HydraHD",
        baseUrl = "https://hydrahd.com",
        homePath = "/series/",
        searchPath = "/index.php?s={query}",
        seriesLinkSelector = "a[href*=/watchseries/], a[href*=/movie/]",
        seriesLinkPattern = "/(?:watchseries|movie)/([\\w-]+)",
        slugRegex = "/(?:watchseries|movie)/([\\w-]+)",
        detailTitleSelector = "h1, .film-name, title",
        detailDescriptionSelector = ".description, .film-description, .plot-text, meta[name=description]",
        episodeLinkSelector = "a[href*=episode], a[href*=-ep-], .episodes-ul a",
        hosterSelector = "iframe[src], .watch_block iframe, #watch iframe, a[data-video], [data-link]"
    )

    val cinezo = SiteProfile(
        id = "cinezo",
        displayName = "Cinezo",
        baseUrl = "https://cinezo.org",
        homePath = "/",
        searchPath = "/search?q={query}",
        seriesLinkSelector = "a[href^=/tv/], a[href^=/movie/]",
        seriesLinkPattern = "/(tv|movie)/(\\d+)",
        slugRegex = "/(tv|movie)/(\\d+)",
        detailTitleSelector = "h1",
        detailDescriptionSelector = ".overview, .description, meta[name=description]",
        episodeLinkSelector = "a[href*=/season/], button[data-season], [data-episode]",
        hosterSelector = "iframe[src], [data-src]"
    )

    val showsSt = SiteProfile(
        id = "showsst",
        displayName = "Shows.st",
        baseUrl = "https://shows.st",
        homePath = "/",
        searchPath = "/search?q={query}",
        seriesLinkSelector = "a[href*=/watch/tv/], a[href*=/watch/movie/]",
        seriesLinkPattern = "/watch/(tv|movie)/(\\d+)",
        slugRegex = "/watch/(tv|movie)/(\\d+)",
        detailTitleSelector = "h1, title",
        hosterSelector = "iframe[src]"
    )

    val phantomFlix = SiteProfile(
        id = "phantomflix",
        displayName = "PhantomFlix",
        baseUrl = "https://phantomflix.net",
        homePath = "/",
        searchPath = "/search?q={query}",
        seriesLinkSelector = "a[href*=/watch/], a[href*=/movie/], a[href*=/tv/]",
        seriesLinkPattern = "/(?:watch|movie|tv)/([\\w-]+)",
        slugRegex = "/(?:watch|movie|tv)/([\\w-]+)",
        hosterSelector = "iframe[src]"
    )

    val flixer = SiteProfile(
        id = "flixer",
        displayName = "Flixer",
        baseUrl = "https://flixer.su",
        homePath = "/shows",
        searchPath = "/search?q={query}",
        seriesLinkSelector = "a[href*=/show/], a[href*=/movie/], a[href*=/tv/]",
        seriesLinkPattern = "/(?:show|movie|tv)/([\\w-]+)",
        slugRegex = "/(?:show|movie|tv)/([\\w-]+)",
        hosterSelector = "iframe[src]"
    )

    val dramaCool = SiteProfile(
        id = "dramacool",
        displayName = "DramaCool",
        baseUrl = "https://dramacoole.buzz",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=-episode-], a[href*=-full-movie], h3 a, .list-episode-item a",
        seriesLinkPattern = "https?://[^/]+/([\\w-]+)/?",
        slugRegex = "https?://[^/]+/([\\w-]+?)(?:-episode-\\d+|-full-movie)?/?",
        detailTitleSelector = "h1, .title",
        episodeLinkSelector = "ul.list-episode-item-wrap.all-episode li a, a[href*=-episode-]",
        hosterSelector = "iframe[src], .watch_block iframe, #watch iframe, .anime_muti_link a"
    )

    val pressPlay = SiteProfile(
        id = "pressplay",
        displayName = "PressPlay",
        baseUrl = "https://pressplay.top",
        homePath = "/",
        searchPath = "/search?q={query}",
        seriesLinkSelector = "a[href*=/movie/], a[href*=/tv/], a[href*=/watch/]",
        seriesLinkPattern = "/(?:movie|tv|watch)/([\\w-]+)",
        slugRegex = "/(?:movie|tv|watch)/([\\w-]+)",
        hosterSelector = "iframe[src]"
    )

    val all: List<SiteProfile> = listOf(
        hydraHd, cinezo, showsSt, phantomFlix, flixer, dramaCool, pressPlay
    )
}
