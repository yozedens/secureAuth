package io.github.yozedens.secureauth

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.yozedens.secureauth.core.settings.AutoLockTimeout
import kotlinx.coroutines.launch

class SecureAuthApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(AutoLockObserver { container })
    }

    /** UI tests install a container with fakes before launching the activity (design §50.8). */
    @VisibleForTesting
    fun replaceContainer(replacement: AppContainer) {
        container = replacement
    }

    /**
     * Re-locks after the app was in the background longer than the configured timeout
     * (design §29). With "immediately", it locks as soon as the app is backgrounded.
     */
    private class AutoLockObserver(private val current: () -> AppContainer) : DefaultLifecycleObserver {

        override fun onStop(owner: LifecycleOwner) {
            val container = current()
            container.autoLock.onBackground()
            if (container.settings.settings.value.autoLock == AutoLockTimeout.IMMEDIATELY) lock(container)
        }

        override fun onStart(owner: LifecycleOwner) {
            val container = current()
            if (container.autoLock.onForeground(container.settings.settings.value.autoLock.millis)) lock(container)
        }

        private fun lock(container: AppContainer) {
            container.applicationScope.launch { container.vault.lock() }
        }
    }
}
