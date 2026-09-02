package com.novastream.app.data.provider

import java.io.IOException

/**
 * Validates provider HTML responses and triggers mirror refresh on bot-walls.
 */
object ProviderHtml {

    class ChallengeException(message: String = "Bot challenge page") : IOException(message)

    fun validateOrThrow(html: String) {
        if (html.isBlank() || ProviderHttp.isChallenge(html)) throw ChallengeException()
    }
}

suspend fun MirrorSupport.requireCatalogHtml(
    fetchPage: suspend () -> String,
    fallbackUrl: String? = null
): String {
    var html = fetchPage()
    if (html.isNotBlank() && !ProviderHttp.isChallenge(html)) return html
    val retryUrl = fallbackUrl ?: "${activeBase()}/"
    html = fetch(retryUrl, webViewFallback = true)
    if (html.isNotBlank() && !ProviderHttp.isChallenge(html)) return html
    activeBase(forceRefresh = true)
    html = fetch(retryUrl, webViewFallback = true)
    if (html.isBlank() || ProviderHttp.isChallenge(html)) {
        throw ProviderHtml.ChallengeException()
    }
    return html
}
