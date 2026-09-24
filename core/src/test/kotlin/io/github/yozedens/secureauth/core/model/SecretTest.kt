package io.github.yozedens.secureauth.core.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretTest {

    @Test
    fun copiesInputSoCallerWipeDoesNotAffectIt() {
        val bytes = byteArrayOf(1, 2, 3)
        val secret = Secret(bytes)
        bytes.fill(0)
        assertArrayEquals(byteArrayOf(1, 2, 3), secret.bytesCopy())
    }

    @Test
    fun toStringNeverRevealsContent() {
        val secret = Secret("Hello!".toByteArray())
        assertEquals("Secret(***)", secret.toString())
        assertFalse(OtpAccount(meta(), secret).toString().contains("Hello"))
    }

    @Test
    fun equalityIsByContent() {
        assertEquals(Secret(byteArrayOf(1, 2)), Secret(byteArrayOf(1, 2)))
        assertEquals(Secret(byteArrayOf(1, 2)).hashCode(), Secret(byteArrayOf(1, 2)).hashCode())
        assertNotEquals(Secret(byteArrayOf(1, 2)), Secret(byteArrayOf(1, 3)))
        assertNotEquals(Secret(byteArrayOf(1)), "not a secret")
    }

    @Test
    fun useWipesTheTemporaryCopy() {
        val secret = Secret(byteArrayOf(7, 7))
        var leaked: ByteArray? = null
        val sum = secret.use { bytes ->
            leaked = bytes
            bytes.sum()
        }
        assertEquals(14, sum)
        assertArrayEquals(byteArrayOf(0, 0), leaked)
        assertArrayEquals(byteArrayOf(7, 7), secret.bytesCopy())
    }

    @Test
    fun wipeZeroesTheSecret() {
        val secret = Secret(byteArrayOf(5))
        secret.wipe()
        assertArrayEquals(byteArrayOf(0), secret.bytesCopy())
        assertEquals(1, secret.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmpty() {
        Secret(ByteArray(0))
    }

    @Test
    fun otpKindAndMetaValidateRanges() {
        assertTrue(runCatching { OtpKind.Totp(0) }.isFailure)
        assertTrue(runCatching { OtpKind.Totp(301) }.isFailure)
        assertTrue(runCatching { OtpKind.Hotp(-1) }.isFailure)
        assertTrue(runCatching { meta().copy(digits = 9) }.isFailure)
        assertEquals(30, OtpKind.Totp().periodSeconds)
        assertEquals(0L, OtpKind.Hotp().counter)
    }

    private fun meta() = AccountMeta(
        id = "id",
        issuer = "GitHub",
        accountName = "user@example.com",
        algorithm = Algorithm.SHA1,
        digits = 6,
        kind = OtpKind.Totp(),
        createdAt = 0,
    )
}
