package io.github.yozedens.secureauth.feature.onboarding

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.yozedens.secureauth.R
import io.github.yozedens.secureauth.feature.common.ScreenColumn

/** First launch (design §32): explain the data-loss risk, then set a PIN. */
@Composable
fun OnboardingScreen(busy: Boolean, onPinChosen: (CharArray) -> Unit) {
    var acknowledged by remember { mutableStateOf(false) }
    var introDone by remember { mutableStateOf(false) }

    if (introDone) {
        PinSetupScreen(
            title = stringResource(R.string.pin_setup_title),
            busy = busy,
            onPinChosen = onPinChosen,
        )
        return
    }

    ScreenColumn {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.onboarding_local_only))
        Text(stringResource(R.string.onboarding_loss_risk), color = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.onboarding_recovery_codes))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
            Text(stringResource(R.string.onboarding_acknowledge))
        }
        Button(
            onClick = { introDone = true },
            enabled = acknowledged,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_continue))
        }
    }
}
