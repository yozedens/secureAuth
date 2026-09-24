package io.github.yozedens.secureauth.core.model

/**
 * TOTP or HOTP together with the parameter that only that type has, so that
 * "TOTP with a counter" or "HOTP without a counter" cannot be represented (design §8.1).
 */
sealed interface OtpKind {

    data class Totp(val periodSeconds: Int = OtpParams.DEFAULT_PERIOD_SECONDS) : OtpKind {
        init {
            require(periodSeconds in OtpParams.PERIOD_RANGE) { "period out of range" }
        }
    }

    /** [counter] is the next counter value to use (design §20). */
    data class Hotp(val counter: Long = OtpParams.DEFAULT_COUNTER) : OtpKind {
        init {
            require(counter >= 0) { "counter must not be negative" }
        }
    }
}
