package io.github.yozedens.secureauth.core.base32

import io.github.yozedens.secureauth.core.error.ParseError
import io.github.yozedens.secureauth.core.error.ParseResult
import io.github.yozedens.secureauth.core.error.SecretProblem

/**
 * RFC 4648 Base32 codec for OTP secrets (design §13).
 *
 * Decoding is lenient about formatting (case, whitespace, `-` separators, optional
 * padding) and strict about content. It never builds an intermediate String of the
 * input, and errors never include it.
 */
object Base32 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val BITS_PER_CHAR = 5
    private const val BITS_PER_BYTE = 8
    private const val CHARS_PER_BLOCK = 8
    private const val BYTE_MASK = 0xFF
    private const val CHAR_MASK = 0x1F

    /** Decoded secrets above this size are rejected (a QR code cannot hold much more). */
    const val MAX_DECODED_BYTES = 256

    /** Remainders of (length mod 8) that cannot come from whole bytes. */
    private val INVALID_REMAINDERS = setOf(1, 3, 6)

    private val LOOKALIKE_DIGITS = setOf('0', '1', '8', '9')

    fun decode(input: CharSequence): ParseResult<ByteArray> {
        val chars = CharArray(input.length)
        try {
            val normalized = normalize(input, chars)
            val problem = normalized.problem ?: validateLength(normalized.count)
            return if (problem != null) {
                ParseResult.Failure(ParseError.InvalidSecret(problem))
            } else {
                ParseResult.Success(decodeNormalized(chars, normalized.count))
            }
        } finally {
            chars.fill('\u0000')
        }
    }

    private class Normalized(val count: Int, val problem: SecretProblem?)

    /**
     * Copies the alphabet characters of [input] into [out] in upper case, skipping
     * whitespace, `-` and trailing `=`.
     */
    private fun normalize(input: CharSequence, out: CharArray): Normalized {
        var count = 0
        var paddingSeen = false
        var problem: SecretProblem? = null
        var i = 0
        while (problem == null && i < input.length) {
            val raw = input[i++]
            val c = raw.uppercaseChar()
            problem = when {
                raw.isWhitespace() || raw == '-' -> null
                raw == '=' -> null.also { paddingSeen = true }
                paddingSeen -> SecretProblem.MISPLACED_PADDING
                ALPHABET.indexOf(c) < 0 && c in LOOKALIKE_DIGITS -> SecretProblem.LOOKALIKE_DIGIT
                ALPHABET.indexOf(c) < 0 -> SecretProblem.INVALID_CHARACTER
                else -> null.also { out[count++] = c }
            }
        }
        return Normalized(count, problem)
    }

    private fun validateLength(count: Int): SecretProblem? = when {
        count == 0 -> SecretProblem.EMPTY
        count % CHARS_PER_BLOCK in INVALID_REMAINDERS -> SecretProblem.INVALID_LENGTH
        count * BITS_PER_CHAR / BITS_PER_BYTE > MAX_DECODED_BYTES -> SecretProblem.TOO_LONG
        else -> null
    }

    private fun decodeNormalized(chars: CharArray, count: Int): ByteArray {
        val out = ByteArray(count * BITS_PER_CHAR / BITS_PER_BYTE)
        var buffer = 0
        var bits = 0
        var index = 0
        for (i in 0 until count) {
            buffer = (buffer shl BITS_PER_CHAR) or ALPHABET.indexOf(chars[i])
            bits += BITS_PER_CHAR
            if (bits >= BITS_PER_BYTE) {
                bits -= BITS_PER_BYTE
                out[index++] = (buffer shr bits and BYTE_MASK).toByte()
            }
        }
        // Leftover bits (< 8) are ignored: real secrets are whole bytes.
        return out
    }

    /** Encodes without padding. Used for tests and display of test data only. */
    fun encode(data: ByteArray): String {
        val sb = StringBuilder((data.size * BITS_PER_BYTE + BITS_PER_CHAR - 1) / BITS_PER_CHAR)
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl BITS_PER_BYTE) or (b.toInt() and BYTE_MASK)
            bits += BITS_PER_BYTE
            while (bits >= BITS_PER_CHAR) {
                bits -= BITS_PER_CHAR
                sb.append(ALPHABET[buffer shr bits and CHAR_MASK])
            }
        }
        if (bits > 0) {
            sb.append(ALPHABET[buffer shl (BITS_PER_CHAR - bits) and CHAR_MASK])
        }
        return sb.toString()
    }
}
