package io.github.yozedens.secureauth.core.vault

import io.github.yozedens.secureauth.core.error.VaultError
import io.github.yozedens.secureauth.core.error.VaultResult
import io.github.yozedens.secureauth.core.model.AccountMeta
import io.github.yozedens.secureauth.core.model.Algorithm
import io.github.yozedens.secureauth.core.model.OtpAccount
import io.github.yozedens.secureauth.core.model.OtpKind
import io.github.yozedens.secureauth.core.model.Secret
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * Plaintext vault format (design §25): JSON with a `schemaVersion`, only ever held in
 * memory and encrypted before it reaches storage.
 */
internal object VaultCodec {

    const val SCHEMA_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(accounts: List<OtpAccount>): ByteArray =
        json.encodeToString<VaultDto>(VaultDto(SCHEMA_VERSION, accounts.map(::toDto)))
            .toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): VaultResult<List<OtpAccount>> {
        val dto = try {
            json.decodeFromString<VaultDto>(bytes.toString(Charsets.UTF_8))
        } catch (ignored: SerializationException) {
            return VaultResult.Failure(VaultError.Corrupted)
        } catch (ignored: IllegalArgumentException) {
            return VaultResult.Failure(VaultError.Corrupted)
        }
        if (dto.schemaVersion > SCHEMA_VERSION) return VaultResult.Failure(VaultError.UnsupportedVersion)
        return try {
            VaultResult.Success(dto.accounts.map(::fromDto))
        } catch (ignored: IllegalArgumentException) {
            // Invalid values (e.g. digits out of range) or Base64: treat as corrupted, never guess.
            VaultResult.Failure(VaultError.Corrupted)
        }
    }

    private fun toDto(account: OtpAccount): AccountDto {
        val meta = account.meta
        return AccountDto(
            id = meta.id,
            issuer = meta.issuer,
            accountName = meta.accountName,
            secret = account.secret.use { Base64.getEncoder().encodeToString(it) },
            algorithm = meta.algorithm.name,
            digits = meta.digits,
            kind = when (val kind = meta.kind) {
                is OtpKind.Totp -> KindDto(type = KindDto.TOTP, periodSeconds = kind.periodSeconds)
                is OtpKind.Hotp -> KindDto(type = KindDto.HOTP, counter = kind.counter)
            },
            createdAt = meta.createdAt,
        )
    }

    private fun fromDto(dto: AccountDto): OtpAccount {
        val kind = when (dto.kind.type) {
            KindDto.TOTP -> OtpKind.Totp(requireNotNull(dto.kind.periodSeconds) { "missing period" })
            KindDto.HOTP -> OtpKind.Hotp(requireNotNull(dto.kind.counter) { "missing counter" })
            else -> throw IllegalArgumentException("unknown kind")
        }
        val meta = AccountMeta(
            id = dto.id,
            issuer = dto.issuer,
            accountName = dto.accountName,
            algorithm = Algorithm.valueOf(dto.algorithm),
            digits = dto.digits,
            kind = kind,
            createdAt = dto.createdAt,
        )
        val bytes = Base64.getDecoder().decode(dto.secret)
        try {
            return OtpAccount(meta, Secret(bytes))
        } finally {
            bytes.fill(0)
        }
    }

    @Serializable
    private class VaultDto(
        val schemaVersion: Int,
        val accounts: List<AccountDto> = emptyList(),
    )

    @Serializable
    private class AccountDto(
        val id: String,
        val issuer: String? = null,
        val accountName: String,
        val secret: String,
        val algorithm: String,
        val digits: Int,
        val kind: KindDto,
        val createdAt: Long,
    )

    @Serializable
    private class KindDto(
        val type: String,
        val periodSeconds: Int? = null,
        val counter: Long? = null,
    ) {
        companion object {
            const val TOTP = "totp"
            const val HOTP = "hotp"
        }
    }
}
