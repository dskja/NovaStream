package com.novastream.app.data.meta

import com.novastream.app.util.AdultContentDetector

/**
 * Merges age-rating signals from multiple free metadata sources into [Series.isAdult] tri-state.
 */
object AgeRatingResolver {

    fun fromCertifications(certifications: List<String>): Boolean? {
        if (certifications.isEmpty()) return null
        val results = certifications.mapNotNull { AdultContentDetector.detectFromText(it) }
        if (results.any { it }) return true
        if (results.any { !it }) return false
        return null
    }

    fun merge(vararg values: Boolean?): Boolean? {
        if (values.any { it == true }) return true
        if (values.any { it == false }) return false
        return null
    }

    fun resolve(
        scraped: Boolean? = null,
        explicitAdult: Boolean? = null,
        certifications: List<String> = emptyList()
    ): AgeRatingResult {
        val fromCerts = fromCertifications(certifications)
        val isAdult = merge(scraped, explicitAdult, fromCerts)
        return AgeRatingResult(
            isAdult = isAdult,
            certifications = certifications.distinct(),
            source = when {
                explicitAdult != null -> "anilist"
                certifications.isNotEmpty() && fromCerts != null -> "certification"
                else -> null
            }
        )
    }

    fun mergeIsAdult(existing: Boolean?, incoming: Boolean?): Boolean? = merge(existing, incoming)
}
