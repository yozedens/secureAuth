package io.github.yozedens.secureauth.core.otpauth

import io.github.yozedens.secureauth.core.base32.Base32
import io.github.yozedens.secureauth.core.error.ParseError
import io.github.yozedens.secureauth.core.error.ParseResult
import io.github.yozedens.secureauth.core.error.ParseWarning
import io.github.yozedens.secureauth.core.error.SecretProblem
import io.github.yozedens.secureauth.core.model.Algorithm
import io.github.yozedens.secureauth.core.model.OtpKind
import io.github.yozedens.secureauth.core.model.Secret
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers every row of design §15 plus the error cases of §50.4. */
class OtpUriParserTest {

    private val secret = "JBSWY3DPEHPK3PXP"
    private val expectedSecret = Secret(Base32.decode(secret).let { (it as ParseResult.Success).value })

    // --- standard URIs -------------------------------------------------------------------

    @Test
    fun parsesStandardTotp() {
        val uri = ok("otpauth://totp/Example:alice@google.com?secret=$secret&issuer=Example")
        assertEquals("Example", uri.issuer)
        assertEquals("alice@google.com", uri.accountName)
        assertEquals(expectedSecret, uri.secret)
        assertEquals(Algorithm.SHA1, uri.algorithm)
        assertEquals(6, uri.digits)
        assertEquals(OtpKind.Totp(30), uri.kind)
        assertTrue(uri.warnings.isEmpty())
    }

    @Test
    fun parsesAllParameters() {
        val uri = ok("otpauth://totp/ACME:john?secret=$secret&issuer=ACME&algorithm=SHA512&digits=8&period=60")
        assertEquals(Algorithm.SHA512, uri.algorithm)
        assertEquals(8, uri.digits)
        assertEquals(OtpKind.Totp(60), uri.kind)
    }

    @Test
    fun parsesHotpWithCounter() {
        val uri = ok("otpauth://hotp/Issuer:account@example.com?secret=$secret&issuer=Issuer&counter=42")
        assertEquals(OtpKind.Hotp(42), uri.kind)
        assertTrue(uri.warnings.isEmpty())
    }

    // --- §15 rows ------------------------------------------------------------------------

    @Test
    fun schemeTypeParamNamesAndAlgorithmAreCaseInsensitive() {
        val uri = ok("OTPAUTH://TOTP/X:y?SECRET=$secret&Algorithm=sha256&DIGITS=7")
        assertEquals(Algorithm.SHA256, uri.algorithm)
        assertEquals(7, uri.digits)
        assertEquals(Algorithm.SHA256, ok("otpauth://totp/x?secret=$secret&algorithm=SHA-256").algorithm)
    }

    @Test
    fun decodesUrlEncodedLabel() {
        val uri = ok("otpauth://totp/Example%20Service:user%40example.com?secret=$secret")
        assertEquals("Example Service", uri.issuer)
        assertEquals("user@example.com", uri.accountName)
    }

    @Test
    fun keepsPlusLiteralInLabel() {
        val uri = ok("otpauth://totp/Service:user+2fa@gmail.com?secret=$secret")
        assertEquals("user+2fa@gmail.com", uri.accountName)
    }

    @Test
    fun treatsPlusAsSpaceInQueryValues() {
        assertEquals("Example Service", ok("otpauth://totp/x?secret=$secret&issuer=Example+Service").issuer)
    }

    @Test
    fun acceptsEncodedColonAndSpaceAfterColon() {
        val uri = ok("otpauth://totp/ACME%3A%20john@example.com?secret=$secret")
        assertEquals("ACME", uri.issuer)
        assertEquals("john@example.com", uri.accountName)
        assertEquals("john", ok("otpauth://totp/ACME:   john?secret=$secret").accountName)
    }

    @Test
    fun acceptsRawSpacesAndUtf8() {
        val uri = ok("otpauth://totp/My Company:张三@例子.com?secret=$secret")
        assertEquals("My Company", uri.issuer)
        assertEquals("张三@例子.com", uri.accountName)
        assertEquals("企业系统", ok("otpauth://totp/%E4%BC%81%E4%B8%9A%E7%B3%BB%E7%BB%9F:a?secret=$secret").issuer)
    }

    @Test
    fun issuerParameterWinsOverLabelPrefix() {
        assertEquals("Real", ok("otpauth://totp/Label:acct?secret=$secret&issuer=Real").issuer)
        assertEquals("Label", ok("otpauth://totp/Label:acct?secret=$secret&issuer=").issuer)
    }

    @Test
    fun allowsMissingIssuerAndEmptyAccount() {
        val noIssuer = ok("otpauth://totp/acct?secret=$secret")
        assertNull(noIssuer.issuer)
        assertEquals("acct", noIssuer.accountName)

        val empty = ok("otpauth://totp/?secret=$secret")
        assertEquals("", empty.accountName)
        assertNull(empty.issuer)

        assertEquals("", ok("otpauth://totp?secret=$secret").accountName)
        assertEquals("", ok("otpauth://totp/Issuer:?secret=$secret").accountName)
    }

    @Test
    fun ignoresUnknownParametersAndFragments() {
        val uri = ok("otpauth://totp/x?secret=$secret&image=https%3A%2F%2Fexample.com%2Fa.png&color=red&lock=true#frag")
        assertEquals(expectedSecret, uri.secret)
    }

    @Test
    fun rejectsDuplicateSecret() {
        fail(ParseError.DuplicateSecret, "otpauth://totp/x?secret=$secret&secret=GEZDGNBV")
        fail(ParseError.DuplicateSecret, "otpauth://totp/x?secret=$secret&SECRET=$secret")
    }

    @Test
    fun firstOccurrenceWinsForOtherDuplicates() {
        assertEquals(6, ok("otpauth://totp/x?secret=$secret&digits=6&digits=8").digits)
    }

    @Test
    fun validatesDigits() {
        listOf("5", "9", "0", "-6", "six", "").forEach {
            fail(ParseError.InvalidDigits, "otpauth://totp/x?secret=$secret&digits=$it")
        }
        listOf(6, 7, 8).forEach { assertEquals(it, ok("otpauth://totp/x?secret=$secret&digits=$it").digits) }
    }

    @Test
    fun validatesPeriod() {
        listOf("0", "-30", "301", "abc", "").forEach {
            fail(ParseError.InvalidPeriod, "otpauth://totp/x?secret=$secret&period=$it")
        }
        assertEquals(OtpKind.Totp(300), ok("otpauth://totp/x?secret=$secret&period=300").kind)
    }

    @Test
    fun hotpWithoutCounterDefaultsToZeroWithWarning() {
        val uri = ok("otpauth://hotp/x?secret=$secret")
        assertEquals(OtpKind.Hotp(0), uri.kind)
        assertEquals(listOf(ParseWarning.HOTP_COUNTER_MISSING), uri.warnings)
    }

    @Test
    fun validatesCounter() {
        listOf("-1", "abc", "1.5", "").forEach {
            fail(ParseError.InvalidCounter, "otpauth://hotp/x?secret=$secret&counter=$it")
        }
        val max = ok("otpauth://hotp/x?secret=$secret&counter=${Long.MAX_VALUE}")
        assertEquals(OtpKind.Hotp(Long.MAX_VALUE), max.kind)
    }

    @Test
    fun ignoresParametersOfTheOtherType() {
        assertEquals(OtpKind.Totp(30), ok("otpauth://totp/x?secret=$secret&counter=5").kind)
        assertEquals(OtpKind.Hotp(5), ok("otpauth://hotp/x?secret=$secret&counter=5&period=0").kind)
    }

    @Test
    fun migrationFormatHasItsOwnError() {
        fail(ParseError.UnsupportedMigrationFormat, "otpauth-migration://offline?data=CjEKCkhlbGxvId6tvu8")
        fail(ParseError.UnsupportedMigrationFormat, "OTPAUTH-MIGRATION://offline?data=x")
    }

    @Test
    fun stripsControlAndBidiCharactersAndCapsLength() {
        val uri = ok("otpauth://totp/Git%E2%80%AEHub%0A:a%00b?secret=$secret")
        assertEquals("GitHub", uri.issuer)
        assertEquals("ab", uri.accountName)

        val long = "a".repeat(1000)
        val capped = ok("otpauth://totp/x:$long?secret=$secret").accountName
        assertEquals(OtpUriParser.MAX_LABEL_FIELD_LENGTH, capped.length)

        val emoji = "a".repeat(OtpUriParser.MAX_LABEL_FIELD_LENGTH - 1) + "😀"
        val cappedEmoji = ok("otpauth://totp/x:$emoji?secret=$secret").accountName
        assertFalse(cappedEmoji.last().isHighSurrogate())
    }

    @Test
    fun keepsInvalidPercentEscapesLiterally() {
        assertEquals("100%", ok("otpauth://totp/100%?secret=$secret").accountName)
        assertEquals("a%zzb", ok("otpauth://totp/a%zzb?secret=$secret").accountName)
    }

    @Test
    fun acceptsSurroundingWhitespaceAndSpacedSecret() {
        val uri = ok("  otpauth://totp/x?secret=JBSW%20Y3DP%20EHPK%203PXP  ")
        assertEquals(expectedSecret, uri.secret)
    }

    // --- §50.4 errors --------------------------------------------------------------------

    @Test
    fun rejectsWrongScheme() {
        listOf("https://example.com", "otpauth:/totp/x?secret=$secret", "", "totp/x?secret=$secret").forEach {
            fail(ParseError.InvalidScheme, it)
        }
    }

    @Test
    fun rejectsWrongType() {
        listOf("otpauth://motp/x?secret=$secret", "otpauth:///x?secret=$secret", "otpauth://steam/x?secret=$secret")
            .forEach { fail(ParseError.InvalidType, it) }
    }

    @Test
    fun rejectsMissingSecret() {
        fail(ParseError.MissingSecret, "otpauth://totp/x?issuer=a")
        fail(ParseError.MissingSecret, "otpauth://totp/x")
    }

    @Test
    fun rejectsInvalidSecret() {
        fail(ParseError.InvalidSecret(SecretProblem.EMPTY), "otpauth://totp/x?secret=")
        fail(ParseError.InvalidSecret(SecretProblem.LOOKALIKE_DIGIT), "otpauth://totp/x?secret=JBSWY3DP0HPK3PXP")
        fail(ParseError.InvalidSecret(SecretProblem.INVALID_LENGTH), "otpauth://totp/x?secret=AAA")
    }

    @Test
    fun rejectsInvalidAlgorithm() {
        listOf("MD5", "SHA3", "", "SHA-384").forEach {
            fail(ParseError.InvalidAlgorithm, "otpauth://totp/x?secret=$secret&algorithm=$it")
        }
    }

    @Test
    fun rejectsOverlongInput() {
        fail(ParseError.InvalidUri, "otpauth://totp/x?secret=$secret&pad=" + "a".repeat(OtpUriParser.MAX_URI_LENGTH))
    }

    @Test
    fun toStringDoesNotLeakSecret() {
        val uri = ok("otpauth://totp/x?secret=$secret")
        assertFalse(uri.toString().contains(secret))
        assertFalse(OtpUriParser.parse("otpauth://totp/x?secret=JBSWY3DP0HPK3PXP").toString().contains("JBSWY3DP"))
    }

    private fun ok(input: String): OtpUri {
        val result = OtpUriParser.parse(input)
        check(result is ParseResult.Success) { "expected success for '$input' but got $result" }
        return result.value
    }

    private fun fail(expected: ParseError, input: String) {
        assertEquals(input, ParseResult.Failure(expected), OtpUriParser.parse(input))
    }
}
