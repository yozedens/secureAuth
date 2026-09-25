package io.github.yozedens.secureauth.core.list

import io.github.yozedens.secureauth.core.model.AccountMeta
import io.github.yozedens.secureauth.core.model.Algorithm
import io.github.yozedens.secureauth.core.model.OtpKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountPresentationTest {

    @Test
    fun sortsByIssuerThenAccountCaseInsensitively() {
        val list = listOf(
            meta("1", "github", "b"),
            meta("2", "Google", "a"),
            meta("3", "GitHub", "a"),
            meta("4", null, "Apple ID"),
            meta("5", "aws", "root"),
        )
        assertEquals(listOf("4", "5", "3", "1", "2"), AccountPresentation.sorted(list).map { it.id })
    }

    @Test
    fun sortIsStableForEqualNames() {
        val list = listOf(meta("b", "X", "y", createdAt = 2), meta("a", "X", "y", createdAt = 1))
        assertEquals(listOf("a", "b"), AccountPresentation.sorted(list).map { it.id })
    }

    @Test
    fun searchMatchesIssuerOrAccount() {
        val m = meta("1", "GitHub", "user@example.com")
        assertTrue(AccountPresentation.matches(m, ""))
        assertTrue(AccountPresentation.matches(m, "  "))
        assertTrue(AccountPresentation.matches(m, "git"))
        assertTrue(AccountPresentation.matches(m, "EXAMPLE"))
        assertFalse(AccountPresentation.matches(m, "google"))
        assertTrue(AccountPresentation.matches(meta("2", null, "admin"), "adm"))
        assertTrue(AccountPresentation.matches(meta("3", "企业系统", "admin"), "企业"))
    }

    @Test
    fun groupsCodes() {
        assertEquals("123 456", AccountPresentation.groupCode("123456"))
        assertEquals("123 4567", AccountPresentation.groupCode("1234567"))
        assertEquals("1234 5678", AccountPresentation.groupCode("12345678"))
        assertEquals("12345", AccountPresentation.groupCode("12345"))
    }

    private fun meta(id: String, issuer: String?, account: String, createdAt: Long = 0) = AccountMeta(
        id = id,
        issuer = issuer,
        accountName = account,
        algorithm = Algorithm.SHA1,
        digits = 6,
        kind = OtpKind.Totp(),
        createdAt = createdAt,
    )
}
