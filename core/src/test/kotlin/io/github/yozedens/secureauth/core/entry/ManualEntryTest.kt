package io.github.yozedens.secureauth.core.entry

import io.github.yozedens.secureauth.core.error.SecretProblem
import io.github.yozedens.secureauth.core.model.Algorithm
import io.github.yozedens.secureauth.core.model.OtpKind
import io.github.yozedens.secureauth.core.model.Secret
import io.github.yozedens.secureauth.core.otpauth.OtpUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ManualEntryTest {

    @Test
    fun validTotpWithSpacedLowercaseSecret() {
        val uri = valid(form(secret = "jbsw y3dp ehpk 3pxp"))
        assertEquals("GitHub", uri.issuer)
        assertEquals("user@example.com", uri.accountName)
        assertEquals(OtpKind.Totp(30), uri.kind)
        assertEquals(Algorithm.SHA1, uri.algorithm)
        assertEquals(10, uri.secret.size)
    }

    @Test
    fun validHotpWithAdvancedOptions() {
        val options = EntryOptions(isHotp = true, counter = " 42 ", algorithm = Algorithm.SHA256, digits = 8)
        val uri = valid(form(options = options))
        assertEquals(OtpKind.Hotp(42), uri.kind)
        assertEquals(Algorithm.SHA256, uri.algorithm)
        assertEquals(8, uri.digits)
    }

    @Test
    fun issuerOrAccountIsEnough() {
        assertNull(valid(form(issuer = "  ")).issuer)
        assertEquals("", valid(form(account = "")).accountName)
        assertEquals(setOf(EntryError.NameRequired), errors(form(issuer = " ", account = "\u0000 ")))
    }

    @Test
    fun reportsAllProblemsAtOnce() {
        val bad = EntryOptions(period = "0", digits = 9)
        val result = errors(form(issuer = "", account = "", secret = "JBSWY3DP0HPK3PXP", options = bad))
        assertEquals(
            setOf(
                EntryError.NameRequired,
                EntryError.InvalidSecret(SecretProblem.LOOKALIKE_DIGIT),
                EntryError.InvalidPeriod,
                EntryError.InvalidDigits,
            ),
            result,
        )
    }

    @Test
    fun validatesPeriodAndCounterText() {
        listOf("", "abc", "0", "301", "-5").forEach {
            assertEquals(it, setOf(EntryError.InvalidPeriod), errors(form(options = EntryOptions(period = it))))
        }
        listOf("", "x", "-1", "1.5").forEach {
            val options = EntryOptions(isHotp = true, counter = it)
            assertEquals(it, setOf(EntryError.InvalidCounter), errors(form(options = options)))
        }
        // The field of the other type is ignored.
        valid(form(options = EntryOptions(isHotp = true, period = "nonsense")))
        valid(form(options = EntryOptions(isHotp = false, counter = "nonsense")))
    }

    @Test
    fun emptySecret() {
        assertEquals(setOf(EntryError.InvalidSecret(SecretProblem.EMPTY)), errors(form(secret = " ")))
    }

    @Test
    fun formToStringHasNoSecret() {
        assertFalse(form().toString().contains("JBSWY3DP"))
    }

    @Test
    fun draftUsesEditedNamesAndCleansThem() {
        val uri = OtpUri(
            issuer = "Scanned",
            accountName = "",
            secret = Secret(byteArrayOf(1, 2, 3)),
            algorithm = Algorithm.SHA512,
            digits = 7,
            kind = OtpKind.Hotp(3),
            warnings = emptyList(),
        )
        val draft = ManualEntry.toDraft(uri, issuer = " Edited‮ ", accountName = " me@example.com ")
        assertEquals("Edited", draft.issuer)
        assertEquals("me@example.com", draft.accountName)
        assertEquals(OtpKind.Hotp(3), draft.kind)
        assertEquals(7, draft.digits)
        assertNull(ManualEntry.toDraft(uri, issuer = "  ", accountName = "x").issuer)
        assertEquals("abc", ManualEntry.cleanName(" a\u0000bc "))
    }

    private fun form(
        issuer: String = "GitHub",
        account: String = "user@example.com",
        secret: String = "JBSWY3DPEHPK3PXP",
        options: EntryOptions = EntryOptions(),
    ) = ManualEntryForm(issuer, account, secret, options)

    private fun valid(form: ManualEntryForm): OtpUri =
        (ManualEntry.validate(form) as? EntryResult.Valid)?.value ?: throw AssertionError(ManualEntry.validate(form))

    private fun errors(form: ManualEntryForm): Set<EntryError> =
        (ManualEntry.validate(form) as? EntryResult.Invalid)?.errors ?: throw AssertionError("expected invalid")
}
