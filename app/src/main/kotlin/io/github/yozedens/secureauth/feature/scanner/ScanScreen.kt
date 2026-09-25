package io.github.yozedens.secureauth.feature.scanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.yozedens.secureauth.R
import io.github.yozedens.secureauth.feature.common.ScreenColumn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Camera QR scanner (design §16, §39). Camera permission is requested only here.
 * Frames are analyzed in memory and never stored; analysis stops at the first result.
 */
@Composable
fun ScanScreen(problemText: String?, onResult: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
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
                CameraPreview(attempt, onDecoded)
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

@Composable
private fun CameraPreview(attempt: Int, onResult: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    DisposableEffect(lifecycleOwner, attempt) {
        val executor = Executors.newSingleThreadExecutor()
        val delivered = AtomicBoolean(false)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { image ->
                image.use {
                    if (delivered.get()) return@use
                    val text = QrDecoder.decode(it) ?: return@use
                    if (delivered.compareAndSet(false, true)) {
                        ContextCompat.getMainExecutor(context).execute { onResult(text) }
                    }
                }
            }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            if (providerFuture.isDone) providerFuture.get().unbindAll()
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
}
