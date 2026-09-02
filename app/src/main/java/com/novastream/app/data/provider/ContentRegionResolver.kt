package com.novastream.app.data.provider

import com.novastream.app.util.AppContext
import com.novastream.app.util.PrefsCache

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
        return tvmazeRegionFor(
            ContentLanguage.fromTag(PrefsCache.contentLanguage(ctx))
        )
    }

    fun currentContentLanguage(): ContentLanguage {
        val ctx = AppContext.getOrNull() ?: return ContentLanguage.DE
        return ContentLanguage.fromTag(PrefsCache.contentLanguage(ctx))
    }
}
