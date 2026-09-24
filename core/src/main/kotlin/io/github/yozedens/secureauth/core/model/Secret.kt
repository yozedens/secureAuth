package io.github.yozedens.secureauth.core.model

import java.security.MessageDigest

/**
 * OTP shared secret (design §9).
 *
 * - The input array is copied, so callers may wipe their own copy.
 * - [toString] never reveals the content.
 * - [equals] compares content in constant time.
 */
class Secret(value: ByteArray) {

    private val value: ByteArray = value.copyOf()

    init {
        require(value.isNotEmpty()) { "secret must not be empty" }
    }

    val size: Int get() = value.size

    /** Runs [block] with a temporary copy of the secret and wipes the copy afterwards. */
    inline fun <R> use(block: (ByteArray) -> R): R {
        val copy = bytesCopy()
        try {
            return block(copy)
        } finally {
            copy.fill(0)
        }
    }

    /** Returns a copy the caller is responsible for wiping. Prefer [use]. */
    fun bytesCopy(): ByteArray = value.copyOf()

    /** Overwrites the secret with zeros. The instance must not be used afterwards. */
    fun wipe() = value.fill(0)

    override fun equals(other: Any?): Boolean =
        other is Secret && MessageDigest.isEqual(value, other.value)

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = "Secret(***)"
}
