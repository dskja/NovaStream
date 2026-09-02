package com.novastream.app.profile

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN hashing with PBKDF2-HMAC-SHA256. Legacy unsalted SHA-256 hashes are verified
 * and upgraded transparently on successful login.
 */
object PinHasher {

    private const val PREFIX = "pbkdf2"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    fun hash(pin: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val derived = derive(pin.toCharArray(), salt)
        return format(PREFIX, ITERATIONS, salt, derived)
    }

    fun verify(pin: String, stored: String?): Boolean {
        if (stored.isNullOrBlank()) return pin.isBlank()
        if (stored.startsWith("$PREFIX$")) {
            val parts = stored.split('$')
            if (parts.size != 4) return false
            val iterations = parts[1].toIntOrNull() ?: return false
            val salt = Base64.getDecoder().decode(parts[2])
            val expected = Base64.getDecoder().decode(parts[3])
            val actual = derive(pin.toCharArray(), salt, iterations)
            return constantTimeEquals(expected, actual)
        }
        return legacySha256(pin) == stored
    }

    fun shouldUpgrade(stored: String?): Boolean =
        !stored.isNullOrBlank() && !stored.startsWith("$PREFIX$")

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun format(prefix: String, iterations: Int, salt: ByteArray, hash: ByteArray): String {
        val saltB64 = Base64.getEncoder().encodeToString(salt)
        val hashB64 = Base64.getEncoder().encodeToString(hash)
        return "$prefix$$iterations$$saltB64$$hashB64"
    }

    private fun legacySha256(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
}
