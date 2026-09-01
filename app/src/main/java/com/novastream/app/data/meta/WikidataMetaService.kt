package com.novastream.app.data.meta

import com.novastream.app.data.api.NetworkModule
import com.novastream.app.data.model.NovaStreamConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Wikidata REST + SPARQL — free, no API key.
 * Used for cross-language titles and external ID cross-refs (IMDb, TMDB via P345/P4983/P4947).
 */
object WikidataMetaService {

    private const val API = "https://www.wikidata.org/w/api.php"
    private const val SPARQL = "https://query.wikidata.org/sparql"
    private val client get() = NetworkModule.okHttpClient

    suspend fun searchEntities(
        query: String,
        language: String = "en",
        limit: Int = 8
    ): List<WikidataEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val lang = language.take(2).ifBlank { "en" }
        val json = get(
            "$API?action=wbsearchentities&search=$q&language=$lang&format=json&limit=${limit.coerceIn(1, 20)}"
        ) ?: return@withContext emptyList()
        val arr = JSONObject(json).optJSONArray("search") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                parseSearchHit(arr.getJSONObject(i))?.let { add(it) }
            }
        }
    }

    suspend fun resolveExternalIds(
        title: String,
        language: String = "en",
        imdbHint: String? = null,
        tvmazeHint: String? = null
    ): ExternalIds = withContext(Dispatchers.IO) {
        if (!imdbHint.isNullOrBlank()) {
            lookupByImdb(imdbHint)?.let { return@withContext it }
        }
        val entities = searchEntities(title, language, limit = 5)
        val best = entities.firstOrNull { FreeMetaService.titlesSimilar(title, it.label) }
            ?: entities.firstOrNull()
            ?: return@withContext ExternalIds()
        entityData(best.id) ?: ExternalIds(wikidataId = best.id)
    }

    suspend fun labelsForEntity(entityId: String, languages: List<String>): Map<String, String> =
        withContext(Dispatchers.IO) {
            val data = entityDataRaw(entityId) ?: return@withContext emptyMap()
            val labels = data.optJSONObject("labels") ?: return@withContext emptyMap()
            buildMap {
                for (lang in languages) {
                    val label = labels.optJSONObject(lang)?.optString("value")
                    if (!label.isNullOrBlank()) put(lang, label)
                }
            }
        }

    private suspend fun lookupByImdb(imdbId: String): ExternalIds? = withContext(Dispatchers.IO) {
        val id = imdbId.trim()
        if (!id.startsWith("tt")) return@withContext null
        val sparql = """
            SELECT ?item ?tmdbTv ?tmdbMovie WHERE {
              ?item wdt:P345 "$id" .
              OPTIONAL { ?item wdt:P4983 ?tmdbTv. }
              OPTIONAL { ?item wdt:P4947 ?tmdbMovie. }
            } LIMIT 1
        """.trimIndent()
        val result = sparqlQuery(sparql) ?: return@withContext ExternalIds(imdbId = id)
        val binding = result.optJSONArray("results")?.optJSONObject(0)
            ?: return@withContext ExternalIds(imdbId = id)
        ExternalIds(
            imdbId = id,
            wikidataId = binding.optString("item").substringAfterLast('/').ifBlank { null },
            tmdbId = binding.optString("tmdbTv").toIntOrNull()
                ?: binding.optString("tmdbMovie").toIntOrNull()
        )
    }

    suspend fun entityData(entityId: String): ExternalIds? = withContext(Dispatchers.IO) {
        val raw = entityDataRaw(entityId) ?: return@withContext null
        parseExternalIdsFromEntity(entityId, raw)
    }

    private suspend fun entityDataRaw(entityId: String): JSONObject? {
        val id = entityId.removePrefix("Q")
        val qid = if (entityId.startsWith("Q")) entityId else "Q$id"
        val json = get("https://www.wikidata.org/wiki/Special:EntityData/$qid.json")
            ?: return null
        return JSONObject(json).optJSONObject("entities")?.optJSONObject(qid)
    }

    fun parseSearchHit(obj: JSONObject): WikidataEntity? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val label = obj.optString("label").takeIf { it.isNotBlank() } ?: return null
        return WikidataEntity(id = id, label = label, description = obj.optString("description"))
    }

    fun parseExternalIdsFromEntity(entityId: String, entity: JSONObject): ExternalIds {
        val claims = entity.optJSONObject("claims") ?: return ExternalIds(wikidataId = entityId)
        return ExternalIds(
            wikidataId = entityId,
            imdbId = claimString(claims, "P345"),
            tmdbId = claimInt(claims, "P4983") ?: claimInt(claims, "P4947")
        )
    }

    fun parseSparqlBindings(json: JSONObject): List<JSONObject> {
        val bindings = json.optJSONObject("results")?.optJSONArray("bindings") ?: return emptyList()
        return buildList {
            for (i in 0 until bindings.length()) {
                val row = bindings.getJSONObject(i)
                val mapped = JSONObject()
                val keys = row.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    mapped.put(key, row.getJSONObject(key).optString("value"))
                }
                add(mapped)
            }
        }
    }

    private fun claimString(claims: JSONObject, property: String): String? {
        val arr = claims.optJSONArray(property) ?: return null
        if (arr.length() == 0) return null
        return arr.getJSONObject(0)
            .optJSONObject("mainsnak")
            ?.optJSONObject("datavalue")
            ?.optString("value")
            ?.takeIf { it.isNotBlank() }
    }

    private fun claimInt(claims: JSONObject, property: String): Int? =
        claimString(claims, property)?.toIntOrNull()

    private suspend fun sparqlQuery(query: String): JSONObject? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val json = get("$SPARQL?format=json&query=$encoded") ?: return null
        val bindings = parseSparqlBindings(JSONObject(json))
        val results = JSONArray()
        bindings.forEach { results.put(it) }
        return JSONObject().put("results", results)
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

data class WikidataEntity(
    val id: String,
    val label: String,
    val description: String? = null
)
