package io.github.yozedens.secureauth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.github.yozedens.secureauth.AppContainer
import io.github.yozedens.secureauth.SecureAuthApplication
import io.github.yozedens.secureauth.feature.scanner.CodeScanner
import io.github.yozedens.secureauth.feature.scanner.QrDecoder
import io.github.yozedens.secureauth.security.biometric.BiometricAuthenticator
import io.github.yozedens.secureauth.security.keystore.KeystoreAeadCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.rules.ExternalResource
import java.io.File
import java.security.KeyStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

/** RFC 6238 appendix B, T = 59 s: time step 1, so a 6-digit SHA-1 code equals HOTP(1). */
val FIXED_CLOCK: Clock = Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC)

/** Biometric prompt that always succeeds and counts how often it was shown. */
class FakeBiometric : BiometricAuthenticator {
    val prompts = AtomicInteger()

    override fun availability() = BiometricAuthenticator.Availability.AVAILABLE

    override fun authenticate(
        activity: FragmentActivity,
        title: String,
        negativeText: String,
        onResult: (BiometricAuthenticator.Result) -> Unit,
    ) {
        prompts.incrementAndGet()
        onResult(BiometricAuthenticator.Result.SUCCESS)
    }
}

/**
 * Fake camera (design §50.8): renders [nextCode] into a synthetic Y-plane frame and runs it
 * through the same decoder as real camera frames.
 */
class FakeFrameScanner : CodeScanner {
    @Volatile
    var nextCode: String? = null

    override fun isAvailable(context: android.content.Context) = true

    @Composable
    override fun Viewfinder(attempt: Int, onResult: (String) -> Unit) {
        LaunchedEffect(attempt) {
            val code = nextCode ?: return@LaunchedEffect
            val decoded = withContext(Dispatchers.Default) {
                QrDecoder.decodeLuminance(qrFrame(code), FRAME_SIZE, FRAME_SIZE, FRAME_SIZE)
            }
            decoded?.let(onResult)
        }
    }

    private fun qrFrame(text: String): ByteArray {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, FRAME_SIZE, FRAME_SIZE)
        return ByteArray(FRAME_SIZE * FRAME_SIZE) { i -> if (matrix[i % FRAME_SIZE, i / FRAME_SIZE]) BLACK else WHITE }
    }

    private companion object {
        const val FRAME_SIZE = 480
        const val BLACK: Byte = 0
        const val WHITE: Byte = -1
    }
}

/**
 * Starts every test from a fresh install: no data files, no Keystore key, and a container
 * with the fake clock, biometrics and camera. Must wrap the activity launch.
 */
class FreshAppRule : ExternalResource() {
    val scanner = FakeFrameScanner()
    val biometric = FakeBiometric()
    lateinit var container: AppContainer
        private set

    private val app: SecureAuthApplication get() = ApplicationProvider.getApplicationContext()

    override fun before() {
        wipe()
        container = AppContainer(app, clock = FIXED_CLOCK, biometricFactory = { biometric }, scanner = scanner)
        app.replaceContainer(container)
    }

    override fun after() {
        // Closes the DataStores so the next test can open the same files.
        runBlocking { container.applicationScope.coroutineContext.job.cancelAndJoin() }
        wipe()
    }

    private fun wipe() {
        File(app.filesDir, "datastore").deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .deleteEntry(KeystoreAeadCipher.MASTER_KEY_ALIAS)
    }
}
