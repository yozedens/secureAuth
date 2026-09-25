package io.github.yozedens.secureauth.core.list

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * One global time source for all TOTP countdowns (design §19). Emits the current
 * epoch millis, then again right after each second boundary, so it does not drift.
 * Cold flow: collect it with `WhileSubscribed` so it stops while the UI is hidden.
 */
class Ticker(private val nowMillis: () -> Long) {

    fun ticks(): Flow<Long> = flow {
        while (true) {
            emit(nowMillis())
            // Re-read the clock after the collector ran, so a slow collector cannot shift the phase.
            delay(MILLIS_PER_SECOND - Math.floorMod(nowMillis(), MILLIS_PER_SECOND))
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
