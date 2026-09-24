package io.github.yozedens.secureauth.core.otpauth

import io.github.yozedens.secureauth.core.base32.Base32
import org.junit.Test
import kotlin.random.Random

/** Random and mutated input must only ever produce a ParseResult, never an exception (design §50.9). */
class OtpUriParserFuzzTest {

    private val random = Random(20260924)
    private val alphabet = "otpauth:/?&=%+#-_.~ ABCXYZabcxyz0123456789JBSWY3DP\u0000\n‮😀张".toCharArray()

    private val seeds = listOf(
        "otpauth://totp/Example:alice@google.com?secret=JBSWY3DPEHPK3PXP&issuer=Example",
        "otpauth://hotp/Issuer:acct?secret=JBSWY3DPEHPK3PXP&counter=1&digits=8&algorithm=SHA256",
        "otpauth://totp/A%20B:c%40d?secret=jbsw%20y3dp&period=60",
        "otpauth-migration://offline?data=abc",
    )

    @Test
    fun randomStringsNeverThrow() {
        repeat(20_000) {
            val length = random.nextInt(0, 200)
            val input = String(CharArray(length) { alphabet[random.nextInt(alphabet.size)] })
            OtpUriParser.parse(input)
            OtpUriParser.parse("otpauth://$input")
            Base32.decode(input)
        }
    }

    @Test
    fun mutatedValidUrisNeverThrow() {
        repeat(20_000) {
            val chars = seeds[random.nextInt(seeds.size)].toCharArray().toMutableList()
            repeat(random.nextInt(1, 8)) {
                when (random.nextInt(3)) {
                    0 -> if (chars.isNotEmpty()) chars.removeAt(random.nextInt(chars.size))
                    1 -> chars.add(random.nextInt(chars.size + 1), alphabet[random.nextInt(alphabet.size)])
                    else -> if (chars.isNotEmpty()) {
                        chars[random.nextInt(chars.size)] = alphabet[random.nextInt(alphabet.size)]
                    }
                }
            }
            OtpUriParser.parse(String(chars.toCharArray()))
        }
    }
}
