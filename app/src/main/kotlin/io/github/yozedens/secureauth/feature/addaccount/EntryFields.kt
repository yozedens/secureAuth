package io.github.yozedens.secureauth.feature.addaccount

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import io.github.yozedens.secureauth.R
import io.github.yozedens.secureauth.core.entry.EntryError
import io.github.yozedens.secureauth.core.entry.EntryOptions
import io.github.yozedens.secureauth.core.error.SecretProblem
import io.github.yozedens.secureauth.core.model.Algorithm
import io.github.yozedens.secureauth.core.model.OtpParams

/**
 * Secret input (design §17): Password keyboard so the IME does not learn it, masked by
 * default. Callers keep the value in a ViewModel, never in saved state.
 */
@Composable
fun SecretField(value: String, onValueChange: (String) -> Unit, label: String, visible: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Algorithm, digits and period/counter, shared by manual entry and editing. */
@Composable
fun AdvancedOptionsFields(options: EntryOptions, onChange: (EntryOptions) -> Unit) {
    Text(stringResource(R.string.manual_algorithm))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Algorithm.entries.forEach { algorithm ->
            FilterChip(
                selected = options.algorithm == algorithm,
                onClick = { onChange(options.copy(algorithm = algorithm)) },
                label = { Text(algorithm.name) },
            )
        }
    }
    Text(stringResource(R.string.manual_digits))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OtpParams.DIGITS_RANGE.forEach { digits ->
            FilterChip(
                selected = options.digits == digits,
                onClick = { onChange(options.copy(digits = digits)) },
                label = { Text(digits.toString()) },
            )
        }
    }
    if (options.isHotp) {
        NumberField(options.counter, { onChange(options.copy(counter = it)) }, stringResource(R.string.manual_counter))
    } else {
        NumberField(options.period, { onChange(options.copy(period = it)) }, stringResource(R.string.manual_period))
    }
}

@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it in '0'..'9' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** User-facing text for a validation error (design §56). */
@Composable
fun entryErrorText(error: EntryError): String = stringResource(
    when (error) {
        EntryError.NameRequired -> R.string.error_name_required
        EntryError.InvalidDigits -> R.string.error_digits
        EntryError.InvalidPeriod -> R.string.error_period
        EntryError.InvalidCounter -> R.string.error_counter
        is EntryError.InvalidSecret -> when (error.problem) {
            SecretProblem.EMPTY -> R.string.error_secret_empty
            SecretProblem.LOOKALIKE_DIGIT -> R.string.error_secret_lookalike
            SecretProblem.INVALID_CHARACTER, SecretProblem.MISPLACED_PADDING -> R.string.error_secret_invalid
            SecretProblem.INVALID_LENGTH -> R.string.error_secret_length
            SecretProblem.TOO_LONG -> R.string.error_secret_too_long
        }
    },
)

/** Issuer and account name inputs, shared by manual entry and edit. */
@Composable
fun NameFields(issuer: String, accountName: String, onIssuer: (String) -> Unit, onAccountName: (String) -> Unit) {
    OutlinedTextField(
        value = issuer,
        onValueChange = onIssuer,
        label = { Text(stringResource(R.string.manual_issuer)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = accountName,
        onValueChange = onAccountName,
        label = { Text(stringResource(R.string.manual_account)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
