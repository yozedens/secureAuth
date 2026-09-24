package io.github.yozedens.secureauth.core.error

/**
 * Parsing and validation errors (design §56). Errors never carry user input,
 * so they can be logged or shown without leaking secrets.
 */
sealed interface ParseError {
    /** Structurally unusable input, e.g. too long or missing the type/label separator. */
    data object InvalidUri : ParseError
    data object InvalidScheme : ParseError
    /** `otpauth-migration://` (Google Authenticator export) is not supported in v0.1. */
    data object UnsupportedMigrationFormat : ParseError
    data object InvalidType : ParseError
    data object MissingSecret : ParseError
    data object DuplicateSecret : ParseError
    data class InvalidSecret(val problem: SecretProblem) : ParseError
    data object InvalidAlgorithm : ParseError
    data object InvalidDigits : ParseError
    data object InvalidPeriod : ParseError
    data object InvalidCounter : ParseError
}

/** Why a Base32 secret was rejected, for a user-facing hint (design §13). */
enum class SecretProblem {
    EMPTY,
    INVALID_CHARACTER,
    /** Contains 0, 1, 8 or 9, which are not in the Base32 alphabet (likely O, I, B typed as digits). */
    LOOKALIKE_DIGIT,
    MISPLACED_PADDING,
    INVALID_LENGTH,
    TOO_LONG,
}

/** Non-fatal findings the confirmation screen should show (design §44). */
enum class ParseWarning {
    /** HOTP URI without `counter`; defaulted to 0 (decision D3). */
    HOTP_COUNTER_MISSING,
}

/** Result of a parse step. */
sealed interface ParseResult<out T> {
    data class Success<out T>(val value: T) : ParseResult<T>
    data class Failure(val error: ParseError) : ParseResult<Nothing>
}
