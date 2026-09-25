package io.github.yozedens.secureauth.core.list

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TickerTest {

    @Test
    fun emitsImmediatelyThenOnSecondBoundaries() = runTest {
        val start = 10_250L
        val ticker = Ticker { start + testScheduler.currentTime }
        val ticks = ticker.ticks().take(4).toList()
        assertEquals(listOf(10_250L, 11_000L, 12_000L, 13_000L), ticks)
    }

    @Test
    fun doesNotDriftWhenCollectorIsSlow() = runTest {
        val ticker = Ticker { testScheduler.currentTime }
        val ticks = mutableListOf<Long>()
        ticker.ticks().take(3).collect {
            ticks += it
            kotlinx.coroutines.delay(300)
        }
        assertEquals(listOf(0L, 1_000L, 2_000L), ticks)
    }
}
