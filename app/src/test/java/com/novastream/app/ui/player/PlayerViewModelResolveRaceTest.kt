package com.novastream.app.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerViewModelResolveRaceTest {

    @Test
    fun isResolveStale_falseForMatchingGeneration() {
        assertFalse(PlayerViewModel.isResolveStale(3, 3))
    }

    @Test
    fun isResolveStale_trueWhenUserSwitchedHoster() {
        assertTrue(PlayerViewModel.isResolveStale(2, 3))
    }

    @Test
    fun isResolveStale_trueWhenResolveWasCancelled() {
        assertTrue(PlayerViewModel.isResolveStale(5, 6))
    }
}
