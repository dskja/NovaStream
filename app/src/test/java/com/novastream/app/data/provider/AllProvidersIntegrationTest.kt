package com.novastream.app.data.provider

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.novastream.app.data.model.Episode
import com.novastream.app.util.CaptchaWebViewFetcher
import com.novastream.app.util.VoeWebViewResolver
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Live integration smoke for all [ProviderManager.providers].
 * Validates home, search, detail, and hoster discovery per provider.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AllProvidersIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CaptchaWebViewFetcher.setContext(context)
        VoeWebViewResolver.setContext(context)
    }

    @Test
    fun allProviders_fullSmokeReport() = runBlocking {
        val results = mutableListOf<ProviderSmokeResult>()
        for (provider in ProviderManager.providers) {
            results += runProviderSmoke(provider)
        }
        printReport(results)
        val hardFailures = results.count { it.status == ProviderSmokeStatus.FAIL }
        val passes = results.count { it.status == ProviderSmokeStatus.PASS }
        assertTrue(
            "At least half of providers must pass full smoke (passed=$passes, failed=$hardFailures, total=${results.size})\n" +
                results.joinToString("\n") { "  ${it.summary()}" },
            passes >= (results.size + 1) / 2
        )
    }

    private suspend fun runProviderSmoke(provider: StreamingProvider): ProviderSmokeResult {
        ActiveProvider.set(provider)
        val query = searchQueryFor(provider)
        val notes = mutableListOf<String>()
        return try {
            withTimeout(120_000) {
                val home = provider.loadHome()
                val homeCount = home.getOrNull()?.size ?: 0
                if (home.isError) notes += "home: ${home.errorOrNull()}"
                if (homeCount == 0) notes += "home empty"

                val search = provider.search(query)
                val searchResults = search.getOrNull().orEmpty()
                if (search.isError) notes += "search: ${search.errorOrNull()}"
                if (searchResults.isEmpty()) notes += "search empty for '$query'"

                val candidate = searchResults.firstOrNull()
                    ?: home.getOrNull()?.firstOrNull()
                if (candidate == null) {
                    return@withTimeout ProviderSmokeResult(
                        providerId = provider.id,
                        displayName = provider.displayName,
                        status = if (homeCount > 0) ProviderSmokeStatus.PARTIAL else ProviderSmokeStatus.FAIL,
                        homeCount = homeCount,
                        searchCount = 0,
                        detailOk = false,
                        hosterCount = 0,
                        notes = notes
                    )
                }

                val detail = provider.loadSeriesDetail(candidate.id)
                val (series, seasons) = detail.getOrNull() ?: (null to emptyList())
                if (detail.isError) notes += "detail: ${detail.errorOrNull()}"

                val episode = seasons.flatMap { it.episodes }.firstOrNull()
                    ?: if (series != null && candidate.isMovie) {
                        Episode(
                            number = 1,
                            title = series.title,
                            slug = candidate.id,
                            season = 1,
                            episodeUrl = candidate.detailUrl.ifBlank { candidate.absoluteDetailUrl }
                        )
                    } else {
                        val seasonNum = seasons.firstOrNull()?.number ?: 1
                        when (val seasonRes = provider.loadSeason(candidate.id, seasonNum)) {
                            is StreamingProvider.ProviderResult.Success ->
                                seasonRes.data.firstOrNull()?.copy(
                                    slug = candidate.id,
                                    season = seasonNum
                                )
                            else -> null
                        }
                    }

                var hosterCount = 0
                if (episode != null) {
                    when (val hosters = provider.loadHosters(episode)) {
                        is StreamingProvider.ProviderResult.Success -> hosterCount = hosters.data.size
                        is StreamingProvider.ProviderResult.Error -> notes += "hosters: ${hosters.message}"
                    }
                } else {
                    notes += "no episode"
                }

                val status = when {
                    homeCount > 0 && searchResults.isNotEmpty() && detail.isSuccess && hosterCount > 0 ->
                        ProviderSmokeStatus.PASS
                    homeCount > 0 && (searchResults.isNotEmpty() || detail.isSuccess) ->
                        ProviderSmokeStatus.PARTIAL
                    else -> ProviderSmokeStatus.FAIL
                }

                ProviderSmokeResult(
                    providerId = provider.id,
                    displayName = provider.displayName,
                    status = status,
                    homeCount = homeCount,
                    searchCount = searchResults.size,
                    detailOk = detail.isSuccess,
                    hosterCount = hosterCount,
                    notes = notes
                )
            }
        } catch (e: Exception) {
            notes += e.message.orEmpty()
            ProviderSmokeResult(
                providerId = provider.id,
                displayName = provider.displayName,
                status = ProviderSmokeStatus.FAIL,
                homeCount = 0,
                searchCount = 0,
                detailOk = false,
                hosterCount = 0,
                notes = notes
            )
        }
    }

    private fun searchQueryFor(provider: StreamingProvider): String = when (provider.id) {
        "aniworld" -> "Naruto"
        "kinoger", "filmpalast", "kinoz", "megakino", "hydrahd", "cinezo" -> "Avatar"
        "dramacool" -> "Squid Game"
        "freecatalog" -> "Breaking Bad"
        else -> "Dark"
    }

    private fun printReport(results: List<ProviderSmokeResult>) {
        println("\n=== NovaStream Provider Smoke Report ===")
        results.forEach { println(it.summary()) }
        val pass = results.count { it.status == ProviderSmokeStatus.PASS }
        val partial = results.count { it.status == ProviderSmokeStatus.PARTIAL }
        val fail = results.count { it.status == ProviderSmokeStatus.FAIL }
        println("PASS=$pass PARTIAL=$partial FAIL=$fail TOTAL=${results.size}")
    }

    private enum class ProviderSmokeStatus { PASS, PARTIAL, FAIL }

    private data class ProviderSmokeResult(
        val providerId: String,
        val displayName: String,
        val status: ProviderSmokeStatus,
        val homeCount: Int,
        val searchCount: Int,
        val detailOk: Boolean,
        val hosterCount: Int,
        val notes: List<String>
    ) {
        fun summary(): String {
            val noteText = if (notes.isEmpty()) "" else " [" + notes.joinToString("; ") + "]"
            return "${status.name.padEnd(7)} $displayName ($providerId) home=$homeCount search=$searchCount detail=$detailOk hosters=$hosterCount$noteText"
        }
    }
}
