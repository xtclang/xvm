package org.xvm.lsp.run

import org.xvm.debug.TestEvent
import org.xvm.debug.TestSelector
import org.xvm.lsp.adapter.xdk.XdkLibraries
import org.xvm.lsp.adapter.xdk.XdkTest
import org.xvm.tool.Launcher
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlin.text.Charsets.UTF_8

/**
 * A run of a module's tests in a process of its own, the launcher's `xtc test`, which reports their execution as events
 * in its output ([REPORT], [TestEvent]). Its output goes to the listener line by line; [ended] completes with its exit
 * code once it has ended and all of its output has gone.
 */
internal class TestProcess private constructor(
    private val process: Process,
    output: (String) -> Unit,
) {
    @Volatile
    var stopped = false
        private set

    val ended: CompletableFuture<Int> =
        CompletableFuture
            .runAsync({ process.inputReader(UTF_8).useLines { lines -> lines.forEach { output("$it\n") } } }, OUTPUT)
            .handle { _, _ -> }
            .thenCompose { process.onExit() }
            .thenApply { it.exitValue() }

    init {
        process.outputStream.close()
    }

    /** Ends the process, and the processes it started. */
    fun stop() {
        stopped = true
        process.descendants().forEach(ProcessHandle::destroy)
        process.destroy()
    }

    companion object {
        /** The injection that has xunit report the execution as events, its `json` report. */
        const val REPORT = "xvm.xunit.report=${TestEvent.REPORT}"

        /** Where the process's output is read: a thread of its own for each. */
        private val OUTPUT = Executor { Thread.ofVirtual().name("xtc-test-output").start(it) }

        /**
         * The `xtc test` command for the tests that [tests] select of the module [artifact], all of them when it is
         * empty: it runs from this server's own class path, with the bundled XDK's libraries and [modulePath], and
         * xunit writes to [outputDirectory]. With [debug], the launcher's `--debug` options, it runs under the
         * debugger.
         */
        fun command(
            artifact: String,
            modulePath: List<String>,
            tests: List<TestSelector>,
            outputDirectory: String,
            debug: String?,
        ): List<String> =
            buildList {
                add(
                    ProcessHandle
                        .current()
                        .info()
                        .command()
                        .orElse("java"),
                )
                add("-Dfile.encoding=UTF-8")
                add("-cp")
                add(System.getProperty("java.class.path"))
                add(Launcher::class.java.name)
                add("test")
                (listOf(XdkLibraries.directory.path) + modulePath).forEach {
                    add("-L")
                    add(it)
                }
                add("--no-recompile")
                add("-x")
                add(outputDirectory)
                add("-I")
                add(REPORT)
                tests.forEach { test ->
                    option(test)?.let {
                        add(it)
                        add(test.selector)
                    }
                }
                debug?.let {
                    add("--debug")
                    add(it)
                }
                add(artifact)
            }

        /** The option that selects [test], or null for a module, whose selection is all of its tests. */
        private fun option(test: TestSelector): String? =
            when (test.kind) {
                XdkTest.PACKAGE -> "-p"
                XdkTest.CLASS -> "-c"
                XdkTest.TEST -> "-t"
                XdkTest.MODULE -> null
                else -> throw IllegalArgumentException("A test's kind is module, package, class or test, not ${test.kind}")
            }

        /** Starts [command] in [workingDirectory]; [output] receives what it prints, its errors with the rest. */
        fun start(
            command: List<String>,
            workingDirectory: File,
            output: (String) -> Unit,
        ): TestProcess = TestProcess(ProcessBuilder(command).directory(workingDirectory).redirectErrorStream(true).start(), output)
    }
}
