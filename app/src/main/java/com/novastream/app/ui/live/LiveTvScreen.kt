package com.novastream.app.ui.live

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.novastream.app.R
import com.novastream.app.data.iptv.IptvChannel
import com.novastream.app.data.iptv.IptvChannelGroup
import com.novastream.app.data.iptv.IptvRegistry
import com.novastream.app.data.iptv.EpgProgram
import com.novastream.app.data.iptv.XmlTvEpgParser
import com.novastream.app.data.prefs.AppSettings
import java.net.URL
import com.novastream.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LiveTvUiState(
    val loading: Boolean = true,
    val groups: List<IptvChannelGroup> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<IptvChannel> = emptyList(),
    val error: String? = null,
    val iptvEnabled: Boolean = false,
    val epgPrograms: List<EpgProgram> = emptyList(),
    val epgLoading: Boolean = false
)

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val appSettings: AppSettings
) : ViewModel() {

    private val _state = MutableStateFlow(LiveTvUiState())
    val state: StateFlow<LiveTvUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            appSettings.iptvEnabled.collect { enabled ->
                _state.update { it.copy(iptvEnabled = enabled) }
                if (enabled && _state.value.groups.isEmpty()) {
                    loadChannels()
                    loadEpg()
                }
            }
        }
    }

    fun refresh() {
        if (_state.value.iptvEnabled) {
            loadChannels()
            loadEpg()
        }
    }

    fun loadEpg() {
        viewModelScope.launch {
            _state.update { it.copy(epgLoading = true) }
            try {
                val url = appSettings.epgUrl.first().trim()
                if (url.isBlank()) {
                    _state.update { it.copy(epgLoading = false, epgPrograms = emptyList()) }
                    return@launch
                }
                val xml = withContext(Dispatchers.IO) { URL(url).readText() }
                _state.update { it.copy(epgLoading = false, epgPrograms = XmlTvEpgParser.parse(xml)) }
            } catch (e: Exception) {
                _state.update { it.copy(epgLoading = false, epgPrograms = emptyList()) }
            }
        }
    }

    fun epgNow(channel: IptvChannel): EpgProgram? {
        val key = channel.tvgId?.takeIf { it.isNotBlank() } ?: channel.id
        return XmlTvEpgParser.nowPlaying(_state.value.epgPrograms, key)
    }

    fun loadChannels() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val providers = IptvRegistry.activeProviders(appSettings)
                val groups = providers.flatMap { it.loadChannelGroups() }
                _state.update { it.copy(loading = false, groups = groups) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun search(query: String) {
        _state.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            val providers = IptvRegistry.activeProviders(appSettings)
            val results = providers.flatMap { it.searchChannels(query) }.distinctBy { it.name }
            _state.update { it.copy(searchResults = results) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvScreen(
    onBack: () -> Unit,
    onPlayChannel: (IptvChannel) -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel()
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.live_tv_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        if (!uiState.iptvEnabled) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.iptv_disabled_hint))
            }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::search,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text(stringResource(R.string.live_tv_search_hint)) },
                singleLine = true
            )

            if (uiState.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.error != null) {
                Text(uiState.error!!, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            } else {
                val showSearch = uiState.searchQuery.isNotBlank()
                LazyColumn(Modifier.fillMaxSize()) {
                    if (uiState.epgLoading) {
                        item {
                            Text(
                                stringResource(R.string.live_tv_epg_loading),
                                Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (showSearch) {
                        items(uiState.searchResults, key = { it.id }) { channel ->
                            LiveChannelRow(
                                channel = channel,
                                epgTitle = viewModel.epgNow(channel)?.title,
                                onClick = { onPlayChannel(channel) }
                            )
                        }
                    } else {
                        uiState.groups.forEach { group ->
                            item {
                                Text(
                                    group.name,
                                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            items(group.channels, key = { it.id }) { channel ->
                                LiveChannelRow(
                                    channel = channel,
                                    epgTitle = viewModel.epgNow(channel)?.title,
                                    onClick = { onPlayChannel(channel) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveChannelRow(channel: IptvChannel, epgTitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!channel.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Icon(Icons.Default.LiveTv, null, Modifier.size(48.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(channel.name, fontWeight = FontWeight.Medium)
            channel.group?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            epgTitle?.let {
                Text(
                    stringResource(R.string.live_tv_epg_now, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}