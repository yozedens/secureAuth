package io.github.yozedens.secureauth.feature.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yozedens.secureauth.AppContainer
import io.github.yozedens.secureauth.core.error.VaultError
import io.github.yozedens.secureauth.core.error.VaultResult
import io.github.yozedens.secureauth.core.security.PinCheck
import io.github.yozedens.secureauth.core.settings.AppSettings
import io.github.yozedens.secureauth.core.settings.AutoLockTimeout
import io.github.yozedens.secureauth.core.vault.VaultState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException

/** Which top-level screen the root gate shows (design §40). */
sealed interface Screen {
    data object Loading : Screen
    data object Onboarding : Screen
    data object Lock : Screen
    data class Unreadable(val reason: VaultError) : Screen
    data object Home : Screen
    data object Settings : Screen
    data object ChangePin : Screen
}

/** Feedback shown on the lock screen. */
sealed interface LockMessage {
    data class WrongPin(val failures: Int) : LockMessage
    data class LockedOut(val seconds: Int) : LockMessage
    data object Error : LockMessage
}

/** One-off feedback for unlocked screens. */
enum class Notice { PIN_CHANGED, WRONG_CURRENT_PIN, SAVE_FAILED }

data class RootUiState(
    val screen: Screen = Screen.Loading,
    val busy: Boolean = false,
    val lockMessage: LockMessage? = null,
    val notice: Notice? = null,
    val settings: AppSettings = AppSettings(),
)

/**
 * Drives the root gate (design §28). The lock state *is* the vault state: it starts as
 * Unknown/Locked in every new process and is never restored from saved state, so a
 * process restored from recents always shows the lock screen first.
 */
class AppViewModel(private val c: AppContainer) : ViewModel() {

    internal enum class Page { HOME, SETTINGS, CHANGE_PIN }

    private data class Local(
        /** Set when the PIN store is permanently unreadable, without ever unlocking the vault. */
        val forcedError: VaultError? = null,
        val page: Page = Page.HOME,
        val busy: Boolean = false,
        val lockMessage: LockMessage? = null,
        val notice: Notice? = null,
    )

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<RootUiState> =
        combine(c.vault.state, local, c.settings.settings) { vault, l, settings ->
            RootUiState(
                screen = l.forcedError?.let { Screen.Unreadable(it) } ?: screenFor(vault, l.page),
                busy = l.busy,
                lockMessage = l.lockMessage,
                notice = l.notice,
                settings = settings,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, RootUiState())

    init {
        viewModelScope.launch {
            c.settings.load()
            if (c.vault.refresh() == VaultState.Locked && !keyExists(c)) {
                // Restored from backup or transfer: data present, key gone (ADR 0001 §7).
                local.update { it.copy(forcedError = VaultError.KeyMissing) }
            }
        }
    }

    /** First launch: create the key, store the PIN, create the empty vault (design §32). */
    fun completeOnboarding(pin: CharArray) = runBusy {
        val ok = withContext(c.defaultDispatcher) {
            try {
                c.cipher.createKeyIfAbsent()
                c.pins.setPin(pin) is VaultResult.Success && c.vault.initialize() is VaultResult.Success
            } catch (ignored: GeneralSecurityException) {
                false
            }
        }
        pin.fill('\u0000')
        if (!ok) local.update { it.copy(notice = Notice.SAVE_FAILED) }
    }

    fun unlockWithPin(pin: CharArray) = runBusy {
        val check = withContext(c.defaultDispatcher) { c.pins.verify(pin) }
        pin.fill('\u0000')
        val message = when (check) {
            PinCheck.Correct -> null.also { unlockVault() }
            is PinCheck.Wrong ->
                if (check.lockoutMillis > 0) {
                    LockMessage.LockedOut(seconds(check.lockoutMillis))
                } else {
                    LockMessage.WrongPin(check.failures)
                }
            is PinCheck.LockedOut -> LockMessage.LockedOut(seconds(check.remainingMillis))
            is PinCheck.Failed -> LockMessage.Error.also {
                if (check.error.isPermanent()) local.update { l -> l.copy(forcedError = check.error) }
            }
            PinCheck.NotSet -> LockMessage.Error
        }
        local.update { it.copy(lockMessage = message) }
    }

    fun onBiometricUnlocked() = runBusy {
        c.pins.resetFailures()
        unlockVault()
        local.update { it.copy(lockMessage = null) }
    }

    fun lockNow() {
        viewModelScope.launch { c.vault.lock() }
    }

    /** "Clear all data and start over" (design §26.1). The Keystore key is kept. */
    fun resetAll() = runBusy {
        c.pins.clear()
        c.vault.reset()
        local.value = Local()
    }

    fun retry() = runBusy {
        local.update { it.copy(forcedError = null, lockMessage = null) }
        if (c.vault.refresh() == VaultState.Locked && !keyExists(c)) {
            local.update { it.copy(forcedError = VaultError.KeyMissing) }
        }
    }

    fun openSettings() = local.update { it.copy(page = Page.SETTINGS, notice = null) }

    fun openChangePin() = local.update { it.copy(page = Page.CHANGE_PIN, notice = null) }

    fun back() = local.update {
        it.copy(page = if (it.page == Page.CHANGE_PIN) Page.SETTINGS else Page.HOME, notice = null)
    }

    fun setBiometricEnabled(enabled: Boolean) = saveSettings { it.copy(biometricEnabled = enabled) }

    fun setAutoLock(timeout: AutoLockTimeout) = saveSettings { it.copy(autoLock = timeout) }

    fun changePin(current: CharArray, new: CharArray) = runBusy {
        val notice = withContext(c.defaultDispatcher) {
            when (c.pins.verify(current)) {
                PinCheck.Correct ->
                    if (c.pins.setPin(new) is VaultResult.Success) Notice.PIN_CHANGED else Notice.SAVE_FAILED
                else -> Notice.WRONG_CURRENT_PIN
            }
        }
        current.fill('\u0000')
        new.fill('\u0000')
        local.update {
            it.copy(page = if (notice == Notice.PIN_CHANGED) Page.SETTINGS else it.page, notice = notice)
        }
    }

    private suspend fun unlockVault() {
        c.vault.unlock()
        local.update { it.copy(page = Page.HOME) }
    }

    private fun saveSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            try {
                c.settings.update(transform)
            } catch (ignored: IOException) {
                local.update { it.copy(notice = Notice.SAVE_FAILED) }
            }
        }
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (local.value.busy) return
        viewModelScope.launch {
            local.update { it.copy(busy = true) }
            try {
                block()
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }
}

/** Whether the vault key exists; a temporary Keystore error counts as "exists" so unlock reports it. */
private suspend fun keyExists(c: AppContainer): Boolean = withContext(c.ioDispatcher) {
    try {
        c.cipher.keyExists()
    } catch (ignored: GeneralSecurityException) {
        true
    } catch (ignored: IOException) {
        true
    }
}

private fun VaultError.isPermanent(): Boolean = this != VaultError.Io && this != VaultError.CryptoUnavailable

private fun screenFor(vault: VaultState, page: AppViewModel.Page): Screen = when (vault) {
    VaultState.Unknown -> Screen.Loading
    VaultState.Uninitialized -> Screen.Onboarding
    VaultState.Locked -> Screen.Lock
    is VaultState.Unreadable -> Screen.Unreadable(vault.reason)
    VaultState.Unlocked -> when (page) {
        AppViewModel.Page.HOME -> Screen.Home
        AppViewModel.Page.SETTINGS -> Screen.Settings
        AppViewModel.Page.CHANGE_PIN -> Screen.ChangePin
    }
}

private const val MILLIS_PER_SECOND = 1000L

private fun seconds(millis: Long): Int = ((millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
