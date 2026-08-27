package com.novastream.app.data.model

/** Eine Serie auf serienstream.to. */
data class Series(
    val id: String,           // Slug, z.B. "breaking-bad"
    val title: String,
    val coverUrl: String? = null,
    val detailUrl: String = "",
    val year: String? = null,
    val description: String? = null
) {
    val absoluteDetailUrl: String
        get() = NovaStreamConfig.abs(detailUrl.ifBlank { "/serie/$id" })

    /** True wenn ein Cover-Bild vorhanden ist. */
    val hasCover: Boolean get() = !coverUrl.isNullOrBlank()

    /** Initialen des Titels für Fallback-Anzeige (max 2 Zeichen). */
    val initials: String
        get() = title.takeIf { it.isNotBlank() }?.take(2)?.uppercase() ?: "—"
}
