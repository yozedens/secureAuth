package io.github.yozedens.secureauth.core.vault

import io.github.yozedens.secureauth.core.crypto.AeadCipher
import io.github.yozedens.secureauth.core.crypto.VaultEnvelope
import io.github.yozedens.secureauth.core.error.VaultError
import io.github.yozedens.secureauth.core.error.VaultResult
import io.github.yozedens.secureauth.core.model.OtpAccount

/** Converts between accounts and stored envelope bytes: JSON codec plus AEAD (design §26). */
internal class VaultSealer(private val cipher: AeadCipher, private val keyId: Int) {

    fun open(bytes: ByteArray?): VaultResult<List<OtpAccount>> {
        if (bytes == null) return VaultResult.Failure(VaultError.Corrupted)
        val envelope = when (val decoded = VaultEnvelope.decode(bytes)) {
            is VaultResult.Success -> decoded.value
            is VaultResult.Failure -> return decoded
        }
        val plaintext = when (val opened = VaultEnvelope.open(cipher, envelope)) {
            is VaultResult.Success -> opened.value
            is VaultResult.Failure -> return opened
        }
        try {
            return VaultCodec.decode(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    fun seal(accounts: List<OtpAccount>): ByteArray {
        val plaintext = VaultCodec.encode(accounts)
        try {
            return when (val sealed = VaultEnvelope.seal(cipher, keyId, plaintext)) {
                is VaultResult.Success -> sealed.value.encode()
                is VaultResult.Failure -> throw VaultAbort(sealed.error)
            }
        } finally {
            plaintext.fill(0)
        }
    }
}

/** Aborts a [VaultStore.update] without writing. */
internal class VaultAbort(val error: VaultError) : Exception(null, null, false, false)
