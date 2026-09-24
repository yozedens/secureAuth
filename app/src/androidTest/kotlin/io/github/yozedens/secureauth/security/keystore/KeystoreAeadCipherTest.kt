package io.github.yozedens.secureauth.security.keystore

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yozedens.secureauth.core.crypto.KeyUnavailableException
import io.github.yozedens.secureauth.core.crypto.VaultEnvelope
import io.github.yozedens.secureauth.core.error.VaultError
import io.github.yozedens.secureauth.core.error.VaultResult
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/** Design §50.5, Keystore part: runs on a device or emulator. */
@RunWith(AndroidJUnit4::class)
class KeystoreAeadCipherTest {

    private val alias = "secureauth_test_key"
    private val cipher = KeystoreAeadCipher(alias)
    private val aad = VaultEnvelope.header(1)
    private val plaintext = "hello vault".toByteArray()

    @Before
    fun setUp() = deleteKey()

    @After
    fun tearDown() = deleteKey()

    @Test
    fun createsKeyOnlyOnce() {
        assertFalse(cipher.keyExists())
        assertNotNull(cipher.createKeyIfAbsent())
        assertTrue(cipher.keyExists())
        assertNull(cipher.createKeyIfAbsent())
    }

    @Test
    fun roundTripWithKeystoreGeneratedIv() {
        cipher.createKeyIfAbsent()
        val sealed = cipher.encrypt(plaintext, aad)
        assertEquals(12, sealed.iv.size)
        assertArrayEquals(plaintext, cipher.decrypt(sealed.iv, sealed.ciphertext, aad))
        val again = cipher.encrypt(plaintext, aad)
        assertFalse(sealed.iv.contentEquals(again.iv))
    }

    @Test
    fun tamperingIsDetected() {
        cipher.createKeyIfAbsent()
        val envelope = (VaultEnvelope.seal(cipher, 1, plaintext) as VaultResult.Success).value
        val bytes = envelope.encode()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        val tampered = (VaultEnvelope.decode(bytes) as VaultResult.Success).value
        assertEquals(VaultResult.Failure(VaultError.AuthenticationFailed), VaultEnvelope.open(cipher, tampered))
    }

    @Test
    fun missingKeyIsReported() {
        assertTrue(runCatching { cipher.encrypt(plaintext, aad) }.exceptionOrNull() is KeyUnavailableException)
        assertEquals(
            VaultResult.Failure(VaultError.KeyMissing),
            VaultEnvelope.open(cipher, VaultEnvelope(1, ByteArray(12), ByteArray(32))),
        )
    }

    private fun deleteKey() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }
}
