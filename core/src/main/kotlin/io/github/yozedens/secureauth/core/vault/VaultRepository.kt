package io.github.yozedens.secureauth.core.vault

import io.github.yozedens.secureauth.core.crypto.AeadCipher
import io.github.yozedens.secureauth.core.error.VaultError
import io.github.yozedens.secureauth.core.error.VaultResult
import io.github.yozedens.secureauth.core.model.AccountMeta
import io.github.yozedens.secureauth.core.model.OtpAccount
import io.github.yozedens.secureauth.core.model.OtpKind
import io.github.yozedens.secureauth.core.model.Secret
import io.github.yozedens.secureauth.core.otp.Hotp
import io.github.yozedens.secureauth.core.otp.Totp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Clock
import java.util.UUID

/**
 * Encrypted account storage and code generation (design §21, §22, §26).
 *
 * Secrets only go in: callers get [AccountMeta] and [OtpCode], never a [Secret].
 * Decrypted accounts are held only between [unlock] and [lock]. Every write is an atomic
 * read-decrypt-modify-encrypt of the stored envelope; a stored vault that cannot be
 * read is never overwritten.
 */
class VaultRepository(
    private val store: VaultStore,
    private val cipher: AeadCipher,
    private val clock: Clock,
    private val keyId: Int = DEFAULT_KEY_ID,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    private val sealer = VaultSealer(cipher, keyId)
    private val mutex = Mutex()
    private var unlocked: List<OtpAccount>? = null

    private val _state = MutableStateFlow<VaultState>(VaultState.Unknown)
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private val _accounts = MutableStateFlow<List<AccountMeta>>(emptyList())

    /** Metadata of all accounts in stored order; empty while locked. */
    val accounts: StateFlow<List<AccountMeta>> = _accounts.asStateFlow()

    /** Determines whether a vault exists. Does not decrypt. */
    suspend fun refresh(): VaultState = mutex.withLock {
        if (unlocked != null) return@withLock _state.value
        val next = try {
            if (store.read() == null) VaultState.Uninitialized else VaultState.Locked
        } catch (ignored: IOException) {
            VaultState.Unreadable(VaultError.Io)
        }
        next.also { _state.value = it }
    }

    /**
     * Creates an empty vault on first launch.
     *
     * @throws IllegalStateException if a vault already exists; it is never replaced.
     */
    suspend fun initialize(): VaultResult<Unit> = mutex.withLock {
        val result = io {
            store.update { current ->
                check(current == null) { "vault already exists" }
                sealer.seal(emptyList())
            }
        }
        if (result is VaultResult.Success) setUnlocked(emptyList())
        result
    }

    /** Decrypts the vault into memory. On failure the state becomes [VaultState.Unreadable]. */
    suspend fun unlock(): VaultResult<Unit> = mutex.withLock {
        val result = when (val read = io { store.read() }) {
            is VaultResult.Success -> sealer.open(read.value)
            is VaultResult.Failure -> read
        }
        when (result) {
            is VaultResult.Success -> {
                setUnlocked(result.value)
                VaultResult.Success(Unit)
            }
            is VaultResult.Failure -> {
                _state.value = VaultState.Unreadable(result.error)
                result
            }
        }
    }

    /** Drops and wipes all decrypted data (design §28). */
    suspend fun lock() = mutex.withLock {
        unlocked?.forEach { it.secret.wipe() }
        unlocked = null
        _accounts.value = emptyList()
        if (_state.value == VaultState.Unlocked) _state.value = VaultState.Locked
    }

    /** True if an account with the same secret already exists (design §44). */
    suspend fun containsSecret(secret: Secret): Boolean = mutex.withLock {
        requireUnlocked().any { it.secret == secret }
    }

    suspend fun add(draft: AccountDraft): VaultResult<AccountMeta> {
        val meta = AccountMeta(
            id = newId(),
            issuer = draft.issuer,
            accountName = draft.accountName,
            algorithm = draft.algorithm,
            digits = draft.digits,
            kind = draft.kind,
            createdAt = clock.millis(),
        )
        return modify { accounts -> accounts + OtpAccount(meta, Secret(draft.secret.bytesCopy())) }
            .map { meta }
    }

    suspend fun updateMeta(meta: AccountMeta): VaultResult<Unit> =
        modify { accounts -> accounts.map { if (it.meta.id == meta.id) OtpAccount(meta, it.secret) else it } }
            .map { }

    suspend fun replaceSecret(id: String, secret: Secret): VaultResult<Unit> =
        modify { accounts ->
            accounts.map { if (it.meta.id == id) OtpAccount(it.meta, Secret(secret.bytesCopy())) else it }
        }.map { }

    suspend fun delete(id: String): VaultResult<Unit> =
        modify { accounts -> accounts.filterNot { it.meta.id == id } }.map { }

    /** TOTP code at [epochMillis], or `null` if [id] is unknown or not TOTP. */
    suspend fun totpAt(id: String, epochMillis: Long): OtpCode? = mutex.withLock {
        val account = requireUnlocked().firstOrNull { it.meta.id == id } ?: return@withLock null
        val kind = account.meta.kind as? OtpKind.Totp ?: return@withLock null
        val seconds = Math.floorDiv(epochMillis, MILLIS_PER_SECOND)
        OtpCode(
            value = account.secret.use {
                Totp.generate(it, seconds, kind.periodSeconds, account.meta.algorithm, account.meta.digits)
            },
            remainingSeconds = Totp.remainingSeconds(seconds, kind.periodSeconds),
        )
    }

    /**
     * Next HOTP code (design §20): the incremented counter is persisted first, and the
     * code for the previous counter is returned only after the write succeeded.
     */
    suspend fun nextHotp(id: String): VaultResult<OtpCode> {
        val known = mutex.withLock { requireUnlocked().any { it.meta.id == id && it.meta.kind is OtpKind.Hotp } }
        require(known) { "no HOTP account with this id" }
        var code: OtpCode? = null
        val result = modify { accounts ->
            accounts.map { account ->
                val kind = account.meta.kind
                if (account.meta.id != id || kind !is OtpKind.Hotp) return@map account
                code = OtpCode(
                    value = account.secret.use {
                        Hotp.generate(it, kind.counter, account.meta.algorithm, account.meta.digits)
                    },
                    remainingSeconds = null,
                )
                OtpAccount(account.meta.copy(kind = OtpKind.Hotp(kind.counter + 1)), account.secret)
            }
        }
        return when (result) {
            is VaultResult.Failure -> result
            is VaultResult.Success -> code?.let { VaultResult.Success(it) }
                // Deleted concurrently between the check above and the write.
                ?: error("HOTP account disappeared")
        }
    }

    /**
     * Applies [transform] to the stored vault (not the in-memory copy) and writes it back
     * atomically. The in-memory copy is updated only after the write succeeded.
     */
    private suspend fun modify(transform: (List<OtpAccount>) -> List<OtpAccount>): VaultResult<List<OtpAccount>> =
        mutex.withLock {
            requireUnlocked()
            var stored: List<OtpAccount> = emptyList()
            var updated: List<OtpAccount> = emptyList()
            val result = io {
                store.update { current ->
                    stored = when (val opened = sealer.open(current)) {
                        is VaultResult.Success -> opened.value
                        is VaultResult.Failure -> throw VaultAbort(opened.error)
                    }
                    updated = transform(stored)
                    sealer.seal(updated)
                }
            }
            when (result) {
                is VaultResult.Success -> {
                    wipeAllExcept(stored, keep = updated)
                    setUnlocked(updated)
                    VaultResult.Success(updated)
                }
                is VaultResult.Failure -> {
                    wipeAllExcept(stored + updated, keep = emptyList())
                    if (result.error.isPermanent()) _state.value = VaultState.Unreadable(result.error)
                    result
                }
            }
        }

    private fun setUnlocked(accounts: List<OtpAccount>) {
        unlocked?.let { wipeAllExcept(it, keep = accounts) }
        unlocked = accounts
        _accounts.value = accounts.map { it.meta }
        _state.value = VaultState.Unlocked
    }

    private fun requireUnlocked(): List<OtpAccount> = checkNotNull(unlocked) { "vault is locked" }

    companion object {
        const val DEFAULT_KEY_ID = 1
        private const val MILLIS_PER_SECOND = 1000L
    }
}

private inline fun <T> io(block: () -> T): VaultResult<T> =
    try {
        VaultResult.Success(block())
    } catch (e: VaultAbort) {
        VaultResult.Failure(e.error)
    } catch (ignored: IOException) {
        VaultResult.Failure(VaultError.Io)
    }

private fun VaultError.isPermanent(): Boolean =
    this != VaultError.Io && this != VaultError.CryptoUnavailable

private inline fun <T, R> VaultResult<T>.map(f: (T) -> R): VaultResult<R> = when (this) {
    is VaultResult.Success -> VaultResult.Success(f(value))
    is VaultResult.Failure -> this
}


/** Wipes secrets of [accounts] that are not (by identity) still used in [keep]. */
private fun wipeAllExcept(accounts: List<OtpAccount>, keep: List<OtpAccount>) {
    accounts.filter { a -> keep.none { it.secret === a.secret } }.forEach { it.secret.wipe() }
}
