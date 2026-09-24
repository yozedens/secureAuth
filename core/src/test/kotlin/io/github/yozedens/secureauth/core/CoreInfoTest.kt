package io.github.yozedens.secureauth.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CoreInfoTest {
    @Test
    fun moduleIsPureJvm() {
        assertEquals("core", CoreInfo.MODULE)
        // Android classes must not be resolvable from :core.
        val androidPresent = runCatching { Class.forName("android.os.Build") }.isSuccess
        assertEquals(false, androidPresent)
    }
}
