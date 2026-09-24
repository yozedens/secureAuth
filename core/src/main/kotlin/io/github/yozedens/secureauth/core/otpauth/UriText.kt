package io.github.yozedens.secureauth.core.otpauth

import java.io.ByteArrayOutputStream

/** Text helpers for [OtpUriParser]. */
internal object UriText {

    /** Maximum length of issuer and account name after sanitizing. */
    const val MAX_FIELD_LENGTH = 256

    private const val HEX_RADIX = 16
    private const val ESCAPE_LENGTH = 3

    /**
     * Percent-decodes as UTF-8. Invalid escapes are kept literally. `+` becomes a space
     * only in query values ([plusAsSpace]); in the label it is literal (`user+tag@gmail.com`).
     */
    fun percentDecode(s: String, plusAsSpace: Boolean): String {
        if ('%' !in s && !(plusAsSpace && '+' in s)) return s
        val out = ByteArrayOutputStream(s.length)
        var i = 0
        while (i < s.length) {
            val escaped = escapedByte(s, i)
            i = when {
                escaped >= 0 -> {
                    out.write(escaped)
                    i + ESCAPE_LENGTH
                }
                s[i] == '+' && plusAsSpace -> {
                    out.write(' '.code)
                    i + 1
                }
                else -> {
                    val end = if (s[i].isHighSurrogate() && i + 1 < s.length) i + 2 else i + 1
                    out.write(s.substring(i, end).toByteArray(Charsets.UTF_8))
                    end
                }
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    /** The byte encoded by a valid `%XX` escape at [i], or -1. */
    private fun escapedByte(s: String, i: Int): Int {
        if (s[i] != '%' || i + ESCAPE_LENGTH > s.length) return -1
        val hi = hexValue(s[i + 1])
        val lo = hexValue(s[i + 2])
        return if (hi >= 0 && lo >= 0) hi * HEX_RADIX + lo else -1
    }

    private fun hexValue(c: Char): Int = Character.digit(c, HEX_RADIX)

    /**
     * Removes control and bidirectional-formatting characters (which could make a label
     * display as something else), trims, and caps the length without splitting a surrogate pair.
     */
    fun sanitize(s: String): String {
        val cleaned = buildString(s.length) {
            for (c in s) {
                if (!Character.isISOControl(c) && !isBidiControl(c)) append(c)
            }
        }.trim()
        if (cleaned.length <= MAX_FIELD_LENGTH) return cleaned
        val cut = if (cleaned[MAX_FIELD_LENGTH - 1].isHighSurrogate()) {
            MAX_FIELD_LENGTH - 1
        } else {
            MAX_FIELD_LENGTH
        }
        return cleaned.substring(0, cut).trim()
    }

    private fun isBidiControl(c: Char): Boolean =
        c == '؜' || c == '‎' || c == '‏' ||
            c in '‪'..'‮' || c in '⁦'..'⁩'
}
