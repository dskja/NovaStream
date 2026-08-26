package com.novastream.app.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novastream.app.data.model.Episode
import com.novastream.app.data.model.HosterLink
import com.novastream.app.data.model.StreamSource
import com.novastream.app.data.repository.NovaStreamRepository
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
    val error: String? = null
) {
    val currentSource: StreamSource?
        get() = sources.getOrNull(0)
}

class PlayerViewModel(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val slug: String = checkNotNull(savedStateHandle.get<String>("slug"))
    private val season: Int = checkNotNull(savedStateHandle.get<String>("season")).toInt()
    private val episode: Int = checkNotNull(savedStateHandle.get<String>("episode")).toInt()
    private val title: String = savedStateHandle.get<String>("title") ?: "Episode $episode"

    private val repo = NovaStreamRepository()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            // 1. Hosters der Episode laden (Episoden-Seite fetchen)
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

    private suspend fun resolveHoster(index: Int) {
        val hoster = _state.value.hosters.getOrNull(index) ?: return
        if (hoster.redirectUrl.isBlank()) {
            // Keine Redirect-URL → nächsten Hoster versuchen
            tryNextHoster(index)
            return
        }
        _state.update { it.copy(selectedHosterIndex = index, loading = true, error = null) }
        when (val res = repo.resolveHoster(hoster)) {
            is NovaStreamRepository.RepoResult.Success -> {
                if (res.data.isEmpty()) {
                    // Keine Stream-URL gefunden → nächsten Hoster versuchen
                    tryNextHoster(index)
                } else {
                    _state.update { it.copy(loading = false, sources = res.data, error = null) }
                }
            }
            is NovaStreamRepository.RepoResult.Error -> {
                // Fehler → nächsten Hoster versuchen
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
