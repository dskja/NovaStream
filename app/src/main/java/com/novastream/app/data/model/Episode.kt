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

    /** Kurzes Display-Format: "S1 E2" */
    val shortDisplay: String get() = "S$season E$number"

    /** True wenn ein Thumbnail vorhanden ist. */
    val hasThumbnail: Boolean get() = !thumbnailUrl.isNullOrBlank()

    /** Anzahl der verfügbaren Hoster. */
    val hosterCount: Int get() = hosters.size

    /** True wenn die Episode eine gültige URL hat. */
    val hasEpisodeUrl: Boolean get() = episodeUrl.isNotBlank()

    /** Deutsche Hoster (falls vorhanden). */
    val germanHosters: List<HosterLink>
        get() = hosters.filter { it.language.contains("Deutsch", ignoreCase = true) }

    /** Beste Hoster-Reihenfolge: Deutsche zuerst, dann nach Index. */
    val sortedHosters: List<HosterLink>
        get() = hosters.sortedWith(
            compareByDescending<HosterLink> { it.language.contains("Deutsch", ignoreCase = true) }
                .thenBy { it.index }
        )
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

    /** True wenn es ein VOE-Hoster ist. */
    val isVoe: Boolean get() = name.contains("voe", ignoreCase = true)

    /** True wenn es ein Streamtape-Hoster ist. */
    val isStreamtape: Boolean get() = name.contains("streamtape", ignoreCase = true)

    /** True wenn es ein Doodstream-Hoster ist. */
    val isDoodstream: Boolean get() = name.contains("dood", ignoreCase = true)

    /** True wenn es ein Vidoza-Hoster ist. */
    val isVidoza: Boolean get() = name.contains("vidoza", ignoreCase = true)

    /** True wenn es ein Filemoon-Hoster ist. */
    val isFilemoon: Boolean get() = name.contains("filemoon", ignoreCase = true)

    /** True wenn die Sprache Deutsch ist. */
    val isGerman: Boolean get() = language.contains("Deutsch", ignoreCase = true)

    /** True wenn die Sprache Englisch ist. */
    val isEnglish: Boolean get() = language.contains("Eng", ignoreCase = true)

    /** True wenn es ein Sub-Hoster ist (Ger-Sub, Eng-Sub). */
    val isSub: Boolean get() = language.contains("Sub", ignoreCase = true)
}
