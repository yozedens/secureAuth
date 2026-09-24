package io.github.yozedens.secureauth.core.security

import io.github.yozedens.secureauth.core.crypto.AeadCipher
import io.github.yozedens.secureauth.core.crypto.VaultEnvelope
import io.github.yozedens.secureauth.core.error.VaultError
import io.github.yozedens.secureauth.core.error.VaultResult
import io.github.yozedens.secureauth.core.vault.VaultStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Clock
import java.util.Base64

/** Outcome of a PIN check. */
sealed interface PinCheck {
    data object Correct : PinCheck
    /** Wrong PIN; [lockoutMillis] is the wait now required (0 if none). */
    data class Wrong(val failures: Int, val lockoutMillis: Long) : PinCheck
    /** Too many failures; the PIN was not checked. */
    data class LockedOut(val remainingMillis: Long) : PinCheck
    data object NotSet : PinCheck
    data class Failed(val error: VaultError) : PinCheck
}

/**
 * PIN verifier and failure counter, stored in `security.pb` encrypted with the master key
 * (ADR 0001 §5), so a copied file cannot be brute-forced off the device. The failure
 * count is persisted: restarting the app does not reset the backoff.
 */
class PinManager(
    private val store: VaultStore,
    private val cipher: AeadCipher,
    private val clock: Clock,
    private val iterations: () -> Int,
    private val keyId: Int = DEFAULT_KEY_ID,
) {
    private val mutex = Mutex()

    suspend fun hasPin(): Boolean = mutex.withLock {
        (load() as? VaultResult.Success)?.value?.verifier != null
    }

    /** Sets a new PIN and clears failures. Caller must have validated [PinPolicy]. */
    suspend fun setPin(pin: CharArray): VaultResult<Unit> = mutex.withLock {
        val verifier = PinHasher.create(pin, iterations())
        save(Record(verifier = VerifierDto.of(verifier)))
    }

    suspend fun verify(pin: CharArray): PinCheck = mutex.withLock {
        val record = when (val loaded = load()) {
            is VaultResult.Success -> loaded.value
            is VaultResult.Failure -> return@withLock PinCheck.Failed(loaded.error)
        }
        val verifier = record.verifier?.toVerifier() ?: return@withLock PinCheck.NotSet
        val remaining = remainingLockout(record)
        if (remaining > 0) return@withLock PinCheck.LockedOut(remaining)

        if (PinHasher.matches(pin, verifier)) {
            if (record.failedAttempts != 0) save(record.copy(failedAttempts = 0, lastFailureAt = 0))
            PinCheck.Correct
        } else {
            val failures = record.failedAttempts + 1
            when (val saved = save(record.copy(failedAttempts = failures, lastFailureAt = clock.millis()))) {
                is VaultResult.Failure -> PinCheck.Failed(saved.error)
                is VaultResult.Success -> PinCheck.Wrong(failures, PinBackoff.waitMillis(failures))
            }
        }
    }

    /** Remaining wait before the next attempt is allowed, in milliseconds. */
    suspend fun remainingLockoutMillis(): Long = mutex.withLock {
        (load() as? VaultResult.Success)?.value?.let(::remainingLockout) ?: 0
    }

    /** Clears the failure count, e.g. after a successful biometric unlock. */
    suspend fun resetFailures(): VaultResult<Unit> = mutex.withLock {
        when (val loaded = load()) {
            is VaultResult.Failure -> loaded
            is VaultResult.Success ->
                if (loaded.value.failedAttempts == 0) {
                    VaultResult.Success(Unit)
                } else {
                    save(loaded.value.copy(failedAttempts = 0, lastFailureAt = 0))
                }
        }
    }

    /** Removes the PIN record (used by "clear all data"). */
    suspend fun clear(): VaultResult<Unit> = mutex.withLock {
        try {
            store.update { ByteArray(0) }
            VaultResult.Success(Unit)
        } catch (ignored: IOException) {
            VaultResult.Failure(VaultError.Io)
        }
    }

    /** A clock moved backwards restarts the wait instead of shortening it. */
    private fun remainingLockout(record: Record): Long {
        val wait = PinBackoff.waitMillis(record.failedAttempts)
        if (wait == 0L) return 0
        val elapsed = clock.millis() - record.lastFailureAt
        return if (elapsed < 0) wait else (wait - elapsed).coerceAtLeast(0)
    }

    private suspend fun load(): VaultResult<Record> {
        val bytes = try {
            store.read()
        } catch (ignored: IOException) {
            return VaultResult.Failure(VaultError.Io)
        } ?: return VaultResult.Success(Record())
        val envelope = when (val decoded = VaultEnvelope.decode(bytes)) {
            is VaultResult.Success -> decoded.value
            is VaultResult.Failure -> return decoded
        }
        val plaintext = when (val opened = VaultEnvelope.open(cipher, envelope)) {
            is VaultResult.Success -> opened.value
            is VaultResult.Failure -> return opened
        }
        return try {
            VaultResult.Success(json.decodeFromString<Record>(plaintext.toString(Charsets.UTF_8)))
        } catch (ignored: SerializationException) {
            VaultResult.Failure(VaultError.Corrupted)
        } catch (ignored: IllegalArgumentException) {
            VaultResult.Failure(VaultError.Corrupted)
        } finally {
            plaintext.fill(0)
        }
    }

    private suspend fun save(record: Record): VaultResult<Unit> {
        val plaintext = json.encodeToString<Record>(record).toByteArray(Charsets.UTF_8)
        val sealed = try {
            VaultEnvelope.seal(cipher, keyId, plaintext)
        } finally {
            plaintext.fill(0)
        }
        val envelope = when (sealed) {
            is VaultResult.Success -> sealed.value
            is VaultResult.Failure -> return sealed
        }
        return try {
            store.update { envelope.encode() }
            VaultResult.Success(Unit)
        } catch (ignored: IOException) {
            VaultResult.Failure(VaultError.Io)
        }
    }

    @Serializable
    private data class Record(
        val verifier: VerifierDto? = null,
        val failedAttempts: Int = 0,
        val lastFailureAt: Long = 0,
    )

    @Serializable
    private class VerifierDto(
        val algorithm: String,
        val iterations: Int,
        val salt: String,
        val hash: String,
    ) {
        fun toVerifier() = PinVerifier(
            algorithm = algorithm,
            iterations = iterations,
            salt = Base64.getDecoder().decode(salt),
            hash = Base64.getDecoder().decode(hash),
        )

        companion object {
            fun of(v: PinVerifier) = VerifierDto(
                algorithm = v.algorithm,
                iterations = v.iterations,
                salt = Base64.getEncoder().encodeToString(v.salt),
                hash = Base64.getEncoder().encodeToString(v.hash),
            )
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

private const val DEFAULT_KEY_ID = 1
