package com.openbubbles.wear.bridge

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

internal fun interface NativeSendAttempt {
    @Throws(Exception::class)
    suspend fun send(): Boolean
}

internal suspend fun retryNativeSendTimeout(
    attempt: NativeSendAttempt,
    isTimeout: (Exception) -> Boolean,
    stillPending: () -> Boolean,
): Boolean {
    try {
        return attempt.send()
    } catch (error: Exception) {
        if (!isTimeout(error)) throw error
        delay(3_000)
        if (!stillPending()) throw error
        currentCoroutineContext().ensureActive()
        return attempt.send()
    }
}
