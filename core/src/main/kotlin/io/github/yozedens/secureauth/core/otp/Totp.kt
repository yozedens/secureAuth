package io.github.yozedens.secureauth.core.otp

import io.github.yozedens.secureauth.core.model.Algorithm
import io.github.yozedens.secureauth.core.model.OtpParams
import java.time.Clock

/** TOTP, RFC 6238 with T0 = 0 (design §11). */
object Totp {

    /** Time step for [epochSeconds]; 64-bit, so it stays correct beyond 2038. */
    fun timeStep(epochSeconds: Long, periodSeconds: Int): Long {
        require(periodSeconds in OtpParams.PERIOD_RANGE) { "period out of range" }
        return Math.floorDiv(epochSeconds, periodSeconds.toLong())
    }

    /** Seconds until the code for [epochSeconds] changes, in 1..[periodSeconds]. */
    fun remainingSeconds(epochSeconds: Long, periodSeconds: Int): Int {
        require(periodSeconds in OtpParams.PERIOD_RANGE) { "period out of range" }
        return periodSeconds - Math.floorMod(epochSeconds, periodSeconds.toLong()).toInt()
    }

    fun generate(
        key: ByteArray,
        epochSeconds: Long,
        periodSeconds: Int,
        algorithm: Algorithm,
        digits: Int,
    ): String = Hotp.generate(key, timeStep(epochSeconds, periodSeconds), algorithm, digits)

    fun generate(
        key: ByteArray,
        clock: Clock,
        periodSeconds: Int,
        algorithm: Algorithm,
        digits: Int,
    ): String = generate(key, clock.instant().epochSecond, periodSeconds, algorithm, digits)
}
