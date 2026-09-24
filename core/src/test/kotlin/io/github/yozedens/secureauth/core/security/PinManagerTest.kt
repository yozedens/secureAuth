package io.github.yozedens.secureauth.core.security

import io.github.yozedens.secureauth.core.crypto.JvmAesGcmCipher
import io.github.yozedens.secureauth.core.error.VaultError
import io.github.yozedens.secureauth.core.error.VaultResult
import io.github.yozedens.secureauth.core.vault.FakeVaultStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.crypto.KeyGenerator

class PinManagerTest {

    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val store = FakeVaultStore()
    private val clock = MutableClock(1_000_000)
    private val pins = newManager()

    @Test
    fun setAndVerify() = runTest {
        assertFalse(pins.hasPin())
        assertEquals(PinCheck.NotSet, pins.verify(pin("123456")))
        assertEquals(VaultResult.Success(Unit), pins.setPin(pin("123456")))
        assertTrue(pins.hasPin())
        assertEquals(PinCheck.Correct, pins.verify(pin("123456")))
        assertEquals(PinCheck.Wrong(1, 0), pins.verify(pin("654321")))
    }

    @Test
    fun storageIsEncrypted() = runTest {
        pins.setPin(pin("123456"))
        val raw = String(store.bytes!!, Charsets.ISO_8859_1)
        assertTrue(raw.startsWith("SAV1"))
        listOf("verifier", "PBKDF2", "failedAttempts").forEach { assertFalse(it, raw.contains(it)) }
    }

    @Test
    fun backoffAfterFiveFailuresAndSurvivesRestart() = runTest {
        pins.setPin(pin("123456"))
        repeat(4) { assertEquals(PinCheck.Wrong(it + 1, 0), pins.verify(pin("000000"))) }
        assertEquals(PinCheck.Wrong(5, 30_000), pins.verify(pin("000000")))

        // Even the correct PIN is not checked during the lockout.
        assertEquals(PinCheck.LockedOut(30_000), pins.verify(pin("123456")))

        val restarted = newManager()
        clock.advance(10_000)
        assertEquals(20_000L, restarted.remainingLockoutMillis())
        assertEquals(PinCheck.LockedOut(20_000), restarted.verify(pin("123456")))

        clock.advance(20_000)
        assertEquals(PinCheck.Wrong(6, 60_000), restarted.verify(pin("000000")))
        clock.advance(60_000)
        assertEquals(PinCheck.Correct, restarted.verify(pin("123456")))
        assertEquals(0L, restarted.remainingLockoutMillis())
        assertEquals(PinCheck.Wrong(1, 0), restarted.verify(pin("000000")))
    }

    @Test
    fun clockMovedBackRestartsWait() = runTest {
        pins.setPin(pin("123456"))
        repeat(5) { pins.verify(pin("000000")) }
        clock.advance(-3_600_000)
        assertEquals(30_000L, pins.remainingLockoutMillis())
    }

    @Test
    fun resetFailuresAfterBiometricUnlock() = runTest {
        pins.setPin(pin("123456"))
        repeat(5) { pins.verify(pin("000000")) }
        assertEquals(VaultResult.Success(Unit), pins.resetFailures())
        assertEquals(0L, pins.remainingLockoutMillis())
        assertEquals(PinCheck.Correct, pins.verify(pin("123456")))
        assertEquals(VaultResult.Success(Unit), pins.resetFailures())
    }

    @Test
    fun changingPinClearsFailures() = runTest {
        pins.setPin(pin("123456"))
        repeat(3) { pins.verify(pin("000000")) }
        pins.setPin(pin("99887766"))
        assertEquals(PinCheck.Wrong(1, 0), pins.verify(pin("123456")))
        assertEquals(PinCheck.Correct, pins.verify(pin("99887766")))
    }

    @Test
    fun errors() = runTest {
        pins.setPin(pin("123456"))
        val otherKey = PinManager(
            store,
            JvmAesGcmCipher(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()),
            clock,
            iterations = { 1_000 },
        )
        assertEquals(PinCheck.Failed(VaultError.AuthenticationFailed), otherKey.verify(pin("123456")))
        assertFalse(otherKey.hasPin())

        store.failWrites = true
        assertEquals(PinCheck.Failed(VaultError.Io), pins.verify(pin("000000")))
        assertEquals(VaultResult.Failure(VaultError.Io), pins.clear())
        store.failWrites = false
        store.failReads = true
        assertEquals(PinCheck.Failed(VaultError.Io), pins.verify(pin("123456")))
        assertEquals(0L, pins.remainingLockoutMillis())
        store.failReads = false

        store.bytes = "SAV".toByteArray()
        assertEquals(PinCheck.Failed(VaultError.Corrupted), pins.verify(pin("123456")))
    }

    @Test
    fun clearRemovesPin() = runTest {
        pins.setPin(pin("123456"))
        assertEquals(VaultResult.Success(Unit), pins.clear())
        assertFalse(pins.hasPin())
    }

    private fun newManager() = PinManager(store, JvmAesGcmCipher(key), clock, iterations = { 1_000 })

    private fun pin(s: String) = s.toCharArray()

    private class MutableClock(private var now: Long) : Clock() {
        fun advance(millis: Long) {
            now += millis
        }

        override fun instant(): Instant = Instant.ofEpochMilli(now)
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }
}
