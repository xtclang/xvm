package org.xvm.lsp.run

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.xvm.debug.ProgramEvents
import org.xvm.lsp.Slow
import org.xvm.lsp.awaitUntil
import org.xvm.lsp.awaited
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds

/**
 * How the server supervises its execution workers, with a stand-in worker ([FakeRunWorker]) that can fail to
 * start, report another identity, ignore Stop, crash or keep too much: such workers are ended or retired,
 * their runs say why, and the next run gets a working worker. Idle workers are retired. A worker's events come
 * over a connection of their own, which garbage on its JVM's streams cannot break, and which only it can make.
 */
@Slow
class RunWorkerSupervisionTest {
    /** One run's output and its single exit. */
    private class Run : ProgramEvents {
        val output = StringBuffer()
        val exited = CompletableFuture<Pair<Long?, String?>>()

        override fun output(text: String) {
            output.append(text)
        }

        override fun exited(
            code: Long?,
            error: String?,
        ) {
            check(exited.complete(code to error)) { "A run exits once" }
        }

        fun exit() = exited.awaited()
    }

    private fun workers(
        limits: RunWorkerProcess.Limits,
        mode: String = "",
    ) = RunWorkerProcess(
        { options ->
            listOfNotNull(
                ProcessHandle
                    .current()
                    .info()
                    .command()
                    .orElse("java"),
            ) + options +
                listOfNotNull(
                    "-cp",
                    System.getProperty("java.class.path"),
                    FakeRunWorker::class.java.name,
                    mode.ifEmpty { null },
                )
        },
        limits,
        { FakeRunWorker.IDENTITY },
    )

    private fun RunWorkerProcess.run(
        module: String,
        run: Run = Run(),
        options: List<String> = emptyList(),
    ): Pair<Long, Run> = start(RunWorkerProcess.Request(emptyList(), module, "run", emptyList(), null, jvmOptions = options), run) to run

    /** Runs [body] while recording the worker process's log, debug included, which it is given, and returns what was logged. */
    private fun logged(body: (List<ILoggingEvent>) -> Unit): List<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(RunWorkerProcess::class.java) as Logger
        val events = CopyOnWriteArrayList<ILoggingEvent>()
        val appender =
            object : AppenderBase<ILoggingEvent>() {
                override fun append(event: ILoggingEvent) {
                    events += event
                }
            }.apply { start() }
        val level = logger.level
        logger.level = Level.DEBUG
        logger.addAppender(appender)
        try {
            body(events)
        } finally {
            logger.detachAppender(appender)
            logger.level = level
            appender.stop()
        }
        return events
    }

    @Test
    fun `what a worker's JVM writes to its standard output and error, within its messages too, breaks none and goes to its run`() {
        workers(LIVELOCK_LIMITS).use { workers ->
            val (id, garbage) = workers.run("Garbage")
            val line = FakeRunWorker.GARBAGE.joinToString("")
            val message = Regex("""\d+:y+""")

            fun lines() = garbage.output.lines().filter { it.isNotEmpty() }
            // the fake writes its garbage byte by byte, while it sends its long messages: every one arrives whole, and the
            // garbage of both streams as lines of their own, between the program's
            awaitUntil {
                val all = lines()
                all.count { it == line } == 2 * FakeRunWorker.MESSAGES && all.count(message::matches) == FakeRunWorker.MESSAGES
            }
            workers.input(id, "done\n")
            // the text of an exit with 7 among the garbage is no message
            assertThat(garbage.exit()).isEqualTo(0L to null)
            assertThat(lines().filter(message::matches))
                .containsExactlyElementsOf((0 until FakeRunWorker.MESSAGES).map { FakeRunWorker.message(it).trimEnd() })
            assertThat(lines()).allMatch { it == line || message.matches(it) }
        }
    }

    @Test
    fun `what a worker's JVM writes outside a run goes to the log, standard output at debug and standard error as warnings`() {
        val events =
            logged { events ->
                workers(LIVELOCK_LIMITS).use { workers ->
                    val (id, raw) = workers.run("Raw")
                    // the run waits for input until its JVM's line has reached its output
                    awaitUntil { FakeRunWorker.RAW in raw.output }
                    workers.input(id, "done\n")
                    assertThat(raw.exit()).isEqualTo(0L to null)
                    assertThat(raw.output.toString()).contains("ok")
                }
                // closing has the idle worker exit, which writes its lines as it does, outside a run; how many there were
                // is logged once both its streams have ended
                awaitUntil { events.any { it.formattedMessage.startsWith("execution worker wrote ${FakeRunWorker.ERRORS_AFTER} lines") } }
            }
        // its standard output: the first 20 lines at debug, and how many there were
        assertThat(events.filter { it.formattedMessage.startsWith("execution worker output outside a run:") })
            .hasSize(20)
            .allMatch { it.level == Level.DEBUG }
        assertThat(events.map { it.formattedMessage })
            .anyMatch { it.startsWith("execution worker wrote ${FakeRunWorker.RAW_AFTER} lines to its standard output outside a run") }
        // its standard error, where a crash says why: the first 20 lines as warnings, and the last of the others
        val warnings = events.filter { it.level.isGreaterOrEqual(Level.WARN) }.map { it.formattedMessage }
        assertThat(warnings.filter { it.startsWith("execution worker errors outside a run:") }.map { it.substringAfter("text=") })
            .containsExactlyElementsOf((0 until 20).map(FakeRunWorker::error))
        assertThat(warnings.filterNot { it.startsWith("execution worker errors outside a run:") })
            .singleElement()
            .matches { it.startsWith("execution worker wrote ${FakeRunWorker.ERRORS_AFTER} lines to its standard error outside a run") }
            .matches { FakeRunWorker.LAST_ERROR in it && FakeRunWorker.error(20) in it && FakeRunWorker.error(19) !in it }
    }

    @Test
    fun `a worker that dies before it connects ends its run at once, with what its JVM printed`() {
        // the startup limit is one that only a hang reaches: the worker's end, not the limit, ends the wait for it
        workers(LIVELOCK_LIMITS, mode = "dies").use { workers ->
            val (_, run) = workers.run("Quick")
            val (code, error) = run.exit()
            assertThat(code).isNull()
            assertThat(error).isEqualTo("The execution worker stopped as it started; what its JVM printed, in the output above, says why")
            assertThat(run.output.toString()).contains(FakeRunWorker.DIES)
            awaitUntil { workers.workerPids().isEmpty() }
        }
    }

    @Test
    fun `a connection with another token is refused, and the worker's own then serves its run`() {
        val events =
            logged {
                workers(LIVELOCK_LIMITS, mode = "wrongToken").use { workers ->
                    val (_, quick) = workers.run("Quick")
                    assertThat(quick.exit()).isEqualTo(0L to null)
                    assertThat(quick.output.toString()).contains("ok")
                }
            }
        assertThat(events.filter { it.formattedMessage.startsWith("execution worker connection refused:") }.map { it.formattedMessage })
            .singleElement()
            .matches { it.endsWith("reason=a wrong token") }
    }

    @Test
    fun `a connection that sends nothing holds up no other, and the worker's own then serves its run`() {
        // the startup limit is one that only a hang reaches: a wait for the idle connection's token would be one
        workers(LIVELOCK_LIMITS, mode = "idleFirst").use { workers ->
            val (_, quick) = workers.run("Quick")
            assertThat(quick.exit()).isEqualTo(0L to null)
            assertThat(quick.output.toString()).contains("ok")
        }
    }

    @Test
    fun `a worker whose JVM prefers IPv6 connects to its server's IPv4 loopback port`() {
        workers(LIVELOCK_LIMITS).use { workers ->
            val (_, quick) = workers.run("Quick", options = listOf("-Djava.net.preferIPv6Addresses=true"))
            assertThat(quick.exit()).isEqualTo(0L to null)
        }
    }

    @Test
    fun `what the JVM printed in a run is that run's, and a later run on the same worker that crashes says nothing of it`() {
        workers(LIVELOCK_LIMITS).use { workers ->
            val (id, raw) = workers.run("Raw")
            awaitUntil { FakeRunWorker.RAW in raw.output }
            workers.input(id, "done\n")
            assertThat(raw.exit()).isEqualTo(0L to null)
            val worker = workers.workerPids().single()
            val (_, crash) = workers.run("Crash")
            assertThat(crash.exit()).isEqualTo(null to "The execution worker stopped unexpectedly")
            assertThat(workers.workerPids()).doesNotContain(worker)
        }
    }

    @Test
    fun `a second connection to a worker's port is refused once the worker's own is taken`() {
        workers(LIVELOCK_LIMITS).use { workers ->
            val (_, second) = workers.run("Second")
            assertThat(second.exit()).isEqualTo(0L to null)
            assertThat(second.output.toString()).contains("second connection refused")
        }
    }

    @Test
    fun `a worker that does not report ready is ended and its run says so`() {
        workers(LIVELOCK_LIMITS.copy(startup = 500.milliseconds), mode = "silent").use { workers ->
            val (_, run) = workers.run("Quick")
            val (code, error) = run.exit()
            assertThat(code).isNull()
            assertThat(error).contains("did not start within")
            awaitUntil { workers.workerPids().isEmpty() }
        }
    }

    @Test
    fun `a worker that reports another XDK or no protocol is ended before it runs anything, and its run says why`() {
        for ((mode, reported) in listOf(
            "mismatch" to "protocol ${RunWorkerIdentity.PROTOCOL} with XDK other-xdk",
            "unnumbered" to "an earlier protocol",
        )) {
            workers(LIVELOCK_LIMITS, mode = mode).use { workers ->
                val (_, run) = workers.run("Quick")
                val (code, error) = run.exit()
                assertThat(code).isNull()
                assertThat(error)
                    .contains("runs $reported", "expects protocol ${RunWorkerIdentity.PROTOCOL} with XDK fake-xdk")
                    .contains("restart the language server")
                assertThat(run.output.toString()).doesNotContain("ok")
                awaitUntil { workers.workerPids().isEmpty() }
            }
        }
    }

    @Test
    fun `a worker that keeps too much heap or too many threads after a run is retired, and the next run gets a new one`() {
        workers(LIVELOCK_LIMITS.copy(heap = 1L shl 30, threads = 1_000)).use { workers ->
            val (_, quick) = workers.run("Quick")
            assertThat(quick.exit()).isEqualTo(0L to null)
            awaitUntil { workers.usage().isNotEmpty() }
            val kept = workers.workerPids().single()
            assertThat(workers.usage().getValue(kept).copy(run = null)).isEqualTo(RunWorkerProcess.Usage(1L shl 20, 8L shl 30, 10, 8))

            for (module in listOf("Hoard", "Spawn")) {
                val (_, run) = workers.run(module)
                assertThat(run.exit()).isEqualTo(0L to null)
                // the only idle worker, yet not kept for the next run
                awaitUntil { workers.workerPids().isEmpty() }
            }
            val (_, next) = workers.run("Quick")
            assertThat(next.exit()).isEqualTo(0L to null)
            assertThat(workers.workerPids()).doesNotContain(kept)
        }
    }

    @Test
    fun `a worker that holds what an ended run left or grew its runtime pool is retired`() {
        // each bound on its own: none of these reaches the heap or thread totals
        workers(LIVELOCK_LIMITS.copy(heap = 1L shl 30, threads = 1_000)).use { workers ->
            for (module in listOf("Leak", "Unended", "Pool")) {
                val (_, quick) = workers.run("Quick")
                assertThat(quick.exit()).isEqualTo(0L to null)
                val kept = workers.workerPids().single()
                val (_, run) = workers.run(module)
                assertThat(run.exit()).describedAs(module).isEqualTo(0L to null)
                awaitUntil { kept !in workers.workerPids() }
            }
            assertThat(workers.tracked()).isZero()
        }
    }

    @Test
    fun `a program that ignores Stop has its worker ended, and the next run gets a new worker`() {
        workers(LIVELOCK_LIMITS.copy(stop = 500.milliseconds)).use { workers ->
            val (id, stubborn) = workers.run("Stubborn")
            awaitUntil { "running" in stubborn.output }
            val first = workers.workerPids().single()
            workers.stop(id)
            val (code, error) = stubborn.exit()
            assertThat(code).isNull()
            assertThat(error).contains("did not stop within")

            val (_, quick) = workers.run("Quick")
            assertThat(quick.exit()).isEqualTo(0L to null)
            assertThat(workers.workerPids()).doesNotContain(first)
        }
    }

    @Test
    fun `a worker that crashes during a run ends that run, and the next run gets a new worker`() {
        workers(LIVELOCK_LIMITS).use { workers ->
            val (_, crash) = workers.run("Crash")
            val (code, error) = crash.exit()
            assertThat(code).isNull()
            assertThat(error).contains("stopped unexpectedly")
            val (_, quick) = workers.run("Quick")
            assertThat(quick.exit()).isEqualTo(0L to null)
        }
    }

    @Test
    fun `programs run side by side up to the limit, and one more is refused with the reason`() {
        workers(LIVELOCK_LIMITS.copy(running = 2)).use { workers ->
            val (first, one) = workers.run("Polite")
            val (second, two) = workers.run("Polite")
            awaitUntil { "running" in one.output && "running" in two.output }
            assertThat(workers.workerPids()).hasSize(2)

            val (_, third) = workers.run("Quick")
            val (code, error) = third.exit()
            assertThat(code).isNull()
            assertThat(error).contains("2 programs are already running")

            workers.stop(first)
            workers.stop(second)
            assertThat(one.exit().second).isEqualTo("stopped")
            assertThat(two.exit().second).isEqualTo("stopped")
            // one idle worker stays for the next run; the other exits
            awaitUntil { workers.workerPids().size == 1 }
            val kept = workers.workerPids().single()
            val (_, quick) = workers.run("Quick")
            assertThat(quick.exit()).isEqualTo(0L to null)
            assertThat(workers.workerPids()).containsExactly(kept)
        }
    }

    @Test
    fun `a run's worker has its JVM options, runs with the same share it, and one with others retires it when idle`() {
        workers(LIVELOCK_LIMITS).use { workers ->
            val gradle = listOf("-D${FakeRunWorker.OPTION}=gradle")
            val (_, first) = workers.run("Options", options = gradle)
            assertThat(first.exit()).isEqualTo(0L to null)
            assertThat(first.output.toString()).contains("option=gradle")
            val kept = workers.workerPids().single()
            val (_, again) = workers.run("Options", options = gradle)
            assertThat(again.exit()).isEqualTo(0L to null)
            assertThat(again.output.toString()).contains("option=gradle")
            assertThat(workers.workerPids()).containsExactly(kept)

            // a run with the worker's own options, none, gets a new worker, and the idle one with others goes
            val (_, plain) = workers.run("Options")
            assertThat(plain.exit()).isEqualTo(0L to null)
            assertThat(plain.output.toString()).contains("option=null")
            awaitUntil { workers.workerPids().size == 1 }
            assertThat(workers.workerPids()).doesNotContain(kept)
        }
    }

    @Test
    fun `a worker that stops as it starts, with JVM options its JVM refuses, ends its run, which names them`() {
        workers(LIVELOCK_LIMITS).use { workers ->
            val (_, refused) = workers.run("Quick", options = listOf("-XX:+NoSuchOptionOfTheWorkersJvm"))
            val (code, error) = refused.exit()
            assertThat(code).isNull()
            assertThat(error).isEqualTo(
                "The execution worker stopped as it started, with the JVM options [-XX:+NoSuchOptionOfTheWorkersJvm]; " +
                    "what its JVM printed, in the output above, says why",
            )
            // the JVM's own message, which says which option it refuses
            assertThat(refused.output.toString()).contains("NoSuchOptionOfTheWorkersJvm")
            awaitUntil { workers.workerPids().isEmpty() }
            val (_, next) = workers.run("Quick")
            assertThat(next.exit()).isEqualTo(0L to null)
        }
    }

    @Test
    fun `an idle worker exits after the idle limit, and the next run starts a new one`() {
        workers(LIVELOCK_LIMITS.copy(idle = 300.milliseconds)).use { workers ->
            val (_, first) = workers.run("Quick")
            assertThat(first.exit()).isEqualTo(0L to null)
            awaitUntil { workers.workerPids().isEmpty() }
            val (_, second) = workers.run("Quick")
            assertThat(second.exit()).isEqualTo(0L to null)
            assertThat(second.output.toString()).contains("ok")
        }
    }

    @Test
    fun `closing ends every run once, with the reason`() {
        val workers = workers(LIVELOCK_LIMITS)
        val (_, one) = workers.run("Polite")
        awaitUntil { "running" in one.output }
        workers.close()
        assertThat(one.exit()).isEqualTo(null to "The execution worker was shut down")
        // a worker that is slow to exit is ended, which may take a moment more
        awaitUntil { workers.workerPids().isEmpty() }
    }
}
