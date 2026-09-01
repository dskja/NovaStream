package com.novastream.app.data.scraper

/** International / v9 SiteProfile definitions (ported from BetterStreamflix). */
object InternationalSiteProfiles {

    val sflix = SiteProfile(
        id = "sflix",
        displayName = "SFlix",
        baseUrl = "https://sflix.to",
        homePath = "/home",
        searchPath = "/search/{query}",
        seriesLinkSelector = "a[href*=/tv-show/], a[href*=/movie/]",
        seriesLinkPattern = "/(?:tv-show|movie)/([\\w-]+)",
        slugRegex = "/(?:tv-show|movie)/([\\w-]+)",
        hosterSelector = "iframe[src], a[data-id]",
        catalogPageTemplate = "/home?page={page}",
        moviePath = "/movie/"
    )

    val ridomovies = SiteProfile(
        id = "ridomovies",
        displayName = "Ridomovies",
        baseUrl = "https://ridomovies.su",
        homePath = "/home-rd1",
        searchPath = "/search?q={query}",
        seriesLinkSelector = "a[href*=/tv/], a[href*=/movie/]",
        seriesLinkPattern = "/(?:tv|movie)/([\\w-]+)",
        slugRegex = "/(?:tv|movie)/([\\w-]+)",
        hosterSelector = "iframe[src], [data-server-embed], #player-cover[data-embed]"
    )

    val anikoto = SiteProfile(
        id = "anikoto",
        displayName = "Anikoto",
        baseUrl = "https://anikototv.to",
        homePath = "/home",
        searchPath = "/filter?keyword={query}",
        seriesLinkSelector = "a[href*=/watch/]",
        seriesLinkPattern = "/watch/([\\w-]+)",
        slugRegex = "/watch/([\\w-]+)",
        hosterSelector = "iframe[src], [data-link-id]"
    )

    val hdfilme = SiteProfile(
        id = "hdfilme",
        displayName = "HDFilme",
        baseUrl = "https://hdfilme.win",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=stream], a[href*=serien/]",
        seriesLinkPattern = "([\\w-]+-stream(?:ing)?-stream\\.html)",
        slugRegex = "([\\w-]+-stream(?:ing)?-stream\\.html)",
        hosterSelector = "iframe[src], li[data-link], a[data-link]",
        moviePath = "/filme1/"
    )

    val einschalten = SiteProfile(
        id = "einschalten",
        displayName = "Einschalten",
        baseUrl = "https://einschalten.in",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=/movie/], a[href*=/film/]",
        seriesLinkPattern = "/(?:movie|film)/([\\w-]+)",
        slugRegex = "/(?:movie|film)/([\\w-]+)",
        hosterSelector = "iframe[src], video source",
        supportsSeries = false,
        isMovieFocused = true
    )

    val wiflix = SiteProfile(
        id = "wiflix",
        displayName = "Wiflix",
        baseUrl = "https://flemmix.team",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=serie-en-streaming/], a[href*=vf/]",
        seriesLinkPattern = "serie-en-streaming/([\\w-]+)",
        slugRegex = "serie-en-streaming/([\\w-]+)",
        hosterSelector = "iframe[src], div.blocvostfr a, div.blocfr a"
    )

    val frenchStream = SiteProfile(
        id = "frenchstream",
        displayName = "FrenchStream",
        baseUrl = "https://fs16.lol",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=s-tv/], a[href*=-saison-]",
        seriesLinkPattern = "s-tv/([\\w-]+)",
        slugRegex = "s-tv/([\\w-]+)",
        hosterSelector = "iframe[src], a[href*=film_api]"
    )

    val frenchAnime = SiteProfile(
        id = "frenchanime",
        displayName = "FrenchAnime",
        baseUrl = "https://french-anime.com",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=animes-vf/], a[href*=animes-vostfr/], a[href*=.html]",
        seriesLinkPattern = "animes-(?:vf|vostfr)/([\\w-]+)",
        slugRegex = "([\\w-]+)\\.html",
        hosterSelector = "iframe[src]"
    )

    val frembed = SiteProfile(
        id = "frembed",
        displayName = "Frembed",
        baseUrl = "https://frembed.casa",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=/tv-show/], a[href*=/movie/]",
        seriesLinkPattern = "/(?:tv-show|movie)/([\\w-]+)",
        slugRegex = "/(?:tv-show|movie)/([\\w-]+)",
        hosterSelector = "iframe[src]"
    )

    val fanpelis = SiteProfile(
        id = "fanpelis",
        displayName = "Fanpelis",
        baseUrl = "https://fanpelis.to",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=/tvshows/], a[href*=/movies/], a[href*=/animes/]",
        seriesLinkPattern = "/(?:tvshows|movies|animes)/([\\w-]+)",
        slugRegex = "/(?:tvshows|movies|animes)/([\\w-]+)",
        hosterSelector = "iframe[src]"
    )

    val animeFlv = SiteProfile(
        id = "animeflv",
        displayName = "AnimeFLV",
        baseUrl = "https://www3.animeflv.net",
        homePath = "/",
        searchPath = "/browse?q={query}",
        seriesLinkSelector = "a[href*=/anime/]",
        seriesLinkPattern = "/anime/([\\w-]+)",
        slugRegex = "/anime/([\\w-]+)",
        hosterSelector = "iframe[src]"
    )

    val jkAnime = SiteProfile(
        id = "jkanime",
        displayName = "JKAnime",
        baseUrl = "https://jkanime.net",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=jkanime.net/]",
        seriesLinkPattern = "jkanime\\.net/([\\w-]+)",
        slugRegex = "jkanime\\.net/([\\w-]+)",
        hosterSelector = "iframe[src]"
    )

    val pelisplusto = SiteProfile(
        id = "pelisplusto",
        displayName = "Pelisplusto",
        baseUrl = "https://pelisplus.to",
        homePath = "/",
        searchPath = "/search/{query}",
        seriesLinkSelector = "a[href*=/serie/], a[href*=/anime/]",
        seriesLinkPattern = "/(?:serie|anime)/([\\w-]+)",
        slugRegex = "/(?:serie|anime)/([\\w-]+)",
        hosterSelector = "iframe[src], li[data-server]"
    )

    val doramasflix = SiteProfile(
        id = "doramasflix",
        displayName = "Doramasflix",
        baseUrl = "https://doramasflix.in",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=doramas-online/]",
        seriesLinkPattern = "doramas-online/([\\w-]+)",
        slugRegex = "doramas-online/([\\w-]+)",
        hosterSelector = "iframe[src]"
    )

    val guardaSerie = SiteProfile(
        id = "guardaserie",
        displayName = "GuardaSerie",
        baseUrl = "https://guardoserie.study",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=/serie/]",
        seriesLinkPattern = "/serie/([\\w-]+)",
        slugRegex = "/serie/([\\w-]+)",
        hosterSelector = "iframe[src], iframe[data-src]"
    )

    val cb01 = SiteProfile(
        id = "cb01",
        displayName = "CB01",
        baseUrl = "https://cb01official.uno",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=serietv/], a[href*=.html]",
        seriesLinkPattern = "serietv/([\\w-]+)",
        slugRegex = "([\\w-]+)\\.html",
        hosterSelector = "iframe[src], table.cbtable a[href]"
    )

    val altadefinizione01 = SiteProfile(
        id = "altadefinizione01",
        displayName = "Altadefinizione01",
        baseUrl = "https://altadefinizione-01.fun",
        homePath = "/",
        searchPath = "/index.php?do=search&subaction=search&story={query}",
        seriesLinkSelector = "a[href*=serie-tv/]",
        seriesLinkPattern = "serie-tv/([\\w-]+)",
        slugRegex = "serie-tv/([\\w-]+)",
        hosterSelector = "iframe[src], li[data-link], a[data-link]"
    )

    val animeUnity = SiteProfile(
        id = "animeunity",
        displayName = "AnimeUnity",
        baseUrl = "https://www.animeunity.so",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=/anime/]",
        seriesLinkPattern = "/anime/([\\w-]+)",
        slugRegex = "/anime/([\\w-]+-\\d+|\\d+-[\\w-]+)",
        hosterSelector = "iframe[src], video-player[embed_url]"
    )

    val streamingCommunityIt = SiteProfile(
        id = "streamingcommunity_it",
        displayName = "StreamingCommunity",
        baseUrl = "https://streamingunity.cc",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=/title/], a[href*=/watch/]",
        seriesLinkPattern = "/(?:title|watch)/([\\w-]+)",
        slugRegex = "/(?:title|watch)/([\\w-]+)",
        hosterSelector = "iframe[src]"
    )

    val streamingCommunityEn = SiteProfile(
        id = "streamingcommunity_en",
        displayName = "StreamingCommunity EN",
        baseUrl = "https://streamingunity.cc",
        homePath = "/en/",
        searchPath = "/en/?s={query}",
        seriesLinkSelector = "a[href*=/title/], a[href*=/watch/]",
        seriesLinkPattern = "/(?:title|watch)/([\\w-]+)",
        slugRegex = "/(?:title|watch)/([\\w-]+)",
        hosterSelector = "iframe[src]"
    )

    val filmyOnline = SiteProfile(
        id = "filmyonline",
        displayName = "FilmyOnline",
        baseUrl = "https://filmyonline.cc",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=/titles/]",
        seriesLinkPattern = "/titles/([\\d]+)",
        slugRegex = "/titles/([\\d]+)",
        hosterSelector = "iframe[src]"
    )

    val zaluknij = SiteProfile(
        id = "zaluknij",
        displayName = "Zaluknij",
        baseUrl = "https://zaluknij.cc",
        homePath = "/",
        searchPath = "/wyszukiwarka?phrase={query}",
        seriesLinkSelector = "a[href*=/serial-online/]",
        seriesLinkPattern = "/serial-online/([\\w-]+)",
        slugRegex = "/serial-online/([\\w-]+)",
        hosterSelector = "iframe[src], a[data-iframe]"
    )

    val mkissa = SiteProfile(
        id = "mkissa",
        displayName = "Mkissa",
        baseUrl = "https://mkissa.com",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=/movies/], a[href*=/series/]",
        seriesLinkPattern = "/(?:movies|series)/([\\w-]+)",
        slugRegex = "/(?:movies|series)/([\\w-]+)",
        hosterSelector = "iframe[src]"
    )

    val cuevana3 = SiteProfile(
        id = "cuevana3",
        displayName = "Cuevana3",
        baseUrl = "https://www3.cuevana3.ai",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=/serie/], a[href*=/pelicula/]",
        seriesLinkPattern = "/(?:serie|pelicula)/([\\w-]+)",
        slugRegex = "/(?:serie|pelicula)/([\\w-]+)",
        hosterSelector = "iframe[src], li[data-tr]"
    )

    val moflix = SiteProfile(
        id = "moflix",
        displayName = "Moflix",
        baseUrl = "https://moflix-stream.xyz",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=stream/]",
        seriesLinkPattern = "stream/([\\w-]+)",
        slugRegex = "stream/([\\w-]+)",
        hosterSelector = "iframe[src]"
    )

    val animeworld = SiteProfile(
        id = "animeworld",
        displayName = "AnimeWorld",
        baseUrl = "https://www.animeworld.ac",
        homePath = "/",
        searchPath = "/search?q={query}",
        seriesLinkSelector = "a[href*=/play/]",
        seriesLinkPattern = "/play/([\\w.-]+)",
        slugRegex = "/play/([\\w.-]+)",
        hosterSelector = "iframe[src], video source"
    )

    val lookmovie2 = SiteProfile(
        id = "lookmovie2",
        displayName = "LookMovie2",
        baseUrl = "https://www.lookmovie2.to",
        homePath = "/",
        searchPath = "/search?q={query}",
        seriesLinkSelector = "a[href*=/movies/], a[href*=/shows/]",
        seriesLinkPattern = "/(?:movies|shows)/([\\w-]+)",
        slugRegex = "/(?:movies|shows)/([\\w-]+)",
        hosterSelector = "iframe[src]"
    )

    val pelisflix = SiteProfile(
        id = "pelisflix",
        displayName = "Pelisflix",
        baseUrl = "https://pelisflix20.biz",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=/pelicula/], a[href*=/serie/]",
        seriesLinkPattern = "/(?:pelicula|serie)/([\\w-]+)",
        slugRegex = "/(?:pelicula|serie)/([\\w-]+)",
        hosterSelector = "iframe[src]"
    )

    val filmpertutti = SiteProfile(
        id = "filmpertutti",
        displayName = "FilmPerTutti",
        baseUrl = "https://filmpertutti.asia",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=film/]",
        seriesLinkPattern = "film/([\\w-]+)",
        slugRegex = "film/([\\w-]+)",
        hosterSelector = "iframe[src]"
    )

    val cineblog01 = SiteProfile(
        id = "cineblog01",
        displayName = "Cineblog01",
        baseUrl = "https://cineblog01.hair",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=serietv/]",
        seriesLinkPattern = "serietv/([\\w-]+)",
        slugRegex = "serietv/([\\w-]+)",
        hosterSelector = "iframe[src]"
    )

    val voirfilms = SiteProfile(
        id = "voirfilms",
        displayName = "VoirFilms",
        baseUrl = "https://voirfilms.ws",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=film/]",
        seriesLinkPattern = "film/([\\w-]+)",
        slugRegex = "film/([\\w-]+)",
        hosterSelector = "iframe[src]"
    )

    val nekoSama = SiteProfile(
        id = "nekosama",
        displayName = "Neko-Sama",
        baseUrl = "https://neko-sama.fr",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=/anime/]",
        seriesLinkPattern = "/anime/([\\w-]+)",
        slugRegex = "/anime/([\\w-]+)",
        hosterSelector = "iframe[src]"
    )

    val soap2day = SiteProfile(
        id = "soap2day",
        displayName = "Soap2Day",
        baseUrl = "https://soap2day.rs",
        homePath = "/",
        searchPath = "/search/{query}",
        seriesLinkSelector = "a[href*=/movie/], a[href*=/tv-show/]",
        seriesLinkPattern = "/(?:movie|tv-show)/([\\w-]+)",
        slugRegex = "/(?:movie|tv-show)/([\\w-]+)",
        hosterSelector = "iframe[src]"
    )

    val mkvMovies = SiteProfile(
        id = "mkvmovies",
        displayName = "MKVMovies",
        baseUrl = "https://mkvmoviespoint.casa",
        homePath = "/",
        searchPath = "/?s={query}",
        seriesLinkSelector = "a[href*=movies/]",
        seriesLinkPattern = "movies/([\\w-]+)",
        slugRegex = "movies/([\\w-]+)",
        hosterSelector = "iframe[src], a[href*=download]"
    )

    val all: List<SiteProfile> = listOf(
        sflix, ridomovies, anikoto, hdfilme, einschalten, wiflix, frenchStream,
        frenchAnime, frembed, fanpelis, animeFlv, jkAnime, pelisplusto, doramasflix,
        guardaSerie, cb01, altadefinizione01, animeUnity, streamingCommunityIt,
        streamingCommunityEn, filmyOnline, zaluknij, mkissa, cuevana3, moflix,
        animeworld, lookmovie2, pelisflix, filmpertutti, cineblog01, voirfilms,
        nekoSama, soap2day, mkvMovies
    )
}
