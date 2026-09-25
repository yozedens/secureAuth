package io.github.yozedens.secureauth.feature.addaccount

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import io.github.yozedens.secureauth.feature.common.ScreenColumn
import kotlinx.coroutines.launch

/** Manual entry (design §17). Advanced options are collapsed by default. */
@Composable
fun ManualEntryScreen(viewModel: AddAccountViewModel, form: ManualFormState, onValid: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var showSecret by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    ScreenColumn {
        Text(stringResource(R.string.add_manual), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.manual_type))
        Row {
            FilterChip(
                selected = !form.options.isHotp,
                onClick = { viewModel.updateManual { it.copy(options = it.options.copy(isHotp = false)) } },
                label = { Text("TOTP") },
            )
            FilterChip(
                selected = form.options.isHotp,
                onClick = { viewModel.updateManual { it.copy(options = it.options.copy(isHotp = true)) } },
                label = { Text("HOTP") },
            )
        }
        NameFields(
            issuer = form.issuer,
            accountName = form.accountName,
            onIssuer = { v -> viewModel.updateManual { it.copy(issuer = v) } },
            onAccountName = { v -> viewModel.updateManual { it.copy(accountName = v) } },
        )
        SecretField(
            value = form.secret,
            onValueChange = { v -> viewModel.updateManual { it.copy(secret = v) } },
            label = stringResource(R.string.manual_secret),
            visible = showSecret,
        )
        TextButton(onClick = { showSecret = !showSecret }) {
            Text(stringResource(if (showSecret) R.string.manual_hide_secret else R.string.manual_show_secret))
        }
        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text((if (showAdvanced) "▾ " else "▸ ") + stringResource(R.string.manual_advanced))
        }
        if (showAdvanced) {
            AdvancedOptionsFields(form.options) { options -> viewModel.updateManual { it.copy(options = options) } }
        }
        form.errors.forEach { Text(entryErrorText(it), color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = { scope.launch { if (viewModel.submitManual()) onValid() } },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.manual_next))
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}
