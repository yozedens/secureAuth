package io.github.yozedens.secureauth.core.base32

import io.github.yozedens.secureauth.core.error.ParseError
import io.github.yozedens.secureauth.core.error.ParseResult
import io.github.yozedens.secureauth.core.error.SecretProblem
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class Base32Test {

    /** "Hello!" + DE AD BE EF: 10 bytes, 80 bits (shorter than RFC 4226's 128, still accepted). */
    private val expected = "Hello!".toByteArray() +
        byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())

    @Test
    fun acceptsCommonFormattingOfTheSameSecret() {
        listOf(
            "JBSWY3DPEHPK3PXP",
            "jbswy3dpehpk3pxp",
            "JBSWY3DPEHPK3PXP====",
            "JBSWY3DPEHPK3PXP======",
            "  JBSWY3DPEHPK3PXP  ",
            "JBSW Y3DP EHPK 3PXP",
            "jbsw-y3dp-ehpk-3pxp",
            "JBSW\tY3DP\nEHPK 3PXP",
        ).forEach { input ->
            assertArrayEquals(input, expected, decodeOk(input))
        }
    }

    @Test
    fun rejectsEmpty() {
        listOf("", "   ", "====", " - ").forEach { assertProblem(it, SecretProblem.EMPTY) }
    }

    @Test
    fun rejectsInvalidCharacters() {
        assertProblem("JBSWY3DP!HPK3PXP", SecretProblem.INVALID_CHARACTER)
        assertProblem("JBSWY3DPÉHPK3PXP", SecretProblem.INVALID_CHARACTER)
    }

    @Test
    fun flagsDigitsThatLookLikeLetters() {
        listOf("JBSWY3DP0HPK3PXP", "JBSWY3DP1HPK3PXP", "JBSWY3DP8HPK3PXP", "JBSWY3DP9HPK3PXP").forEach {
            assertProblem(it, SecretProblem.LOOKALIKE_DIGIT)
        }
    }

    @Test
    fun rejectsPaddingInTheMiddle() {
        assertProblem("JBSW=Y3DPEHPK3PXP", SecretProblem.MISPLACED_PADDING)
    }

    @Test
    fun rejectsLengthsThatCannotComeFromWholeBytes() {
        // Unpadded length mod 8 of 1, 3 or 6 is invalid.
        listOf("A", "AAA", "AAAAAA", "AAAAAAAAA").forEach { assertProblem(it, SecretProblem.INVALID_LENGTH) }
        listOf("AA", "AAAA", "AAAAA", "AAAAAAA", "AAAAAAAA").forEach { decodeOk(it) }
    }

    @Test
    fun rejectsOversizedSecrets() {
        val ok = Base32.encode(ByteArray(Base32.MAX_DECODED_BYTES) { 1 })
        assertEquals(Base32.MAX_DECODED_BYTES, decodeOk(ok).size)
        val tooLong = Base32.encode(ByteArray(Base32.MAX_DECODED_BYTES + 5) { 1 })
        assertProblem(tooLong, SecretProblem.TOO_LONG)
    }

    @Test
    fun errorsDoNotContainTheInput() {
        val result = Base32.decode("JBSWY3DP!HPK3PXP")
        assertEquals(false, result.toString().contains("JBSWY3DP"))
    }

    @Test
    fun roundTripsRandomData() {
        val random = Random(42)
        repeat(500) {
            val data = random.nextBytes(random.nextInt(1, 80))
            assertArrayEquals(data, decodeOk(Base32.encode(data)))
            assertArrayEquals(data, decodeOk(Base32.encode(data).lowercase()))
        }
    }

    @Test
    fun encodesKnownVector() {
        assertEquals("JBSWY3DPEHPK3PXP", Base32.encode(expected))
        assertEquals("MZXW6YTBOI", Base32.encode("foobar".toByteArray()))
    }

    private fun decodeOk(input: String): ByteArray {
        val result = Base32.decode(input)
        check(result is ParseResult.Success) { "expected success for '$input' but got $result" }
        return result.value
    }

    private fun assertProblem(input: String, problem: SecretProblem) {
        assertEquals(input, ParseResult.Failure(ParseError.InvalidSecret(problem)), Base32.decode(input))
    }
}
