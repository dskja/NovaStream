package com.novastream.app.data.model

/** Eine Episode mit ihren verfügbaren Hostern. */
data class Episode(
    val number: Int,
    val title: String = "Episode $number",
    val hosters: List<HosterLink> = emptyList(),
    val slug: String = "",          // Serien-Slug
    val season: Int = 1,
    val episodeUrl: String = "",    // z.B. /serie/breaking-bad/staffel-1/episode-1
    val thumbnailUrl: String? = null // Episoden-Thumbnail
) {
    /** True wenn diese Episode mindestens einen Hoster hat. */
    val hasHosters: Boolean get() = hosters.isNotEmpty()

    /** Display-Format: "S1E2 - Titel" */
    val displayTitle: String
        get() = "S${season}E${number} - $title"

    /** Sortierschlüssel für korrekte Reihenfolge. */
    val sortKey: Long
        get() = season.toLong() * 1000L + number.toLong()
}

/**
 * Ein Hoster-Eintrag für eine Episode.
 * Auf der Episoden-Seite sind dies <button class="link-box"> mit:
 *   data-link-id    = interne ID
 *   data-play-url   = Redirect-URL (/r?t=eyJ...) die zum Hoster führt
 *   data-provider-name = Hoster-Name (VOE, Streamtape, ...)
 *   data-language-label = Sprache (Deutsch, Englisch, ...)
 */
data class HosterLink(
    val name: String,           // z.B. "VOE"
    val redirectUrl: String,    // data-play-url, z.B. /r?t=eyJ...
    val language: String = "",  // z.B. "Deutsch"
    val linkId: String = "",    // data-link-id
    val index: Int = 0
) {
    /** True wenn ein Redirect vorhanden ist. */
    val hasRedirect: Boolean get() = redirectUrl.isNotBlank()

    /** Display-Format: "VOE (Deutsch)" oder nur "VOE" wenn keine Sprache. */
    val displayName: String
        get() = if (language.isNotBlank()) "$name ($language)" else name
}
