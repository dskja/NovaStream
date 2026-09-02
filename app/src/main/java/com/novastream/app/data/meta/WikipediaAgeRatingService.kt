package com.novastream.app.data.meta

import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.model.NovaStreamConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * Wikipedia MediaWiki API — free, no API key.
 * Extracts FSK/MPAA/BBFC certifications from article wikitext infoboxes.
 */
object WikipediaAgeRatingService {

    private val endpoints = listOf(
        "https://de.wikipedia.org/w/api.php",
        "https://en.wikipedia.org/w/api.php"
    )

    private val certificationPatterns = listOf(
        Pattern.compile("""\|\s*(?:fsk|altersfreigabe)\s*=\s*([^|\n}]+)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\|\s*(?:mpaa|mpaa_rating|mpaa-film)\s*=\s*([^|\n}]+)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\|\s*(?:bbfc|bbfc_rating)\s*=\s*([^|\n}]+)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\|\s*(?:certification|content_rating|rating)\s*=\s*([^|\n}]+)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bFSK\s*(\d{1,2})\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(TV-MA|TV-14|TV-PG|TV-G|NC-17|PG-13|R)\b""", Pattern.CASE_INSENSITIVE)
    )

    private val client get() = NetworkModule.okHttpClient

    suspend fun lookup(title: String, languages: List<String> = listOf("de", "en")): List<String> =
        withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext emptyList()
            val found = linkedSetOf<String>()
            for (lang in languages) {
                val endpoint = when (lang.take(2)) {
                    "de" -> endpoints[0]
                    else -> endpoints[1]
                }
                val pageTitle = searchPageTitle(title, endpoint) ?: continue
                val wikitext = fetchWikitext(pageTitle, endpoint) ?: continue
                found.addAll(parseCertifications(wikitext))
                if (found.isNotEmpty()) break
            }
            found.toList()
        }

    fun parseCertifications(wikitext: String): List<String> {
        if (wikitext.isBlank()) return emptyList()
        val sample = wikitext.replace("<!--", " ").replace("-->", " ")
        val results = linkedSetOf<String>()
        for (pattern in certificationPatterns) {
            val matcher = pattern.matcher(sample)
            while (matcher.find()) {
                val raw = matcher.group(1) ?: matcher.group(0)
                normalizeCertification(raw)?.let { results.add(it) }
            }
        }
        return results.toList()
    }

    internal fun normalizeCertification(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw
            .replace(Regex("\\[\\[([^|\\]]+\\|)?([^\\]]+)\\]\\]"), "$2")
            .replace("{{", "")
            .replace("}}", "")
            .replace("<br>", " ")
            .replace("<br/>", " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('|', '{', '}', ' ')
        if (cleaned.isBlank() || cleaned.equals("n/a", true) || cleaned == "—") return null
        val fsk = Regex("""(\d{1,2})""").find(cleaned)?.groupValues?.get(1)
        return when {
            cleaned.matches(Regex("""(?i)fsk\s*\d+""")) -> cleaned.uppercase().replace("FSK", "FSK ").replace(Regex("\\s+"), " ").trim()
            fsk != null && cleaned.length <= 4 -> "FSK $fsk"
            cleaned.matches(Regex("""(?i)tv-?\w+""")) -> cleaned.uppercase().replace("TV", "TV-")
            else -> cleaned
        }
    }

    private suspend fun searchPageTitle(query: String, endpoint: String): String? {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "$endpoint?action=query&list=search&srsearch=$q&srlimit=3&format=json"
        val json = get(url) ?: return null
        val hits = JSONObject(json).optJSONObject("query")?.optJSONArray("search") ?: return null
        if (hits.length() == 0) return null
        return hits.getJSONObject(0).optString("title").takeIf { it.isNotBlank() }
    }

    private suspend fun fetchWikitext(title: String, endpoint: String): String? {
        val t = URLEncoder.encode(title, "UTF-8")
        val url = "$endpoint?action=query&prop=revisions&rvprop=content&rvslots=main&titles=$t&format=json"
        val json = get(url) ?: return null
        val pages = JSONObject(json).optJSONObject("query")?.optJSONObject("pages") ?: return null
        val keys = pages.keys()
        if (!keys.hasNext()) return null
        val page = pages.getJSONObject(keys.next())
        return page.optJSONArray("revisions")
            ?.optJSONObject(0)
            ?.optJSONObject("slots")
            ?.optJSONObject("main")
            ?.optString("*")
            ?.takeIf { it.isNotBlank() }
            ?: page.optJSONArray("revisions")
                ?.optJSONObject(0)
                ?.optJSONObject("slots")
                ?.optJSONObject("main")
                ?.optString("content")
                ?.takeIf { it.isNotBlank() }
    }

    private fun get(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", NovaStreamConfig.USER_AGENT)
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }
}
