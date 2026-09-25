package io.github.yozedens.secureauth.feature.scanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import io.github.yozedens.secureauth.R
import io.github.yozedens.secureauth.feature.common.ScreenColumn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Camera QR scanner (design §16, §39). Camera permission is requested only here.
 * Frames are analyzed in memory and never stored; analysis stops at the first result.
 */
@Composable
fun ScanScreen(scanner: CodeScanner, problemText: String?, onResult: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val hasCamera = remember { scanner.isAvailable(context) }
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var denied by remember { mutableStateOf(false) }
    // Restarts analysis a moment after a result, so a rejected QR code does not end scanning.
    var attempt by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val onDecoded: (String) -> Unit = { text ->
        onResult(text)
        scope.launch {
            delay(RESUME_DELAY_MILLIS)
            attempt++
        }
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }

    ScreenColumn {
        Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.headlineSmall)
        when {
            !hasCamera -> Text(stringResource(R.string.scan_no_camera))
            granted -> {
                Text(stringResource(R.string.scan_hint))
                scanner.Viewfinder(attempt, onDecoded)
            }
            denied -> {
                Text(stringResource(R.string.scan_permission_denied))
                Button(onClick = { openAppSettings(context) }) { Text(stringResource(R.string.scan_open_settings)) }
            }
            else -> {
                Text(stringResource(R.string.scan_permission_rationale))
                Button(onClick = { request.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.scan_permission_grant))
                }
            }
        }
        problemText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

private const val RESUME_DELAY_MILLIS = 1_500L

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
}
