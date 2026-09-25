package io.github.yozedens.secureauth.feature.addaccount

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import io.github.yozedens.secureauth.feature.common.ScreenColumn
import kotlinx.coroutines.launch

/**
 * Confirmation before anything is stored (design §44). Names can be corrected here;
 * the secret is never shown.
 */
@Composable
fun ConfirmAccountScreen(
    viewModel: AddAccountViewModel,
    pending: PendingSummary,
    busy: Boolean,
    onAdded: () -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var issuer by remember(pending) { mutableStateOf(pending.issuer) }
    var account by remember(pending) { mutableStateOf(pending.accountName) }
    var failed by remember { mutableStateOf(false) }
    val nameMissing = issuer.isBlank() && account.isBlank()

    ScreenColumn {
        Text(stringResource(R.string.confirm_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = issuer,
            onValueChange = { issuer = it },
            label = { Text(stringResource(R.string.confirm_issuer)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = account,
            onValueChange = { account = it },
            label = { Text(stringResource(R.string.confirm_account)) },
            singleLine = true,
            isError = nameMissing,
            modifier = Modifier.fillMaxWidth(),
        )
        val uri = pending.uri
        Detail(stringResource(R.string.confirm_type), if (uri.counter != null) "HOTP" else "TOTP")
        Detail(stringResource(R.string.confirm_algorithm), uri.algorithm)
        Detail(stringResource(R.string.confirm_digits), uri.digits.toString())
        uri.periodSeconds?.let {
            Detail(stringResource(R.string.confirm_period), stringResource(R.string.confirm_period_value, it))
        }
        uri.counter?.let { Detail(stringResource(R.string.confirm_counter), it.toString()) }

        if (pending.duplicate) Warning(stringResource(R.string.confirm_duplicate))
        if (pending.hotpCounterMissing) Warning(stringResource(R.string.confirm_hotp_counter_missing))
        if (nameMissing) Warning(stringResource(R.string.error_name_required))
        if (failed) Warning(stringResource(R.string.error_generic))

        Button(
            onClick = {
                scope.launch {
                    failed = false
                    if (viewModel.confirm(issuer, account)) onAdded() else failed = true
                }
            },
            enabled = !busy && !nameMissing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.confirm_add))
        }
        TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row {
        Text("$label：", style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Warning(text: String) {
    Text("⚠ $text", color = MaterialTheme.colorScheme.error)
}
