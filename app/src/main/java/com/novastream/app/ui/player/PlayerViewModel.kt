package com.novastream.app.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novastream.app.data.meta.CatalogMetaEnricher
import com.novastream.app.data.meta.MetaEnrichmentCache
import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.Series
import com.novastream.app.data.provider.ActiveProvider
import com.novastream.app.data.provider.ContentLanguage
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.StreamSource
import com.novastream.app.data.prefs.AppSettings
import com.novastream.app.data.repository.NovaStreamRepository
import com.novastream.app.data.repository.WatchRepository
import com.novastream.app.data.playback.PlaybackRequestStore
import com.novastream.app.download.DownloadManagerHelper
import android.content.Context
import com.novastream.app.R
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.novastream.app.profile.ProfileManager
import com.novastream.app.util.AppContext
import com.novastream.app.util.KidsContentFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val loading: Boolean = true,
    val hosters: List<HosterLink> = emptyList(),
    val selectedHosterIndex: Int = 0,
    val sources: List<StreamSource> = emptyList(),
    val selectedSourceIndex: Int = 0,
    val error: String? = null,
    val hosterFallbackNotice: String? = null,
    val episodeTitle: String = "",
    val seriesTitle: String = "",
    val coverUrl: String? = null,
    val resumePositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isFinished: Boolean = false,
    val nextEpisode: NextEpisodeInfo? = null,
    val previousEpisode: PreviousEpisodeInfo? = null,
    val hosterSwitching: Boolean = false,
    val autoplayNext: Boolean = true,
    val playbackSpeed: Float = 1.0f,
    val skipIntroButton: Boolean = true,
    val dataSaverMode: Boolean = false,
    val preferredHoster: String = "VOE",
    val preferredLanguage: String = "Deutsch",
    val season: Int = 1,
    val episode: Int = 1,
    val isMovie: Boolean = false,
    val isLive: Boolean = false,
    val adjacentEpisodesLoading: Boolean = false,
    val isBuffering: Boolean = false
) {
    val currentSource: StreamSource?
        get() = sources.getOrNull(selectedSourceIndex.coerceAtMost(sources.lastIndex.coerceAtLeast(0)))

    val hasHosters: Boolean get() = hosters.isNotEmpty()
    val hasCurrentSource: Boolean get() = currentSource != null
    val hasMultipleSources: Boolean get() = sources.size > 1
    val hosterCount: Int get() = hosters.size
    val hasNextEpisode: Boolean get() = nextEpisode != null
    val hasPreviousEpisode: Boolean get() = previousEpisode != null
    val hasAlternateHoster: Boolean
        get() = hosters.isNotEmpty() && selectedHosterIndex < hosters.lastIndex

    val episodeDisplay: String
        get() = if (isMovie) episodeTitle.ifBlank { seriesTitle } else "S$season E$episode"
}

data class NextEpisodeInfo(
    val season: Int,
    val episode: Int,
    val title: String,
    val coverUrl: String? = null
)

data class PreviousEpisodeInfo(
    val season: Int,
    val episode: Int,
    val title: String,
    val coverUrl: String? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val repo: NovaStreamRepository,
    private val watchRepo: WatchRepository,
    private val appSettings: AppSettings,
    private val downloadHelper: DownloadManagerHelper,
    private val playbackRequestStore: PlaybackRequestStore,
    private val profileManager: ProfileManager,
    private val catalogMetaEnricher: CatalogMetaEnricher
) : ViewModel() {

    private val slug: String = run {
        val raw = checkNotNull(savedStateHandle.get<String>("slug")) { "slug required" }
        try {
            java.net.URLDecoder.decode(raw, "UTF-8")
        } catch (_: Exception) {
            raw
        }
    }
    private val season: Int = savedStateHandle.get<Int>("season") ?: 1
    private val episode: Int = savedStateHandle.get<Int>("episode") ?: 1
    private val title: String = run {
        val raw = savedStateHandle.get<String>("title") ?: ""
        val decoded = try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
        decoded.ifBlank { null }
    } ?: AppContext.get().getString(R.string.player_episode_fallback_fmt, episode)
    private val seriesTitle: String = run {
        val raw = savedStateHandle.get<String>("seriesTitle") ?: ""
        try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
    }
    private val coverUrl: String? = savedStateHandle.get<String>("coverUrl")?.let {
        try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
    }?.takeIf { it.isNotBlank() }
    private val isMovie: Boolean = savedStateHandle.get<Boolean>("isMovie") ?: false
    private val isLive: Boolean = savedStateHandle.get<Boolean>("isLive") ?: false
    private val downloadId: String? = savedStateHandle.get<String>("downloadId")?.let {
        try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
    }?.takeIf { it.isNotBlank() }
    private val playbackId: String? = savedStateHandle.get<String>("playbackId")?.let {
        try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
    }?.takeIf { it.isNotBlank() }
    private val storedPlayback = playbackId?.let { playbackRequestStore.get(it) }
    private val directStreamUrl: String? = storedPlayback?.streamUrl
        ?: savedStateHandle.get<String>("streamUrl")?.let {
            try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
        }?.takeIf { it.isNotBlank() }
    private val resolvedIsLive: Boolean = isLive || (storedPlayback?.isLive == true)

    private val _state = MutableStateFlow(PlayerUiState(
        episodeTitle = title,
        seriesTitle = seriesTitle,
        coverUrl = coverUrl,
        season = season,
        episode = episode,
        isMovie = isMovie,
        isLive = resolvedIsLive
    ))
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    val castEnabled = appSettings.castEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        true
    )

    companion object {
        internal fun isResolveStale(requestGeneration: Int, currentGeneration: Int): Boolean =
            requestGeneration != currentGeneration

        internal fun resolveNextEpisode(
            episodes: List<Episode>,
            currentSeason: Int,
            currentEpisode: Int,
            coverUrl: String?
        ): NextEpisodeInfo? {
            val sorted = episodes.sortedBy { it.number }
            val idx = sorted.indexOfFirst { it.number == currentEpisode }
            if (idx in 0 until sorted.lastIndex) {
                val next = sorted[idx + 1]
                return NextEpisodeInfo(currentSeason, next.number, next.title.ifBlank { "Episode ${next.number}" }, coverUrl)
            }
            return null
        }

        internal fun resolvePreviousEpisode(
            episodes: List<Episode>,
            currentSeason: Int,
            currentEpisode: Int,
            coverUrl: String?
        ): PreviousEpisodeInfo? {
            val sorted = episodes.sortedBy { it.number }
            val idx = sorted.indexOfFirst { it.number == currentEpisode }
            if (idx > 0) {
                val prev = sorted[idx - 1]
                return PreviousEpisodeInfo(currentSeason, prev.number, prev.title.ifBlank { "Episode ${prev.number}" }, coverUrl)
            }
            return null
        }
    }

    private var resolveJob: Job? = null
    private var resolveGeneration = 0
    private var adjacentEpisodesJob: Job? = null
    private var saveProgressJob: Job? = null
    private var lastSavedProgressBucket = -1L
    private var contentIsAdult: Boolean? = null

    init {
        viewModelScope.launch {
            appSettings.autoplayNext.collect { v -> _state.update { it.copy(autoplayNext = v) } }
        }
        viewModelScope.launch {
            appSettings.playbackSpeed.collect { v -> _state.update { it.copy(playbackSpeed = v) } }
        }
        viewModelScope.launch {
            appSettings.skipIntroButton.collect { v -> _state.update { it.copy(skipIntroButton = v) } }
        }
        viewModelScope.launch {
            appSettings.dataSaverMode.collect { v -> _state.update { it.copy(dataSaverMode = v) } }
        }
        viewModelScope.launch {
            appSettings.preferredHoster.collect { v -> _state.update { it.copy(preferredHoster = v) } }
        }
        viewModelScope.launch {
            appSettings.preferredLanguage.collect { v -> _state.update { it.copy(preferredLanguage = v) } }
        }
        load()
    }

    fun ensureAdjacentEpisodesLoaded() {
        if (isMovie) return
        val current = _state.value
        if (current.nextEpisode != null && current.previousEpisode != null) return
        if (adjacentEpisodesJob?.isActive == true) return
        adjacentEpisodesJob = viewModelScope.launch { loadAdjacentEpisodesFromSeason() }
    }

    private suspend fun isKidsSafeForPlayback(): Boolean {
        val stub = Series(
            id = slug,
            title = seriesTitle,
            isMovie = isMovie,
            providerId = ActiveProvider.id
        )
        MetaEnrichmentCache.get(MetaEnrichmentCache.cacheKey(stub))?.let { cached ->
            val enriched = catalogMetaEnricher.applyEnrichment(stub, cached)
            contentIsAdult = enriched.isAdult
            return KidsContentFilter.isKidsSafe(enriched)
        }
        val language = ContentLanguage.fromTag(appSettings.contentLanguage.first())
        val enriched = catalogMetaEnricher.enrichOne(stub, language, ActiveProvider.isAniWorld)
        contentIsAdult = enriched.isAdult
        return KidsContentFilter.isKidsSafe(enriched)
    }

    private suspend fun resolveContentIsAdult(): Boolean? {
        contentIsAdult?.let { return it }
        val stub = Series(id = slug, title = seriesTitle, isMovie = isMovie, providerId = ActiveProvider.id)
        MetaEnrichmentCache.get(MetaEnrichmentCache.cacheKey(stub))?.let { cached ->
            contentIsAdult = catalogMetaEnricher.applyEnrichment(stub, cached).isAdult
            return contentIsAdult
        }
        val language = ContentLanguage.fromTag(appSettings.contentLanguage.first())
        contentIsAdult = catalogMetaEnricher.enrichOne(stub, language, ActiveProvider.isAniWorld).isAdult
        return contentIsAdult
    }

    private fun load() {
        _state.update { it.copy(loading = true, error = null, hosterFallbackNotice = null) }
        viewModelScope.launch {
            if (profileManager.getActiveProfile().isKids) {
                if (KidsContentFilter.isBlockedForKidsPlayback(slug, seriesTitle, title)) {
                    _state.update {
                        it.copy(loading = false, error = context.getString(R.string.kids_content_blocked))
                    }
                    return@launch
                }
                if (!isKidsSafeForPlayback()) {
                    _state.update {
                        it.copy(loading = false, error = context.getString(R.string.kids_content_blocked))
                    }
                    return@launch
                }
            }

            if (!downloadId.isNullOrBlank()) {
                val source = downloadHelper.offlineSourceFor(downloadId)
                if (source == null) {
                    _state.update {
                        it.copy(
                            loading = false,
                            error = context.getString(R.string.player_no_hosters_found)
                        )
                    }
                    return@launch
                }
                _state.update {
                    it.copy(
                        loading = false,
                        hosters = listOf(HosterLink(name = "Offline", redirectUrl = source.url, language = "Offline", index = 0)),
                        sources = listOf(source),
                        selectedHosterIndex = 0,
                        selectedSourceIndex = 0,
                        error = null
                    )
                }
                return@launch
            }

            if (!directStreamUrl.isNullOrBlank()) {
                val url = directPlaybackUrl(directStreamUrl)
                val label = if (resolvedIsLive) "Live" else "Offline"
                val source = StreamSource(
                    hoster = label,
                    url = url,
                    mimeType = when {
                        url.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
                        url.startsWith("file://") || url.startsWith("content://") -> "video/mp4"
                        else -> "video/mp4"
                    },
                    isHls = url.contains(".m3u8", ignoreCase = true)
                )
                _state.update {
                    it.copy(
                        loading = false,
                        hosters = listOf(HosterLink(name = label, redirectUrl = url, language = label, index = 0)),
                        sources = listOf(source),
                        selectedHosterIndex = 0,
                        selectedSourceIndex = 0,
                        error = null
                    )
                }
                return@launch
            }

            val saved = watchRepo.getProgress(slug, season, episode)
            if (saved != null && !saved.isCompleted && saved.durationMs > 0 && saved.positionMs < saved.durationMs) {
                _state.update { it.copy(resumePositionMs = saved.positionMs, durationMs = saved.durationMs) }
            }

            val epUrl = if (isMovie) {
                com.novastream.app.data.provider.ActiveProvider.movieDetailUrl(slug)
            } else {
                com.novastream.app.data.provider.ActiveProvider.episodeUrl(slug, season, episode)
            }
            val ep = Episode(
                number = episode,
                title = title,
                slug = slug,
                season = season,
                episodeUrl = epUrl
            )
            try {
                when (val h = repo.loadHosters(ep)) {
                    is NovaStreamRepository.RepoResult.Success -> {
                        val hosters = h.data
                        if (hosters.isEmpty()) {
                            _state.update { it.copy(loading = false, error = context.getString(R.string.player_no_hosters_found)) }
                            return@launch
                        }
                        val prefs = _state.value
                        val sorted = sortHosters(hosters, prefs.preferredHoster, prefs.preferredLanguage)
                        _state.update { it.copy(hosters = sorted, loading = false) }
                        resolveHoster(0)
                    }
                    is NovaStreamRepository.RepoResult.Error ->
                        _state.update { it.copy(loading = false, error = h.message) }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = com.novastream.app.util.ErrorMapper.toUserMessage(context, e)
                    )
                }
            }
        }
    }

    fun selectHoster(index: Int) {
        resolveJob?.cancel()
        _state.update {
            it.copy(
                selectedHosterIndex = index,
                selectedSourceIndex = 0,
                sources = emptyList(),
                loading = true,
                error = null,
                hosterFallbackNotice = null,
                hosterSwitching = true
            )
        }
        resolveJob = viewModelScope.launch { resolveHoster(index) }
    }

    fun selectSource(index: Int) {
        val safeIndex = index.coerceIn(0, _state.value.sources.lastIndex.coerceAtLeast(0))
        _state.update { it.copy(selectedSourceIndex = safeIndex) }
    }

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            appSettings.setPlaybackSpeed(speed)
            _state.update { it.copy(playbackSpeed = speed.coerceIn(0.25f, 4.0f)) }
        }
    }

    fun tryAlternateHoster() {
        val nextIndex = _state.value.selectedHosterIndex + 1
        if (nextIndex < _state.value.hosters.size) {
            selectHoster(nextIndex)
        } else {
            _state.update { it.copy(error = context.getString(R.string.player_no_more_hosters)) }
        }
    }

    fun dismissHosterFallbackNotice() {
        _state.update { it.copy(hosterFallbackNotice = null) }
    }

    fun setBuffering(buffering: Boolean) {
        _state.update { it.copy(isBuffering = buffering) }
    }

    fun onPlayerError(message: String) {
        _state.update { it.copy(error = message, loading = false, isBuffering = false) }
    }

    fun retry() {
        if (!downloadId.isNullOrBlank() || !directStreamUrl.isNullOrBlank()) {
            load()
            return
        }
        val index = _state.value.selectedHosterIndex
        resolveJob?.cancel()
        _state.update { it.copy(error = null, loading = true, hosterFallbackNotice = null) }
        resolveJob = viewModelScope.launch { resolveHoster(index) }
    }

    @UnstableApi
    fun buildPlayer(context: Context): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(downloadHelper.cacheDataSourceFactory()))
            .setTrackSelector(DefaultTrackSelector(context))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .build()
    }

    fun saveProgress(positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        val safePosition = positionMs.coerceIn(0L, durationMs)
        val bucket = safePosition / 5000L
        if (bucket == lastSavedProgressBucket && saveProgressJob?.isActive == true) return
        saveProgressJob?.cancel()
        saveProgressJob = viewModelScope.launch {
            delay(750)
            lastSavedProgressBucket = bucket
            val isAdult = resolveContentIsAdult()
            watchRepo.saveProgress(
                slug = slug,
                seriesTitle = seriesTitle,
                coverUrl = coverUrl,
                season = season,
                episode = episode,
                episodeTitle = title,
                positionMs = safePosition,
                durationMs = durationMs,
                isMovie = isMovie,
                isAdult = isAdult
            )
            _state.update { it.copy(resumePositionMs = safePosition, durationMs = durationMs) }
        }
    }

    fun saveProgressImmediate(positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        saveProgressJob?.cancel()
        val safePosition = positionMs.coerceIn(0L, durationMs)
        viewModelScope.launch {
            val isAdult = resolveContentIsAdult()
            watchRepo.saveProgress(
                slug = slug,
                seriesTitle = seriesTitle,
                coverUrl = coverUrl,
                season = season,
                episode = episode,
                episodeTitle = title,
                positionMs = safePosition,
                durationMs = durationMs,
                isMovie = isMovie,
                isAdult = isAdult
            )
            _state.update { it.copy(resumePositionMs = safePosition, durationMs = durationMs) }
        }
    }

    fun onEpisodeFinished() {
        _state.update { it.copy(isFinished = true) }
        viewModelScope.launch {
            watchRepo.removeProgressForEpisode(slug, season, episode)
            ensureAdjacentEpisodesLoaded()
        }
    }

    private suspend fun loadAdjacentEpisodesFromSeason() {
        _state.update { it.copy(adjacentEpisodesLoading = true) }
        try {
            when (val currentSeasonRes = repo.loadSeason(slug, season)) {
                is NovaStreamRepository.RepoResult.Success -> {
                    val episodes = currentSeasonRes.data
                    val next = resolveNextEpisode(episodes, season, episode, coverUrl)
                    val previous = resolvePreviousEpisode(episodes, season, episode, coverUrl)
                    var resolvedNext = next
                    var resolvedPrevious = previous

                    if (next == null && episodes.any { it.number == episode }) {
                        val nextSeason = season + 1
                        when (val nextSeasonRes = repo.loadSeason(slug, nextSeason)) {
                            is NovaStreamRepository.RepoResult.Success -> {
                                val first = nextSeasonRes.data.minByOrNull { it.number }
                                if (first != null) {
                                    resolvedNext = NextEpisodeInfo(
                                        season = nextSeason,
                                        episode = first.number,
                                        title = first.title.ifBlank {
                                            context.getString(R.string.player_season_episode_title_fmt, nextSeason, first.number)
                                        },
                                        coverUrl = coverUrl
                                    )
                                }
                            }
                            else -> {}
                        }
                    }

                    if (previous == null && episode <= 1 && season > 1) {
                        val prevSeason = season - 1
                        when (val prevSeasonRes = repo.loadSeason(slug, prevSeason)) {
                            is NovaStreamRepository.RepoResult.Success -> {
                                val last = prevSeasonRes.data.maxByOrNull { it.number }
                                if (last != null) {
                                    resolvedPrevious = PreviousEpisodeInfo(
                                        season = prevSeason,
                                        episode = last.number,
                                        title = last.title.ifBlank {
                                            context.getString(R.string.player_season_episode_title_fmt, prevSeason, last.number)
                                        },
                                        coverUrl = coverUrl
                                    )
                                }
                            }
                            else -> {}
                        }
                    }

                    _state.update {
                        it.copy(
                            nextEpisode = resolvedNext ?: it.nextEpisode,
                            previousEpisode = resolvedPrevious ?: it.previousEpisode,
                            adjacentEpisodesLoading = false
                        )
                    }
                }
                else -> {
                    _state.update { it.copy(adjacentEpisodesLoading = false) }
                }
            }
        } catch (e: Exception) {
            if (com.novastream.app.BuildConfig.DEBUG) {
                android.util.Log.w("PlayerVM", "adjacent episodes failed", e)
            }
            _state.update { it.copy(adjacentEpisodesLoading = false) }
        }
    }

    fun clearProgress() {
        viewModelScope.launch {
            watchRepo.removeProgressForEpisode(slug, season, episode)
        }
    }

    private suspend fun resolveHoster(index: Int) {
        val generation = ++resolveGeneration
        val hoster = _state.value.hosters.getOrNull(index) ?: return
        if (hoster.redirectUrl.isBlank()) {
            tryNextHoster(index, generation)
            return
        }
        _state.update { it.copy(selectedHosterIndex = index, loading = true, error = null) }
        val result = kotlinx.coroutines.withTimeoutOrNull(com.novastream.app.data.model.NovaStreamConfig.HOSTER_RESOLVE_TIMEOUT_MS) {
            repo.resolveHoster(hoster)
        }
        if (isResolveStale(generation, resolveGeneration)) return
        when (result) {
            is NovaStreamRepository.RepoResult.Success -> {
                if (result.data.isEmpty()) {
                    tryNextHoster(index, generation)
                } else {
                    val sortedSources = sortSources(result.data)
                    _state.update {
                        it.copy(
                            loading = false,
                            sources = sortedSources,
                            selectedSourceIndex = 0,
                            error = null,
                            hosterSwitching = false,
                            hosterFallbackNotice = null
                        )
                    }
                }
            }
            is NovaStreamRepository.RepoResult.Error -> tryNextHoster(index, generation)
            null -> tryNextHoster(index, generation)
        }
    }

    private fun tryNextHoster(currentIndex: Int, generation: Int) {
        if (isResolveStale(generation, resolveGeneration)) return
        val nextIndex = currentIndex + 1
        if (nextIndex < _state.value.hosters.size) {
            val failedName = _state.value.hosters.getOrNull(currentIndex)?.name.orEmpty()
            val nextName = _state.value.hosters.getOrNull(nextIndex)?.name.orEmpty()
            _state.update {
                it.copy(
                    selectedHosterIndex = nextIndex,
                    hosterFallbackNotice = if (failedName.isNotBlank() && nextName.isNotBlank()) {
                        context.getString(R.string.player_hoster_failed_fmt, failedName, nextName)
                    } else {
                        context.getString(R.string.player_hoster_failed_next)
                    }
                )
            }
            resolveJob = viewModelScope.launch { resolveHoster(nextIndex) }
        } else {
            _state.update {
                it.copy(
                    loading = false,
                    hosterSwitching = false,
                    hosterFallbackNotice = null,
                    error = context.getString(R.string.player_resolve_failed)
                )
            }
        }
    }

    private fun sortHosters(
        hosters: List<HosterLink>,
        preferredHoster: String,
        preferredLanguage: String
    ): List<HosterLink> {
        return hosters.sortedWith(
            compareByDescending<HosterLink> { hosterMatchesPreference(it.name, preferredHoster) }
                .thenByDescending { it.language.contains(preferredLanguage, ignoreCase = true) }
                .thenByDescending { it.language.contains("Deutsch", ignoreCase = true) }
                .thenBy { it.index }
        )
    }

    private fun hosterMatchesPreference(name: String, preferredHoster: String): Boolean {
        if (preferredHoster.isBlank()) return false
        return name.contains(preferredHoster, ignoreCase = true) ||
            preferredHoster.contains(name, ignoreCase = true)
    }

    private fun sortSources(sources: List<StreamSource>): List<StreamSource> {
        return sources.sortedByDescending { it.qualityRank }
    }

    /** Direct navigation stream (live IPTV, offline download, deep link). */
    private fun directPlaybackUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("file://") || trimmed.startsWith("content://") || trimmed.startsWith("/")) {
            return if (trimmed.startsWith("/")) "file://$trimmed" else trimmed
        }
        return com.novastream.app.util.MediaUrls.playbackUrl(trimmed)
    }

    override fun onCleared() {
        playbackId?.let { playbackRequestStore.remove(it) }
        super.onCleared()
    }
}
