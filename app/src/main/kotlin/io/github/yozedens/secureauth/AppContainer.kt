package io.github.yozedens.secureauth

import android.content.Context
import android.os.SystemClock
import io.github.yozedens.secureauth.core.security.AutoLock
import io.github.yozedens.secureauth.core.security.PinHasher
import io.github.yozedens.secureauth.core.security.PinManager
import io.github.yozedens.secureauth.core.settings.SettingsRepository
import io.github.yozedens.secureauth.core.vault.VaultRepository
import io.github.yozedens.secureauth.data.vault.DataStoreVaultStore
import io.github.yozedens.secureauth.security.biometric.BiometricAuthenticator
import io.github.yozedens.secureauth.security.keystore.KeystoreAeadCipher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock

/**
 * Manual dependency container (design §48). Everything is lazy: no Keystore or file I/O
 * happens in Application.onCreate.
 */
class AppContainer(
    context: Context,
    /** For Keystore and file access. */
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** For CPU-bound work such as PIN hashing. */
    val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val appContext = context.applicationContext

    /** Outlives screens: DataStore actors and the clipboard timer run here. */
    val applicationScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    val cipher: KeystoreAeadCipher by lazy { KeystoreAeadCipher() }

    val vault: VaultRepository by lazy {
        VaultRepository(DataStoreVaultStore(appContext, applicationScope, VAULT_FILE), cipher, Clock.systemUTC())
    }

    /** PBKDF2 iterations calibrated on this device for new PINs (ADR 0001 §5). */
    private val pinIterations: Int by lazy { PinHasher.calibrate(PIN_TARGET_MILLIS) }

    val pins: PinManager by lazy {
        PinManager(
            DataStoreVaultStore(appContext, applicationScope, SECURITY_FILE),
            cipher,
            Clock.systemUTC(),
            iterations = { pinIterations },
        )
    }

    val settings: SettingsRepository by lazy {
        SettingsRepository(DataStoreVaultStore(appContext, applicationScope, SETTINGS_FILE))
    }

    val autoLock = AutoLock(SystemClock::elapsedRealtime)

    val biometric: BiometricAuthenticator by lazy { BiometricAuthenticator(appContext) }

    private companion object {
        const val VAULT_FILE = "vault.pb"
        const val SECURITY_FILE = "security.pb"
        const val SETTINGS_FILE = "settings.pb"
        const val PIN_TARGET_MILLIS = 400L
    }
}
