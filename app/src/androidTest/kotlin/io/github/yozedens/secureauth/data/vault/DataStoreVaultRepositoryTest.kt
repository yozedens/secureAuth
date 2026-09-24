package io.github.yozedens.secureauth.data.vault

import android.content.Context
import androidx.datastore.dataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yozedens.secureauth.core.base32.Base32
import io.github.yozedens.secureauth.core.error.ParseResult
import io.github.yozedens.secureauth.core.error.VaultError
import io.github.yozedens.secureauth.core.error.VaultResult
import io.github.yozedens.secureauth.core.model.Algorithm
import io.github.yozedens.secureauth.core.model.OtpKind
import io.github.yozedens.secureauth.core.model.Secret
import io.github.yozedens.secureauth.core.vault.AccountDraft
import io.github.yozedens.secureauth.core.vault.VaultRepository
import io.github.yozedens.secureauth.core.vault.VaultState
import io.github.yozedens.secureauth.security.keystore.KeystoreAeadCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.time.Clock

/** Design §50.6 on a device: real DataStore file and real Keystore key. */
@RunWith(AndroidJUnit4::class)
class DataStoreVaultRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fileName = "vault_test.pb"
    private val alias = "secureauth_test_vault_key"
    private val secretBase32 = "JBSWY3DPEHPK3PXP"

    @Before
    fun setUp() = clean()

    @After
    fun tearDown() = clean()

    @Test
    fun dataSurvivesRestartAndIsEncryptedOnDisk() = runTest {
        val cipher = KeystoreAeadCipher(alias).apply { createKeyIfAbsent() }

        val scope1 = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val repo1 = VaultRepository(DataStoreVaultStore(context, scope1, fileName), cipher, Clock.systemUTC())
        assertEquals(VaultState.Uninitialized, repo1.refresh())
        ok(repo1.initialize())
        val meta = ok(repo1.add(draft("UniqueIssuerName", "unique.user@example.com")))
        // Only one active DataStore per file: shut the first one down, as a process restart would.
        scope1.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()

        val raw = context.dataStoreFile(fileName).readBytes()
        val text = String(raw, Charsets.ISO_8859_1)
        for (needle in listOf(secretBase32, "UniqueIssuerName", "unique.user", "Hello!", "SGVsbG8h")) {
            assertFalse("found '$needle' on disk", text.contains(needle))
        }
        assertArrayEquals("SAV1".toByteArray(), raw.copyOfRange(0, 4))

        val scope2 = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val repo2 = VaultRepository(DataStoreVaultStore(context, scope2, fileName), cipher, Clock.systemUTC())
        assertEquals(VaultState.Locked, repo2.refresh())
        ok(repo2.unlock())
        assertEquals(listOf(meta), repo2.accounts.value)
        scope2.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()
    }

    @Test
    fun missingKeyLeavesFileUntouched() = runTest {
        val cipher = KeystoreAeadCipher(alias).apply { createKeyIfAbsent() }
        val scope1 = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val repo1 = VaultRepository(DataStoreVaultStore(context, scope1, fileName), cipher, Clock.systemUTC())
        ok(repo1.initialize())
        ok(repo1.add(draft("GitHub", "user")))
        scope1.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()
        val before = context.dataStoreFile(fileName).readBytes()

        // Simulates a restore from backup: file present, Keystore key gone.
        deleteKey()
        val scope2 = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val repo2 = VaultRepository(DataStoreVaultStore(context, scope2, fileName), cipher, Clock.systemUTC())
        assertEquals(VaultState.Locked, repo2.refresh())
        assertEquals(VaultResult.Failure(VaultError.KeyMissing), repo2.unlock())
        assertEquals(VaultState.Unreadable(VaultError.KeyMissing), repo2.state.value)
        scope2.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()

        assertArrayEquals(before, context.dataStoreFile(fileName).readBytes())
        assertFalse(cipher.keyExists())
    }

    private fun draft(issuer: String, account: String) = AccountDraft(
        issuer = issuer,
        accountName = account,
        algorithm = Algorithm.SHA1,
        digits = 6,
        kind = OtpKind.Totp(),
        secret = Secret((Base32.decode(secretBase32) as ParseResult.Success).value),
    )

    private fun <T> ok(result: VaultResult<T>): T = when (result) {
        is VaultResult.Success -> result.value
        is VaultResult.Failure -> throw AssertionError("unexpected ${result.error}")
    }

    private fun clean() {
        context.dataStoreFile(fileName).delete()
        deleteKey()
    }

    private fun deleteKey() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }
}
