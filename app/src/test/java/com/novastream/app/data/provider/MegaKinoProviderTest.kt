package com.novastream.app.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MegaKinoProviderTest {

    private val provider = MegaKinoProvider()
    private val base = "https://megakino17.com"

    @Test
    fun parseContentList_readsDlePosterGrid() {
        val html = """
            <div id="dle-content">
              <a class="poster grid-item" href="/serials/6406-reacher-4-staffel.html">
                <div class="poster__img"><img data-src="/uploads/poster.webp"></div>
                <h3 class="poster__title">Reacher - 4 Staffel</h3>
              </a>
              <a class="poster grid-item" href="/films/6456-mutiny.html">
                <h3 class="poster__title">Mutiny</h3>
              </a>
            </div>
        """.trimIndent()
        val items = provider.parseContentList(html, base)
        assertEquals(2, items.size)
        assertTrue(items.any { it.id == "serials/6406-reacher-4-staffel.html" && !it.isMovie })
        assertTrue(items.any { it.id == "films/6456-mutiny.html" && it.isMovie })
    }

    @Test
    fun parseEpisodes_readsSeasonSelectOptions() {
        val html = """
            <select class="se-select">
              <option value="ep1">Episode 1</option>
              <option value="ep2">Episode 2</option>
            </select>
            <select id="ep1"><option value="https://voe.sx/e/test1">Voe</option></select>
            <select id="ep2"><option value="https://voe.sx/e/test2">Voe</option></select>
        """.trimIndent()
        val episodes = provider.parseEpisodes(html, "serials/6406-reacher-4-staffel.html", 4)
        assertEquals(2, episodes.size)
        assertEquals("serials/6406-reacher-4-staffel.html|ep1", episodes[0].episodeUrl)
        assertEquals(1, episodes[0].number)
    }

    @Test
    fun parseHosters_readsEpisodeServerSelect() {
        val html = """
            <select id="ep1">
              <option value="https://voe.sx/e/luyeihgucqcb">Voe</option>
            </select>
        """.trimIndent()
        val hosters = provider.parseHosters(html, "serials/6406-reacher-4-staffel.html", "ep1")
        assertEquals(1, hosters.size)
        assertEquals("Voe", hosters[0].name)
        assertTrue(hosters[0].redirectUrl.contains("voe.sx"))
    }

    @Test
    fun parseHosters_readsMovieIframeTabs() {
        val html = """
            <div class="tabs-block__select"><span>Voe</span><span>Mirror</span></div>
            <div class="tabs-block__content"><iframe data-src="https://voe.sx/e/qz3yqhaqsy86"></iframe></div>
            <div class="tabs-block__content d-none"><iframe src="https://watch.example/watch?v=abc"></iframe></div>
        """.trimIndent()
        val hosters = provider.parseHosters(html, "films/6456-mutiny.html")
        assertFalse(hosters.isEmpty())
        assertEquals("Voe", hosters[0].name)
    }

    @Test
    fun providerHttp_dleCatalogSignalsAreNotChallenge() {
        val html = buildString {
            repeat(20) {
                append("""<a href="/films/$it-movie.html">Movie</a>""")
            }
            append("""<div id="dle-content"><a class="poster grid-item" href="/serials/1-show.html">Show</a></div>""")
        }
        assertFalse(ProviderHttp.isChallenge(html))
    }
}
