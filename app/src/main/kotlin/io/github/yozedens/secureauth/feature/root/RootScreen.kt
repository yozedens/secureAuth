package io.github.yozedens.secureauth.feature.root

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yozedens.secureauth.R
import io.github.yozedens.secureauth.feature.accounts.AccountListScreen
import io.github.yozedens.secureauth.feature.accounts.AccountListViewModel
import io.github.yozedens.secureauth.feature.lock.LockScreen
import io.github.yozedens.secureauth.feature.lock.VaultErrorScreen
import io.github.yozedens.secureauth.feature.onboarding.OnboardingScreen
import io.github.yozedens.secureauth.feature.onboarding.PinSetupScreen
import io.github.yozedens.secureauth.feature.settings.SettingsScreen
import io.github.yozedens.secureauth.security.biometric.BiometricAuthenticator

/**
 * The lock gate (design §28): exactly one of these screens is composed, chosen from the
 * vault state. Nothing sensitive is composed while locked.
 */
@Composable
fun RootScreen(
    viewModel: AppViewModel,
    accountListViewModel: AccountListViewModel,
    biometric: BiometricAuthenticator,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = checkNotNull(LocalActivity.current) as FragmentActivity
    val biometricTitle = stringResource(R.string.biometric_title)
    val biometricNegative = stringResource(R.string.biometric_negative)
    val biometricAvailable = biometric.availability() == BiometricAuthenticator.Availability.AVAILABLE

    when (val screen = state.screen) {
        Screen.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        Screen.Onboarding -> OnboardingScreen(busy = state.busy, onPinChosen = viewModel::completeOnboarding)
        Screen.Lock -> LockScreen(
            busy = state.busy,
            message = state.lockMessage,
            biometricEnabled = state.settings.biometricEnabled && biometricAvailable,
            onUnlockWithPin = viewModel::unlockWithPin,
            onBiometric = {
                biometric.authenticate(activity, biometricTitle, biometricNegative) { result ->
                    if (result == BiometricAuthenticator.Result.SUCCESS) viewModel.onBiometricUnlocked()
                }
            },
            onReset = viewModel::resetAll,
        )
        is Screen.Unreadable -> VaultErrorScreen(
            reason = screen.reason,
            busy = state.busy,
            onRetry = viewModel::retry,
            onReset = viewModel::resetAll,
        )
        Screen.Home -> AccountListScreen(
            viewModel = accountListViewModel,
            onOpenSettings = viewModel::openSettings,
            onLockNow = viewModel::lockNow,
        )
        Screen.Settings -> SettingsRoute(state, viewModel, biometric, biometricAvailable)
        Screen.ChangePin -> {
            BackHandler(onBack = viewModel::back)
            PinSetupScreen(
                title = stringResource(R.string.pin_change_title),
                busy = state.busy,
                onPinChosen = {},
                requireCurrent = true,
                onChangePin = viewModel::changePin,
                onCancel = viewModel::back,
                externalError = noticeText(state.notice),
            )
        }
    }
}

/** Settings with biometric enabling confirmed by a successful prompt first. */
@Composable
private fun SettingsRoute(
    state: RootUiState,
    viewModel: AppViewModel,
    biometric: BiometricAuthenticator,
    biometricAvailable: Boolean,
) {
    val activity = checkNotNull(LocalActivity.current) as FragmentActivity
    val biometricTitle = stringResource(R.string.biometric_title)
    val biometricNegative = stringResource(R.string.biometric_negative)
    BackHandler(onBack = viewModel::back)
    SettingsScreen(
        settings = state.settings,
        biometricAvailable = biometricAvailable,
        errorText = noticeText(state.notice),
        onBiometricToggle = { enable ->
            if (!enable) {
                viewModel.setBiometricEnabled(false)
            } else {
                biometric.authenticate(activity, biometricTitle, biometricNegative) { result ->
                    if (result == BiometricAuthenticator.Result.SUCCESS) viewModel.setBiometricEnabled(true)
                }
            }
        },
        onAutoLockChange = viewModel::setAutoLock,
        onChangePin = viewModel::openChangePin,
        onBack = viewModel::back,
    )
}

@Composable
private fun noticeText(notice: Notice?): String? = when (notice) {
    null -> null
    Notice.PIN_CHANGED -> stringResource(R.string.pin_changed)
    Notice.WRONG_CURRENT_PIN -> stringResource(R.string.pin_current_wrong)
    Notice.SAVE_FAILED -> stringResource(R.string.settings_save_failed)
}
