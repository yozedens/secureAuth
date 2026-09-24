package io.github.yozedens.secureauth.core.crypto

import io.github.yozedens.secureauth.core.error.VaultError
import io.github.yozedens.secureauth.core.error.VaultResult
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException

/**
 * Binary envelope stored in DataStore (design §25, ADR 0001):
 *
 * ```
 * magic "SAV1" (4) | version (1) | keyId (1) | ivLen (1) | iv (ivLen) | ciphertext + tag
 * ```
 *
 * The 7-byte header is authenticated as AAD, so changing the version or key id makes
 * decryption fail instead of silently misinterpreting the data.
 */
class VaultEnvelope(
    val keyId: Int,
    val iv: ByteArray,
    val ciphertext: ByteArray,
) {
    init {
        require(keyId in 0..MAX_UNSIGNED_BYTE) { "keyId out of range" }
        require(iv.size == IV_BYTES) { "unexpected IV length" }
    }

    fun encode(): ByteArray = header(keyId) + iv + ciphertext

    companion object {
        const val VERSION = 1
        const val IV_BYTES = 12
        private val MAGIC = byteArrayOf('S'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), '1'.code.toByte())
        private const val HEADER_BYTES = 7
        private const val VERSION_OFFSET = 4
        private const val KEY_ID_OFFSET = 5
        private const val IV_LEN_OFFSET = 6
        private const val MAX_UNSIGNED_BYTE = 0xFF

        /** The authenticated header; also used as AAD. */
        fun header(keyId: Int): ByteArray =
            MAGIC + byteArrayOf(VERSION.toByte(), keyId.toByte(), IV_BYTES.toByte())

        fun decode(bytes: ByteArray): VaultResult<VaultEnvelope> {
            if (bytes.size < HEADER_BYTES || !bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
                return VaultResult.Failure(VaultError.Corrupted)
            }
            if (bytes[VERSION_OFFSET].toInt() and MAX_UNSIGNED_BYTE != VERSION) {
                return VaultResult.Failure(VaultError.UnsupportedVersion)
            }
            val ivLength = bytes[IV_LEN_OFFSET].toInt() and MAX_UNSIGNED_BYTE
            if (ivLength != IV_BYTES || bytes.size < HEADER_BYTES + ivLength) {
                return VaultResult.Failure(VaultError.Corrupted)
            }
            return VaultResult.Success(
                VaultEnvelope(
                    keyId = bytes[KEY_ID_OFFSET].toInt() and MAX_UNSIGNED_BYTE,
                    iv = bytes.copyOfRange(HEADER_BYTES, HEADER_BYTES + ivLength),
                    ciphertext = bytes.copyOfRange(HEADER_BYTES + ivLength, bytes.size),
                ),
            )
        }

        /** Encrypts [plaintext] into a new envelope. */
        fun seal(cipher: AeadCipher, keyId: Int, plaintext: ByteArray): VaultResult<VaultEnvelope> =
            guard {
                val sealed = cipher.encrypt(plaintext, header(keyId))
                VaultEnvelope(keyId, sealed.iv, sealed.ciphertext)
            }

        /** Decrypts [envelope]; any modification of header, IV or ciphertext fails. */
        fun open(cipher: AeadCipher, envelope: VaultEnvelope): VaultResult<ByteArray> =
            guard { cipher.decrypt(envelope.iv, envelope.ciphertext, header(envelope.keyId)) }

        /**
         * Maps crypto exceptions to [VaultError]. The exceptions themselves are dropped on
         * purpose: nothing is logged (design §55) and the type carries all we act on.
         */
        private inline fun <T> guard(block: () -> T): VaultResult<T> =
            try {
                VaultResult.Success(block())
            } catch (ignored: KeyUnavailableException) {
                VaultResult.Failure(VaultError.KeyMissing)
            } catch (ignored: AEADBadTagException) {
                VaultResult.Failure(VaultError.AuthenticationFailed)
            } catch (ignored: GeneralSecurityException) {
                // e.g. Keystore temporarily unavailable: retryable, never treated as data loss.
                VaultResult.Failure(VaultError.CryptoUnavailable)
            }
    }
}
