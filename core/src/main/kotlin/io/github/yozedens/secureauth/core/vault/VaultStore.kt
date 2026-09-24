package io.github.yozedens.secureauth.core.vault

import java.io.IOException

/**
 * Raw persistence of the encrypted vault envelope (design §22). The Android
 * implementation is backed by DataStore; it never sees plaintext.
 */
interface VaultStore {

    /** The stored envelope bytes, or `null` if nothing is stored (an empty write means "nothing"). */
    @Throws(IOException::class)
    suspend fun read(): ByteArray?

    /**
     * Atomically replaces the stored bytes with `transform(current)`. If [transform]
     * throws, nothing is written and the exception propagates.
     */
    @Throws(IOException::class)
    suspend fun update(transform: suspend (current: ByteArray?) -> ByteArray)
}
