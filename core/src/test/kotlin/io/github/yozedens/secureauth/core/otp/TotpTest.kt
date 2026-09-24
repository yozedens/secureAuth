package io.github.yozedens.secureauth.core.otp

import io.github.yozedens.secureauth.core.model.Algorithm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class TotpTest {

    /** RFC 6238 Appendix B: each algorithm has its own seed length. */
    private val seeds = mapOf(
        Algorithm.SHA1 to "12345678901234567890",
        Algorithm.SHA256 to "12345678901234567890123456789012",
        Algorithm.SHA512 to "1234567890123456789012345678901234567890123456789012345678901234",
    ).mapValues { it.value.toByteArray(Charsets.US_ASCII) }

    private val vectors = listOf(
        Triple(59L, Algorithm.SHA1, "94287082"),
        Triple(59L, Algorithm.SHA256, "46119246"),
        Triple(59L, Algorithm.SHA512, "90693936"),
        Triple(1111111109L, Algorithm.SHA1, "07081804"),
        Triple(1111111109L, Algorithm.SHA256, "68084774"),
        Triple(1111111109L, Algorithm.SHA512, "25091201"),
        Triple(1111111111L, Algorithm.SHA1, "14050471"),
        Triple(1111111111L, Algorithm.SHA256, "67062674"),
        Triple(1111111111L, Algorithm.SHA512, "99943326"),
        Triple(1234567890L, Algorithm.SHA1, "89005924"),
        Triple(1234567890L, Algorithm.SHA256, "91819424"),
        Triple(1234567890L, Algorithm.SHA512, "93441116"),
        Triple(2000000000L, Algorithm.SHA1, "69279037"),
        Triple(2000000000L, Algorithm.SHA256, "90698825"),
        Triple(2000000000L, Algorithm.SHA512, "38618901"),
        Triple(20000000000L, Algorithm.SHA1, "65353130"),
        Triple(20000000000L, Algorithm.SHA256, "77737706"),
        Triple(20000000000L, Algorithm.SHA512, "47863826"),
    )

    @Test
    fun rfc6238TestVectors() {
        for ((time, algorithm, otp) in vectors) {
            val clock = Clock.fixed(Instant.ofEpochSecond(time), ZoneOffset.UTC)
            assertEquals("$algorithm @ $time", otp, Totp.generate(seeds.getValue(algorithm), clock, 30, algorithm, 8))
        }
    }

    @Test
    fun wrongSeedLengthGivesDifferentResult() {
        // Common implementation bug: reusing the 20-byte SHA1 seed for SHA256.
        val wrong = Totp.generate(seeds.getValue(Algorithm.SHA1), 59, 30, Algorithm.SHA256, 8)
        assertEquals("32247374", wrong)
        assertNotEquals("46119246", wrong)
    }

    @Test
    fun timeStepIsSixtyFourBit() {
        assertEquals(666666666L, Totp.timeStep(20000000000L, 30))
        assertEquals(100_000_000_000L, Totp.timeStep(3_000_000_000_000L, 30))
    }

    @Test
    fun remainingSeconds() {
        assertEquals(30, Totp.remainingSeconds(0, 30))
        assertEquals(1, Totp.remainingSeconds(59, 30))
        assertEquals(30, Totp.remainingSeconds(60, 30))
        assertEquals(30, Totp.remainingSeconds(1234567890, 30))
        assertEquals(29, Totp.remainingSeconds(1111111111, 30))
        assertEquals(60, Totp.remainingSeconds(120, 60))
    }

    @Test
    fun codeChangesAtPeriodBoundary() {
        val key = seeds.getValue(Algorithm.SHA1)
        val a = Totp.generate(key, 89, 30, Algorithm.SHA1, 6)
        assertEquals(a, Totp.generate(key, 60, 30, Algorithm.SHA1, 6))
        assertNotEquals(a, Totp.generate(key, 90, 30, Algorithm.SHA1, 6))
    }

    @Test
    fun rejectsInvalidPeriodAndNegativeTime() {
        assertTrue(runCatching { Totp.timeStep(0, 0) }.isFailure)
        assertTrue(runCatching { Totp.remainingSeconds(0, 301) }.isFailure)
        assertTrue(runCatching { Totp.generate(seeds.getValue(Algorithm.SHA1), -1, 30, Algorithm.SHA1, 6) }.isFailure)
    }
}
