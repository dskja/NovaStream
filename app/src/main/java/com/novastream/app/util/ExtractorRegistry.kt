package com.novastream.app.util

import com.novastream.app.data.model.StreamSource
import com.novastream.app.extractor.ExtractorEngine
import com.novastream.app.extractor.StreamExtractor

/**
 * Facade over Extractor Engine 2.0 plugin registry (v12).
 * Delegates resolution to [ExtractorEngine] with prioritized fallback chain.
 */
object ExtractorRegistry {

    /** @deprecated Use plugin metadata via [ExtractorEngine.allPlugins]. */
    data class ExtractorEntry(
        val name: String,
        val mainDomain: String,
        val aliasDomains: List<String> = emptyList(),
        val rotatingPatterns: List<Regex> = emptyList()
    )

    fun findMatching(url: String, hosterName: String = ""): List<ExtractorEntry> =
        ExtractorEngine.findMatching(url, hosterName).map { it.toEntry() }

    suspend fun resolve(hosterName: String, redirectUrl: String, baseUrl: String): List<StreamSource> =
        ExtractorEngine.resolve(hosterName, redirectUrl, baseUrl)

    fun registeredCount(): Int = ExtractorEngine.registeredCount()

    private fun StreamExtractor.toEntry() = ExtractorEntry(
        name = name,
        mainDomain = mainDomain,
        aliasDomains = aliasDomains,
        rotatingPatterns = rotatingPatterns
    )
}
