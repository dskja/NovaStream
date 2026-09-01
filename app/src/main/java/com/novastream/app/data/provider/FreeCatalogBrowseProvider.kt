package com.novastream.app.data.provider

import com.novastream.app.data.meta.FreeMetaGraph
import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.Genre
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.StreamSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Regional browse-only catalog via free metadata (TVMaze + AniList).
 * Playback is not supported — users switch to a streaming provider to watch.
 */
class FreeCatalogBrowseProvider(
    private val metaGraph: FreeMetaGraph = FreeMetaGraph(),
    private val language: ContentLanguage = ContentLanguage.MULTI,
    override val id: String = "freecatalogbrowse",
    override val displayName: String = "Free Catalog",
    override val baseUrl: String = "https://api.tvmaze.com",
    override val supportsSeries: Boolean = true
) : StreamingProvider {

    override val supportsMovies: Boolean = false
    override val catalogHint: String = "Trending TV & anime via free APIs"
    override val availableGenres: List<Genre> = ContentLanguageGenres.forLanguage(
        if (language == ContentLanguage.MULTI) ContentLanguage.EN else language
    )

    private fun effectiveLanguage(): ContentLanguage =
        if (language == ContentLanguage.MULTI) ContentRegionResolver.currentContentLanguage() else language

    override suspend fun loadHome(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        metaGraph.browseRegional(effectiveLanguage(), page = 0)
            .map { metaGraph.toSeries(it, id) }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun search(query: String): StreamingProvider.ProviderResult<List<Series>> {
        if (query.trim().isBlank()) return StreamingProvider.ProviderResult.Error("Leere Suche")
        return runCatching {
            metaGraph.search(query.trim(), preferAnime = false)
                .map { metaGraph.toSeries(it, id) }
        }.fold(
            onSuccess = { StreamingProvider.ProviderResult.Success(it) },
            onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
        )
    }

    override suspend fun loadSeriesDetail(slug: String): StreamingProvider.ProviderResult<Pair<Series, List<Season>>> =
        StreamingProvider.ProviderResult.Error("Browse only — open via a streaming provider to play")

    override suspend fun loadSeason(slug: String, season: Int): StreamingProvider.ProviderResult<List<Episode>> =
        StreamingProvider.ProviderResult.Error("Browse only")

    override suspend fun loadHosters(episode: Episode): StreamingProvider.ProviderResult<List<HosterLink>> =
        StreamingProvider.ProviderResult.Error("Browse only")

    override suspend fun resolveHoster(hoster: HosterLink): StreamingProvider.ProviderResult<List<StreamSource>> =
        StreamingProvider.ProviderResult.Error("Browse only")

    override suspend fun loadGenre(genre: String): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        coroutineScope {
            val lang = effectiveLanguage()
            val region = ContentRegionResolver.tvmazeRegionFor(lang)
            val scheduleDef = async {
                com.novastream.app.data.meta.FreeMetaService.schedule(region)
            }
            val searchDef = async {
                metaGraph.search(genre, preferAnime = genre.contains("anime", true))
            }
            (scheduleDef.await() + searchDef.await())
                .filter { show ->
                    show.genres.any {
                        it.equals(genre, ignoreCase = true) ||
                            it.contains(genre, ignoreCase = true) ||
                            genre.contains(it, ignoreCase = true)
                    }
                }
                .map { metaGraph.toSeries(it, id) }
        }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadNewest(): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        val region = ContentRegionResolver.tvmazeRegionFor(effectiveLanguage())
        com.novastream.app.data.meta.FreeMetaService.schedule(region)
            .map { metaGraph.toSeries(it, id) }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )

    override suspend fun loadPopular(): StreamingProvider.ProviderResult<List<Series>> = loadHome()

    override suspend fun loadCatalogPage(page: Int): StreamingProvider.ProviderResult<List<Series>> = runCatching {
        metaGraph.browseRegional(effectiveLanguage(), page = page.coerceAtLeast(0))
            .map { metaGraph.toSeries(it, id) }
    }.fold(
        onSuccess = { StreamingProvider.ProviderResult.Success(it) },
        onFailure = { StreamingProvider.ProviderResult.Error(com.novastream.app.util.ErrorMapper.toUserMessage(it), it) }
    )
}
