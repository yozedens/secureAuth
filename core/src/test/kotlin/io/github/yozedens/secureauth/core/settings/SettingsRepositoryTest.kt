package io.github.yozedens.secureauth.core.settings

import io.github.yozedens.secureauth.core.vault.FakeVaultStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {

    private val store = FakeVaultStore()

    @Test
    fun defaultsAndPersistence() = runTest {
        val repo = SettingsRepository(store)
        assertEquals(AppSettings(biometricEnabled = false, autoLock = AutoLockTimeout.ONE_MINUTE), repo.load())
        repo.update { it.copy(biometricEnabled = true, autoLock = AutoLockTimeout.IMMEDIATELY) }
        assertEquals(true, repo.settings.value.biometricEnabled)

        val reloaded = SettingsRepository(store).load()
        assertEquals(AppSettings(true, AutoLockTimeout.IMMEDIATELY), reloaded)
    }

    @Test
    fun unreadableSettingsFallBackToDefaults() = runTest {
        store.bytes = "not json".toByteArray()
        assertEquals(AppSettings(), SettingsRepository(store).load())
        store.bytes = """{"autoLock":"NEVER"}""".toByteArray()
        assertEquals(AppSettings(), SettingsRepository(store).load())
        store.failReads = true
        assertEquals(AppSettings(), SettingsRepository(store).load())
    }

    @Test
    fun writeFailureKeepsPreviousValue() = runTest {
        val repo = SettingsRepository(store)
        repo.load()
        store.failWrites = true
        assertTrue(runCatching { repo.update { it.copy(biometricEnabled = true) } }.isFailure)
        assertEquals(false, repo.settings.value.biometricEnabled)
    }
}
