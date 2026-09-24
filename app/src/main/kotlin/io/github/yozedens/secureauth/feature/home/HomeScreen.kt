package io.github.yozedens.secureauth.feature.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.yozedens.secureauth.R
import io.github.yozedens.secureauth.feature.common.ScreenColumn

/** Placeholder for the account list, which arrives in M4. */
@Composable
fun HomeScreen(onOpenSettings: () -> Unit, onLockNow: () -> Unit) {
    ScreenColumn {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.home_empty))
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_settings))
        }
        OutlinedButton(onClick = onLockNow, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_lock))
        }
    }
}
