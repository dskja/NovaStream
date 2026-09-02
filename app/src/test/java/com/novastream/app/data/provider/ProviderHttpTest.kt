package com.novastream.app.data.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderHttpTest {

    @Test
    fun `blank html is challenge`() {
        assertTrue(ProviderHttp.isChallenge(""))
        assertTrue(ProviderHttp.isChallenge("   "))
    }

    @Test
    fun `short cloudflare wall is challenge`() {
        val html = "<html><body>Just a moment... checking your browser</body></html>"
        assertTrue(ProviderHttp.isChallenge(html))
    }

    @Test
    fun `catalog page with recaptcha script is not challenge`() {
        val html = buildString {
            repeat(80) { append("<div class=\"item\"><a href=\"/serie/dark\">Dark</a></div>") }
            append("<script src=\"https://www.google.com/recaptcha/api.js\"></script>")
            append("<noscript>enable javascript</noscript>")
        }
        assertFalse(ProviderHttp.isChallenge(html))
    }

    @Test
    fun `intl catalog signals are not challenge`() {
        val html = buildString {
            repeat(40) {
                append("""<a href="/pelicula/inception-$it">Inception</a>""")
            }
            append("<meta property=\"og:title\" content=\"Cuevana\"/>")
        }
        assertFalse(ProviderHttp.isChallenge(html))
    }

    @Test
    fun acceptLanguageHeader_matchesProviderLanguage() {
        assertTrue(ProviderHttp.acceptLanguageHeader("wiflix").startsWith("fr-FR"))
        assertTrue(ProviderHttp.acceptLanguageHeader("filmyonline").startsWith("pl-PL"))
        assertTrue(ProviderHttp.acceptLanguageHeader("serienstream").startsWith("de-DE"))
        assertTrue(ProviderHttp.acceptLanguageHeader("cinezo").startsWith("en-US"))
    }
}
