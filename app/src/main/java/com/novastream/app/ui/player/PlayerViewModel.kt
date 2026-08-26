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
    val resumePositionMs: Long = 0L,
    val durationMs: Long = 0L
) {
    val currentSource: StreamSource?
        get() = sources.getOrNull(0)
}

class PlayerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val slug: String = checkNotNull(savedStateHandle.get<String>("slug"))
    private val season: Int = savedStateHandle.get<String>("season")?.toIntOrNull() ?: 1
    private val episode: Int = savedStateHandle.get<String>("episode")?.toIntOrNull() ?: 1
    private val title: String = run {
        val raw = savedStateHandle.get<String>("title") ?: ""
        try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
    }.ifBlank { "Episode $episode" }

    private val repo = NovaStreamRepository()
    private val watchRepo = WatchRepository(application)

    private val _state = MutableStateFlow(PlayerUiState(episodeTitle = title))
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            // Restore saved position
            val saved = watchRepo.getProgress(slug, season, episode)
            if (saved != null && !saved.isCompleted) {
                _state.update { it.copy(resumePositionMs = saved.positionMs, durationMs = saved.durationMs) }
            }

            val ep = Episode(
                number = episode,
                title = title,
                slug = slug,
                season = season,
                episodeUrl = "/serie/$slug/staffel-$season/episode-$episode"
            )
            when (val h = repo.loadHosters(ep)) {
                is NovaStreamRepository.RepoResult.Success -> {
                    val hosters = h.data
                    if (hosters.isEmpty()) {
                        _state.update { it.copy(loading = false, error = "Keine Hoster gefunden") }
                        return@launch
                    }
                    _state.update { it.copy(hosters = hosters, loading = false) }
                    resolveHoster(0)
                }
                is NovaStreamRepository.RepoResult.Error ->
                    _state.update { it.copy(loading = false, error = h.message) }
            }
        }
    }

    fun selectHoster(index: Int) {
        _state.update { it.copy(selectedHosterIndex = index, sources = emptyList(), loading = true) }
        viewModelScope.launch { resolveHoster(index) }
    }

    /**
     * Speichert den Wiedergabefortschritt.
     * Wird periodisch vom Player aufgerufen.
     */
    fun saveProgress(positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        viewModelScope.launch {
            watchRepo.saveProgress(
                slug = slug,
                seriesTitle = "", // wird vom Screen gesetzt via setSeriesInfo
                coverUrl = null,
                season = season,
                episode = episode,
                episodeTitle = title,
                positionMs = positionMs,
                durationMs = durationMs
            )
            _state.update { it.copy(resumePositionMs = positionMs, durationMs = durationMs) }
        }
    }

    /** Setzt Serien-Info für den Progress-Eintrag (Titel, Cover). */
    fun updateSeriesInfo(seriesTitle: String, coverUrl: String?) {
        viewModelScope.launch {
            val existing = watchRepo.getProgress(slug, season, episode)
            if (existing != null) {
                watchRepo.saveProgress(
                    slug = slug,
                    seriesTitle = seriesTitle,
                    coverUrl = coverUrl,
                    season = season,
                    episode = episode,
                    episodeTitle = title,
                    positionMs = existing.positionMs,
                    durationMs = existing.durationMs
                )
            }
        }
    }

    /** Entfernt den Fortschritt (z.B. wenn Episode fertig geschaut). */
    fun clearProgress() {
        viewModelScope.launch {
            watchRepo.removeProgress("$slug-$season-$episode")
        }
    }

    private suspend fun resolveHoster(index: Int) {
        val hoster = _state.value.hosters.getOrNull(index) ?: return
        if (hoster.redirectUrl.isBlank()) {
            tryNextHoster(index)
            return
        }
        _state.update { it.copy(selectedHosterIndex = index, loading = true, error = null) }
        when (val res = repo.resolveHoster(hoster)) {
            is NovaStreamRepository.RepoResult.Success -> {
                if (res.data.isEmpty()) {
                    tryNextHoster(index)
                } else {
                    _state.update { it.copy(loading = false, sources = res.data, error = null) }
                }
            }
            is NovaStreamRepository.RepoResult.Error -> {
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
                it.copy(loading = false, error = "Kein Hoster konnte aufgelöst werden. Versuche es später erneut oder wähle einen anderen Hoster.")
            }
        }
    }
}
