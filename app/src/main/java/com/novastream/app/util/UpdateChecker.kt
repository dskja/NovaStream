package com.novastream.app.util

import com.novastream.app.BuildConfig
import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.model.NovaStreamConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Prüft GitHub Releases auf Updates für NovaStream.
 * Repo: dskja/NovaStream – kostenlos, kein Token nötig für öffentliche Repos.
 */
object UpdateChecker {

    const val GITHUB_REPO = "dskja/NovaStream"
    private const val RELEASES_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/$GITHUB_REPO/releases/latest"

    data class UpdateInfo(
        val latestVersion: String,
        val currentVersion: String,
        val releaseUrl: String,
        val downloadUrl: String?,
        val releaseNotes: String?,
        val publishedAt: String?,
        val isNewer: Boolean
    )

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(RELEASES_URL)
                .header("User-Agent", NovaStreamConfig.USER_AGENT)
                .header("Accept", "application/vnd.github+json")
                .build()
            val body = NetworkModule.okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.string()
            } ?: return@withContext null

            val json = JSONObject(body)
            if (json.optBoolean("draft") || json.optBoolean("prerelease")) {
                // trotzdem anzeigen, aber markieren – wir akzeptieren latest
            }
            val tag = json.optString("tag_name").removePrefix("v").trim()
            if (tag.isBlank()) return@withContext null
            val htmlUrl = json.optString("html_url").ifBlank { RELEASES_PAGE }
            val notes = json.optString("body").takeIf { it.isNotBlank() && it != "null" }
            val published = json.optString("published_at").takeIf { it.isNotBlank() && it != "null" }
            val apk = findApkAsset(json.optJSONArray("assets"))
            val current = BuildConfig.VERSION_NAME.removePrefix("v").trim()
            UpdateInfo(
                latestVersion = tag,
                currentVersion = current,
                releaseUrl = htmlUrl,
                downloadUrl = apk,
                releaseNotes = notes,
                publishedAt = published,
                isNewer = isNewerVersion(tag, current)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun releasesPageUrl(): String = RELEASES_PAGE

    private fun findApkAsset(assets: JSONArray?): String? {
        if (assets == null) return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            val url = a.optString("browser_download_url")
            if (name.endsWith(".apk", ignoreCase = true) && url.startsWith("http")) return url
        }
        return null
    }

    /** SemVer-ähnlicher Vergleich: 3.2 > 3.1.0 > 3.1 */
    fun isNewerVersion(latest: String, current: String): Boolean {
        val l = parseVersion(latest)
        val c = parseVersion(current)
        val max = maxOf(l.size, c.size)
        for (i in 0 until max) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }

    private fun parseVersion(v: String): List<Int> =
        v.trim().removePrefix("v")
            .split('.', '-', '_')
            .mapNotNull { part -> part.filter { it.isDigit() }.toIntOrNull() }
}
