package com.novastream.app.data.meta

import com.novastream.app.data.db.ContentDao
import com.novastream.app.data.db.ContentEntity
import com.novastream.app.data.model.Series
import com.novastream.app.data.provider.ProviderRegistry

/** Cross-provider slug resolution via persisted content_mapping rows. */
object ContentMappingResolver {

    data class AlsoOnEntry(
        val providerId: String,
        val slug: String,
        val displayName: String
    )

    suspend fun alsoOnEntries(
        dao: ContentDao,
        enrichment: MetaEnrichment,
        excludeProviderId: String
    ): List<AlsoOnEntry> {
        val ids = enrichment.externalIds
        val show = enrichment.show
        val byKey = enrichment.canonicalKey?.let {
            dao.findByCanonicalKeyExcluding(it, excludeProviderId)
        }.orEmpty()
        val byCross = dao.findRelatedExcluding(
            excludeProviderId = excludeProviderId,
            imdbId = ids.imdbId ?: show.imdbId,
            tvmazeId = ids.tvmazeId ?: show.tvmazeId,
            anilistId = ids.anilistId ?: show.anilistId,
            wikidataId = ids.wikidataId ?: show.wikidataId
        )
        return (byKey + byCross)
            .distinctBy { "${it.providerId}:${it.slug}" }
            .mapNotNull { entity -> toAlsoOnEntry(entity) }
    }

    suspend fun resolveProviderSlug(
        dao: ContentDao,
        target: Series,
        providerId: String,
        searchByTitle: suspend (String) -> Series?
    ): String? {
        if (isProviderSlug(target.id)) return target.id
        target.canonicalKey?.let { key ->
            dao.findByCanonicalKeyAndProvider(key, providerId)?.slug?.let { return it }
        }
        target.imdbId?.let { dao.findByImdbAndProvider(it, providerId)?.slug?.let { return it } }
        target.tvmazeId?.let { dao.findByTvmazeAndProvider(it, providerId)?.slug?.let { return it } }
        target.anilistId?.let { dao.findByAnilistAndProvider(it, providerId)?.slug?.let { return it } }
        target.anilistId ?: target.id.removePrefix("anilist-").toIntOrNull()?.let { id ->
            dao.findByAnilistAndProvider(id, providerId)?.slug?.let { return it }
        }
        return searchByTitle(target.title)?.id
    }

    fun isProviderSlug(id: String): Boolean =
        !id.startsWith("anilist-") &&
            !id.startsWith("mal-") &&
            !id.startsWith("kitsu-") &&
            !id.startsWith("shikimori-") &&
            !id.startsWith("wikidata-") &&
            id != "free-meta"

    private fun toAlsoOnEntry(entity: ContentEntity): AlsoOnEntry? {
        val name = ProviderRegistry.getProviderOrNull(entity.providerId)?.displayName
            ?: return null
        return AlsoOnEntry(
            providerId = entity.providerId,
            slug = entity.slug,
            displayName = name
        )
    }
}
