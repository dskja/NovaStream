package com.novastream.app.data.model

/** Eine Serie auf SerienStream. */
data class Series(
    val id: String,           // Slug, z.B. "breaking-bad"
    val title: String,
    val coverUrl: String? = null,
    val detailUrl: String = "",
    val year: String? = null,
    val description: String? = null
) {
    val absoluteDetailUrl: String
        get() = SerienStreamConfig.abs(detailUrl.ifBlank { "/serie/$id" })
}
