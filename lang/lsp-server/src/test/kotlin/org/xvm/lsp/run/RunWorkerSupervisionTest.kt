package org.xvm.lsp.run

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * How the server supervises its execution workers, with a stand-in worker ([FakeRunWorker]) that can fail to
 * start, ignore Stop or crash: deadlines end such workers, their runs say why, and the next run gets a
 * working worker. Idle workers are retired.
 */
class RunWorkerSupervisionTest {
    /** One run's output and its single exit. */
    private class Run : RunWorkerProcess.Listener {
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

        fun exit() = exited.get(30, SECONDS)
    }

    private fun workers(
        limits: RunWorkerProcess.Limits,
        mode: String = "",
    ) = RunWorkerProcess(
        {
            listOfNotNull(
                ProcessHandle
                    .current()
                    .info()
                    .command()
                    .orElse("java"),
                "-cp",
                System.getProperty("java.class.path"),
                FakeRunWorker::class.java.name,
                mode.ifEmpty { null },
            )
        },
        limits,
    )

    private fun RunWorkerProcess.run(
        module: String,
        run: Run = Run(),
    ): Pair<Long, Run> = start(RunWorkerProcess.Request(emptyList(), module, "run", emptyList(), null), run) to run

    @Test
    fun `a worker that does not report ready is ended and its run says so`() {
        workers(RunWorkerProcess.Limits(startup = 500.milliseconds), mode = "silent").use { workers ->
            val (_, run) = workers.run("Quick")
            val (code, error) = run.exit()
            assertThat(code).isNull()
            assertThat(error).contains("did not start within")
            await().atMost(Duration.ofSeconds(10)).until { workers.workerPids().isEmpty() }
        }
    }

    @Test
    fun `a program that ignores Stop has its worker ended, and the next run gets a new worker`() {
        workers(RunWorkerProcess.Limits(stop = 500.milliseconds)).use { workers ->
            val (id, stubborn) = workers.run("Stubborn")
            await().atMost(Duration.ofSeconds(30)).until { "running" in stubborn.output }
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
        workers(RunWorkerProcess.Limits()).use { workers ->
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
        workers(RunWorkerProcess.Limits(running = 2)).use { workers ->
            val (first, one) = workers.run("Polite")
            val (second, two) = workers.run("Polite")
            await().atMost(Duration.ofSeconds(30)).until { "running" in one.output && "running" in two.output }
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
            await().atMost(Duration.ofSeconds(10)).until { workers.workerPids().size == 1 }
            val kept = workers.workerPids().single()
            val (_, quick) = workers.run("Quick")
            assertThat(quick.exit()).isEqualTo(0L to null)
            assertThat(workers.workerPids()).containsExactly(kept)
        }
    }

    @Test
    fun `an idle worker exits after the idle limit, and the next run starts a new one`() {
        workers(RunWorkerProcess.Limits(idle = 300.milliseconds)).use { workers ->
            val (_, first) = workers.run("Quick")
            assertThat(first.exit()).isEqualTo(0L to null)
            await().atMost(Duration.ofSeconds(10)).until { workers.workerPids().isEmpty() }
            val (_, second) = workers.run("Quick")
            assertThat(second.exit()).isEqualTo(0L to null)
            assertThat(second.output.toString()).contains("ok")
        }
    }

    @Test
    fun `closing ends every run once, with the reason`() {
        val workers = workers(RunWorkerProcess.Limits(idle = 10.minutes, stop = 10.seconds))
        val (_, one) = workers.run("Polite")
        await().atMost(Duration.ofSeconds(30)).until { "running" in one.output }
        workers.close()
        assertThat(one.exit()).isEqualTo(null to "The execution worker was shut down")
        assertThat(workers.workerPids()).isEmpty()
    }
}
