package com.novastream.app.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.StreamSource
import com.novastream.app.data.prefs.AppSettings
import com.novastream.app.data.repository.NovaStreamRepository
import com.novastream.app.data.repository.WatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
    val selectedSourceIndex: Int = 0,
    val error: String? = null,
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
    val isMovie: Boolean = false
) {
    val currentSource: StreamSource?
        get() = sources.getOrNull(selectedSourceIndex.coerceAtMost(sources.lastIndex.coerceAtLeast(0)))

    /** True wenn mindestens ein Hoster verfügbar ist. */
    val hasHosters: Boolean get() = hosters.isNotEmpty()

    /** True wenn der ausgewählte Hoster einen Source hat. */
    val hasCurrentSource: Boolean get() = currentSource != null

    /** True wenn mehrere Qualitätsstufen verfügbar sind. */
    val hasMultipleSources: Boolean get() = sources.size > 1

    /** Anzahl der verfügbaren Hosters. */
    val hosterCount: Int get() = hosters.size

    /** True wenn die nächste Episode verfügbar ist. */
    val hasNextEpisode: Boolean get() = nextEpisode != null

    /** True wenn die vorherige Episode verfügbar ist. */
    val hasPreviousEpisode: Boolean get() = previousEpisode != null

    /** True wenn ein alternativer Hoster verfügbar ist. */
    val hasAlternateHoster: Boolean
        get() = hosters.isNotEmpty() && selectedHosterIndex < hosters.lastIndex

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

data class PreviousEpisodeInfo(
    val season: Int,
    val episode: Int,
    val title: String,
    val coverUrl: String? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: NovaStreamRepository,
    private val watchRepo: WatchRepository,
    private val appSettings: AppSettings
) : ViewModel() {

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
        if (!isMovie) {
            viewModelScope.launch { loadNextEpisode() }
            viewModelScope.launch { loadPreviousEpisode() }
        }
    }

    private fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
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
            when (val h = repo.loadHosters(ep)) {
                is NovaStreamRepository.RepoResult.Success -> {
                    val hosters = h.data
                    if (hosters.isEmpty()) {
                        _state.update { it.copy(loading = false, error = "Keine Hoster gefunden") }
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
        }
    }

    fun selectHoster(index: Int) {
        _state.update {
            it.copy(
                selectedHosterIndex = index,
                selectedSourceIndex = 0,
                sources = emptyList(),
                loading = true,
                error = null,
                hosterSwitching = true
            )
        }
        viewModelScope.launch { resolveHoster(index) }
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
            _state.update { it.copy(error = "Keine weiteren Hoster verfügbar") }
        }
    }

    fun retry() {
        val index = _state.value.selectedHosterIndex
        _state.update { it.copy(error = null, loading = true) }
        viewModelScope.launch { resolveHoster(index) }
    }

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

    private suspend fun loadPreviousEpisode() {
        if (isMovie) return
        if (episode > 1) {
            val prevEp = episode - 1
            val prevEpUrl = com.novastream.app.data.provider.ActiveProvider.episodeUrl(slug, season, prevEp)
            val ep = Episode(
                number = prevEp,
                title = "",
                slug = slug,
                season = season,
                episodeUrl = prevEpUrl
            )
            when (val h = repo.loadHosters(ep)) {
                is NovaStreamRepository.RepoResult.Success -> {
                    if (h.data.isNotEmpty()) {
                        _state.update {
                            it.copy(previousEpisode = PreviousEpisodeInfo(
                                season = season,
                                episode = prevEp,
                                title = "Episode $prevEp",
                                coverUrl = coverUrl
                            ))
                        }
                    }
                }
                else -> {}
            }
            return
        }
        if (season > 1) {
            val prevSeason = season - 1
            val prevSeasonUrl = com.novastream.app.data.provider.ActiveProvider.episodeUrl(slug, prevSeason, 1)
            val prevSeasonEpisode = Episode(
                number = 1,
                title = "",
                slug = slug,
                season = prevSeason,
                episodeUrl = prevSeasonUrl
            )
            when (val h = repo.loadHosters(prevSeasonEpisode)) {
                is NovaStreamRepository.RepoResult.Success -> {
                    if (h.data.isNotEmpty()) {
                        _state.update {
                            it.copy(previousEpisode = PreviousEpisodeInfo(
                                season = prevSeason,
                                episode = 1,
                                title = "Staffel $prevSeason Episode 1",
                                coverUrl = coverUrl
                            ))
                        }
                    }
                }
                else -> {}
            }
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
                    val sortedSources = sortSources(result.data)
                    _state.update {
                        it.copy(
                            loading = false,
                            sources = sortedSources,
                            selectedSourceIndex = 0,
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
}
