package io.github.yozedens.secureauth.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN format (ADR 0001 §5, decision D2): 6–12 ASCII digits. */
object PinPolicy {
    const val MIN_LENGTH = 6
    const val MAX_LENGTH = 12

    fun isValid(pin: CharArray): Boolean = pin.size in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }
}

/**
 * Stored PIN verifier. The algorithm and iteration count are stored with it so the
 * parameters can be raised later (design §30.2). Not a data class: no generated toString.
 */
class PinVerifier(
    val algorithm: String,
    val iterations: Int,
    val salt: ByteArray,
    val hash: ByteArray,
) {
    override fun toString(): String = "PinVerifier(algorithm=$algorithm, iterations=$iterations)"
}

/** PBKDF2-HMAC-SHA256 PIN hashing (design §30.2). Slow on purpose: call off the main thread. */
object PinHasher {

    const val ALGORITHM = "PBKDF2WithHmacSHA256"
    const val SALT_BYTES = 16
    const val HASH_BITS = 256

    const val MIN_ITERATIONS = 100_000
    const val MAX_ITERATIONS = 2_000_000
    private const val CALIBRATION_ITERATIONS = 20_000
    private const val NANOS_PER_MILLI = 1_000_000L

    fun create(pin: CharArray, iterations: Int, random: SecureRandom = SecureRandom()): PinVerifier {
        require(PinPolicy.isValid(pin)) { "invalid PIN format" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return PinVerifier(ALGORITHM, iterations, salt, derive(pin, salt, iterations, ALGORITHM))
    }

    /** Constant-time comparison against [verifier]. */
    fun matches(pin: CharArray, verifier: PinVerifier): Boolean {
        val candidate = derive(pin, verifier.salt, verifier.iterations, verifier.algorithm)
        try {
            return MessageDigest.isEqual(candidate, verifier.hash)
        } finally {
            candidate.fill(0)
        }
    }

    /**
     * Iterations that take about [targetMillis] on this device, clamped to
     * [MIN_ITERATIONS]..[MAX_ITERATIONS].
     */
    fun calibrate(targetMillis: Long, nanoTime: () -> Long = System::nanoTime): Int {
        val probe = "000000".toCharArray()
        val salt = ByteArray(SALT_BYTES)
        val start = nanoTime()
        derive(probe, salt, CALIBRATION_ITERATIONS, ALGORITHM).fill(0)
        val elapsedMillis = ((nanoTime() - start) / NANOS_PER_MILLI).coerceAtLeast(1)
        val scaled = CALIBRATION_ITERATIONS.toLong() * targetMillis / elapsedMillis
        return scaled.coerceIn(MIN_ITERATIONS.toLong(), MAX_ITERATIONS.toLong()).toInt()
    }

    private fun derive(pin: CharArray, salt: ByteArray, iterations: Int, algorithm: String): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, HASH_BITS)
        try {
            return SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

/** Online throttling after wrong PINs (design §30.3). */
object PinBackoff {
    private const val FREE_ATTEMPTS = 4
    private val WAIT_SECONDS = longArrayOf(30, 60, 300, 900)
    private const val MILLIS_PER_SECOND = 1000L

    /** Required wait after [failures] consecutive wrong PINs. */
    fun waitMillis(failures: Int): Long {
        if (failures <= FREE_ATTEMPTS) return 0
        val index = (failures - FREE_ATTEMPTS - 1).coerceAtMost(WAIT_SECONDS.size - 1)
        return WAIT_SECONDS[index] * MILLIS_PER_SECOND
    }
}
