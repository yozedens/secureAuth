package io.github.yozedens.secureauth.core.otp

import io.github.yozedens.secureauth.core.model.Algorithm
import io.github.yozedens.secureauth.core.model.OtpParams
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** HOTP, RFC 4226 (design §10). */
object Hotp {

    private const val COUNTER_BYTES = 8
    private const val BITS_PER_BYTE = 8
    private const val OFFSET_MASK = 0x0F
    private const val SIGN_MASK = 0x7F
    private const val BYTE_MASK = 0xFF
    private const val TRUNCATED_BYTES = 4
    private const val DECIMAL = 10

    /** 10^n for n in 0..MAX_DIGITS, avoiding floating point. */
    private val POWERS_OF_TEN = IntArray(OtpParams.MAX_DIGITS + 1).also { powers ->
        powers[0] = 1
        for (n in 1 until powers.size) powers[n] = powers[n - 1] * DECIMAL
    }

    fun generate(key: ByteArray, counter: Long, algorithm: Algorithm, digits: Int): String {
        require(key.isNotEmpty()) { "key must not be empty" }
        require(counter >= 0) { "counter must not be negative" }
        require(digits in OtpParams.DIGITS_RANGE) { "digits out of range" }

        val message = ByteArray(COUNTER_BYTES)
        var value = counter
        for (i in COUNTER_BYTES - 1 downTo 0) {
            message[i] = (value and BYTE_MASK.toLong()).toByte()
            value = value ushr BITS_PER_BYTE
        }

        val mac = Mac.getInstance(algorithm.jcaName)
        mac.init(SecretKeySpec(key, algorithm.jcaName))
        val hash = mac.doFinal(message)
        try {
            // Dynamic truncation: offset from the low 4 bits of the last byte (also for SHA-256/512).
            val offset = hash[hash.size - 1].toInt() and OFFSET_MASK
            var binary = hash[offset].toInt() and SIGN_MASK
            for (i in 1 until TRUNCATED_BYTES) {
                binary = (binary shl BITS_PER_BYTE) or (hash[offset + i].toInt() and BYTE_MASK)
            }
            // Integer.toString is locale independent; String.format is not (Lint DefaultLocale).
            return (binary % POWERS_OF_TEN[digits]).toString().padStart(digits, '0')
        } finally {
            hash.fill(0)
        }
    }
}
