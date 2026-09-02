package com.novastream.app.data.meta

import com.novastream.app.data.provider.ContentLanguage
import com.novastream.app.data.provider.ContentRegionResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Unified age-rating lookup across free no-key sources:
 * Wikidata (FSK, MPAA, BBFC, …), Wikipedia infobox, AniList, Jikan (MAL).
 */
object AgeRatingService {

    suspend fun resolve(
        title: String,
        isMovie: Boolean = false,
        language: ContentLanguage = ContentLanguage.DE,
        imdbId: String? = null,
        wikidataId: String? = null,
        anilistIsAdult: Boolean? = null,
        idMal: Int? = null,
        scrapedIsAdult: Boolean? = null
    ): AgeRatingResult = withContext(Dispatchers.IO) {
        val langTag = ContentRegionResolver.wikidataLanguageFor(language)
        val certifications = mutableListOf<String>()
        var source: String? = null

        val wikidataCerts = WikidataMetaService.resolveAgeRatingLabels(
            imdbId = imdbId,
            entityId = wikidataId,
            title = if (imdbId.isNullOrBlank() && wikidataId.isNullOrBlank()) title else null,
            language = langTag
        )
        if (wikidataCerts.isNotEmpty()) {
            certifications.addAll(wikidataCerts)
            source = "wikidata"
        }

        if (certifications.isEmpty() && title.isNotBlank()) {
            val wikiCerts = WikipediaAgeRatingService.lookup(
                title = title,
                languages = listOf(langTag, "en").distinct()
            )
            if (wikiCerts.isNotEmpty()) {
                certifications.addAll(wikiCerts)
                source = "wikipedia"
            }
        }

        var jikanAdult: Boolean? = null
        if (idMal != null && idMal > 0) {
            val malRating = JikanAgeRatingService.lookup(idMal)
            if (!malRating.isNullOrBlank()) {
                certifications.add(malRating)
                jikanAdult = JikanAgeRatingService.isAdultFromRating(malRating)
                if (source == null) source = "jikan"
            }
        }

        val explicitAdult = AgeRatingResolver.merge(anilistIsAdult, jikanAdult)
        val resolved = AgeRatingResolver.resolve(
            scraped = scrapedIsAdult,
            explicitAdult = explicitAdult,
            certifications = certifications
        )
        resolved.copy(source = source ?: resolved.source)
    }
}
