package com.novastream.app.data.meta

import com.novastream.app.data.provider.ContentLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class AgeRatingPrimaryLanguageTest {

    @Test
    fun primaryForLanguage_prefersFskForGerman() {
        val result = AgeRatingResult(
            certifications = listOf("TV-MA", "FSK 16", "R"),
            source = "wikidata"
        )
        assertEquals("FSK 16", result.primaryForLanguage(ContentLanguage.DE))
    }

    @Test
    fun primaryForLanguage_prefersTvForEnglish() {
        val result = AgeRatingResult(
            certifications = listOf("FSK 12", "TV-14", "PG-13"),
            source = "wikidata"
        )
        assertEquals("TV-14", result.primaryForLanguage(ContentLanguage.EN))
    }

    @Test
    fun primaryForLanguage_fallsBackToFirst() {
        val result = AgeRatingResult(certifications = listOf("R"), source = "jikan")
        assertEquals("R", result.primaryForLanguage(ContentLanguage.DE))
    }
}
