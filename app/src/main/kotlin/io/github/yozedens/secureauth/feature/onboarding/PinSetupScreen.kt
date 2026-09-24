package io.github.yozedens.secureauth.feature.onboarding

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
import io.github.yozedens.secureauth.core.security.PinPolicy
import io.github.yozedens.secureauth.feature.common.PinField
import io.github.yozedens.secureauth.feature.common.ScreenColumn

/**
 * New PIN entered twice (design §30.4). With [requireCurrent] (change PIN) the current
 * PIN is asked first. PIN text lives only in `remember` state.
 */
@Composable
fun PinSetupScreen(
    title: String,
    busy: Boolean,
    onPinChosen: (CharArray) -> Unit,
    requireCurrent: Boolean = false,
    onChangePin: (current: CharArray, new: CharArray) -> Unit = { _, _ -> },
    onCancel: (() -> Unit)? = null,
    externalError: String? = null,
) {
    var current by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidFormat = stringResource(R.string.pin_invalid_format)
    val mismatch = stringResource(R.string.pin_mismatch)

    val submit: () -> Unit = {
        when {
            !PinPolicy.isValid(first.toCharArray()) -> error = invalidFormat
            first != second -> {
                error = mismatch
                first = ""
                second = ""
            }
            requireCurrent -> onChangePin(current.toCharArray(), first.toCharArray())
            else -> onPinChosen(first.toCharArray())
        }
    }

    ScreenColumn {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (requireCurrent) {
            PinField(current, { current = it }, stringResource(R.string.pin_current_hint), enabled = !busy)
        }
        PinField(
            value = first,
            onValueChange = { first = it; error = null },
            label = stringResource(R.string.pin_setup_hint),
            enabled = !busy,
        )
        PinField(
            value = second,
            onValueChange = { second = it; error = null },
            label = stringResource(R.string.pin_confirm_hint),
            enabled = !busy,
            onDone = submit,
        )
        (error ?: externalError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = submit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (busy) R.string.pin_saving else R.string.action_confirm))
        }
        onCancel?.let {
            TextButton(onClick = it, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}
