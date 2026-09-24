package io.github.yozedens.secureauth.core.otp

import io.github.yozedens.secureauth.core.model.Algorithm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HotpTest {

    private val rfcSecret = "12345678901234567890".toByteArray(Charsets.US_ASCII)

    /** RFC 4226 Appendix D. */
    @Test
    fun rfc4226TestVectors() {
        val expected = listOf(
            "755224", "287082", "359152", "969429", "338314",
            "254676", "287922", "162583", "399871", "520489",
        )
        expected.forEachIndexed { counter, otp ->
            assertEquals("counter $counter", otp, Hotp.generate(rfcSecret, counter.toLong(), Algorithm.SHA1, 6))
        }
    }

    @Test
    fun padsLeadingZeros() {
        // RFC 6238 vector at T=1111111109 truncates to a value with a leading zero.
        assertEquals("07081804", Hotp.generate(rfcSecret, 1111111109L / 30, Algorithm.SHA1, 8))
    }

    @Test
    fun sevenDigitsIsSuffixOfEight() {
        val eight = Hotp.generate(rfcSecret, 1, Algorithm.SHA1, 8)
        assertEquals(eight.takeLast(7), Hotp.generate(rfcSecret, 1, Algorithm.SHA1, 7))
        assertEquals(eight.takeLast(6), Hotp.generate(rfcSecret, 1, Algorithm.SHA1, 6))
    }

    @Test
    fun outputIsAsciiDigitsRegardlessOfDefaultLocale() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"))
            val otp = Hotp.generate(rfcSecret, 0, Algorithm.SHA1, 6)
            assertEquals("755224", otp)
            assertTrue(otp.all { it in '0'..'9' })
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun rejectsInvalidArguments() {
        assertTrue(runCatching { Hotp.generate(ByteArray(0), 0, Algorithm.SHA1, 6) }.isFailure)
        assertTrue(runCatching { Hotp.generate(rfcSecret, -1, Algorithm.SHA1, 6) }.isFailure)
        assertTrue(runCatching { Hotp.generate(rfcSecret, 0, Algorithm.SHA1, 5) }.isFailure)
        assertTrue(runCatching { Hotp.generate(rfcSecret, 0, Algorithm.SHA1, 9) }.isFailure)
    }

    @Test
    fun handlesLargeCounters() {
        assertEquals(6, Hotp.generate(rfcSecret, Long.MAX_VALUE, Algorithm.SHA512, 6).length)
    }
}
