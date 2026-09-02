package com.novastream.app.provider

import android.content.Context
import com.google.gson.Gson
import com.novastream.app.data.provider.ConfigurableSiteProvider
import com.novastream.app.data.provider.ContentLanguage
import com.novastream.app.data.provider.ProviderRegistry
import com.novastream.app.data.provider.RegisteredProvider
import com.novastream.app.data.provider.StreamingProvider
import com.novastream.app.data.scraper.SiteProfile

/**
 * Import custom SiteProfile JSON from Settings (v15).
 * Format: single SiteProfile object or array of SiteProfile objects.
 * Imported providers receive [appContext] for mirror failover when the registry is initialized.
 */
object SiteProfileImporter {

    private val gson = Gson()
    private val imported = mutableListOf<RegisteredProvider>()

    @Volatile
    private var appContext: Context? = null

    /** Called from [ProviderRegistry.initialize] so custom profiles get mirror support. */
    internal fun bindContext(context: Context) {
        appContext = context.applicationContext
    }

    fun importFromJson(json: String): ImportResult {
        return try {
            val trimmed = json.trim()
            val profiles = when {
                trimmed.startsWith("[") -> {
                    gson.fromJson(trimmed, Array<SiteProfile>::class.java)?.toList().orEmpty()
                }
                else -> listOfNotNull(gson.fromJson(trimmed, SiteProfile::class.java))
            }
            if (profiles.isEmpty()) return ImportResult.Error("No profiles found in JSON")
            val ctx = appContext
            val added = profiles.mapNotNull { profile ->
                if (profile.id.isBlank() || profile.baseUrl.isBlank()) return@mapNotNull null
                val provider = ConfigurableSiteProvider(profile, ctx)
                RegisteredProvider(
                    provider = provider,
                    support = com.novastream.app.data.provider.ProviderSupport(
                        movies = profile.supportsMovies,
                        series = profile.supportsSeries
                    ),
                    contentLanguage = ContentLanguage.MULTI,
                    regionLabel = "Custom",
                    logoUrl = null
                ).also { imported.add(it) }
            }
            ImportResult.Success(added.size, added.map { it.provider.displayName })
        } catch (e: Exception) {
            ImportResult.Error(e.message ?: "Parse error")
        }
    }

    fun importedProviders(): List<StreamingProvider> = imported.map { it.provider }

    fun registeredProviders(): List<RegisteredProvider> = imported.toList()

    fun clearImported() {
        imported.clear()
    }

    sealed class ImportResult {
        data class Success(val count: Int, val names: List<String>) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }
}

/*
 * v15 Architecture note — target multi-module split:
 *
 * :core-model       → StreamSource, Series, Episode, HosterLink (shared data classes)
 * :provider-api     → StreamingProvider interface, ProviderResult, IptvStreamingProvider
 * :provider-registry→ ProviderRegistry, SiteProfile, ConfigurableSiteProvider
 * :playback-engine  → ExtractorEngine, StreamExtractor, DownloadManagerHelper
 * :metadata-free  → FreeMetaGraph (TVMaze + AniList + Wikidata)
 * :sync             → BackupRestoreManager, CloudSyncManager, ProfileManager
 * :feature-iptv     → IptvRegistry, LiveTvScreen
 * :app              → UI, Hilt wiring, navigation
 *
 * Current monolith keeps these packages as logical modules until full Gradle split.
 */
