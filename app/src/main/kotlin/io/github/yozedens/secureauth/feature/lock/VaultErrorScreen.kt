package io.github.yozedens.secureauth.feature.lock

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.yozedens.secureauth.R
import io.github.yozedens.secureauth.core.error.VaultError
import io.github.yozedens.secureauth.feature.common.ResetDialog
import io.github.yozedens.secureauth.feature.common.ScreenColumn

/**
 * Vault cannot be read (design §26.1). Nothing has been overwritten; the user can retry
 * or, after a second confirmation, clear everything.
 */
@Composable
fun VaultErrorScreen(reason: VaultError, busy: Boolean, onRetry: () -> Unit, onReset: () -> Unit) {
    var showReset by remember { mutableStateOf(false) }

    ScreenColumn {
        Text(stringResource(R.string.vault_error_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(reasonText(reason)))
        Text(stringResource(R.string.vault_error_no_overwrite))
        Button(onClick = onRetry, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_retry))
        }
        TextButton(onClick = { showReset = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.reset_action), color = MaterialTheme.colorScheme.error)
        }
    }

    if (showReset) {
        ResetDialog(onConfirm = { showReset = false; onReset() }, onDismiss = { showReset = false })
    }
}

private fun reasonText(reason: VaultError): Int = when (reason) {
    VaultError.KeyMissing -> R.string.vault_error_key_missing
    VaultError.AuthenticationFailed -> R.string.vault_error_auth_failed
    VaultError.Corrupted -> R.string.vault_error_corrupted
    VaultError.UnsupportedVersion -> R.string.vault_error_unsupported_version
    VaultError.CryptoUnavailable, VaultError.Io -> R.string.vault_error_temporary
}
