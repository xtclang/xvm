package org.xvm.lsp.run

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.DirRepository
import org.xvm.asm.ErrorList
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Embedded build to disk through the compiler queue, and runs in the execution worker JVM. The worker
 * tests start a real worker process from the test classpath, as the language server does from its jar.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmbeddedExecutionTest {
    @TempDir lateinit var directory: Path

    private lateinit var lib: File
    private val worker = RunWorkerProcess()

    @BeforeAll
    fun compileProgramsOnce() {
        CompilerTestSupport.configure()
        lib = Files.createTempDirectory("xtc-run-test").toFile()
        listOf(
            """
            module Echo {
                void run(String[] args) {
                    @Inject Console console;
                    console.print($"args={args.size}:{args[0]}|{args[1]}");
                }
            }
            """,
            "module Exit { Int run() = 42; }",
            "module Greeter { void greet() { @Inject Console console; console.print(\"greetings\"); } }",
            "module Thrower { void run() { throw new IllegalState(\"boom\"); } }",
            """
            module Forever {
                void run() {
                    @Inject Console console;
                    console.print("started");
                    // runs until stopped: an empty while (True) is rejected as non-terminating
                    for (Int count = 0; count >= 0; ++count) {}
                }
            }
            """,
        ).forEach { source ->
            val errors = ErrorList()
            val module = EmbeddingSupport.instance().compile(source.trimIndent(), null, errors)
            assertThat(module).describedAs(errors.errors.toString()).isNotNull()
            DirRepository(lib, false).storeModule(module)
        }
    }

    @AfterAll
    fun closeWorker() {
        worker.close()
        lib.deleteRecursively()
    }

    @BeforeEach
    fun canonicalDirectory() {
        directory = directory.toRealPath()
    }

    @Test
    fun `arguments reach the entry method unchanged, including spaces and non-ASCII text`() {
        val run = run("Echo", args = listOf("hello world", "ünïcødé ✓"))
        assertThat(run.code).isEqualTo(0)
        assertThat(run.error).isNull()
        assertThat(run.output).contains("args=2:hello world|ünïcødé ✓")
    }

    @Test
    fun `the entry method's Int result is the exit code`() {
        assertThat(run("Exit").code).isEqualTo(42)
    }

    @Test
    fun `a named entry method other than run can be invoked`() {
        val run = run("Greeter", method = "greet")
        assertThat(run.error).isNull()
        assertThat(run.output).contains("greetings")
    }

    @Test
    fun `an unhandled exception ends the run with an error`() {
        val run = run("Thrower")
        assertThat(run.code).isNull()
        assertThat(run.error).contains("boom")
    }

    @Test
    fun `a missing entry method is reported instead of starting`() {
        val run = run("Exit", method = "nope")
        assertThat(run.code).isNull()
        assertThat(run.error).contains("nope")
    }

    @Test
    fun `stop ends a program that never finishes, and the worker serves the next run`() {
        val output = StringBuffer()
        val exited = CompletableFuture<Long?>()
        val started = CompletableFuture<Unit>()
        val id =
            worker.start(
                RunWorkerProcess.Request(listOf(lib.path), "Forever", "run", emptyList(), directory.toString()),
                object : RunWorkerProcess.Listener {
                    override fun output(text: String) {
                        output.append(text)
                        if ("started" in output) started.complete(Unit)
                    }

                    override fun exited(
                        code: Long?,
                        error: String?,
                    ) {
                        exited.complete(code)
                    }
                },
            )
        started.get(60, SECONDS)
        worker.stop(id)
        exited.get(30, SECONDS)

        assertThat(run("Exit").code).isEqualTo(42)
    }

    @Test
    fun `runs do not start the runner's HTTP endpoint`() {
        assertThat(run("Exit").output).doesNotContain("Runner is listening")
    }

    @Test
    fun `an embedded build writes modules in dependency order into the workspace's embedded output`() {
        CompilerTestSupport.configure()
        val library = source("Library.x", "module Library { static Int value() = 7; }")
        val app = source("App.x", "module App { package lib import Library; Int run() = lib.value(); }")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(
                listOf(XdkSourceModule("Library", library), XdkSourceModule("App", app, setOf("Library"))),
            )
            val build = adapter.buildAsync("App", rebuild = false).get(60, SECONDS)

            assertThat(build.succeeded).describedAs(build.diagnostics.toString()).isTrue()
            assertThat(build.modules.map { it.name }).containsExactly("Library", "App")
            val output = directory.resolve("build/xtc/embedded/lib").toFile()
            assertThat(File(output, "Library.xtc")).isFile()
            assertThat(File(output, "App.xtc")).isFile()
            assertThat(build.workingDirectory).isEqualTo(directory.toString())
        }
    }

    @Test
    fun `a failed build reports located diagnostics and skips dependent modules`() {
        CompilerTestSupport.configure()
        val broken =
            source(
                "Broken.x",
                """
                module Broken {
                    Int value() = "not a number";
                }
                """.trimIndent(),
            )
        val consumer = source("Consumer.x", "module Consumer { package b import Broken; Int run() = b.value(); }")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(
                listOf(XdkSourceModule("Broken", broken), XdkSourceModule("Consumer", consumer, setOf("Broken"))),
            )
            val build = adapter.buildAsync("Consumer", rebuild = false).get(60, SECONDS)

            assertThat(build.succeeded).isFalse()
            assertThat(build.modules.first { it.name == "Broken" }.succeeded).isFalse()
            assertThat(build.modules.first { it.name == "Consumer" }.skipped).isTrue()
            assertThat(build.diagnostics).anySatisfy {
                assertThat(it.location.uri).isEqualTo(broken)
                assertThat(it.location.startLine).isEqualTo(1)
            }
        }
    }

    @Test
    fun `only modules with a callable run method are runnable targets with a Run lens`() {
        CompilerTestSupport.configure()
        val libraryText = "module Library { static Int value() = 7; }"
        val appText = "module App { void run() {} }"
        val library = source("Library.x", libraryText)
        val app = source("App.x", appText)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Library", library), XdkSourceModule("App", app)))
            assertThat(adapter.compile(library, libraryText).success).isTrue()
            assertThat(adapter.compile(app, appText).success).isTrue()

            assertThat(adapter.runnableTargets().map { it.module }).containsExactly("App")
            assertThat(adapter.getCodeLensesAsync(app, false).get(30, SECONDS).mapNotNull { it.command?.command })
                .contains("xtc.runModule")
            assertThat(adapter.getCodeLensesAsync(library, false).get(30, SECONDS).mapNotNull { it.command?.command })
                .doesNotContain("xtc.runModule")
        }
    }

    private data class Outcome(
        val output: String,
        val code: Long?,
        val error: String?,
    )

    private fun run(
        module: String,
        method: String = "run",
        args: List<String> = emptyList(),
    ): Outcome {
        val output = StringBuffer()
        val exited = CompletableFuture<Pair<Long?, String?>>()
        worker.start(
            RunWorkerProcess.Request(listOf(lib.path), module, method, args, directory.toString()),
            object : RunWorkerProcess.Listener {
                override fun output(text: String) {
                    output.append(text)
                }

                override fun exited(
                    code: Long?,
                    error: String?,
                ) {
                    exited.complete(code to error)
                }
            },
        )
        val (code, error) = exited.get(60, SECONDS)
        return Outcome(output.toString(), code, error)
    }

    private fun source(
        path: String,
        text: String,
    ): String {
        val file = directory.resolve(path).toFile()
        file.parentFile.mkdirs()
        file.writeText(text)
        return file.toURI().toString()
    }
}
