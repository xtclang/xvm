package org.xvm.lsp.run

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import java.io.PrintWriter
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/** How the execution worker batches a run's output and bounds what waits ([OutputBatches]). */
class OutputBatchesTest {
    @Test
    fun `prints within the delay go out as one event, and closing sends what waits`() {
        val sent = CopyOnWriteArrayList<String>()
        PrintWriter(OutputBatches(sent::add, limit = 1_000, delay = 1.hours), true).use { console ->
            repeat(100) { console.println("line $it") }
            assertThat(sent).isEmpty()
        }
        assertThat(sent).containsExactly((0 until 100).joinToString("") { "line $it\n" })
    }

    @Test
    fun `a full batch goes out at once, and no more than the limit waits`() {
        val sent = CopyOnWriteArrayList<String>()
        val output = OutputBatches(sent::add, limit = 100, delay = 1.hours)
        val text = "x".repeat(1_050)
        output.write(text)
        assertThat(sent).hasSize(10).allMatch { it.length == 100 }
        output.close()
        assertThat(sent.joinToString("")).isEqualTo(text)
    }

    @Test
    fun `sending now sends what waits without waiting for the delay`() {
        val sent = CopyOnWriteArrayList<String>()
        val output = OutputBatches(sent::add, delay = 1.hours)
        output.write("before")
        output.sendNow()
        assertThat(sent).containsExactly("before")
        output.sendNow()
        assertThat(sent).containsExactly("before")
    }

    @Test
    fun `text goes out after the delay when nothing follows`() {
        val sent = LinkedBlockingQueue<String>()
        val output = OutputBatches(sent::add, delay = 50.milliseconds)
        output.write("hello")
        assertThat(sent.poll(10, SECONDS)).isEqualTo("hello")
        output.write(" again")
        assertThat(sent.poll(10, SECONDS)).isEqualTo(" again")
    }

    @Test
    fun `the delayed send runs in a thread group of its own, not that of the runtime thread that prints`() {
        // the server counts the threads in the runtime pool's group after each run, and retires a worker whose
        // count grew; the timer thread starts on the first print, from one of those threads
        val groups = LinkedBlockingQueue<String>()
        val output = OutputBatches({ groups.add(Thread.currentThread().threadGroup.name) }, delay = 10.milliseconds)
        Thread(ThreadGroup("XVM"), { output.write("printed") }).apply {
            start()
            join()
        }
        assertThat(groups.poll(10, SECONDS)).isEqualTo("xtc-run-output")
    }

    @Test
    fun `a surrogate pair is never split between events`() {
        val sent = CopyOnWriteArrayList<String>()
        OutputBatches(sent::add, limit = 4, delay = 1.hours).use { it.write("abc😀def") }
        assertThat(sent.joinToString("")).isEqualTo("abc😀def")
        assertThat(sent).hasSizeGreaterThan(1).noneMatch { it.last().isHighSurrogate() || it.first().isLowSurrogate() }
    }

    @Test
    fun `a program printing faster than the server reads waits in its print, with no more than the limit waiting`() {
        val reading = CountDownLatch(1)
        val sent = CopyOnWriteArrayList<String>()
        val output =
            OutputBatches(
                {
                    reading.await()
                    sent += it
                },
                limit = 100,
                delay = 1.hours,
            )
        val printed = AtomicInteger()
        val program =
            Thread.ofPlatform().start {
                repeat(1_000) {
                    output.write("0123456789")
                    printed.incrementAndGet()
                }
            }
        // the first full batch waits for the server, and the program waits with it
        await().atMost(Duration.ofSeconds(10)).until { program.state == Thread.State.WAITING }
        assertThat(printed.get()).isLessThan(10)
        reading.countDown()
        program.join(Duration.ofSeconds(10))
        output.close()
        assertThat(sent.joinToString("")).isEqualTo("0123456789".repeat(1_000))
        assertThat(sent).allMatch { it.length <= 100 }
    }
}
