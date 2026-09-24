package io.github.yozedens.secureauth.core.vault

import io.github.yozedens.secureauth.core.error.VaultError
import io.github.yozedens.secureauth.core.error.VaultResult
import io.github.yozedens.secureauth.core.model.AccountMeta
import io.github.yozedens.secureauth.core.model.Algorithm
import io.github.yozedens.secureauth.core.model.OtpAccount
import io.github.yozedens.secureauth.core.model.OtpKind
import io.github.yozedens.secureauth.core.model.Secret
import org.junit.Assert.assertEquals
import org.junit.Test

class VaultCodecTest {

    @Test
    fun roundTripsBothKinds() {
        val accounts = listOf(
            OtpAccount(meta("a", OtpKind.Totp(60)), Secret(byteArrayOf(1, 2, 3))),
            OtpAccount(meta("b", OtpKind.Hotp(42)), Secret(byteArrayOf(4, 5))),
        )
        val decoded = (VaultCodec.decode(VaultCodec.encode(accounts)) as VaultResult.Success).value
        assertEquals(accounts.map { it.meta }, decoded.map { it.meta })
        assertEquals(accounts.map { it.secret }, decoded.map { it.secret })
    }

    @Test
    fun ignoresUnknownFieldsForForwardCompatibility() {
        val json = """{"schemaVersion":1,"future":true,"accounts":[{"id":"x","accountName":"a",""" +
            """"secret":"AQI=","algorithm":"SHA1","digits":6,""" +
            """"kind":{"type":"totp","periodSeconds":30,"extra":1},"createdAt":0,"tag":"t"}]}"""
        val decoded = (VaultCodec.decode(json.toByteArray()) as VaultResult.Success).value
        assertEquals("x", decoded.single().meta.id)
    }

    @Test
    fun rejectsNewerSchema() {
        assertEquals(
            VaultResult.Failure(VaultError.UnsupportedVersion),
            VaultCodec.decode("""{"schemaVersion":2,"accounts":[]}""".toByteArray()),
        )
    }

    @Test
    fun invalidContentIsCorrupted() {
        val corrupted = VaultResult.Failure(VaultError.Corrupted)
        listOf(
            "not json",
            """{"accounts":[]}""",
            account(digits = 9),
            account(algorithm = "MD5"),
            account(kind = """{"type":"totp"}"""),
            account(kind = """{"type":"hotp"}"""),
            account(kind = """{"type":"motp"}"""),
            account(secret = "!!!"),
            account(secret = ""),
        ).forEach { assertEquals(it, corrupted, VaultCodec.decode(it.toByteArray())) }
    }

    private fun account(
        digits: Int = 6,
        algorithm: String = "SHA1",
        kind: String = """{"type":"totp","periodSeconds":30}""",
        secret: String = "AQI=",
    ) = """{"schemaVersion":1,"accounts":[{"id":"x","accountName":"a","secret":"$secret",""" +
        """"algorithm":"$algorithm","digits":$digits,"kind":$kind,"createdAt":0}]}"""

    private fun meta(id: String, kind: OtpKind) = AccountMeta(
        id = id,
        issuer = null,
        accountName = "acct-$id",
        algorithm = Algorithm.SHA256,
        digits = 7,
        kind = kind,
        createdAt = 123,
    )
}
