package io.github.yozedens.secureauth.feature.lock

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.yozedens.secureauth.R
import io.github.yozedens.secureauth.feature.common.PinField
import io.github.yozedens.secureauth.feature.common.ResetDialog
import io.github.yozedens.secureauth.feature.common.ScreenColumn
import io.github.yozedens.secureauth.feature.root.LockMessage

/** Lock screen (design §41): biometric first if enabled, PIN always available. */
@Composable
fun LockScreen(
    busy: Boolean,
    message: LockMessage?,
    biometricEnabled: Boolean,
    onUnlockWithPin: (CharArray) -> Unit,
    onBiometric: () -> Unit,
    onReset: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var showForgot by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }

    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled) onBiometric()
    }
    LaunchedEffect(message) {
        if (message != null) pin = ""
    }

    val submit: () -> Unit = {
        if (pin.isNotEmpty()) onUnlockWithPin(pin.toCharArray())
        pin = ""
    }

    ScreenColumn {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.lock_title))
        PinField(pin, { pin = it }, stringResource(R.string.lock_title), enabled = !busy, onDone = submit)
        message?.let { Text(lockMessageText(it), color = MaterialTheme.colorScheme.error) }
        Button(onClick = submit, enabled = !busy && pin.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.lock_unlock))
        }
        if (biometricEnabled) {
            OutlinedButton(onClick = onBiometric, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.lock_use_biometric))
            }
        }
        TextButton(onClick = { showForgot = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.lock_forgot_pin))
        }
    }

    if (showForgot) {
        AlertDialog(
            onDismissRequest = { showForgot = false },
            title = { Text(stringResource(R.string.lock_forgot_pin)) },
            text = { Text(stringResource(R.string.lock_forgot_pin_message)) },
            confirmButton = {
                TextButton(onClick = { showForgot = false; showReset = true }) {
                    Text(stringResource(R.string.reset_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgot = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    if (showReset) {
        ResetDialog(onConfirm = { showReset = false; onReset() }, onDismiss = { showReset = false })
    }
}

@Composable
private fun lockMessageText(message: LockMessage): String = when (message) {
    is LockMessage.WrongPin -> stringResource(R.string.lock_wrong_pin, message.failures)
    is LockMessage.LockedOut -> stringResource(R.string.lock_locked_out, message.seconds)
    LockMessage.Error -> stringResource(R.string.error_generic)
}
