package io.github.yozedens.secureauth.core.vault

import io.github.yozedens.secureauth.core.error.VaultError
import io.github.yozedens.secureauth.core.model.Algorithm
import io.github.yozedens.secureauth.core.model.OtpKind
import io.github.yozedens.secureauth.core.model.Secret

/** Vault lifecycle (design §26.1). */
sealed interface VaultState {
    /** State not determined yet. */
    data object Unknown : VaultState
    /** First launch: no vault stored. */
    data object Uninitialized : VaultState
    data object Locked : VaultState
    data object Unlocked : VaultState
    /** Stored vault cannot be read. Nothing is written or regenerated (ADR 0001 §7). */
    data class Unreadable(val reason: VaultError) : VaultState
}

/** A generated code. [remainingSeconds] is set for TOTP only. */
data class OtpCode(val value: String, val remainingSeconds: Int?)

/** Input for a new account. Not a data class, so the secret never appears in toString. */
class AccountDraft(
    val issuer: String?,
    val accountName: String,
    val algorithm: Algorithm,
    val digits: Int,
    val kind: OtpKind,
    val secret: Secret,
)
