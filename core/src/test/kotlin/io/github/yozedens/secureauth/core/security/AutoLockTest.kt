package io.github.yozedens.secureauth.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLockTest {

    private var now = 0L
    private val autoLock = AutoLock { now }

    @Test
    fun locksOnlyAfterTimeout() {
        assertFalse(autoLock.onForeground(60_000))
        autoLock.onBackground()
        now += 59_999
        assertFalse(autoLock.onForeground(60_000))
        autoLock.onBackground()
        now += 60_000
        assertTrue(autoLock.onForeground(60_000))
        assertFalse("background mark is consumed", autoLock.onForeground(60_000))
    }

    @Test
    fun immediatelyLocksOnAnyBackgrounding() {
        autoLock.onBackground()
        assertTrue(autoLock.onForeground(0))
    }
}
