package com.novastream.app.data.meta

import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.model.NovaStreamConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * AniList GraphQL API — free, no API key.
 * https://anilist.gitbook.io/anilist-apiv2-docs
 */
object AniListMetaService {

    private const val ENDPOINT = "https://graphql.anilist.co"
    private val client get() = NetworkModule.okHttpClient

    private val mediaFields = """
        id
        idMal
        isAdult
        title { romaji english native }
        description(asHtml: false)
        coverImage { large extraLarge }
        bannerImage
        averageScore
        startDate { year }
        genres
        status
        characters(perPage: 12, sort: ROLE) {
          edges {
            role
            node { name { full } image { medium } }
          }
        }
    """.trimIndent()

    suspend fun search(query: String, limit: Int = 10): List<MetaShow> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val gql = """
            query(${ '$' }search: String, ${ '$' }perPage: Int) {
              Page(page: 1, perPage: ${ '$' }perPage) {
                media(search: ${ '$' }search, type: ANIME, sort: SEARCH_MATCH) {
                  $mediaFields
                }
              }
            }
        """.trimIndent()
        val variables = JSONObject()
            .put("search", query.trim())
            .put("perPage", limit.coerceIn(1, 25))
        val body = postGraphQl(gql, variables) ?: return@withContext emptyList()
        val media = body.optJSONObject("data")
            ?.optJSONObject("Page")
            ?.optJSONArray("media")
            ?: return@withContext emptyList()
        buildList {
            for (i in 0 until media.length()) {
                parseMedia(media.getJSONObject(i))?.let { add(it) }
            }
        }
    }

    suspend fun mediaById(id: Int): MetaShow? = withContext(Dispatchers.IO) {
        if (id <= 0) return@withContext null
        val gql = """
            query(${ '$' }id: Int) {
              Media(id: ${ '$' }id, type: ANIME) {
                $mediaFields
                relations(perPage: 12) {
                  edges {
                    relationType
                    node {
                      id
                      idMal
                      isAdult
                      title { romaji english }
                      coverImage { large }
                      bannerImage
                      averageScore
                      startDate { year }
                      genres
                    }
                  }
                }
              }
            }
        """.trimIndent()
        val body = postGraphQl(gql, JSONObject().put("id", id)) ?: return@withContext null
        val media = body.optJSONObject("data")?.optJSONObject("Media") ?: return@withContext null
        parseMedia(media, withRelations = true)
    }

    suspend fun similar(id: Int, limit: Int = 12): List<MetaShow> = withContext(Dispatchers.IO) {
        val show = mediaById(id) ?: return@withContext emptyList()
        show.similar.take(limit)
    }

    suspend fun trending(limit: Int = 20): List<MetaShow> = withContext(Dispatchers.IO) {
        val gql = """
            query(${ '$' }perPage: Int) {
              Page(page: 1, perPage: ${ '$' }perPage) {
                media(type: ANIME, sort: TRENDING_DESC) {
                  $mediaFields
                }
              }
            }
        """.trimIndent()
        val body = postGraphQl(gql, JSONObject().put("perPage", limit.coerceIn(1, 50)))
            ?: return@withContext emptyList()
        val media = body.optJSONObject("data")
            ?.optJSONObject("Page")
            ?.optJSONArray("media")
            ?: return@withContext emptyList()
        buildList {
            for (i in 0 until media.length()) {
                parseMedia(media.getJSONObject(i))?.let { add(it) }
            }
        }
    }

    fun parseMedia(obj: JSONObject, withRelations: Boolean = false): MetaShow? {
        val id = obj.optInt("id", -1)
        if (id <= 0) return null
        val titles = obj.optJSONObject("title")
        val title = titles?.optString("english")?.takeIf { it.isNotBlank() && it != "null" }
            ?: titles?.optString("romaji")?.takeIf { it.isNotBlank() && it != "null" }
            ?: titles?.optString("native")?.takeIf { it.isNotBlank() && it != "null" }
            ?: return null
        val cover = obj.optJSONObject("coverImage")
        val poster = cover?.optString("extraLarge")?.ifBlank { null }
            ?: cover?.optString("large")?.ifBlank { null }
        val score = obj.optInt("averageScore", -1).takeIf { it > 0 }?.let { it / 10.0 }
        val year = obj.optJSONObject("startDate")?.optInt("year")?.takeIf { it > 0 }?.toString()
        val genres = parseStringArray(obj.optJSONArray("genres"))
        val cast = parseCharacters(obj.optJSONObject("characters"))
        val similar = if (withRelations) parseRelations(obj.optJSONObject("relations")) else emptyList()
        val isAdultFlag = obj.optBoolean("isAdult", false) ||
            genres.any { it.equals("Hentai", ignoreCase = true) }
        val idMal = obj.optInt("idMal", -1).takeIf { it > 0 }
        return MetaShow(
            id = "anilist-$id",
            title = title,
            summary = stripHtml(obj.optString("description")),
            genres = genres,
            status = obj.optString("status").takeIf { it.isNotBlank() && it != "null" },
            premiered = year?.let { "$it-01-01" },
            rating = score,
            posterUrl = poster,
            backdropUrl = obj.optString("bannerImage").takeIf { it.isNotBlank() && it != "null" },
            language = "Japanese",
            cast = cast,
            anilistId = id,
            idMal = idMal,
            mediaType = "anime",
            similar = similar,
            isAdult = if (isAdultFlag) true else null,
            contentRatingSource = if (isAdultFlag) "anilist" else null
        )
    }

    fun parseCharacters(characters: JSONObject?): List<MetaPerson> {
        val edges = characters?.optJSONArray("edges") ?: return emptyList()
        return buildList {
            for (i in 0 until minOf(edges.length(), 12)) {
                val edge = edges.getJSONObject(i)
                val node = edge.optJSONObject("node") ?: continue
                val name = node.optJSONObject("name")?.optString("full") ?: continue
                add(
                    MetaPerson(
                        name = name,
                        character = edge.optString("role").takeIf { it.isNotBlank() && it != "null" },
                        imageUrl = node.optJSONObject("image")?.optString("medium")?.ifBlank { null }
                    )
                )
            }
        }
    }

    fun parseRelations(relations: JSONObject?): List<MetaShow> {
        val edges = relations?.optJSONArray("edges") ?: return emptyList()
        return buildList {
            for (i in 0 until edges.length()) {
                val node = edges.getJSONObject(i).optJSONObject("node") ?: continue
                parseMedia(node)?.let { add(it) }
            }
        }
    }

    private fun parseStringArray(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val s = arr.optString(i)
                if (s.isNotBlank()) add(s)
            }
        }
    }

    private fun stripHtml(html: String?): String? {
        if (html.isNullOrBlank() || html == "null") return null
        return html.replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { null }
    }

    private fun postGraphQl(query: String, variables: JSONObject): JSONObject? {
        return try {
            val payload = JSONObject()
                .put("query", query)
                .put("variables", variables)
                .toString()
            val body = payload.toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url(ENDPOINT)
                .post(body)
                .header("User-Agent", NovaStreamConfig.USER_AGENT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val text = resp.body?.string() ?: return null
                JSONObject(text)
            }
        } catch (_: Exception) {
            null
        }
    }
}
