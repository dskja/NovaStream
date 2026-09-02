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

    /** Picks locale-appropriate certification (FSK for DE, TV/MPAA for EN). */
    fun primaryForLanguage(language: com.novastream.app.data.provider.ContentLanguage): String? {
        if (certifications.isEmpty()) return null
        return when (language) {
            com.novastream.app.data.provider.ContentLanguage.DE,
            com.novastream.app.data.provider.ContentLanguage.MULTI -> {
                certifications.firstOrNull { it.contains("FSK", ignoreCase = true) }
                    ?: certifications.firstOrNull { Regex("""\b(0|6|12|16|18)\b""").containsMatchIn(it) }
            }
            else -> null
        }
            ?: certifications.firstOrNull {
                it.contains("TV-", ignoreCase = true) ||
                    it.equals("R", ignoreCase = true) ||
                    it.contains("PG", ignoreCase = true) ||
                    it.contains("BBFC", ignoreCase = true)
            }
            ?: certifications.firstOrNull()
    }
}
