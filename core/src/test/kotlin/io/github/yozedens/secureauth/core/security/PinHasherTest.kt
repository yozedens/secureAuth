package io.github.yozedens.secureauth.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    private val fast = 1_000

    @Test
    fun policyAcceptsSixToTwelveDigits() {
        listOf("123456", "000000", "123456789012").forEach { assertTrue(it, PinPolicy.isValid(it.toCharArray())) }
        listOf("12345", "1234567890123", "12345a", "١٢٣٤٥٦", "", "12 3456").forEach {
            assertFalse(it, PinPolicy.isValid(it.toCharArray()))
        }
    }

    @Test
    fun verifiesCorrectPinOnly() {
        val verifier = PinHasher.create("123456".toCharArray(), fast)
        assertTrue(PinHasher.matches("123456".toCharArray(), verifier))
        assertFalse(PinHasher.matches("123457".toCharArray(), verifier))
        assertFalse(PinHasher.matches("1234567".toCharArray(), verifier))
        assertEquals(PinHasher.ALGORITHM, verifier.algorithm)
        assertEquals(16, verifier.salt.size)
        assertEquals(32, verifier.hash.size)
    }

    @Test
    fun saltMakesHashesUnique() {
        val a = PinHasher.create("123456".toCharArray(), fast)
        val b = PinHasher.create("123456".toCharArray(), fast)
        assertFalse(a.salt.contentEquals(b.salt))
        assertFalse(a.hash.contentEquals(b.hash))
    }

    @Test
    fun toStringHasNoHash() {
        val verifier = PinHasher.create("123456".toCharArray(), fast)
        assertEquals("PinVerifier(algorithm=PBKDF2WithHmacSHA256, iterations=1000)", verifier.toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidPin() {
        PinHasher.create("12".toCharArray(), fast)
    }

    @Test
    fun calibrationScalesAndClamps() {
        // Fake clock: 20k iterations appear to take 10 ms -> 400 ms target needs 800k.
        var t = 0L
        val tenMs = { t.also { t += 10_000_000 } }
        assertEquals(800_000, PinHasher.calibrate(400, tenMs))
        var u = 0L
        val slow = { u.also { u += 10_000_000_000 } }
        assertEquals(PinHasher.MIN_ITERATIONS, PinHasher.calibrate(400, slow))
        var v = 0L
        val instant = { v }
        assertEquals(PinHasher.MAX_ITERATIONS, PinHasher.calibrate(400, instant))
    }

    @Test
    fun backoffSchedule() {
        (0..4).forEach { assertEquals(0L, PinBackoff.waitMillis(it)) }
        assertEquals(30_000L, PinBackoff.waitMillis(5))
        assertEquals(60_000L, PinBackoff.waitMillis(6))
        assertEquals(300_000L, PinBackoff.waitMillis(7))
        assertEquals(900_000L, PinBackoff.waitMillis(8))
        assertEquals(900_000L, PinBackoff.waitMillis(50))
    }
}
