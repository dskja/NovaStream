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

    /** Wikidata properties for age/content ratings (free SPARQL, no key). */
    private val ratingProperties = listOf(
        "P1981", // FSK (Germany)
        "P1657", // MPAA
        "P2758", // US TV parental guideline
        "P3408", // BBFC (UK)
        "P2629", // CNC (France)
        "P2460", // Australian Classification Board
        "P2756", // EIRIN (Japan)
        "P2529", // CSA (France)
        "P9159"  // ICAA (Spain)
    )

    private val knownRatingLabels = mapOf(
        "Q23817737" to "FSK 0",
        "Q23817738" to "FSK 6",
        "Q23817739" to "FSK 12",
        "Q23817741" to "FSK 16",
        "Q23817740" to "FSK 18",
        "Q18665349" to "FSK 18",
        "Q18665348" to "FSK 16",
        "Q18665347" to "FSK 12",
        "Q18665346" to "FSK 6",
        "Q18665345" to "FSK 0",
        "Q215616" to "R",
        "Q18665324" to "PG-13",
        "Q18665322" to "PG",
        "Q18665321" to "G",
        "Q23649980" to "TV-MA",
        "Q23649979" to "TV-14",
        "Q23649978" to "TV-PG",
        "Q23649977" to "TV-G",
        "Q18665325" to "NC-17"
    )

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

    suspend fun resolveAgeRatingLabels(
        imdbId: String? = null,
        entityId: String? = null,
        title: String? = null,
        language: String = "de"
    ): List<String> = withContext(Dispatchers.IO) {
        val fromSparql = when {
            !imdbId.isNullOrBlank() -> ageRatingLabelsByImdb(imdbId.trim())
            !entityId.isNullOrBlank() -> ageRatingLabelsByEntity(entityId.trim())
            !title.isNullOrBlank() -> {
                val entity = searchEntities(title, language, limit = 3).firstOrNull()?.id
                if (entity != null) ageRatingLabelsByEntity(entity) else emptyList()
            }
            else -> emptyList()
        }
        if (fromSparql.isNotEmpty()) return@withContext fromSparql

        val qid = entityId?.let { if (it.startsWith("Q")) it else "Q$it" }
            ?: imdbId?.let { lookupByImdb(it)?.wikidataId }
        if (qid != null) {
            val raw = entityDataRaw(qid) ?: return@withContext emptyList()
            return@withContext parseAgeRatingsFromEntity(raw)
        }
        emptyList()
    }

    fun parseAgeRatingsFromEntity(entity: JSONObject): List<String> {
        val claims = entity.optJSONObject("claims") ?: return emptyList()
        val labels = linkedSetOf<String>()
        for (property in ratingProperties) {
            val arr = claims.optJSONArray(property) ?: continue
            for (i in 0 until arr.length()) {
                val qid = claimEntityId(arr.getJSONObject(i)) ?: continue
                knownRatingLabels[qid]?.let { labels.add(it) }
            }
        }
        return labels.toList()
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

    private fun claimEntityId(claim: JSONObject): String? {
        val value = claim.optJSONObject("mainsnak")
            ?.optJSONObject("datavalue")
            ?.optJSONObject("value")
            ?: return null
        return value.optString("id").takeIf { it.isNotBlank() }
    }

    private suspend fun ageRatingLabelsByImdb(imdbId: String): List<String> {
        if (!imdbId.startsWith("tt")) return emptyList()
        val unions = ratingProperties.joinToString("\n    UNION\n    ") { prop ->
            "{ ?item p:$prop ?st . ?st ps:$prop ?rating . }"
        }
        val sparql = """
            SELECT ?ratingLabel WHERE {
              ?item wdt:P345 "$imdbId" .
              $unions
              SERVICE wikibase:label { bd:serviceParam wikibase:language "de,en". }
              ?rating rdfs:label ?ratingLabel .
            } LIMIT 20
        """.trimIndent()
        return sparqlRatingLabels(sparql)
    }

    private suspend fun ageRatingLabelsByEntity(entityId: String): List<String> {
        val qid = if (entityId.startsWith("Q")) entityId else "Q$entityId"
        val unions = ratingProperties.joinToString("\n    UNION\n    ") { prop ->
            "{ ?item p:$prop ?st . ?st ps:$prop ?rating . }"
        }
        val sparql = """
            SELECT ?ratingLabel WHERE {
              BIND(wd:$qid AS ?item)
              $unions
              SERVICE wikibase:label { bd:serviceParam wikibase:language "de,en". }
              ?rating rdfs:label ?ratingLabel .
            } LIMIT 20
        """.trimIndent()
        return sparqlRatingLabels(sparql)
    }

    private suspend fun sparqlRatingLabels(query: String): List<String> {
        val result = sparqlQuery(query) ?: return emptyList()
        val arr = result.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val label = arr.getJSONObject(i).optString("ratingLabel").trim()
                if (label.isNotBlank()) add(label)
            }
        }.distinct()
    }

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
