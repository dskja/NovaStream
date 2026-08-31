package com.novastream.app.data.model

/** Eine Serie / ein Titel im Katalog. */
data class Series(
    val id: String,           // Slug, z.B. "breaking-bad"
    val title: String,
    val coverUrl: String? = null,
    val detailUrl: String = "",
    val year: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val rating: String? = null,
    val backdropUrl: String? = null,
    val originalTitle: String? = null,
    val status: String? = null,
    val seasonCount: Int? = null,
    val isMovie: Boolean = false
) {
    val absoluteDetailUrl: String
        get() = NovaStreamConfig.abs(detailUrl.ifBlank { "/serie/$id" })

    /** True wenn ein Cover-Bild vorhanden ist. */
    val hasCover: Boolean get() = !coverUrl.isNullOrBlank()

    /** Initialen des Titels für Fallback-Anzeige (max 2 Zeichen). */
    val initials: String
        get() = title.takeIf { it.isNotBlank() }?.take(2)?.uppercase() ?: "—"

    /** True wenn ein Jahr vorhanden ist. */
    val hasYear: Boolean get() = !year.isNullOrBlank()

    /** True wenn eine Beschreibung vorhanden ist. */
    val hasDescription: Boolean get() = !description.isNullOrBlank()

    /** True wenn Genres vorhanden sind. */
    val hasGenres: Boolean get() = genres.isNotEmpty()

    /** True wenn ein Rating vorhanden ist. */
    val hasRating: Boolean get() = !rating.isNullOrBlank()

    /** True wenn ein Backdrop vorhanden ist. */
    val hasBackdrop: Boolean get() = !backdropUrl.isNullOrBlank()

    /** Display-Format: "Title (2024)" oder nur "Title" wenn kein Jahr. */
    val displayTitle: String
        get() = if (hasYear) "$title ($year)" else title

    /** Genres als kommagetrennter String. */
    val genresLabel: String
        get() = genres.joinToString(", ")

    /** Kurze Beschreibung (max 150 Zeichen) für Vorschau. */
    val shortDescription: String
        get() = description?.take(150)?.let { if ((description?.length ?: 0) > 150) "$it…" else it } ?: ""

    /** Bestes verfügbares Bild (Backdrop bevorzugt für Hero, sonst Cover). */
    val bestImageUrl: String?
        get() = backdropUrl?.takeIf { it.isNotBlank() } ?: coverUrl
}

/**
 * Strukturierte Startseiten-Sektionen aus dem Scraper.
 * Ersetzt das frühere Round-Robin-Splitten einer flachen Liste.
 */
data class HomeCatalog(
    val hero: List<Series> = emptyList(),
    val popular: List<Series> = emptyList(),
    val newest: List<Series> = emptyList(),
    val trending: List<Series> = emptyList(),
    val latestEpisodes: List<LatestEpisode> = emptyList(),
    val topShows: List<Series> = emptyList(),
    val all: List<Series> = emptyList()
) {
    val isEmpty: Boolean
        get() = hero.isEmpty() && popular.isEmpty() && newest.isEmpty() &&
            trending.isEmpty() && all.isEmpty()

    /** Flache, deduplizierte Serienliste (Hero zuerst). */
    fun flattened(): List<Series> {
        val seen = linkedMapOf<String, Series>()
        for (s in hero + topShows + trending + popular + newest + all) {
            if (!seen.containsKey(s.id)) seen[s.id] = s
        }
        return seen.values.toList()
    }
}

/** Ein Eintrag aus „Neue Episoden“. */
data class LatestEpisode(
    val seriesSlug: String,
    val seriesTitle: String,
    val season: Int,
    val episode: Int,
    val language: String = "",
    val timeLabel: String = "",
    val episodeUrl: String = "",
    val coverUrl: String? = null
) {
    val displayTitle: String get() = "$seriesTitle · S${season}E$episode"
    val shortDisplay: String get() = "S$season E$episode"
}

/** Genre-Eintrag (Slug + Anzeigename). */
data class Genre(
    val slug: String,
    val name: String
) {
    val displayName: String get() = name.ifBlank { slug.replaceFirstChar { it.uppercase() } }
}
