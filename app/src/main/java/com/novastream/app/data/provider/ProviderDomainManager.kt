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
        "wiflix" to listOf(
            "https://flemmix.team/",
            "https://ww1.wiflix-adresses.fun/",
            "https://wiflix.voto/",
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
            "https://megakino.ms",
            "https://megakino6.com",
            "https://megakino8.com",
            "https://megakino2.com",
            "https://megakino.how"
        ),
        "burningseries" to listOf(
            "https://burningseries.cx",
            "https://bs.to"
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
            "https://filmyonline.cc/",
            "https://filmyonline.pl/",
            "https://filmyonline.eu/"
        ),
        "zaluknij" to listOf(
            "https://zaluknij.cc/",
            "https://zalukaj.pl/",
            "https://zalukaj-film.pl/"
        ),
        "lookmovie2" to listOf(
            "https://www.lookmovie2.to/",
            "https://lookmovie2.la/",
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
