package io.github.yozedens.secureauth.core

import org.junit.Assert.assertFalse
import org.junit.Test

class ModuleBoundaryTest {
    @Test
    fun androidIsNotOnTheClasspath() {
        val androidPresent = runCatching { Class.forName("android.os.Build") }.isSuccess
        assertFalse("core must stay a pure JVM module (design §49)", androidPresent)
    }
}
