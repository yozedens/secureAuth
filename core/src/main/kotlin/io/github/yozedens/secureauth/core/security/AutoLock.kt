package io.github.yozedens.secureauth.core.security

/**
 * Decides when to re-lock after the app was in the background (design §29, ADR 0001 §6).
 *
 * [elapsedRealtime] must be a monotonic clock (Android `SystemClock.elapsedRealtime()`),
 * so changing the system time cannot extend an unlocked session.
 */
class AutoLock(private val elapsedRealtime: () -> Long) {

    private var backgroundedAt: Long? = null

    fun onBackground() {
        backgroundedAt = elapsedRealtime()
    }

    /** True if the app has been in the background for at least [timeoutMillis]. */
    fun onForeground(timeoutMillis: Long): Boolean {
        val since = backgroundedAt ?: return false
        backgroundedAt = null
        return elapsedRealtime() - since >= timeoutMillis
    }
}
