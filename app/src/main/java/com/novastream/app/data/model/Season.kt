package com.novastream.app.data.model

/** Eine Staffel einer Serie. */
data class Season(
    val number: Int,
    val title: String = "Staffel $number",
    val episodes: List<Episode> = emptyList()
)
