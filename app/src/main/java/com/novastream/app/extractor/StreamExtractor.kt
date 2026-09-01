package com.novastream.app.extractor

import com.novastream.app.data.model.StreamSource

/**
 * Extractor Engine 2.0 — plugin base class (ported from BetterStreamflix extractors/).
 * Each hoster implements domain matching + extraction logic.
 */
abstract class StreamExtractor {

    abstract val name: String
    abstract val mainDomain: String
    open val aliasDomains: List<String> = emptyList()
    open val rotatingPatterns: List<Regex> = emptyList()

    /** Priority for fallback chain (higher = tried first among matches). */
    open val priority: Int = 0

    abstract suspend fun extract(url: String, baseUrl: String = ""): List<StreamSource>

    fun matches(url: String, hosterHint: String = ""): Boolean {
        val lower = url.lowercase()
        val hint = hosterHint.lowercase()
        if (lower.contains(mainDomain) || aliasDomains.any { lower.contains(it) }) return true
        if (rotatingPatterns.any { it.containsMatchIn(lower) }) return true
        if (hint.isNotBlank() && hint.contains(name.lowercase())) return true
        return false
    }

    class ExtractionFailedException(
        val link: String,
        val attemptedExtractors: List<String>,
        cause: Throwable? = null
    ) : Exception(
        if (attemptedExtractors.isEmpty()) "No extractors for $link"
        else "Extraction failed for $link (tried: ${attemptedExtractors.joinToString()})",
        cause
    )
}
