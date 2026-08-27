package com.novastream.app.data.model

/** Eine Staffel einer Serie. */
data class Season(
    val number: Int,
    val title: String = "Staffel $number",
    val episodes: List<Episode> = emptyList()
) {
    /** Anzahl Episoden in dieser Staffel. */
    val episodeCount: Int get() = episodes.size

    /** True wenn die Staffel Episoden hat. */
    val hasEpisodes: Boolean get() = episodes.isNotEmpty()

    /** True wenn alle Episoden mindestens einen Hoster haben. */
    val allEpisodesHaveHosters: Boolean
        get() = episodes.isNotEmpty() && episodes.all { it.hasHosters }

    /** Display-Format: "Staffel 1 (12 Episoden)". */
    val displayTitle: String
        get() = if (episodeCount > 0) "$title ($episodeCount)" else title
}
