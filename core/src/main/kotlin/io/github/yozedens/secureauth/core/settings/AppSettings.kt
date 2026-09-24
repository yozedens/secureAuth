package io.github.yozedens.secureauth.core.settings

import io.github.yozedens.secureauth.core.vault.VaultStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

/** Non-sensitive settings, stored in plaintext `settings.pb` (design §23.1). */
@Serializable
data class AppSettings(
    val biometricEnabled: Boolean = false,
    val autoLock: AutoLockTimeout = AutoLockTimeout.ONE_MINUTE,
)

/** Auto-lock choices (ADR 0001 §6); default one minute. */
enum class AutoLockTimeout(val millis: Long) {
    IMMEDIATELY(0),
    THIRTY_SECONDS(30_000),
    ONE_MINUTE(60_000),
    FIVE_MINUTES(300_000),
}

class SettingsRepository(private val store: VaultStore) {

    private val mutex = Mutex()
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /** Loads stored settings; unreadable settings fall back to defaults (they are not sensitive). */
    suspend fun load(): AppSettings = mutex.withLock {
        val loaded = try {
            store.read()?.let { json.decodeFromString<AppSettings>(it.toString(Charsets.UTF_8)) } ?: AppSettings()
        } catch (ignored: IOException) {
            AppSettings()
        } catch (ignored: SerializationException) {
            AppSettings()
        } catch (ignored: IllegalArgumentException) {
            AppSettings()
        }
        loaded.also { _settings.value = it }
    }

    /** @throws IOException if the settings cannot be written. */
    suspend fun update(transform: (AppSettings) -> AppSettings) = mutex.withLock {
        val next = transform(_settings.value)
        store.update { json.encodeToString<AppSettings>(next).toByteArray(Charsets.UTF_8) }
        _settings.value = next
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
