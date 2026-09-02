package com.novastream.app.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class PinHasherTest {

    @Test
    fun hashAndVerifyRoundTrip() {
        val stored = PinHasher.hash("1234")
        assertTrue(stored.startsWith("pbkdf2$"))
        assertTrue(PinHasher.verify("1234", stored))
        assertFalse(PinHasher.verify("0000", stored))
    }

    @Test
    fun legacySha256StillVerifies() {
        val legacy = MessageDigest.getInstance("SHA-256")
            .digest("5678".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertTrue(PinHasher.verify("5678", legacy))
        assertTrue(PinHasher.shouldUpgrade(legacy))
    }

    @Test
    fun pbkdf2DoesNotNeedUpgrade() {
        val stored = PinHasher.hash("9999")
        assertFalse(PinHasher.shouldUpgrade(stored))
    }
}
