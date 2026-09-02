package com.novastream.app.data.provider

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.domainDataStore by preferencesDataStore("provider_domains")

object ProviderDomainManager {

    private val domainPatterns: Map<String, List<String>> = mapOf(
        "serienstream" to listOf(
            "http://186.2.175.5",
            "https://serienstream.to",
            "https://s.to",
            "https://serienstream.cx"
        ),
        "serienstream_cx" to listOf(
            "https://serienstream.cx",
            "http://186.2.175.5",
            "https://serienstream.to",
            "https://s.to"
        ),
        "wiflix" to listOf(
            "https://wiflix.voto/",
            "https://flemmix.team/",
            "https://ww1.wiflix-adresses.fun/",
            "https://wiflix.top/"
        ),
        "frenchstream" to listOf(
            "https://fs16.lol/",
            "https://fstream.info/",
            "https://french-stream.lol/",
            "https://fs17.lol/"
        ),
        "cuevana3" to listOf(
            "https://www3.cuevana3.ai/",
            "https://cuevana3.io/",
            "https://cuevana3.me/",
            "https://www.cuevana3.ch/"
        ),
        "frembed" to listOf(
            "https://frembed.casa/",
            "https://audin213.com/",
            "https://frembed.life/"
        ),
        "megakino" to listOf(
            "https://megakino12.com",
            "https://megakino17.com",
            "https://megakino6.com",
            "https://megakino.ms",
            "https://megakino1.org"
        ),
        "burningseries" to listOf(
            "https://burningseries.cx",
            "https://bs.to",
            "https://burningseries.ac"
        ),
        "aniworld" to listOf(
            "https://aniworld.to",
            "https://aniworld.gg",
            "https://aniworld.nu"
        ),
        "kinoger" to listOf(
            "https://kinoger.to",
            "https://kinoger.com",
            "https://kinoger.pw"
        ),
        "streamkiste" to listOf(
            "https://stream-kiste.de",
            "https://streamkiste.tv",
            "https://streamkiste.xyz"
        ),
        "filmpalast" to listOf(
            "https://filmpalast.to",
            "https://filmpalast.cx",
            "https://filmpalast.io"
        ),
        "kinoz" to listOf(
            "https://kinoz.to",
            "https://kinos.to",
            "https://kinoz.tv"
        ),
        "sflix" to listOf(
            "https://sflix.to/",
            "https://sflix.se/",
            "https://sflix2.to/"
        ),
        "dramacool" to listOf(
            "https://dramacoole.buzz/",
            "https://dramacool9.co/",
            "https://dramacool.com.de/"
        ),
        "fanpelis" to listOf(
            "https://fanpelis.to/",
            "https://fanpelis.net/",
            "https://fanpelis.tv/"
        ),
        "pelisplusto" to listOf(
            "https://pelisplus.to/",
            "https://pelisplus.lat/",
            "https://pelisplushd.net/"
        ),
        "doramasflix" to listOf(
            "https://doramasflix.in/",
            "https://doramasflix.co/",
            "https://doramasflix.com/"
        ),
        "guardaserie" to listOf(
            "https://guardoserie.study/",
            "https://guardoserie.one/",
            "https://guardoserie.life/"
        ),
        "cb01" to listOf(
            "https://cb01official.uno/",
            "https://cb01.red/",
            "https://cb01.uno/"
        ),
        "altadefinizione01" to listOf(
            "https://altadefinizione-01.fun/",
            "https://altadefinizione01.boo/",
            "https://altadefinizione01.cafe/"
        ),
        "streamingcommunity_it" to listOf(
            "https://streamingunity.cc/",
            "https://streamingcommunity.cafe/",
            "https://streamingcommunity.gratis/"
        ),
        "streamingcommunity_en" to listOf(
            "https://streamingunity.cc/",
            "https://streamingcommunity.cafe/",
            "https://streamingcommunity.gratis/"
        ),
        "filmyonline" to listOf(
            "https://filmyonline.eu/",
            "https://filmyonline.pl/",
            "https://filmyonline.cc/"
        ),
        "zaluknij" to listOf(
            "https://zalukaj.pl/",
            "https://zaluknij.cc/",
            "https://zalukaj-film.pl/"
        ),
        "lookmovie2" to listOf(
            "https://lookmovie2.la/",
            "https://www.lookmovie2.to/",
            "https://lookmovie2.site/"
        ),
        "soap2day" to listOf(
            "https://soap2day.rs/",
            "https://soap2day.to/",
            "https://soap2day.ac/"
        ),
        "pelisflix" to listOf(
            "https://pelisflix20.biz/",
            "https://pelisflix2.io/",
            "https://pelisflix.lat/"
        ),
        "hdfilme" to listOf(
            "https://hdfilme.win/",
            "https://hdfilme.cx/",
            "https://hdfilme.top/"
        ),
        "moflix" to listOf(
            "https://moflix-stream.xyz/",
            "https://moflix.to/",
            "https://moflix.net/"
        ),
        "voirfilms" to listOf(
            "https://voirfilms.ws/",
            "https://voirfilms.io/",
            "https://voirfilms.co/"
        ),
        "nekosama" to listOf(
            "https://neko-sama.fr/",
            "https://neko-sama.net/",
            "https://neko-sama.tv/"
        ),
        "animeflv" to listOf(
            "https://www3.animeflv.net/",
            "https://animeflv.ru/",
            "https://animeflv.one/"
        ),
        "jkanime" to listOf(
            "https://jkanime.net/",
            "https://jkanime.bz/",
            "https://jkanime.su/"
        ),
        "ridomovies" to listOf(
            "https://ridomovies.su/",
            "https://ridomovies.tv/",
            "https://ridomovies.net/"
        ),
        "flixer" to listOf(
            "https://flixer.su/",
            "https://flixer.sh/",
            "https://flixer.it/"
        ),
        "hianime" to listOf(
            "https://hianime.to/",
            "https://hianime.nz/",
            "https://hianime.bz/"
        ),
        "animeworld" to listOf(
            "https://www.animeworld.ac/",
            "https://animeworld.tv/",
            "https://www.animeworld.so/"
        ),
        "anikoto" to listOf(
            "https://anikototv.to/",
            "https://anikoto.tv/",
            "https://anikoto.to/"
        ),
        "animeunity" to listOf(
            "https://www.animeunity.so/",
            "https://animeunity.to/",
            "https://animeunity.cc/"
        ),
        "hydrahd" to listOf(
            "https://hydrahd.cc/",
            "https://hydrahd.com/",
            "https://hydrahd.sh/"
        ),
        "cinezo" to listOf(
            "https://cinezo.net/",
            "https://cinezo.to/",
            "https://cinezo.tv/"
        ),
        "showsst" to listOf(
            "https://shows.st/",
            "https://showsst.com/",
            "https://showsst.to/"
        ),
        "phantomflix" to listOf(
            "https://phantomflix.net/",
            "https://phantomflix.co/",
            "https://phantomflix.tv/"
        ),
        "pressplay" to listOf(
            "https://pressplay.top/",
            "https://pressplay.cam/",
            "https://pressplay.store/"
        ),
        "einschalten" to listOf(
            "https://einschalten.in/",
            "https://einschalten.to/",
            "https://einschalten.cx/"
        ),
        "frenchanime" to listOf(
            "https://french-anime.com/",
            "https://french-streaming.com/",
            "https://french-anime.tv/"
        ),
        "animefenix" to listOf(
            "https://www.animefenix.tv/",
            "https://animefenix.com/",
            "https://animefenix2.com/"
        ),
        "tioanime" to listOf(
            "https://tioanime.com/",
            "https://tioanime.org/",
            "https://tioanime.net/"
        ),
        "otakufr" to listOf(
            "https://otakufr.cc/",
            "https://otakufr.tv/",
            "https://otakufr.net/"
        ),
        "latanime" to listOf(
            "https://latanime.org/",
            "https://latanime.com/",
            "https://latanime.net/"
        ),
        "seriesflix" to listOf(
            "https://seriesflix.store/",
            "https://seriesflix.tv/",
            "https://seriesflix.net/"
        ),
        "anymovie" to listOf(
            "https://anymovie.cc/",
            "https://anymovie.to/",
            "https://anymovie.net/"
        ),
        "flixlatam" to listOf(
            "https://flixlatam.com/",
            "https://flixlatam.tv/",
            "https://flixlatam.net/"
        ),
        "filmpertutti" to listOf(
            "https://filmpertutti.asia/",
            "https://filmpertutti.io/",
            "https://filmpertutti.net/"
        ),
        "cineblog01" to listOf(
            "https://cineblog01.hair/",
            "https://cineblog01.vin/",
            "https://cineblog01.bond/"
        ),
        "guardaflix" to listOf(
            "https://guardaflix.blog/",
            "https://guardaflix.net/",
            "https://guardaflix.tv/"
        ),
        "mkvmovies" to listOf(
            "https://mkvmoviespoint.casa/",
            "https://mkvmoviespoint.com/",
            "https://mkvmoviespoint.me/"
        ),
        "mkissa" to listOf(
            "https://mkissa.com/",
            "https://mkissa.net/",
            "https://mkissa.tv/"
        ),
        "cinecalidad" to listOf(
            "https://www.cinecalidad.ec/",
            "https://cinecalidad.la/",
            "https://cinecalidad.rs/"
        )
    )

    private fun key(providerId: String) = stringPreferencesKey("domain_$providerId")

    suspend fun getResolvedBaseUrl(context: Context, providerId: String, fallback: String): String {
        val stored = context.domainDataStore.data.map { it[key(providerId)] }.first()
        if (!stored.isNullOrBlank()) return stored
        return domainPatterns[providerId]?.firstOrNull() ?: fallback
    }

    suspend fun setResolvedBaseUrl(context: Context, providerId: String, url: String) {
        context.domainDataStore.edit { it[key(providerId)] = url.trimEnd('/') }
    }

    fun alternateDomains(providerId: String): List<String> =
        domainPatterns[providerId].orEmpty()
}
