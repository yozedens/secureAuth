package io.github.yozedens.secureauth.core.vault

import io.github.yozedens.secureauth.core.base32.Base32
import io.github.yozedens.secureauth.core.crypto.AeadCipher
import io.github.yozedens.secureauth.core.crypto.JvmAesGcmCipher
import io.github.yozedens.secureauth.core.crypto.KeyUnavailableException
import io.github.yozedens.secureauth.core.error.ParseResult
import io.github.yozedens.secureauth.core.error.VaultError
import io.github.yozedens.secureauth.core.error.VaultResult
import io.github.yozedens.secureauth.core.model.Algorithm
import io.github.yozedens.secureauth.core.model.OtpKind
import io.github.yozedens.secureauth.core.model.Secret
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Design §50.6 in JVM form; the Android DataStore/Keystore variants run as instrumented tests. */
class VaultRepositoryTest {

    private val key = newKey()
    private val store = FakeVaultStore()
    private val clock = Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC)
    private var ids = 0
    private val repo = newRepo()

    private val rfcSecret = Secret("12345678901234567890".toByteArray())
    private val base32Secret = "JBSWY3DPEHPK3PXP"

    @Test
    fun firstLaunchIsUninitializedThenEmptyAndUnlocked() = runTest {
        assertEquals(VaultState.Unknown, repo.state.value)
        assertEquals(VaultState.Uninitialized, repo.refresh())
        ok(repo.initialize())
        assertEquals(VaultState.Unlocked, repo.state.value)
        assertTrue(repo.accounts.value.isEmpty())
        assertNotNull(store.bytes)
    }

    @Test
    fun initializeNeverReplacesAnExistingVault() = runTest {
        ok(repo.initialize())
        ok(repo.add(draft()))
        val before = store.bytes!!.copyOf()
        val second = newRepo()
        assertEquals(VaultState.Locked, second.refresh())
        assertTrue(runCatching { second.initialize() }.isFailure)
        assertArrayEquals(before, store.bytes)
    }

    @Test
    fun addGetUpdateDelete() = runTest {
        ok(repo.initialize())
        val meta = ok(repo.add(draft(issuer = "GitHub", account = "user@example.com")))
        assertEquals("id-1", meta.id)
        assertEquals(clock.millis(), meta.createdAt)
        assertEquals(listOf(meta), repo.accounts.value)

        val renamed = meta.copy(issuer = "GitHub Enterprise")
        ok(repo.updateMeta(renamed))
        assertEquals(listOf(renamed), repo.accounts.value)

        ok(repo.delete(meta.id))
        assertTrue(repo.accounts.value.isEmpty())
    }

    @Test
    fun keepsInsertionOrder() = runTest {
        ok(repo.initialize())
        val names = listOf("c", "a", "b")
        names.forEach { ok(repo.add(draft(account = it))) }
        assertEquals(names, repo.accounts.value.map { it.accountName })
    }

    @Test
    fun dataSurvivesRestart() = runTest {
        ok(repo.initialize())
        val meta = ok(repo.add(draft(issuer = "GitHub")))

        val restarted = newRepo()
        assertEquals(VaultState.Locked, restarted.refresh())
        assertTrue(restarted.accounts.value.isEmpty())
        ok(restarted.unlock())
        assertEquals(listOf(meta), restarted.accounts.value)
        assertEquals(repo.totpAt(meta.id, 59_000), restarted.totpAt(meta.id, 59_000))
    }

    @Test
    fun noPlaintextInStorage() = runTest {
        ok(repo.initialize())
        ok(repo.add(draft(issuer = "UniqueIssuerName", account = "unique.user@example.com")))
        val raw = store.bytes!!
        val text = String(raw, Charsets.ISO_8859_1)
        val secretBytes = (Base32.decode(base32Secret) as ParseResult.Success).value
        for (needle in listOf(base32Secret, "UniqueIssuerName", "unique.user", "Hello!", "SGVsbG8h")) {
            assertFalse("found '$needle' in storage", text.contains(needle))
        }
        assertFalse(text.contains(String(secretBytes, Charsets.ISO_8859_1)))
    }

    @Test
    fun totpCodeMatchesRfcVector() = runTest {
        ok(repo.initialize())
        val meta = ok(repo.add(draft(secret = rfcSecret, digits = 8)))
        assertEquals(OtpCode("94287082", remainingSeconds = 1), repo.totpAt(meta.id, 59_000))
        assertNull(repo.totpAt("unknown", 59_000))
    }

    @Test
    fun hotpPersistsIncrementBeforeReturningCode() = runTest {
        ok(repo.initialize())
        val meta = ok(repo.add(draft(secret = rfcSecret, kind = OtpKind.Hotp(0))))
        assertEquals("755224", ok(repo.nextHotp(meta.id)).value)
        assertEquals(OtpKind.Hotp(1), repo.accounts.value.single().kind)

        val restarted = newRepo()
        ok(restarted.unlock())
        assertEquals(OtpKind.Hotp(1), restarted.accounts.value.single().kind)
        assertEquals("287082", ok(restarted.nextHotp(meta.id)).value)
        assertNull(restarted.totpAt(meta.id, 0))
    }

    @Test
    fun hotpWriteFailureReturnsNoCodeAndKeepsCounter() = runTest {
        ok(repo.initialize())
        val meta = ok(repo.add(draft(secret = rfcSecret, kind = OtpKind.Hotp(5))))
        store.failWrites = true
        assertEquals(VaultResult.Failure(VaultError.Io), repo.nextHotp(meta.id))
        assertEquals(OtpKind.Hotp(5), repo.accounts.value.single().kind)
        assertEquals(VaultState.Unlocked, repo.state.value)
    }

    @Test
    fun concurrentHotpGenerationIsStrictlySequential() = runTest {
        ok(repo.initialize())
        val meta = ok(repo.add(draft(secret = rfcSecret, kind = OtpKind.Hotp(0))))
        val codes = (1..10).map { async { ok(repo.nextHotp(meta.id)).value } }.awaitAll()
        val expected = listOf(
            "755224", "287082", "359152", "969429", "338314",
            "254676", "287922", "162583", "399871", "520489",
        )
        assertEquals(expected.toSet(), codes.toSet())
        assertEquals(OtpKind.Hotp(10), repo.accounts.value.single().kind)
    }

    @Test
    fun replaceSecretChangesCodes() = runTest {
        ok(repo.initialize())
        val meta = ok(repo.add(draft(secret = rfcSecret, digits = 8)))
        val before = repo.totpAt(meta.id, 59_000)
        ok(repo.replaceSecret(meta.id, Secret(byteArrayOf(1, 2, 3, 4))))
        assertFalse(before == repo.totpAt(meta.id, 59_000))
    }

    @Test
    fun duplicateDetection() = runTest {
        ok(repo.initialize())
        ok(repo.add(draft(secret = secretOf(base32Secret))))
        assertTrue(repo.containsSecret(secretOf(base32Secret)))
        assertFalse(repo.containsSecret(Secret(byteArrayOf(9))))
    }

    @Test
    fun lockClearsAccountsAndBlocksAccess() = runTest {
        ok(repo.initialize())
        val meta = ok(repo.add(draft()))
        repo.lock()
        assertEquals(VaultState.Locked, repo.state.value)
        assertTrue(repo.accounts.value.isEmpty())
        assertTrue(runCatching { repo.totpAt(meta.id, 0) }.isFailure)
        assertTrue(runCatching { repo.add(draft()) }.isFailure)
        ok(repo.unlock())
        assertEquals(listOf(meta), repo.accounts.value)
    }

    @Test
    fun draftSecretIsCopied() = runTest {
        ok(repo.initialize())
        val secret = Secret("12345678901234567890".toByteArray())
        val meta = ok(repo.add(draft(secret = secret, digits = 8)))
        secret.wipe()
        assertEquals("94287082", repo.totpAt(meta.id, 59_000)!!.value)
    }

    @Test
    fun wrongKeyMakesVaultUnreadableWithoutOverwriting() = runTest {
        ok(repo.initialize())
        ok(repo.add(draft()))
        val before = store.bytes!!.copyOf()
        val otherKey = newRepo(JvmAesGcmCipher(newKey()))
        assertEquals(VaultResult.Failure(VaultError.AuthenticationFailed), otherKey.unlock())
        assertEquals(VaultState.Unreadable(VaultError.AuthenticationFailed), otherKey.state.value)
        assertArrayEquals(before, store.bytes)
    }

    @Test
    fun missingKeyIsReportedAndNothingIsWritten() = runTest {
        ok(repo.initialize())
        val before = store.bytes!!.copyOf()
        val writes = store.writes
        val gone = KeyUnavailableException("gone")
        val noKey = newRepo(object : AeadCipher {
            override fun encrypt(plaintext: ByteArray, aad: ByteArray) = throw gone
            override fun decrypt(iv: ByteArray, ciphertext: ByteArray, aad: ByteArray) = throw gone
        })
        assertEquals(VaultResult.Failure(VaultError.KeyMissing), noKey.unlock())
        assertEquals(VaultState.Unreadable(VaultError.KeyMissing), noKey.state.value)
        assertArrayEquals(before, store.bytes)
        assertEquals(writes, store.writes)
    }

    @Test
    fun externalCorruptionDetectedOnWriteAndNotOverwritten() = runTest {
        ok(repo.initialize())
        ok(repo.add(draft()))
        val corrupted = store.bytes!!.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        store.bytes = corrupted
        assertEquals(VaultResult.Failure(VaultError.AuthenticationFailed), repo.add(draft()))
        assertEquals(VaultState.Unreadable(VaultError.AuthenticationFailed), repo.state.value)
        assertArrayEquals(corrupted, store.bytes)
    }

    @Test
    fun ioErrors() = runTest {
        store.failReads = true
        assertEquals(VaultState.Unreadable(VaultError.Io), repo.refresh())
        store.failReads = false
        ok(repo.initialize())
        store.failWrites = true
        assertEquals(VaultResult.Failure(VaultError.Io), repo.add(draft()))
        assertEquals(VaultState.Unlocked, repo.state.value)
        assertTrue(repo.accounts.value.isEmpty())
    }

    @Test
    fun unlockWithoutVaultIsCorrupted() = runTest {
        assertEquals(VaultResult.Failure(VaultError.Corrupted), repo.unlock())
    }

    @Test
    fun refreshWhileUnlockedKeepsState() = runTest {
        ok(repo.initialize())
        assertEquals(VaultState.Unlocked, repo.refresh())
    }

    @Test
    fun resetDeletesAccountsAndReturnsToFirstLaunch() = runTest {
        ok(repo.initialize())
        ok(repo.add(draft()))
        ok(repo.reset())
        assertEquals(VaultState.Uninitialized, repo.state.value)
        assertTrue(repo.accounts.value.isEmpty())
        assertEquals(VaultState.Uninitialized, newRepo().refresh())
        ok(repo.initialize())
        assertTrue(repo.accounts.value.isEmpty())

        store.failWrites = true
        assertEquals(VaultResult.Failure(VaultError.Io), repo.reset())
        assertEquals(VaultState.Unlocked, repo.state.value)
    }

    private fun newRepo(cipher: AeadCipher = JvmAesGcmCipher(key)) =
        VaultRepository(store, cipher, clock, newId = { "id-${++ids}" })

    private fun draft(
        issuer: String? = "Issuer",
        account: String = "account",
        secret: Secret = secretOf(base32Secret),
        digits: Int = 6,
        kind: OtpKind = OtpKind.Totp(),
    ) = AccountDraft(
        issuer = issuer,
        accountName = account,
        algorithm = Algorithm.SHA1,
        digits = digits,
        kind = kind,
        secret = secret,
    )

    private fun secretOf(base32: String) = Secret((Base32.decode(base32) as ParseResult.Success).value)

    private fun <T> ok(result: VaultResult<T>): T = when (result) {
        is VaultResult.Success -> result.value
        is VaultResult.Failure -> throw AssertionError("unexpected ${result.error}")
    }


    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
}
