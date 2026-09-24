package io.github.yozedens.secureauth.core.crypto

import io.github.yozedens.secureauth.core.error.VaultError
import io.github.yozedens.secureauth.core.error.VaultResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.GeneralSecurityException
import java.security.KeyStoreException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Design §50.5: tampering must never decrypt successfully. */
class VaultEnvelopeTest {

    private val key = newKey()
    private val cipher = JvmAesGcmCipher(key)
    private val plaintext = """{"schemaVersion":1,"accounts":[{"secret":"SGVsbG8h"}]}""".toByteArray()

    @Test
    fun roundTrip() {
        val encoded = seal(plaintext).encode()
        assertArrayEquals(plaintext, open(decode(encoded)).orThrow())
    }

    @Test
    fun encodedFormHasDocumentedLayout() {
        val env = seal(plaintext, keyId = 1)
        val bytes = env.encode()
        assertArrayEquals("SAV1".toByteArray(), bytes.copyOfRange(0, 4))
        assertEquals(1, bytes[4].toInt())
        assertEquals(1, bytes[5].toInt())
        assertEquals(12, bytes[6].toInt())
        // ciphertext = plaintext + 16-byte tag
        assertEquals(7 + 12 + plaintext.size + 16, bytes.size)
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("SGVsbG8h"))
    }

    @Test
    fun emptyAndLargePayloads() {
        assertArrayEquals(ByteArray(0), open(seal(ByteArray(0))).orThrow())
        val large = ByteArray(1024 * 1024) { (it % 251).toByte() }
        assertArrayEquals(large, open(seal(large)).orThrow())
    }

    @Test
    fun freshIvEveryTime() {
        val ivs = (1..1000).map { seal(plaintext).iv.toList() }.toSet()
        assertEquals(1000, ivs.size)
    }

    @Test
    fun wrongKeyFails() {
        val env = seal(plaintext)
        assertEquals(auth(), VaultEnvelope.open(JvmAesGcmCipher(newKey()), env))
    }

    @Test
    fun wrongIvFails() {
        val env = seal(plaintext)
        val iv = env.iv.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertEquals(auth(), open(VaultEnvelope(env.keyId, iv, env.ciphertext)))
    }

    @Test
    fun everyFlippedCiphertextOrTagByteFails() {
        val encoded = seal(plaintext).encode()
        for (i in 7 until encoded.size) {
            val tampered = encoded.copyOf().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
            assertEquals("byte $i", auth(), open(decode(tampered)))
        }
    }

    @Test
    fun truncatedTagFails() {
        val encoded = seal(plaintext).encode()
        assertEquals(auth(), open(decode(encoded.copyOf(encoded.size - 1))))
    }

    @Test
    fun changedKeyIdIsDetectedThroughAad() {
        val encoded = seal(plaintext, keyId = 1).encode()
        encoded[5] = 2
        assertEquals(auth(), open(decode(encoded)))
    }

    @Test
    fun rejectsNonEnvelopes() {
        val corrupted = VaultResult.Failure(VaultError.Corrupted)
        assertEquals(corrupted, VaultEnvelope.decode(ByteArray(0)))
        assertEquals(corrupted, VaultEnvelope.decode("SAV".toByteArray()))
        assertEquals(corrupted, VaultEnvelope.decode("XXXX\u0001\u0001\u000C".toByteArray() + ByteArray(40)))
        assertEquals(corrupted, VaultEnvelope.decode("SAV1\u0001\u0001\u0008".toByteArray() + ByteArray(40)))
        assertEquals(corrupted, VaultEnvelope.decode("SAV1\u0001\u0001\u000C".toByteArray() + ByteArray(5)))
    }

    @Test
    fun rejectsNewerVersion() {
        val encoded = seal(plaintext).encode()
        encoded[4] = 2
        assertEquals(VaultResult.Failure(VaultError.UnsupportedVersion), VaultEnvelope.decode(encoded))
    }

    @Test
    fun mapsKeystoreFailures() {
        val missing = failingCipher(KeyUnavailableException("gone"))
        assertEquals(VaultResult.Failure(VaultError.KeyMissing), VaultEnvelope.seal(missing, 1, plaintext))
        assertEquals(VaultResult.Failure(VaultError.KeyMissing), VaultEnvelope.open(missing, seal(plaintext)))

        val transient = failingCipher(KeyStoreException("not ready"))
        assertEquals(VaultResult.Failure(VaultError.CryptoUnavailable), VaultEnvelope.open(transient, seal(plaintext)))
    }

    @Test
    fun validatesConstructorArguments() {
        assertTrue(runCatching { VaultEnvelope(256, ByteArray(12), ByteArray(0)) }.isFailure)
        assertTrue(runCatching { VaultEnvelope(1, ByteArray(16), ByteArray(0)) }.isFailure)
    }

    private fun seal(data: ByteArray, keyId: Int = 1): VaultEnvelope = VaultEnvelope.seal(cipher, keyId, data).orThrow()

    private fun open(env: VaultEnvelope) = VaultEnvelope.open(cipher, env)

    private fun decode(bytes: ByteArray): VaultEnvelope = VaultEnvelope.decode(bytes).orThrow()

    private fun auth() = VaultResult.Failure(VaultError.AuthenticationFailed)

    private fun <T> VaultResult<T>.orThrow(): T = when (this) {
        is VaultResult.Success -> value
        is VaultResult.Failure -> throw AssertionError("unexpected $error")
    }

    private fun failingCipher(error: GeneralSecurityException) = object : AeadCipher {
        override fun encrypt(plaintext: ByteArray, aad: ByteArray): AeadCipher.Sealed = throw error
        override fun decrypt(iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray = throw error
    }

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
}
