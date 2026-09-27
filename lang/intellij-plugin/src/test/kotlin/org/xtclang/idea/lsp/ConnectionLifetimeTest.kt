package org.xtclang.idea.lsp

import com.redhat.devtools.lsp4ij.server.CannotStartProcessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger

class ConnectionLifetimeTest {
    @Test
    fun `stopping before asynchronous startup cannot spawn an orphan`() {
        val started = AtomicInteger()
        val stopped = AtomicInteger()
        val lifetime = ConnectionLifetime({ started.incrementAndGet() }, { stopped.incrementAndGet() })
        lifetime.stop()
        assertThatThrownBy { lifetime.start() }.isInstanceOf(CannotStartProcessException::class.java)
        lifetime.stop()
        assertThat(started.get()).isZero()
        assertThat(stopped.get()).isEqualTo(1)
    }

    @Test
    fun `concurrent starts retain a single process and stop is final`() {
        val started = AtomicInteger()
        val stopped = AtomicInteger()
        val lifetime = ConnectionLifetime({ started.incrementAndGet() }, { stopped.incrementAndGet() })
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            (1..20).map { executor.submit { lifetime.start() } }.forEach { it.get(10, SECONDS) }
        }
        lifetime.stop()
        lifetime.stop()
        assertThatThrownBy { lifetime.start() }.isInstanceOf(CannotStartProcessException::class.java)
        assertThat(started.get()).isEqualTo(1)
        assertThat(stopped.get()).isEqualTo(1)
    }

    @Test
    fun `stop waits for in flight process creation before releasing it`() {
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val stopping = CountDownLatch(1)
        val children = AtomicInteger()
        val lifetime =
            ConnectionLifetime(
                {
                    entered.countDown()
                    check(finish.await(10, SECONDS))
                    children.incrementAndGet()
                },
                { children.decrementAndGet() },
            )
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val start = executor.submit { lifetime.start() }
            check(entered.await(10, SECONDS))
            val stop =
                executor.submit {
                    stopping.countDown()
                    lifetime.stop()
                }
            try {
                check(stopping.await(10, SECONDS))
                assertThat(children.get()).isZero()
            } finally {
                finish.countDown()
            }
            start.get(10, SECONDS)
            stop.get(10, SECONDS)
        }
        assertThat(children.get()).isZero()
    }

    @Test
    fun `failed startup releases a partially created process`() {
        val children = AtomicInteger()
        val lifetime =
            ConnectionLifetime(
                {
                    children.incrementAndGet()
                    error("listener setup failed")
                },
                { children.decrementAndGet() },
            )
        assertThatThrownBy { lifetime.start() }.isInstanceOf(IllegalStateException::class.java).hasMessage("listener setup failed")
        lifetime.stop()
        assertThat(children.get()).isZero()
        assertThatThrownBy { lifetime.start() }.isInstanceOf(CannotStartProcessException::class.java)
    }
}
