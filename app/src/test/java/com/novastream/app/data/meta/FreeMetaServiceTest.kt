package com.novastream.app.data.meta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeMetaServiceTest {

    @Test
    fun titlesSimilar_matchesNormalizedTitles() {
        assertTrue(FreeMetaService.titlesSimilar("Breaking Bad", "Breaking Bad"))
        assertTrue(FreeMetaService.titlesSimilar("Game of Thrones", "Game Of Thrones"))
    }

    @Test
    fun titlesSimilar_rejectsUnrelatedTitles() {
        assertFalse(FreeMetaService.titlesSimilar("Naruto", "One Piece"))
        assertFalse(FreeMetaService.titlesSimilar("Dark", "Stranger Things"))
    }
}
