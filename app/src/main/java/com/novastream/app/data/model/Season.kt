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

    /** Erste Episode der Staffel oder null. */
    val firstEpisode: Episode? get() = episodes.minByOrNull { it.number }

    /** Letzte Episode der Staffel oder null. */
    val lastEpisode: Episode? get() = episodes.maxByOrNull { it.number }

    /** Gesamtanzahl aller Hoster über alle Episoden. */
    val totalHosters: Int get() = episodes.sumOf { it.hosterCount }

    /** Episoden sortiert nach Nummer. */
    val sortedEpisodes: List<Episode> get() = episodes.sortedBy { it.number }

    /** True wenn die Staffel Episoden mit Thumbnails hat. */
    val hasThumbnails: Boolean get() = episodes.any { it.hasThumbnail }

    /** Finde eine Episode nach Nummer. */
    fun episodeByNumber(number: Int): Episode? = episodes.find { it.number == number }

    /** True wenn eine Episode mit der gegebenen Nummer existiert. */
    fun hasEpisode(number: Int): Boolean = episodes.any { it.number == number }
}
