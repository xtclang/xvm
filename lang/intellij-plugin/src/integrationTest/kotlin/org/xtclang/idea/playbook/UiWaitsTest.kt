package org.xtclang.idea.playbook

import com.intellij.driver.sdk.WaitForException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

class UiWaitsTest {
    @Test
    fun `a successful readiness observation is not checked again`() {
        val checks = AtomicInteger()
        val connection = Any()
        val result =
            awaitUi(
                getter = { connection },
                checker = { checks.incrementAndGet() == 1 },
            )
        assertSame(connection, result)
        assertEquals(1, checks.get())
    }

    @Test
    fun `pending observations are refreshed until a ready value arrives`() {
        val reads = AtomicInteger()
        val ready = awaitUi(getter = reads::incrementAndGet, checker = { it == 2 })
        assertEquals(2, ready)
        assertEquals(2, reads.get())
    }

    @Test
    fun `an expired pending observation reports its value and fails`() {
        val checks = AtomicInteger()
        val failure =
            assertThrows(WaitForException::class.java) {
                awaitUi<String>(
                    message = "connection starts",
                    timeout = Duration.ZERO,
                    getter = { "starting" },
                    checker = { checks.incrementAndGet() > 1 },
                )
            }
        assertEquals(1, checks.get())
        assertTrue(failure.message.orEmpty().contains("connection starts"))
        assertTrue(failure.message.orEmpty().contains("starting"))
    }

    @Test
    fun `cancellation from a readiness read propagates without retry`() {
        val failure = CancellationException("Retired connection")
        assertSame(
            failure,
            assertThrows(CancellationException::class.java) {
                awaitUi(getter = { throw failure }, checker = { true })
            },
        )
    }

    @Test
    fun `nullable readiness returns the observed value or fails at its deadline`() {
        val value = Any()
        assertSame(value, awaitUiNotNull(timeout = Duration.ZERO) { value })
        assertThrows(WaitForException::class.java) {
            awaitUiNotNull<Any>(timeout = Duration.ZERO) { null }
        }
    }
}
