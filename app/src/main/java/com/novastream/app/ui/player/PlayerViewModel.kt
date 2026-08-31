package com.novastream.app.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.StreamSource
import com.novastream.app.data.repository.NovaStreamRepository
import com.novastream.app.data.repository.WatchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val loading: Boolean = true,
    val hosters: List<HosterLink> = emptyList(),
    val selectedHosterIndex: Int = 0,
    val sources: List<StreamSource> = emptyList(),
    val error: String? = null,
    val episodeTitle: String = "",
    val seriesTitle: String = "",
    val coverUrl: String? = null,
    val resumePositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isFinished: Boolean = false,
    val nextEpisode: NextEpisodeInfo? = null,
    val hosterSwitching: Boolean = false,
    val autoplayNext: Boolean = true,
    val playbackSpeed: Float = 1.0f,
    val skipIntroButton: Boolean = true,
    val season: Int = 1,
    val episode: Int = 1,
    val isMovie: Boolean = false
) {
    val currentSource: StreamSource?
        get() = sources.getOrNull(selectedHosterIndex.coerceAtMost(sources.lastIndex.coerceAtLeast(0)))

    /** True wenn mindestens ein Hoster verfügbar ist. */
    val hasHosters: Boolean get() = hosters.isNotEmpty()

    /** True wenn der ausgewählte Hoster einen Source hat. */
    val hasCurrentSource: Boolean get() = currentSource != null

    /** Anzahl der verfügbaren Hosters. */
    val hosterCount: Int get() = hosters.size

    /** True wenn die nächste Episode verfügbar ist. */
    val hasNextEpisode: Boolean get() = nextEpisode != null

    /** Formatierte Episode-Anzeige (z.B. "S1 E5" oder Filmtitel). */
    val episodeDisplay: String
        get() = if (isMovie) episodeTitle.ifBlank { seriesTitle } else "S$season E$episode"
}

data class NextEpisodeInfo(
    val season: Int,
    val episode: Int,
    val title: String,
    val coverUrl: String? = null
)

class PlayerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val slug: String = checkNotNull(savedStateHandle.get<String>("slug")) { "slug required" }
    private val season: Int = savedStateHandle.get<Int>("season") ?: 1
    private val episode: Int = savedStateHandle.get<Int>("episode") ?: 1
    private val title: String = run {
        val raw = savedStateHandle.get<String>("title") ?: ""
        try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
    }.ifBlank { "Episode $episode" }
    private val seriesTitle: String = run {
        val raw = savedStateHandle.get<String>("seriesTitle") ?: ""
        try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
    }
    private val coverUrl: String? = savedStateHandle.get<String>("coverUrl")?.let {
        try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
    }?.takeIf { it.isNotBlank() }
    private val isMovie: Boolean = savedStateHandle.get<Boolean>("isMovie") ?: false

    private val repo = NovaStreamRepository()
    private val watchRepo = WatchRepository.get(application)
    private val appSettings = com.novastream.app.data.prefs.AppSettings(application)

    private val _state = MutableStateFlow(PlayerUiState(
        episodeTitle = title,
        seriesTitle = seriesTitle,
        coverUrl = coverUrl,
        season = season,
        episode = episode,
        isMovie = isMovie
    ))
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init {
        // Load user settings
        viewModelScope.launch {
            appSettings.autoplayNext.collect { v -> _state.update { it.copy(autoplayNext = v) } }
        }
        viewModelScope.launch {
            appSettings.playbackSpeed.collect { v -> _state.update { it.copy(playbackSpeed = v) } }
        }
        viewModelScope.launch {
            appSettings.skipIntroButton.collect { v -> _state.update { it.copy(skipIntroButton = v) } }
        }
        load()
    }

    private fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            // Restore saved position
            val saved = watchRepo.getProgress(slug, season, episode)
            if (saved != null && !saved.isCompleted && saved.durationMs > 0 && saved.positionMs < saved.durationMs) {
                _state.update { it.copy(resumePositionMs = saved.positionMs, durationMs = saved.durationMs) }
            }

            // Build episode URL based on active provider
            val epUrl = com.novastream.app.data.provider.ActiveProvider.episodeUrl(slug, season, episode)
            val ep = Episode(
                number = episode,
                title = title,
                slug = slug,
                season = season,
                episodeUrl = epUrl
            )
            when (val h = repo.loadHosters(ep)) {
                is NovaStreamRepository.RepoResult.Success -> {
                    val hosters = h.data
                    if (hosters.isEmpty()) {
                        _state.update { it.copy(loading = false, error = "Keine Hoster gefunden") }
                        return@launch
                    }
                    // Deutsche Hoster priorisieren
                    val sorted = hosters.sortedWith(
                        compareByDescending { it.language.contains("Deutsch", ignoreCase = true) }
                    )
                    _state.update { it.copy(hosters = sorted, loading = false) }
                    resolveHoster(0)
                }
                is NovaStreamRepository.RepoResult.Error ->
                    _state.update { it.copy(loading = false, error = h.message) }
            }
        }
    }

    fun selectHoster(index: Int) {
        // Mark as switching - keeps old player alive until new source is ready
        _state.update {
            it.copy(
                selectedHosterIndex = index,
                sources = emptyList(),
                loading = true,
                error = null,
                hosterSwitching = true
            )
        }
        viewModelScope.launch { resolveHoster(index) }
    }

    /** Speichert den Wiedergabefortschritt. */
    fun saveProgress(positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        val safePosition = positionMs.coerceIn(0L, durationMs)
        viewModelScope.launch {
            watchRepo.saveProgress(
                slug = slug,
                seriesTitle = seriesTitle,
                coverUrl = coverUrl,
                season = season,
                episode = episode,
                episodeTitle = title,
                positionMs = safePosition,
                durationMs = durationMs,
                isMovie = isMovie
            )
            _state.update { it.copy(resumePositionMs = safePosition, durationMs = durationMs) }
        }
    }

    /** Markiert die Episode als fertig und lädt die nächste Episode Info. */
    fun onEpisodeFinished() {
        _state.update { it.copy(isFinished = true) }
        viewModelScope.launch {
            watchRepo.removeProgress(
                com.novastream.app.data.db.WatchProgress.key(
                    com.novastream.app.data.provider.ActiveProvider.id,
                    slug,
                    season,
                    episode
                )
            )
            if (!isMovie) loadNextEpisode()
        }
    }

    private suspend fun loadNextEpisode() {
        val nextEp = episode + 1
        val nextEpUrl = com.novastream.app.data.provider.ActiveProvider.episodeUrl(slug, season, nextEp)
        val ep = Episode(
            number = nextEp,
            title = "",
            slug = slug,
            season = season,
            episodeUrl = nextEpUrl
        )
        when (val h = repo.loadHosters(ep)) {
            is NovaStreamRepository.RepoResult.Success -> {
                if (h.data.isNotEmpty()) {
                    _state.update {
                        it.copy(nextEpisode = NextEpisodeInfo(
                            season = season,
                            episode = nextEp,
                            title = "Episode $nextEp",
                            coverUrl = coverUrl
                        ))
                    }
                } else {
                    // Try next season
                    val nextSeason = season + 1
                    val nextSeasonUrl = com.novastream.app.data.provider.ActiveProvider.episodeUrl(slug, nextSeason, 1)
                    val nextSeasonEpisode = Episode(
                        number = 1,
                        title = "",
                        slug = slug,
                        season = nextSeason,
                        episodeUrl = nextSeasonUrl
                    )
                    when (val h2 = repo.loadHosters(nextSeasonEpisode)) {
                        is NovaStreamRepository.RepoResult.Success -> {
                            if (h2.data.isNotEmpty()) {
                                _state.update {
                                    it.copy(nextEpisode = NextEpisodeInfo(
                                        season = nextSeason,
                                        episode = 1,
                                        title = "Staffel $nextSeason Episode 1",
                                        coverUrl = coverUrl
                                    ))
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
            else -> {}
        }
    }

    fun clearProgress() {
        viewModelScope.launch {
            watchRepo.removeProgress(
                com.novastream.app.data.db.WatchProgress.key(
                    com.novastream.app.data.provider.ActiveProvider.id,
                    slug,
                    season,
                    episode
                )
            )
        }
    }

    private suspend fun resolveHoster(index: Int) {
        val hoster = _state.value.hosters.getOrNull(index) ?: return
        if (hoster.redirectUrl.isBlank()) {
            tryNextHoster(index)
            return
        }
        _state.update { it.copy(selectedHosterIndex = index, loading = true, error = null) }
        val result = kotlinx.coroutines.withTimeoutOrNull(com.novastream.app.data.model.NovaStreamConfig.HOSTER_RESOLVE_TIMEOUT_MS) { repo.resolveHoster(hoster) }
        when (result) {
            is NovaStreamRepository.RepoResult.Success -> {
                if (result.data.isEmpty()) {
                    tryNextHoster(index)
                } else {
                    // Sources gefunden - hosterSwitching bleibt true bis Player den neuen Source lädt
                    _state.update {
                        it.copy(
                            loading = false,
                            sources = result.data,
                            error = null,
                            hosterSwitching = false
                        )
                    }
                }
            }
            is NovaStreamRepository.RepoResult.Error -> {
                tryNextHoster(index)
            }
            null -> {
                tryNextHoster(index)
            }
        }
    }

    private fun tryNextHoster(currentIndex: Int) {
        val nextIndex = currentIndex + 1
        if (nextIndex < _state.value.hosters.size) {
            _state.update { it.copy(selectedHosterIndex = nextIndex) }
            viewModelScope.launch { resolveHoster(nextIndex) }
        } else {
            _state.update {
                it.copy(
                    loading = false,
                    hosterSwitching = false,
                    error = "Kein Hoster konnte aufgelöst werden. Versuche es später erneut oder wähle einen anderen Hoster."
                )
            }
        }
    }
}
