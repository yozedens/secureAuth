package io.github.yozedens.secureauth.core.model

/** Account metadata without the secret; safe to hand to the UI layer (design §8.3). */
data class AccountMeta(
    val id: String,
    val issuer: String?,
    val accountName: String,
    val algorithm: Algorithm,
    val digits: Int,
    val kind: OtpKind,
    val createdAt: Long,
) {
    init {
        require(digits in OtpParams.DIGITS_RANGE) { "digits out of range" }
    }
}

/**
 * Account including its secret. Lives only in the data layer (design §8.3, §21).
 * Deliberately not a data class, so no generated toString can expose the secret.
 */
class OtpAccount(
    val meta: AccountMeta,
    val secret: Secret,
) {
    override fun toString(): String = "OtpAccount(meta=$meta, secret=$secret)"
}
