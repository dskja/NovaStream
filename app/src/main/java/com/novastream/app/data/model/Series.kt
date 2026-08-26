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
}
