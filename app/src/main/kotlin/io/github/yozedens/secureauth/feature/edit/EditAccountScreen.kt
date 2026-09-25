package io.github.yozedens.secureauth.feature.edit

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.yozedens.secureauth.R
import io.github.yozedens.secureauth.feature.addaccount.AdvancedOptionsFields
import io.github.yozedens.secureauth.feature.addaccount.NameFields
import io.github.yozedens.secureauth.feature.addaccount.SecretField
import io.github.yozedens.secureauth.feature.addaccount.entryErrorText
import io.github.yozedens.secureauth.feature.common.ScreenColumn
import kotlinx.coroutines.launch

/** Edit and delete (design §45, §46). The current secret is never shown. */
@Composable
fun EditAccountScreen(viewModel: EditAccountViewModel, form: EditFormState, onDeleted: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

    ScreenColumn {
        Text(stringResource(R.string.edit_title), style = MaterialTheme.typography.headlineSmall)
        NameFields(
            issuer = form.issuer,
            accountName = form.accountName,
            onIssuer = { v -> viewModel.update { it.copy(issuer = v) } },
            onAccountName = { v -> viewModel.update { it.copy(accountName = v) } },
        )
        Text(stringResource(R.string.edit_params_warning), color = MaterialTheme.colorScheme.error)
        AdvancedOptionsFields(form.options) { options -> viewModel.update { it.copy(options = options) } }
        SecretField(
            value = form.newSecret,
            onValueChange = { v -> viewModel.update { it.copy(newSecret = v) } },
            label = stringResource(R.string.edit_new_secret),
            visible = false,
        )
        form.errors.forEach { Text(entryErrorText(it), color = MaterialTheme.colorScheme.error) }
        if (form.saved) Text(stringResource(R.string.edit_saved))
        if (form.failed) Text(stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
        Button(
            onClick = { scope.launch { viewModel.save() } },
            enabled = !form.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.edit_save))
        }
        TextButton(onClick = { confirmDelete = true }, enabled = !form.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.edit_delete), color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_back))
        }
    }

    if (confirmDelete) {
        DeleteDialog(
            name = listOf(form.issuer, form.accountName).filter { it.isNotBlank() }.joinToString("\n"),
            onConfirm = {
                confirmDelete = false
                scope.launch { if (viewModel.delete()) onDeleted() }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Deletion is permanent and cannot be undone (design §46), so it is always confirmed. */
@Composable
private fun DeleteDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title)) },
        text = { Text(stringResource(R.string.delete_message, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
