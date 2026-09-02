package com.novastream.app.data.provider

import com.novastream.app.data.prefs.AppSettings
import com.novastream.app.util.AppContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Maps [ContentLanguage] to TVMaze schedule country codes and Wikidata language tags. */
object ContentRegionResolver {

    fun tvmazeRegionFor(language: ContentLanguage): String = when (language) {
        ContentLanguage.DE -> "DE"
        ContentLanguage.FR -> "FR"
        ContentLanguage.ES -> "ES"
        ContentLanguage.IT -> "IT"
        ContentLanguage.PL -> "PL"
        ContentLanguage.AR -> "SA"
        ContentLanguage.EN, ContentLanguage.MULTI -> "US"
    }

    fun wikidataLanguageFor(language: ContentLanguage): String = when (language) {
        ContentLanguage.DE -> "de"
        ContentLanguage.EN -> "en"
        ContentLanguage.FR -> "fr"
        ContentLanguage.ES -> "es"
        ContentLanguage.IT -> "it"
        ContentLanguage.PL -> "pl"
        ContentLanguage.AR -> "ar"
        ContentLanguage.MULTI -> "en"
    }

    fun currentTvmazeRegion(): String {
        val ctx = AppContext.getOrNull() ?: return tvmazeRegionFor(ContentLanguage.DE)
        return try {
            val settings = AppSettings(ctx)
            runBlocking {
                tvmazeRegionFor(ContentLanguage.fromTag(settings.contentLanguage.first()))
            }
        } catch (_: Exception) {
            tvmazeRegionFor(ContentLanguage.DE)
        }
    }

    fun currentContentLanguage(): ContentLanguage {
        val ctx = AppContext.getOrNull() ?: return ContentLanguage.DE
        return try {
            val settings = AppSettings(ctx)
            runBlocking {
                ContentLanguage.fromTag(settings.contentLanguage.first())
            }
        } catch (_: Exception) {
            ContentLanguage.DE
        }
    }
}
