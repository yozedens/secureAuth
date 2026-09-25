package io.github.yozedens.secureauth.feature.addaccount

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.yozedens.secureauth.R
import io.github.yozedens.secureauth.feature.common.ScreenColumn
import kotlinx.coroutines.launch

/** Add account entry points (design §43): scan, pick an image, or type the key. */
@Composable
fun AddMenuScreen(
    viewModel: AddAccountViewModel,
    problem: ScanProblem?,
    onScan: () -> Unit,
    onManual: () -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // System photo picker: no storage permission; falls back to the document picker
    // on devices without it (e.g. no Google Play services).
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            if (viewModel.onImagePicked(context.contentResolver, uri)) onConfirm()
        }
    }

    ScreenColumn {
        Text(stringResource(R.string.add_title), style = MaterialTheme.typography.headlineSmall)
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_scan)) }
        OutlinedButton(
            onClick = {
                viewModel.clearScanProblem()
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.add_from_image))
        }
        OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.add_manual))
        }
        problem?.let { Text(stringResource(it.message()), color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

fun ScanProblem.message(): Int = when (this) {
    ScanProblem.INVALID -> R.string.add_invalid_qr
    ScanProblem.MIGRATION -> R.string.add_migration_unsupported
    ScanProblem.NO_CODE_IN_IMAGE -> R.string.add_no_qr_in_image
    ScanProblem.IMAGE_UNREADABLE -> R.string.add_image_unreadable
}
