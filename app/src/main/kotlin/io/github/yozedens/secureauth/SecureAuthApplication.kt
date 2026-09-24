package io.github.yozedens.secureauth

import android.app.Application
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
        ProcessLifecycleOwner.get().lifecycle.addObserver(AutoLockObserver(container))
    }

    /**
     * Re-locks after the app was in the background longer than the configured timeout
     * (design §29). With "immediately", it locks as soon as the app is backgrounded.
     */
    private class AutoLockObserver(private val container: AppContainer) : DefaultLifecycleObserver {

        override fun onStop(owner: LifecycleOwner) {
            container.autoLock.onBackground()
            if (container.settings.settings.value.autoLock == AutoLockTimeout.IMMEDIATELY) lock()
        }

        override fun onStart(owner: LifecycleOwner) {
            if (container.autoLock.onForeground(container.settings.settings.value.autoLock.millis)) lock()
        }

        private fun lock() {
            container.applicationScope.launch { container.vault.lock() }
        }
    }
}
