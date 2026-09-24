package io.github.yozedens.secureauth.feature.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.yozedens.secureauth.R

/** Second confirmation before permanently deleting everything (design §26.1). */
@Composable
fun ResetDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reset_title)) },
        text = { Text(stringResource(R.string.reset_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.reset_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
