package io.github.yozedens.secureauth.security.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Copies OTP codes only, never secrets (design §33, decision D4).
 *
 * - Marked sensitive, so Android 13+ hides the clipboard preview.
 * - Cleared unconditionally after [CLEAR_AFTER_MILLIS]. The timer runs in the
 *   application scope, so leaving the screen does not cancel it. It is not cleared
 *   when the app goes to the background: pasting into a browser is the whole point.
 */
class SecureClipboard(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    private var clearJob: Job? = null

    fun copyCode(code: String) {
        val clip = ClipData.newPlainText(LABEL, code)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(sensitiveExtraKey(), true)
        }
        clipboard.setPrimaryClip(clip)
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(CLEAR_AFTER_MILLIS)
            clear()
        }
    }

    private fun clear() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    private fun sensitiveExtraKey(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ClipDescription.EXTRA_IS_SENSITIVE
        } else {
            LEGACY_SENSITIVE_KEY
        }

    companion object {
        const val CLEAR_AFTER_MILLIS = 30_000L
        private const val LABEL = "OTP"
        private const val LEGACY_SENSITIVE_KEY = "android.content.extra.IS_SENSITIVE"
    }
}
