package com.serienstream.app.data.model

/** Auflösbare Stream-URL eines Hosters. */
data class StreamSource(
    val hoster: String,
    val url: String,
    val mimeType: String = "application/x-mpegURL",
    val isHls: Boolean = true
)
