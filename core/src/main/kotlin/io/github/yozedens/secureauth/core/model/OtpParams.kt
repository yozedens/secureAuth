package io.github.yozedens.secureauth.core.model

/** Accepted OTP parameter ranges and defaults (design §12). */
object OtpParams {
    const val DEFAULT_DIGITS = 6
    const val DEFAULT_PERIOD_SECONDS = 30
    const val DEFAULT_COUNTER = 0L

    const val MIN_DIGITS = 6
    const val MAX_DIGITS = 8
    const val MIN_PERIOD_SECONDS = 1
    const val MAX_PERIOD_SECONDS = 300

    val DIGITS_RANGE = MIN_DIGITS..MAX_DIGITS
    val PERIOD_RANGE = MIN_PERIOD_SECONDS..MAX_PERIOD_SECONDS
}
