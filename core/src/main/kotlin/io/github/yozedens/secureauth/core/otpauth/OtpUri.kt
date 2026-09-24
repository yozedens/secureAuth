package io.github.yozedens.secureauth.core.otpauth

import io.github.yozedens.secureauth.core.error.ParseWarning
import io.github.yozedens.secureauth.core.model.Algorithm
import io.github.yozedens.secureauth.core.model.OtpKind
import io.github.yozedens.secureauth.core.model.Secret

/**
 * Result of parsing an `otpauth://` URI (design §14). The secret is already decoded
 * and validated. Not a data class, so no generated toString can expose it.
 */
class OtpUri(
    val issuer: String?,
    val accountName: String,
    val secret: Secret,
    val algorithm: Algorithm,
    val digits: Int,
    val kind: OtpKind,
    val warnings: List<ParseWarning>,
) {
    override fun toString(): String =
        "OtpUri(issuer=$issuer, accountName=$accountName, secret=$secret, " +
            "algorithm=$algorithm, digits=$digits, kind=$kind, warnings=$warnings)"
}
