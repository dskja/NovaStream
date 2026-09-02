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
    fun `real catalog with stream links is not challenge`() {
        val html = buildString {
            repeat(50) {
                append("""<article><a href="/stream/movie-$it">Movie $it</a><meta property="og:title" content="Test"/></article>""")
            }
        }
        assertFalse(ProviderHttp.isChallenge(html))
    }
}
