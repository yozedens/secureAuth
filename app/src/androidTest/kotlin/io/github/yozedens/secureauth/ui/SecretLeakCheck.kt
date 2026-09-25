package io.github.yozedens.secureauth.ui

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import java.io.File
import java.nio.file.Files

/** Automated parts of the security checklist (design §60, plan §5.2). */
object SecretLeakCheck {

    private const val MAX_FILE_BYTES = 8L * 1024 * 1024

    /** No file in the app's data directory contains any needle as UTF-8 or UTF-16. */
    fun assertNoPlaintextOnDisk(vararg needles: String) {
        val context: Context = ApplicationProvider.getApplicationContext()
        val files = context.dataDir.walkTopDown()
            .onEnter { !Files.isSymbolicLink(it.toPath()) }
            .filter { it.isFile && !Files.isSymbolicLink(it.toPath()) && it.length() <= MAX_FILE_BYTES }
            .toList()
        assertTrue("expected the vault file on disk", files.any { it.name == "vault.pb" })
        val leaks = files.flatMap { file -> needles.filter { contains(file, it) }.map { file.path } }
        assertTrue("plaintext found in: ${leaks.distinct()}", leaks.isEmpty())
    }

    /** The whole device log since boot contains none of the needles. */
    fun assertNotInLogcat(vararg needles: String) {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("logcat -d")
        val log = ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
        assertTrue("logcat was empty; cannot check it", log.isNotBlank())
        val found = needles.count { log.contains(it) }
        // Do not echo the matches: even a test key should not be printed.
        assertTrue("$found test secret variant(s) found in logcat", found == 0)
    }

    private fun contains(file: File, needle: String): Boolean {
        val haystack = String(file.readBytes(), Charsets.ISO_8859_1)
        return listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE).any { charset ->
            haystack.contains(String(needle.toByteArray(charset), Charsets.ISO_8859_1))
        }
    }
}
