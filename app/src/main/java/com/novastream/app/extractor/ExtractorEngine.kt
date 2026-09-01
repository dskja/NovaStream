package com.novastream.app.extractor

import com.novastream.app.data.model.StreamSource
import com.novastream.app.extractor.plugins.PriorityExtractors
import com.novastream.app.extractor.plugins.StandardHosterExtractors
import com.novastream.app.telemetry.PlaySuccessTracker
import com.novastream.app.util.HosterResolver

/**
 * Extractor Engine 2.0 — plugin registry with prioritized fallback chain.
 * Replaces the static domain list in [com.novastream.app.util.ExtractorRegistry].
 */
object ExtractorEngine {

    private val plugins: List<StreamExtractor> by lazy {
        val priority = PriorityExtractors.all()
        val standard = StandardHosterExtractors.all()
        // Priority plugins override standard by name
        val priorityNames = priority.map { it.name }.toSet()
        priority + standard.filter { it.name !in priorityNames }
    }

    fun registeredCount(): Int = plugins.size

    fun allPlugins(): List<StreamExtractor> = plugins

    fun findMatching(url: String, hosterHint: String = ""): List<StreamExtractor> =
        plugins.filter { it.matches(url, hosterHint) }
            .sortedByDescending { it.priority }

    suspend fun resolve(
        hosterName: String,
        redirectUrl: String,
        baseUrl: String,
        trackTelemetry: Boolean = true
    ): List<StreamSource> {
        if (redirectUrl.isBlank()) return emptyList()

        val resolvedUrl = resolveBridgeUrl(redirectUrl)
        val matches = findMatching(resolvedUrl, hosterName)
        val attempted = mutableListOf<String>()

        for (plugin in matches) {
            attempted += plugin.name
            try {
                val sources = plugin.extract(resolvedUrl, baseUrl)
                if (sources.isNotEmpty()) {
                    if (trackTelemetry) PlaySuccessTracker.recordSuccess(plugin.name, hosterName)
                    return sources
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (trackTelemetry) PlaySuccessTracker.recordFailure(plugin.name, hosterName)
            }
        }

        // Global fallback to HosterResolver
        attempted += "HosterResolver"
        val fallback = HosterResolver(baseUrl = baseUrl).resolve(hosterName, resolvedUrl)
        if (fallback.isNotEmpty()) {
            if (trackTelemetry) PlaySuccessTracker.recordSuccess("HosterResolver", hosterName)
            return fallback
        }
        if (trackTelemetry) PlaySuccessTracker.recordFailure(hosterName, hosterName)
        return emptyList()
    }

    /** Universal bridge resolver for mysync.mov/stream/ URLs (from BetterStreamflix). */
    private suspend fun resolveBridgeUrl(link: String): String {
        if (!link.contains("mysync.mov/stream/")) return link
        return try {
            val client = com.novastream.app.data.api.NetworkModule.okHttpClient
            val body = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.newCall(
                    okhttp3.Request.Builder().url(link)
                        .header("User-Agent", com.novastream.app.data.model.NovaStreamConfig.USER_AGENT)
                        .build()
                ).execute().use { it.body?.string().orEmpty() }
            }
            body.substringAfter("window.location.replace(\"", "")
                .substringBefore("\"")
                .ifEmpty { body.substringAfter("window.location.href = \"", "").substringBefore("\"") }
                .ifEmpty { body.substringAfter("src=\"", "").substringBefore("\"") }
                .takeIf { it.startsWith("http") } ?: link
        } catch (_: Exception) {
            link
        }
    }
}
