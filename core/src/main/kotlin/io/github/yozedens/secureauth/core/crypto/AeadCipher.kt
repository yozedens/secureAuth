package io.github.yozedens.secureauth.core.crypto

import java.security.GeneralSecurityException

/**
 * Authenticated encryption used for the vault (design §23–§25).
 *
 * Implementations choose a fresh random IV for every [encrypt] call and never accept
 * one from the caller (Android Keystore enforces this, ADR 0001).
 */
interface AeadCipher {

    /** @throws KeyUnavailableException if the key does not exist or can no longer be used. */
    @Throws(GeneralSecurityException::class)
    fun encrypt(plaintext: ByteArray, aad: ByteArray): Sealed

    /**
     * @throws javax.crypto.AEADBadTagException if the data or AAD was modified, or the key/IV is wrong.
     * @throws KeyUnavailableException if the key does not exist or can no longer be used.
     */
    @Throws(GeneralSecurityException::class)
    fun decrypt(iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray

    /** IV plus ciphertext with the authentication tag appended. */
    class Sealed(val iv: ByteArray, val ciphertext: ByteArray)
}

/** The encryption key is missing (e.g. restored from backup) or permanently unusable. */
class KeyUnavailableException(message: String, cause: Throwable? = null) :
    GeneralSecurityException(message, cause)
