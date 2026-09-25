package io.github.yozedens.secureauth.core.entry

import io.github.yozedens.secureauth.core.base32.Base32
import io.github.yozedens.secureauth.core.error.ParseError
import io.github.yozedens.secureauth.core.error.ParseResult
import io.github.yozedens.secureauth.core.error.SecretProblem
import io.github.yozedens.secureauth.core.model.Algorithm
import io.github.yozedens.secureauth.core.model.OtpKind
import io.github.yozedens.secureauth.core.model.OtpParams
import io.github.yozedens.secureauth.core.model.Secret
import io.github.yozedens.secureauth.core.otpauth.OtpUri
import io.github.yozedens.secureauth.core.otpauth.UriText
import io.github.yozedens.secureauth.core.vault.AccountDraft

/**
 * Raw manual-entry input (design §17). Not a data class: [secret] must never appear in
 * a generated toString.
 */
class ManualEntryForm(
    val issuer: String,
    val accountName: String,
    val secret: String,
    val options: EntryOptions = EntryOptions(),
)

/** Type and advanced options (design §17); no secret, so a data class is fine. */
data class EntryOptions(
    val isHotp: Boolean = false,
    val algorithm: Algorithm = Algorithm.SHA1,
    val digits: Int = OtpParams.DEFAULT_DIGITS,
    val period: String = OtpParams.DEFAULT_PERIOD_SECONDS.toString(),
    val counter: String = OtpParams.DEFAULT_COUNTER.toString(),
)

/** Field-level validation errors; none carries user input. */
sealed interface EntryError {
    /** Issuer and account name are both empty. */
    data object NameRequired : EntryError
    data class InvalidSecret(val problem: SecretProblem) : EntryError
    data object InvalidDigits : EntryError
    data object InvalidPeriod : EntryError
    data object InvalidCounter : EntryError
}

sealed interface EntryResult<out T> {
    data class Valid<out T>(val value: T) : EntryResult<T>
    data class Invalid(val errors: Set<EntryError>) : EntryResult<Nothing>
}

/** Validation shared by manual entry, the confirm screen and account editing. */
object ManualEntry {

    fun validate(form: ManualEntryForm): EntryResult<OtpUri> {
        val errors = mutableSetOf<EntryError>()
        val issuer = UriText.sanitize(form.issuer)
        val account = UriText.sanitize(form.accountName)
        if (issuer.isEmpty() && account.isEmpty()) errors += EntryError.NameRequired
        val options = form.options
        if (options.digits !in OtpParams.DIGITS_RANGE) errors += EntryError.InvalidDigits
        val kind = parseKind(options.isHotp, options.period, options.counter)
        if (kind == null) errors += if (options.isHotp) EntryError.InvalidCounter else EntryError.InvalidPeriod
        val secret = when (val decoded = Base32.decode(form.secret)) {
            is ParseResult.Success -> decoded.value.let { bytes ->
                try {
                    Secret(bytes)
                } finally {
                    bytes.fill(0)
                }
            }
            is ParseResult.Failure -> {
                val problem = (decoded.error as? ParseError.InvalidSecret)?.problem ?: SecretProblem.INVALID_CHARACTER
                errors += EntryError.InvalidSecret(problem)
                null
            }
        }
        if (errors.isNotEmpty() || kind == null || secret == null) return EntryResult.Invalid(errors)
        return EntryResult.Valid(
            OtpUri(
                issuer = issuer.ifEmpty { null },
                accountName = account,
                secret = secret,
                algorithm = options.algorithm,
                digits = options.digits,
                kind = kind,
                warnings = emptyList(),
            ),
        )
    }

    /** Parses the period (TOTP) or counter (HOTP) text; null if out of range. */
    fun parseKind(isHotp: Boolean, period: String, counter: String): OtpKind? =
        if (isHotp) {
            counter.trim().toLongOrNull()?.takeIf { it >= 0 }?.let { OtpKind.Hotp(it) }
        } else {
            period.trim().toIntOrNull()?.takeIf { it in OtpParams.PERIOD_RANGE }?.let { OtpKind.Totp(it) }
        }

    /** Cleans a label field the same way scanned labels are cleaned. */
    fun cleanName(value: String): String = UriText.sanitize(value)

    /** Builds the draft to store, with the account name possibly edited on the confirm screen. */
    fun toDraft(uri: OtpUri, issuer: String?, accountName: String): AccountDraft = AccountDraft(
        issuer = issuer?.let(UriText::sanitize)?.ifEmpty { null },
        accountName = UriText.sanitize(accountName),
        algorithm = uri.algorithm,
        digits = uri.digits,
        kind = uri.kind,
        secret = uri.secret,
    )
}
