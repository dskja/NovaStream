package com.novastream.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novastream.app.data.db.ContentDao
import com.novastream.app.data.db.ContentEntity
import com.novastream.app.data.db.WatchProgress
import com.novastream.app.data.meta.AgeRatingResolver
import com.novastream.app.data.meta.ExternalIds
import com.novastream.app.data.meta.FreeMetaGraph
import com.novastream.app.data.meta.FreeMetaService
import com.novastream.app.data.meta.MetaEnrichment
import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.Season
import com.novastream.app.data.model.Series
import com.novastream.app.data.model.StreamSource
import com.novastream.app.data.prefs.AppSettings
import com.novastream.app.data.provider.ActiveProvider
import com.novastream.app.data.provider.ContentLanguage
import com.novastream.app.data.provider.ProviderController
import com.novastream.app.data.provider.ProviderRegistry
import com.novastream.app.data.repository.NovaStreamRepository
import com.novastream.app.data.repository.NovaStreamRepository.RepoResult
import com.novastream.app.data.repository.WatchRepository
import com.novastream.app.download.DownloadForegroundService
import com.novastream.app.download.DownloadManagerHelper
import com.novastream.app.R
import com.novastream.app.profile.ProfileManager
import com.novastream.app.util.KidsContentFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = false,
    val series: Series? = null,
    val seasons: List<Season> = emptyList(),
    val selectedSeasonIndex: Int = 0,
    val loadingSeason: Boolean = false,
    val seasonError: String? = null,
    val error: String? = null,
    val inWatchlist: Boolean = false,
    val episodeProgress: Map<String, WatchProgress> = emptyMap(),
    val progressBySeasonEpisode: Map<String, WatchProgress> = emptyMap(),
    val currentProgress: WatchProgress? = null,
    val metaCast: List<com.novastream.app.data.meta.MetaPerson> = emptyList(),
    val metaRating: Double? = null,
    val metaNetwork: String? = null,
    val imdbId: String? = null,
    val trailerUrl: String? = null,
    val relatedTitles: List<Series> = emptyList(),
    val alsoOnProviders: List<String> = emptyList(),
    val loadedProviderId: String? = null,
    val providerMismatch: Boolean = false,
    val downloadMessage: String? = null,
    val downloading: Boolean = false,
    val casting: Boolean = false,
    val castStreamUrl: String? = null,
    val castStreamTitle: String? = null
) {
    val selectedSeason: Season?
        get() = seasons.getOrNull(selectedSeasonIndex)

    /** Count of watched episodes in the currently selected season. */
    val selectedSeasonWatchedCount: Int
        get() {
            val season = selectedSeason ?: return 0
            val seriesSlug = series?.id ?: return 0
            return season.episodes.count { ep ->
                progressBySeasonEpisode["${season.number}-${ep.number}"]?.let {
                    it.slug == seriesSlug && it.isCompleted
                } == true
            }
        }

    /** Total episode count across all seasons. */
    val totalEpisodeCount: Int
        get() = seasons.sumOf { it.episodes.size }

    /** Total watched episodes across all seasons. */
    val totalWatchedCount: Int
        get() {
            val seriesSlug = series?.id ?: return 0
            return seasons.sumOf { season ->
                season.episodes.count { ep ->
                    progressBySeasonEpisode["${season.number}-${ep.number}"]?.let {
                        it.slug == seriesSlug && it.isCompleted
                    } == true
                }
            }
        }

    /** True if any season has episodes loaded. */
    val hasEpisodes: Boolean
        get() = seasons.any { it.episodes.isNotEmpty() }

    fun progressFor(season: Int, episode: Int): WatchProgress? {
        val seriesSlug = series?.id ?: return null
        val pid = ActiveProvider.id
        return progressBySeasonEpisode["$season-$episode"]?.takeIf {
            it.slug == seriesSlug &&
                (it.providerId.isBlank() || it.providerId == pid || it.providerId == "unknown")
        }
    }
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val repo: NovaStreamRepository,
    private val watchRepo: WatchRepository,
    private val providerController: ProviderController,
    private val freeMetaGraph: FreeMetaGraph,
    private val contentDao: ContentDao,
    private val appSettings: AppSettings,
    private val downloadHelper: DownloadManagerHelper,
    private val profileManager: ProfileManager
) : ViewModel() {

    private fun decodeSlug(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return try {
            java.net.URLDecoder.decode(raw, "UTF-8")
        } catch (_: Exception) {
            raw
        }
    }

    private var slug: String = decodeSlug(checkNotNull(savedStateHandle.get<String>("slug")) { "slug required" })

    private val _state = MutableStateFlow(DetailUiState(loading = true))
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var loadedProviderId: String? = null

    init {
        viewModelScope.launch {
            savedStateHandle.getStateFlow("slug", "")
                .map { decodeSlug(it) }
                .filter { it.isNotBlank() }
                .distinctUntilChanged()
                .collect { newSlug ->
                    if (newSlug != slug) {
                        slug = newSlug
                        loadedProviderId = null
                        _state.update {
                            DetailUiState(
                                loading = true,
                                inWatchlist = it.inWatchlist,
                                episodeProgress = it.episodeProgress,
                                progressBySeasonEpisode = it.progressBySeasonEpisode,
                                currentProgress = it.currentProgress
                            )
                        }
                        load()
                    }
                }
        }
        viewModelScope.launch {
            providerController.activeProviderId.collect { providerId ->
                val previous = loadedProviderId
                if (previous != null && previous != providerId) {
                    _state.update {
                        it.copy(
                            providerMismatch = true,
                            loading = true,
                            error = null,
                            series = null,
                            seasons = emptyList(),
                            selectedSeasonIndex = 0,
                            seasonError = null,
                            relatedTitles = emptyList()
                        )
                    }
                    load()
                }
            }
        }
        // Watch watchlist state
        viewModelScope.launch {
            watchRepo.isInWatchlist(slug).collect { inList ->
                _state.update { it.copy(inWatchlist = inList) }
            }
        }
        // Watch all progress for this series
        viewModelScope.launch {
            watchRepo.watchProgress().collect { progressList ->
                val pid = ActiveProvider.id
                val progressMap = progressList
                    .filter { it.providerId.isBlank() || it.providerId == pid || it.providerId == "unknown" }
                    .associateBy { it.episodeKey }
                val seasonEpisodeMap = progressList
                    .filter {
                        it.slug == slug &&
                            (it.providerId.isBlank() || it.providerId == pid || it.providerId == "unknown")
                    }
                    .associateBy { "${it.season}-${it.episode}" }
                val current = progressList
                    .filter {
                        it.slug == slug &&
                            (it.providerId.isBlank() || it.providerId == pid || it.providerId == "unknown") &&
                            !it.isCompleted
                    }
                    .maxByOrNull { it.updatedAt }
                _state.update {
                    it.copy(
                        episodeProgress = progressMap,
                        progressBySeasonEpisode = seasonEpisodeMap,
                        currentProgress = current
                    )
                }
            }
        }
        load()
    }

    fun retry() = load()

    private fun load() {
        val expectedProvider = ActiveProvider.id
        viewModelScope.launch {
            try {
                when (val res = repo.loadSeriesDetail(slug)) {
                    is NovaStreamRepository.RepoResult.Success -> {
                        if (ActiveProvider.id != expectedProvider) return@launch
                        val (series, seasons) = res.data
                        if (profileManager.getActiveProfile().isKids && !KidsContentFilter.isKidsSafe(series)) {
                            _state.update {
                                it.copy(
                                    loading = false,
                                    series = null,
                                    seasons = emptyList(),
                                    error = appContext.getString(R.string.kids_content_blocked),
                                    loadedProviderId = expectedProvider,
                                    providerMismatch = false
                                )
                            }
                            return@launch
                        }
                        loadedProviderId = expectedProvider
                        _state.update {
                            it.copy(
                                loading = false,
                                series = series,
                                seasons = seasons,
                                error = null,
                                loadedProviderId = expectedProvider,
                                providerMismatch = false
                            )
                        }
                        loadRelatedTitles(series)
                        viewModelScope.launch {
                            enrichMetadata(series)
                        }
                        val firstWithEps = seasons.indexOfFirst { it.episodes.isNotEmpty() }
                        if (firstWithEps >= 0) {
                            _state.update { it.copy(selectedSeasonIndex = firstWithEps) }
                        } else if (seasons.isNotEmpty()) {
                            _state.update { it.copy(selectedSeasonIndex = 0) }
                            loadSeasonEpisodes(seasons.first().number)
                        }
                    }
                    is NovaStreamRepository.RepoResult.Error -> {
                        if (ActiveProvider.id != expectedProvider) return@launch
                        _state.update { it.copy(loading = false, error = res.message, providerMismatch = false) }
                    }
                }
            } catch (e: Exception) {
                if (ActiveProvider.id != expectedProvider) return@launch
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("DetailVM", "load error", e)
                _state.update {
                    it.copy(
                        loading = false,
                        error = com.novastream.app.util.ErrorMapper.toUserMessage(e),
                        providerMismatch = false
                    )
                }
            }
        }
    }

    fun selectSeason(index: Int) {
        if (index < 0 || index >= _state.value.seasons.size) return  // Validate index
        val current = _state.value
        if (current.loadingSeason) return  // Prevent concurrent loads
        if (current.selectedSeasonIndex == index) return  // Already selected
        _state.update { it.copy(selectedSeasonIndex = index) }
        val season = _state.value.seasons.getOrNull(index)
        if (season != null && season.episodes.isEmpty()) {
            loadSeasonEpisodes(season.number)
        }
    }

    fun toggleWatchlist() {
        val series = _state.value.series ?: return
        viewModelScope.launch {
            if (_state.value.inWatchlist) {
                watchRepo.removeFromWatchlist(series.id)
            } else {
                watchRepo.addToWatchlist(
                    slug = series.id,
                    title = series.title,
                    coverUrl = series.coverUrl,
                    isMovie = series.isMovie
                )
            }
        }
    }

    fun removeProgress(episodeKey: String) {
        viewModelScope.launch { watchRepo.removeProgress(episodeKey) }
    }

    /** Markiert eine Episode als gesehen (>90%) oder entfernt den Status. */
    fun toggleEpisodeWatched(season: Int, episode: Int, episodeTitle: String) {
        val existing = _state.value.progressFor(season, episode)
        viewModelScope.launch {
            if (existing != null && existing.isCompleted) {
                watchRepo.removeProgress(existing.episodeKey)
            } else {
                val series = _state.value.series
                watchRepo.saveProgress(
                    slug = slug,
                    seriesTitle = series?.title ?: "",
                    coverUrl = series?.coverUrl,
                    season = season,
                    episode = episode,
                    episodeTitle = episodeTitle,
                    positionMs = 1L,
                    durationMs = 1L,
                    isMovie = series?.isMovie == true
                )
            }
        }
    }

    /** Markiert alle Episoden einer Staffel als gesehen. */
    fun markSeasonAsWatched(season: Int) {
        val seasonObj = _state.value.seasons.find { it.number == season } ?: return
        val series = _state.value.series ?: return
        viewModelScope.launch {
            seasonObj.episodes.forEach { ep ->
                val existing = _state.value.progressFor(season, ep.number)
                if (existing == null || !existing.isCompleted) {
                    watchRepo.saveProgress(
                        slug = slug,
                        seriesTitle = series.title,
                        coverUrl = series.coverUrl,
                        season = season,
                        episode = ep.number,
                        episodeTitle = ep.title,
                        positionMs = 1L,
                        durationMs = 1L,
                        isMovie = series.isMovie
                    )
                }
            }
        }
    }

    /** Entfernt den "gesehen" Status für alle Episoden einer Staffel. */
    fun markSeasonAsUnwatched(season: Int) {
        val seasonObj = _state.value.seasons.find { it.number == season } ?: return
        viewModelScope.launch {
            seasonObj.episodes.forEach { ep ->
                _state.value.progressFor(season, ep.number)?.let { watchRepo.removeProgress(it.episodeKey) }
            }
        }
    }

    private fun loadSeasonEpisodes(seasonNum: Int) {
        _state.update { it.copy(loadingSeason = true, seasonError = null) }
        val expectedProvider = ActiveProvider.id
        viewModelScope.launch {
            try {
                when (val res = repo.loadSeason(slug, seasonNum)) {
                    is NovaStreamRepository.RepoResult.Success -> {
                        if (ActiveProvider.id != expectedProvider) return@launch
                        _state.update { current ->
                            val updated = current.seasons.map { s ->
                                if (s.number == seasonNum) s.copy(episodes = res.data) else s
                            }
                            current.copy(seasons = updated, loadingSeason = false, seasonError = null)
                        }
                    }
                    is NovaStreamRepository.RepoResult.Error ->
                        _state.update { it.copy(loadingSeason = false, seasonError = res.message) }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.e("DetailVM", "loadSeason error", e)
                _state.update {
                    it.copy(
                        loadingSeason = false,
                        seasonError = com.novastream.app.util.ErrorMapper.toUserMessage(e)
                    )
                }
            }
        }
    }

    /** FreeMetaGraph for cast, backdrop, similar titles, and cross-provider discovery. */
    private fun enrichMetadata(series: Series) {
        viewModelScope.launch {
            try {
                val language = ContentLanguage.fromTag(appSettings.contentLanguage.first())
                val preferAnime = ActiveProvider.isAniWorld ||
                    series.genres.any { it.contains("anime", true) } ||
                    series.detailUrl.contains("/anime/")

                val enrichment = freeMetaGraph.enrichBySeries(series, preferAnime, language)
                    ?: return@launch

                if (!series.id.all { it.isDigit() } &&
                    !FreeMetaService.titlesSimilar(series.title, enrichment.show.title)
                ) {
                    return@launch
                }

                applyGraphEnrichment(series, enrichment)
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.w("DetailVM", "meta enrich failed", e)
            }
        }
    }

    private suspend fun applyGraphEnrichment(series: Series, enrichment: MetaEnrichment) {
        val meta = enrichment.show
        val ids = enrichment.externalIds
        val enriched = mergeSeriesWithMeta(series, meta).copy(
            imdbId = ids.imdbId ?: meta.imdbId,
            tvmazeId = ids.tvmazeId ?: meta.tvmazeId,
            anilistId = ids.anilistId ?: meta.anilistId,
            canonicalKey = enrichment.canonicalKey,
            tmdbId = ids.tmdbId ?: meta.tmdbId
        )
        val providerId = ActiveProvider.id
        ContentEntity.fromExternalIds(
            slug = series.id,
            providerId = providerId,
            contentType = if (series.isMovie) ContentEntity.TYPE_MOVIE else ContentEntity.TYPE_TV,
            imdbId = ids.imdbId,
            tvmazeId = ids.tvmazeId,
            anilistId = ids.anilistId,
            wikidataId = ids.wikidataId,
            tmdbId = ids.tmdbId
        )?.let { contentDao.upsert(it) }

        val alsoOn = enrichment.canonicalKey?.let { key ->
            contentDao.findByCanonicalKeyExcluding(key, providerId)
                .mapNotNull { ProviderRegistry.getProviderOrNull(it.providerId)?.displayName }
                .distinct()
        } ?: emptyList()

        val similar = enrichment.similar.take(20).map { similarShow ->
            freeMetaGraph.toSeries(similarShow, providerId)
        }

        _state.update {
            it.copy(
                series = enriched,
                metaCast = enrichment.cast.ifEmpty { meta.cast },
                metaRating = meta.rating,
                metaNetwork = meta.network,
                imdbId = ids.imdbId ?: meta.imdbId,
                trailerUrl = meta.trailerUrl ?: FreeMetaService.trailerUrlFor(meta),
                relatedTitles = similar.ifEmpty { it.relatedTitles },
                alsoOnProviders = alsoOn
            )
        }
    }

    private fun mergeSeriesWithMeta(
        series: Series,
        meta: com.novastream.app.data.meta.MetaShow
    ): Series = series.copy(
        description = series.description?.takeIf { it.isNotBlank() } ?: meta.summary,
        coverUrl = series.coverUrl ?: meta.posterUrl,
        backdropUrl = series.backdropUrl ?: meta.backdropUrl,
        genres = series.genres.ifEmpty { meta.genres },
        year = series.year ?: meta.year,
        rating = series.rating ?: meta.rating?.let { String.format("%.1f", it) },
        status = series.status ?: meta.status,
        isAdult = AgeRatingResolver.mergeIsAdult(series.isAdult, meta.isAdult)
    )

    private fun loadRelatedTitles(series: Series) {
        viewModelScope.launch {
            try {
                val genre = series.genres.firstOrNull()?.lowercase() ?: return@launch
                val related = when (val res = repo.loadHomeCatalog()) {
                    is NovaStreamRepository.RepoResult.Success -> {
                        res.data.all
                            .asSequence()
                            .filter { it.id != series.id }
                            .filter { candidate ->
                                candidate.genres.any { g ->
                                    g.equals(genre, ignoreCase = true) ||
                                        genre.contains(g, ignoreCase = true) ||
                                        g.contains(genre, ignoreCase = true)
                                }
                            }
                            .ifEmpty {
                                res.data.popular.asSequence().filter { it.id != series.id }
                            }
                            .distinctBy { it.id }
                            .take(20)
                            .toList()
                            .let { KidsContentFilter.filterSeries(it, profileManager.getActiveProfile().isKids) }
                    }
                    else -> emptyList()
                }
                if (related.isNotEmpty()) {
                    _state.update { current ->
                        if (current.relatedTitles.isEmpty()) {
                            current.copy(relatedTitles = related)
                        } else current
                    }
                }
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.w("DetailVM", "related titles failed", e)
            }
        }
    }

    fun downloadCurrentEpisode() {
        val series = _state.value.series ?: return
        viewModelScope.launch {
            _state.update { it.copy(downloading = true, downloadMessage = null) }
            try {
                DownloadForegroundService.ensureChannel(appContext)
                val resolved = resolveTargetEpisode(series) ?: run {
                    _state.update { it.copy(downloading = false, downloadMessage = "detail_download_no_source") }
                    return@launch
                }
                val (seasonNum, ep) = ensureEpisodeLoaded(resolved.first, resolved.second, series) ?: run {
                    _state.update { it.copy(downloading = false, downloadMessage = "detail_download_no_source") }
                    return@launch
                }
                when (val hostersResult = repo.loadHosters(ep)) {
                    is RepoResult.Success -> {
                        val hoster = hostersResult.data.firstOrNull()
                        if (hoster == null) {
                            _state.update { it.copy(downloading = false, downloadMessage = "detail_download_no_source") }
                            return@launch
                        }
                        when (val sourcesResult = repo.resolveHoster(hoster)) {
                            is RepoResult.Success -> {
                                val source = sourcesResult.data.firstOrNull { it.isPlayable }
                                if (source == null) {
                                    _state.update { it.copy(downloading = false, downloadMessage = "detail_download_no_source") }
                                    return@launch
                                }
                                val profileId = profileManager.getActiveProfile().profileId
                                downloadHelper.enqueueDownload(
                                    providerId = ActiveProvider.id,
                                    slug = slug,
                                    title = series.title,
                                    episodeTitle = ep.title,
                                    season = seasonNum,
                                    episode = ep.number,
                                    coverUrl = series.coverUrl,
                                    source = source,
                                    profileId = profileId,
                                    isMovie = series.isMovie
                                )
                                downloadHelper.resumeDownloads()
                                _state.update { it.copy(downloading = false, downloadMessage = "detail_download_started") }
                            }
                            else -> _state.update { it.copy(downloading = false, downloadMessage = "detail_download_failed") }
                        }
                    }
                    else -> _state.update { it.copy(downloading = false, downloadMessage = "detail_download_failed") }
                }
            } catch (_: Exception) {
                _state.update { it.copy(downloading = false, downloadMessage = "detail_download_failed") }
            }
        }
    }

    fun clearDownloadMessage() {
        _state.update { it.copy(downloadMessage = null) }
    }

    fun castCurrentEpisode() {
        val series = _state.value.series ?: return
        viewModelScope.launch {
            _state.update { it.copy(casting = true, castStreamUrl = null, castStreamTitle = null) }
            try {
                val resolved = resolveTargetEpisode(series) ?: run {
                    _state.update { it.copy(casting = false, downloadMessage = "detail_cast_to_tv_failed") }
                    return@launch
                }
                val (seasonNum, ep) = ensureEpisodeLoaded(resolved.first, resolved.second, series) ?: run {
                    _state.update { it.copy(casting = false, downloadMessage = "detail_cast_to_tv_failed") }
                    return@launch
                }
                when (val hostersResult = repo.loadHosters(ep)) {
                    is RepoResult.Success -> {
                        val hoster = hostersResult.data.firstOrNull() ?: run {
                            _state.update { it.copy(casting = false, downloadMessage = "detail_cast_to_tv_failed") }
                            return@launch
                        }
                        when (val sourcesResult = repo.resolveHoster(hoster)) {
                            is RepoResult.Success -> {
                                val source = sourcesResult.data.firstOrNull { it.isPlayable }
                                if (source == null) {
                                    _state.update { it.copy(casting = false, downloadMessage = "detail_cast_to_tv_failed") }
                                    return@launch
                                }
                                _state.update {
                                    it.copy(
                                        casting = false,
                                        castStreamUrl = com.novastream.app.util.MediaUrls.secureUrl(source.url),
                                        castStreamTitle = "${series.title} – ${ep.title}"
                                    )
                                }
                            }
                            else -> _state.update { it.copy(casting = false, downloadMessage = "detail_cast_to_tv_failed") }
                        }
                    }
                    else -> _state.update { it.copy(casting = false, downloadMessage = "detail_cast_to_tv_failed") }
                }
            } catch (_: Exception) {
                _state.update { it.copy(casting = false, downloadMessage = "detail_cast_to_tv_failed") }
            }
        }
    }

    fun retrySeasonLoad() {
        val season = _state.value.selectedSeason ?: return
        loadSeasonEpisodes(season.number)
    }

    private fun resolveTargetEpisode(series: Series): Pair<Int, Int>? {
        if (series.isMovie) return 1 to 1
        val progress = _state.value.currentProgress
        if (progress != null && !progress.isCompleted) {
            return progress.season to progress.episode
        }
        val seasonObj = _state.value.selectedSeason
        val seasonNum = seasonObj?.number ?: _state.value.seasons.firstOrNull()?.number ?: return null
        val episodeNum = seasonObj?.episodes?.firstOrNull()?.number
            ?: _state.value.seasons.firstOrNull { it.episodes.isNotEmpty() }?.episodes?.firstOrNull()?.number
            ?: 1
        return seasonNum to episodeNum
    }

    private suspend fun ensureEpisodeLoaded(
        seasonNum: Int,
        episodeNum: Int,
        series: Series
    ): Pair<Int, Episode>? {
        if (series.isMovie) {
            return 1 to Episode(number = 1, title = series.title, slug = slug, season = 1, hosters = emptyList())
        }
        val seasonObj = _state.value.seasons.find { it.number == seasonNum }
        var ep = seasonObj?.episodes?.find { it.number == episodeNum }
            ?: seasonObj?.episodes?.firstOrNull()
        if (ep == null) {
            when (val epResult = repo.loadSeason(slug, seasonNum)) {
                is RepoResult.Success -> {
                    ep = epResult.data.find { it.number == episodeNum } ?: epResult.data.firstOrNull()
                    if (epResult.data.isNotEmpty()) {
                        _state.update { current ->
                            val updated = current.seasons.map { s ->
                                if (s.number == seasonNum) s.copy(episodes = epResult.data) else s
                            }
                            current.copy(seasons = updated)
                        }
                    }
                }
                else -> return null
            }
        }
        return ep?.let { seasonNum to it }
    }

    fun clearCastRequest() {
        _state.update { it.copy(castStreamUrl = null, castStreamTitle = null) }
    }
}