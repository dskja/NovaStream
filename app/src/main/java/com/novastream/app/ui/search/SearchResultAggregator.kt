package com.novastream.app.ui.search

import com.novastream.app.data.meta.AgeRatingResolver
import com.novastream.app.data.meta.FreeMetaGraph
import com.novastream.app.data.model.Series
import java.text.Normalizer
import kotlin.math.abs

object SearchResultAggregator {

    private val metaGraph = FreeMetaGraph()

    data class AggregatedResult(
        val series: Series,
        val providerIds: Set<String>,
        val score: Int
    )

    fun aggregate(results: List<Pair<String, List<Series>>>): List<Series> =
        aggregateDetailed(results).map { it.series }

    fun aggregateDetailed(results: List<Pair<String, List<Series>>>): List<AggregatedResult> {
        val buckets = linkedMapOf<String, MutableList<Pair<String, Series>>>()
        for ((providerId, list) in results) {
            for (series in list) {
                val key = dedupeKey(series)
                buckets.getOrPut(key) { mutableListOf() }.add(providerId to series)
            }
        }
        return buckets.values
            .map { entries ->
                val merged = mergeSeriesFields(entries.map { it.second })
                val providerCount = entries.map { it.first }.toSet().size
                val tagged = merged.copy(
                    providerId = entries.first().first,
                    title = merged.title.ifBlank { entries.first().second.title },
                    availableProviderCount = providerCount.takeIf { it > 1 }
                )
                AggregatedResult(
                    series = tagged,
                    providerIds = entries.map { it.first }.toSet(),
                    score = entries.maxOf { scoreSeries(it.second) }
                )
            }
            .sortedByDescending { it.score }
    }

    fun mergeSeriesFields(entries: List<Series>): Series {
        if (entries.isEmpty()) throw IllegalArgumentException("entries must not be empty")
        if (entries.size == 1) return entries.first()
        var merged = entries.maxByOrNull { scoreSeries(it) } ?: entries.first()
        var mergedIsAdult: Boolean? = merged.isAdult
        for (candidate in entries) {
            if (candidate === merged) continue
            mergedIsAdult = AgeRatingResolver.mergeIsAdult(mergedIsAdult, candidate.isAdult)
            merged = merged.copy(
                imdbId = merged.imdbId ?: candidate.imdbId,
                tvmazeId = merged.tvmazeId ?: candidate.tvmazeId,
                anilistId = merged.anilistId ?: candidate.anilistId,
                tmdbId = merged.tmdbId ?: candidate.tmdbId,
                canonicalKey = merged.canonicalKey ?: candidate.canonicalKey,
                coverUrl = merged.coverUrl ?: candidate.coverUrl,
                backdropUrl = merged.backdropUrl ?: candidate.backdropUrl,
                description = merged.description?.takeIf { it.isNotBlank() } ?: candidate.description,
                genres = if (merged.genres.isEmpty()) candidate.genres else merged.genres,
                year = merged.year ?: candidate.year,
                rating = merged.rating ?: candidate.rating,
                status = merged.status ?: candidate.status,
                originalTitle = merged.originalTitle ?: candidate.originalTitle
            )
        }
        return merged.copy(isAdult = mergedIsAdult)
    }

    fun dedupeKey(series: Series): String = metaGraph.dedupeKeyForSeries(series)

    private fun normalizeTitle(title: String): String {
        val n = Normalizer.normalize(title.lowercase(), Normalizer.Form.NFD)
        return n.replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    private fun scoreSeries(series: Series): Int {
        var score = 0
        if (series.canonicalKey != null) score += 5
        if (!series.imdbId.isNullOrBlank()) score += 4
        if (series.tvmazeId != null) score += 3
        if (series.anilistId != null) score += 3
        if (series.tmdbId != null) score += 2
        if (series.isAdult == false) score += 1
        if (series.isAdult == true) score -= 5
        if (!series.coverUrl.isNullOrBlank()) score += 2
        if (!series.year.isNullOrBlank()) score += 1
        if (!series.description.isNullOrBlank()) score += 1
        score += abs(series.title.length - 12).coerceAtMost(10)
        return score
    }
}
