package io.github.yozedens.secureauth.feature.accounts

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yozedens.secureauth.R

/** Account list (design §18, §42). */
@Composable
fun AccountListScreen(
    viewModel: AccountListViewModel,
    onOpenSettings: () -> Unit,
    onLockNow: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val autoTimeOff = rememberAutoTimeOff()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.home_settings)) }
                TextButton(onClick = onLockNow) { Text(stringResource(R.string.home_lock)) }
            }
            if (autoTimeOff) {
                Text(
                    stringResource(R.string.accounts_auto_time_off),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.totalCount > 0) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text(stringResource(R.string.accounts_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AccountList(state, onCopy = viewModel::copy, onGenerate = viewModel::generateHotp)
        }
    }
}

@Composable
private fun AccountList(
    state: AccountListUiState,
    onCopy: (AccountUiModel) -> Unit,
    onGenerate: (String) -> Unit,
) {
    when {
        state.totalCount == 0 -> Text(stringResource(R.string.accounts_empty))
        state.accounts.isEmpty() -> Text(stringResource(R.string.accounts_no_match))
        else -> LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.accounts, key = { it.id }) { account ->
                AccountCard(
                    account = account,
                    copied = state.lastCopiedId == account.id,
                    busy = state.busy,
                    onCopy = { onCopy(account) },
                    onGenerate = { onGenerate(account.id) },
                )
            }
        }
    }
}

/**
 * Reads the "automatic date & time" setting on every resume (design §58); no permission
 * is needed to read Settings.Global.
 */
@Composable
private fun rememberAutoTimeOff(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var off by remember { mutableStateOf(false) }
    LifecycleResumeEffect(resolver) {
        off = Settings.Global.getInt(resolver, Settings.Global.AUTO_TIME, 1) == 0
        onPauseOrDispose { }
    }
    return off
}
