package com.openbubbles.wear.bridge

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NativeSendTimeoutTest {
    private val timeout = Exception("native timeout")

    @Test fun retriesOnceAfterASuspendedSendTimesOut() = runBlocking {
        var attempts = 0
        val accepted = retryNativeSendTimeout({
            yield()
            if (++attempts == 1) throw timeout
            true
        }, { it === timeout }, { true })

        assertTrue(accepted)
        assertEquals(2, attempts)
    }

    @Test fun aSecondTimeoutIsReturnedWithoutAnotherAttempt() = runBlocking {
        var attempts = 0
        val result = runCatching {
            retryNativeSendTimeout({
                attempts++
                throw timeout
            }, { it === timeout }, { true })
        }

        assertSame(timeout, result.exceptionOrNull())
        assertEquals(2, attempts)
    }

    @Test fun skipsARetryWhenTheMessageIsNoLongerPending() = runBlocking {
        var attempts = 0
        val result = runCatching {
            retryNativeSendTimeout({
                attempts++
                throw timeout
            }, { it === timeout }, { false })
        }

        assertSame(timeout, result.exceptionOrNull())
        assertEquals(1, attempts)
    }

    @Test fun preservesOtherErrorsAndSuccess() = runBlocking {
        val failure = IllegalStateException("some other failure")
        val result = runCatching {
            retryNativeSendTimeout({ throw failure }, { it === timeout }, { error("checked a failed send") })
        }
        assertSame(failure, result.exceptionOrNull())
        assertTrue(retryNativeSendTimeout({ true }, { error("checked a successful send") }, { false }))
    }

    @Test fun cancellationDuringTheDelayPreventsRetry() = runBlocking {
        var attempts = 0
        val send = launch {
            retryNativeSendTimeout({
                attempts++
                throw timeout
            }, { it === timeout }, { true })
            fail("The retry should have been cancelled")
        }
        yield()
        send.cancelAndJoin()
        assertEquals(1, attempts)
    }
}
