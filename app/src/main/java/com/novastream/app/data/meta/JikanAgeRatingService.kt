package com.novastream.app.data.meta

/**
 * @deprecated Use [JikanMetaService] directly. Kept for backward compatibility.
 */
object JikanAgeRatingService {

    suspend fun lookup(malId: Int): String? = JikanMetaService.lookupRating(malId)

    fun isAdultFromRating(rating: String?): Boolean? = JikanMetaService.isAdultFromRating(rating)
}
