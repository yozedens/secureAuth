package io.github.yozedens.secureauth.feature.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.yozedens.secureauth.R
import io.github.yozedens.secureauth.core.settings.AppSettings
import io.github.yozedens.secureauth.core.settings.AutoLockTimeout
import io.github.yozedens.secureauth.feature.common.ScreenColumn

/** Settings (design §3.1 F22): biometric, auto-lock, change PIN. */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    biometricAvailable: Boolean,
    errorText: String?,
    onBiometricToggle: (Boolean) -> Unit,
    onAutoLockChange: (AutoLockTimeout) -> Unit,
    onChangePin: () -> Unit,
    onBack: () -> Unit,
) {
    ScreenColumn {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_biometric), modifier = Modifier.weight(1f))
            Switch(
                checked = settings.biometricEnabled && biometricAvailable,
                onCheckedChange = onBiometricToggle,
                enabled = biometricAvailable,
            )
        }
        if (!biometricAvailable) {
            Text(stringResource(R.string.settings_biometric_unavailable), style = MaterialTheme.typography.bodySmall)
        }

        Text(stringResource(R.string.settings_auto_lock), style = MaterialTheme.typography.titleMedium)
        AutoLockTimeout.entries.forEach { timeout ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = settings.autoLock == timeout, onClick = { onAutoLockChange(timeout) }),
            ) {
                RadioButton(selected = settings.autoLock == timeout, onClick = { onAutoLockChange(timeout) })
                Text(stringResource(timeout.label()))
            }
        }

        OutlinedButton(onClick = onChangePin, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_change_pin))
        }
        Text(stringResource(R.string.settings_data_warning), style = MaterialTheme.typography.bodySmall)
        errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_back))
        }
    }
}

private fun AutoLockTimeout.label(): Int = when (this) {
    AutoLockTimeout.IMMEDIATELY -> R.string.settings_auto_lock_immediately
    AutoLockTimeout.THIRTY_SECONDS -> R.string.settings_auto_lock_30s
    AutoLockTimeout.ONE_MINUTE -> R.string.settings_auto_lock_1m
    AutoLockTimeout.FIVE_MINUTES -> R.string.settings_auto_lock_5m
}
