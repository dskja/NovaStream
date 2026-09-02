package com.novastream.app.data.meta

/**
 * Resolved age-rating metadata from free no-key sources (Wikidata, Wikipedia, AniList, Jikan).
 */
data class AgeRatingResult(
    val isAdult: Boolean? = null,
    /** Human-readable certifications, e.g. "FSK 12", "TV-MA", "R". */
    val certifications: List<String> = emptyList(),
    val source: String? = null
) {
    val primaryCertification: String? get() = certifications.firstOrNull()
}
