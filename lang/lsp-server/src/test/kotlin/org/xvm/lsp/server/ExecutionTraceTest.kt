package org.xvm.lsp.server

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.xvm.lsp.adapter.xdk.CompilerQueueTrace
import org.xvm.lsp.util.ExecutionTrace
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

class ExecutionTraceTest {
    @Test
    fun `queue lists preserve order and cancellation does not pretend running work stopped`() {
        Recording().use { trace ->
            val queue = CompilerQueueTrace()
            val running = CompletableFuture<Unit>()
            val next = CompletableFuture<Unit>()
            val retired = CompletableFuture<Unit>()
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val first =
                queue.task("compile", "file:///Alpha.x", running) {
                    entered.countDown()
                    check(release.await(10, SECONDS))
                }
            val second =
                queue.task("cursor-COMPLETION", "file:///Beta.x", next) { next.complete(Unit) }
            queue
                .task("compile", "file:///Gamma.x", retired) { error("Retired job executed") }
                .also { it.ready() }
            second.ready()
            Executors.newSingleThreadExecutor().use { executor ->
                val execution = executor.submit(first.ready())
                try {
                    check(entered.await(10, SECONDS))
                    running.cancel(false)
                    val snapshot = trace.entries.last()
                    assertThat(snapshot["queueSize"].asInt).isEqualTo(2)
                    val status = queue.snapshot()
                    assertThat(status["queueSize"]).isEqualTo(2)
                    assertThat(status["runningSize"]).isEqualTo(1)
                    assertThat(queue.progressDescription()).isEqualTo("compiling Alpha.x; 2 jobs queued")
                    assertThat(status["queuedJobs"].toString()).contains("Gamma.x", "Beta.x")
                    assertThat(snapshot["queuedJobs"].asJsonArray.map { it.asString })
                        .satisfiesExactly(
                            { assertThat(it).contains("compile", "Gamma.x") },
                            { assertThat(it).contains("cursor-COMPLETION", "Beta.x") },
                        )
                    assertThat(snapshot["runningSize"].asInt).isEqualTo(1)
                    assertThat(snapshot["runningJobs"].toString()).contains("Alpha.x")
                    retired.cancel(false)
                    assertThat(trace.entries.last()["queueSize"].asInt).isEqualTo(1)
                } finally {
                    release.countDown()
                }
                execution.get(10, SECONDS)
            }
            second.run()
            assertThat(queue.progressDescription()).isNull()
            val final = trace.entries.last()
            assertThat(final["queueSize"].asInt).isZero()
            assertThat(final["runningSize"].asInt).isZero()
            assertThat(final["debouncingSize"].asInt).isZero()
            assertThat(final["startedTotal"].asInt).isEqualTo(2)
            assertThat(
                trace.entries
                    .filter { it["event"].asString == "end" }
                    .first()["outcome"]
                    .asString,
            ).isEqualTo("cancelled")
        }
    }

    @Test
    fun `API trace exposes overlap without counting nesting as parallel threads`() {
        Recording().use { trace ->
            val entered = CountDownLatch(2)
            val release = CountDownLatch(1)
            Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                val work =
                    (1..2).map { index ->
                        executor.submit {
                            ExecutionTrace.api("overlap-$index") {
                                entered.countDown()
                                check(release.await(10, SECONDS))
                                ExecutionTrace.api("nested-$index") { Unit }
                            }
                        }
                    }
                try {
                    check(entered.await(10, SECONDS))
                    assertThat(
                        trace.entries
                            .filter { it["event"].asString == "start" }
                            .map { it["activeApiThreads"].asInt },
                    ).contains(2)
                } finally {
                    release.countDown()
                }
                work.forEach { it.get(10, SECONDS) }
            }
            assertThat(
                trace.entries.filter {
                    it["operation"].asString.startsWith("nested") &&
                        it["event"].asString == "start"
                },
            ).allSatisfy { assertThat(it["depth"].asInt).isEqualTo(1) }
            assertThat(trace.entries).allSatisfy {
                assertThat(it["thread"].asString).isEqualTo("virtual-${it["threadId"].asLong}")
            }
            assertThatThrownBy {
                ExecutionTrace.api("exceptional") { error("source text must not be logged") }
            }.isInstanceOf(IllegalStateException::class.java)
            ExecutionTrace.api("after-failure") { Unit }
            assertThat(trace.entries.toString()).doesNotContain("source text must not be logged")
            assertThat(
                trace.entries
                    .last {
                        it["operation"].asString == "after-failure" &&
                            it["event"].asString == "start"
                    }["activeApiThreads"]
                    .asInt,
            ).isEqualTo(1)
        }
    }

    @Test
    fun `protocol trace ends at the reply and separates bidirectional IDs`() {
        Recording().use { trace ->
            ProtocolTrace().use { protocol ->
                val received = protocol.wrap(MessageConsumer {}, received = true)
                val sent = protocol.wrap(MessageConsumer {}, received = false)
                received.consume(
                    RequestMessage().apply {
                        setId(7)
                        method = "textDocument/hover"
                    },
                )
                sent.consume(
                    RequestMessage().apply {
                        setId(7)
                        method = "workspace/configuration"
                    },
                )
                assertThat(trace.entries.map { it["event"].asString })
                    .containsExactly("start", "start")
                sent.consume(
                    ResponseMessage().apply {
                        setId(7)
                        result = "hover"
                    },
                )
                received.consume(
                    ResponseMessage().apply {
                        setId(7)
                        result = emptyList<String>()
                    },
                )
                val replies = trace.entries.filter { it["event"].asString == "end" }
                assertThat(replies.map { it["operation"].asString })
                    .containsExactly("textDocument/hover", "workspace/configuration")
                assertThat(replies.map { it["boundary"].asString })
                    .containsExactly("server-reply-written", "client-reply-received")
                assertThat(replies).allSatisfy {
                    assertThat(it["elapsedMs"].asDouble).isGreaterThanOrEqualTo(0.0)
                }
                assertThat(replies.first()["writeMs"].asDouble).isGreaterThanOrEqualTo(0.0)
                assertThat(replies.last().has("writeMs")).isFalse()
                assertThat(trace.entries.filter { it["event"].asString == "reply-ready" }.map { it["operation"].asString })
                    .containsExactly("textDocument/hover")
                received.consume(
                    RequestMessage().apply {
                        id = "pending"
                        method = "textDocument/completion"
                    },
                )
            }
            assertThat(trace.entries.last()["outcome"].asString).isEqualTo("transport-closed")
        }
    }

    private class Recording : AutoCloseable {
        val entries = CopyOnWriteArrayList<JsonObject>()
        private val logger = LoggerFactory.getLogger("org.xvm.lsp.trace") as Logger
        private val appender =
            object : AppenderBase<ILoggingEvent>() {
                override fun append(event: ILoggingEvent) {
                    entries += JsonParser.parseString(event.formattedMessage).asJsonObject
                }
            }.apply { start() }

        init {
            logger.addAppender(appender)
        }

        override fun close() {
            logger.detachAppender(appender)
            appender.stop()
        }
    }
}
