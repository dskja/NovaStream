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

    fun currentTvmazeRegion(): String = runBlocking {
        val settings = AppSettings(AppContext.get())
        tvmazeRegionFor(ContentLanguage.fromTag(settings.contentLanguage.first()))
    }

    fun currentContentLanguage(): ContentLanguage = runBlocking {
        val settings = AppSettings(AppContext.get())
        ContentLanguage.fromTag(settings.contentLanguage.first())
    }
}
