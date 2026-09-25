package io.github.yozedens.secureauth.feature.scanner

import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Source of decoded QR text. An interface so UI tests can feed fake frames (design §50.8). */
interface CodeScanner {

    fun isAvailable(context: Context): Boolean

    /** Shows the viewfinder and delivers at most one result per [attempt]. */
    @Composable
    fun Viewfinder(attempt: Int, onResult: (String) -> Unit)
}

/** CameraX back camera with in-memory ZXing analysis; frames are never stored. */
object CameraCodeScanner : CodeScanner {

    override fun isAvailable(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    @Composable
    override fun Viewfinder(attempt: Int, onResult: (String) -> Unit) {
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
}
