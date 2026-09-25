package io.github.yozedens.secureauth.feature.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.yozedens.secureauth.R

/**
 * One account (design §42). Tapping a TOTP card copies its code; HOTP needs an explicit
 * "generate" because every generation consumes a counter value (design §20).
 */
@Composable
fun AccountCard(
    account: AccountUiModel,
    copied: Boolean,
    busy: Boolean,
    onCopy: () -> Unit,
    onGenerate: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = account.code != null, onClick = onCopy),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(account.issuer ?: account.accountName, style = MaterialTheme.typography.titleMedium)
            if (account.issuer != null) {
                Text(account.accountName, style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = account.displayCode ?: stringResource(R.string.accounts_hotp_hidden),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                if (!account.isHotp) TotpCountdown(account)
            }
            if (account.isHotp) {
                Text(stringResource(R.string.accounts_counter, account.counter ?: 0L))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (account.isHotp) {
                    OutlinedButton(onClick = onGenerate, enabled = !busy) {
                        Text(stringResource(R.string.accounts_generate))
                    }
                }
                TextButton(onClick = onCopy, enabled = account.code != null) {
                    Text(stringResource(R.string.accounts_copy))
                }
            }
            if (copied) {
                Text(stringResource(R.string.accounts_copied), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TotpCountdown(account: AccountUiModel) {
    val remaining = account.remainingSeconds ?: return
    val period = account.periodSeconds ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            progress = { remaining.toFloat() / period },
            modifier = Modifier.size(28.dp),
        )
        Text(stringResource(R.string.accounts_remaining, remaining), style = MaterialTheme.typography.bodySmall)
    }
}
