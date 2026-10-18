package org.xvm.lsp.run

import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.awaitility.Awaitility.await
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.DirRepository
import org.xvm.asm.ErrorList
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkBuild
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.server.EmbeddedRuns
import org.xvm.lsp.server.RunExitedParams
import org.xvm.lsp.server.RunInputParams
import org.xvm.lsp.server.RunOutputParams
import org.xvm.lsp.server.RunParams
import org.xvm.lsp.server.XtcLanguageClient
import org.xvm.lsp.server.XtcLanguageServer
import org.xvm.lsp.server.launchStdio
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger

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
            module Chatty {
                void run() {
                    @Inject Console console;
                    for (Int line = 0; line < 5000; ++line) {
                        console.print($"line {line}");
                    }
                }
            }
            """,
            FOREVER,
            ASK,
            """
            module Waiter {
                void run() {
                    @Inject Console console;
                    console.print("waiting");
                    console.readLine();
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
    fun `a program printing in a loop sends all its output, in order, in far fewer events than prints`() {
        val events = AtomicInteger()
        val run = run("Chatty") { events.incrementAndGet() }
        assertThat(run.error).isNull()
        assertThat(run.output.lines().filter { it.isNotEmpty() }).isEqualTo((0 until 5000).map { "line $it" })
        println("5000 prints arrived in ${events.get()} output events")
        assertThat(events.get()).isLessThan(1000)
    }

    @Test
    fun `after a run the worker reports the heap it keeps and its threads, within the limits`() {
        assertThat(run("Exit").code).isEqualTo(42)
        await().atMost(Duration.ofSeconds(30)).until { worker.usage().isNotEmpty() }
        val limits = RunWorkerProcess.Limits()
        assertThat(worker.usage().values).allSatisfy { usage ->
            println("execution worker after a run: $usage")
            assertThat(usage.heapUsed).isPositive().isLessThan(limits.heap)
            assertThat(usage.heapMax).isGreaterThanOrEqualTo(usage.heapUsed)
            assertThat(usage.threads).isPositive().isLessThan(limits.threads)
        }
    }

    @Test
    fun `repeated runs reuse one worker and leave no run, console or pool thread behind`() {
        RunWorkerProcess().use { workers ->
            val reports = mutableListOf<RunWorkerProcess.Usage>()
            val pids = mutableSetOf<Long>()

            fun once(
                module: String,
                args: List<String> = emptyList(),
                act: (Long, StringBuffer) -> Unit = { _, _ -> },
            ): Outcome {
                val output = StringBuffer()
                val exited = CompletableFuture<Pair<Long?, String?>>()
                val id =
                    workers.start(
                        RunWorkerProcess.Request(listOf(lib.path), module, "run", args, directory.toString()),
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
                act(id, output)
                val (code, error) = exited.get(60, SECONDS)
                await().atMost(Duration.ofSeconds(30)).until { workers.usage().values.any { it.run == id } }
                reports += workers.usage().values.single { it.run == id }
                pids += workers.workerPids()
                return Outcome(output.toString(), code, error)
            }

            // a normal exit, arguments, an exception, console input and a stopped program, five times over
            repeat(5) {
                assertThat(once("Echo", listOf("a b", "ü")).output).contains("args=2:a b|ü")
                assertThat(once("Exit").code).isEqualTo(42)
                assertThat(once("Thrower").error).contains("boom")
                val asked =
                    once("Ask") { id, output ->
                        await().atMost(Duration.ofSeconds(60)).until { "Name? " in output }
                        workers.input(id, "Ada\n")
                        workers.endInput(id)
                    }
                assertThat(asked.output).contains("Hello, Ada!")
                once("Waiter") { id, output ->
                    await().atMost(Duration.ofSeconds(60)).until { "waiting" in output }
                    workers.stop(id)
                }
            }
            println("repeated runs: first ${reports.first()}, last ${reports.last()}")

            assertThat(pids).describedAs("one worker served every run").hasSize(1)
            assertThat(reports).hasSize(25).allSatisfy {
                assertThat(it.consoles).isZero()
                assertThat(it.runs).isZero()
                assertThat(it.poolThreads).isPositive().isEqualTo(reports.first().poolThreads)
            }
            // after the first round has loaded what runs need, more runs retain nothing that adds up
            assertThat(reports.last().heapUsed - reports[4].heapUsed).isLessThan(64L shl 20)
            assertThat(workers.tracked()).isZero()
        }
    }

    @Test
    fun `a worker exits when the language server process dies`() {
        val host =
            ProcessBuilder(
                ProcessHandle
                    .current()
                    .info()
                    .command()
                    .orElse("java"),
                "-cp",
                System.getProperty("java.class.path"),
                RunWorkerHost::class.java.name,
                lib.path,
                "Forever",
                directory.toString(),
            ).redirectErrorStream(true)
                .start()
        try {
            val pid =
                CompletableFuture
                    .supplyAsync {
                        host
                            .inputReader()
                            .lineSequence()
                            .first { it.startsWith("worker-pid=") }
                            .removePrefix("worker-pid=")
                            .toLong()
                    }.get(60, SECONDS)
            val worker = ProcessHandle.of(pid).orElseThrow()
            // killed, as when the IDE or the operating system ends the server: nothing tells the worker
            host.destroyForcibly()
            assertThat(worker.onExit().get(30, SECONDS).isAlive).isFalse()
        } finally {
            host.destroyForcibly()
        }
    }

    @Test
    fun `closing the editor ends the server's runs and their workers`() {
        CompilerTestSupport.configure()
        val forever = source("Forever.x", FOREVER.trimIndent())
        val adapter = XdkAdapter()
        adapter.initializeWorkspace(listOf(directory.toString()))
        adapter.replaceSourceModules(listOf(XdkSourceModule("Forever", forever)))
        val server = XtcLanguageServer(adapter)
        assertThat(server.run(RunParams(module = "Forever", runId = "editor-closes")).get(60, SECONDS).runId)
            .isEqualTo("editor-closes")
        val workers = server.runWorkerPids().map { ProcessHandle.of(it).orElseThrow() }
        assertThat(workers).isNotEmpty()
        // the editor closes its end: the server reads the end of its input, shuts down and ends its workers
        launchStdio(server, ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream())
        workers.forEach { assertThat(it.onExit().get(30, SECONDS).isAlive).isFalse() }
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
    fun `two programs run at the same time, each in its own worker`() {
        val outputs = List(2) { StringBuffer() }
        val exits = List(2) { CompletableFuture<Long?>() }
        val ids =
            (0..1).map { index ->
                worker.start(
                    RunWorkerProcess.Request(listOf(lib.path), "Forever", "run", emptyList(), directory.toString()),
                    object : RunWorkerProcess.Listener {
                        override fun output(text: String) {
                            outputs[index].append(text)
                        }

                        override fun exited(
                            code: Long?,
                            error: String?,
                        ) {
                            exits[index].complete(code)
                        }
                    },
                )
            }
        await().atMost(Duration.ofSeconds(60)).until { outputs.all { "started" in it } }
        assertThat(worker.workerPids()).hasSizeGreaterThanOrEqualTo(2)
        ids.forEach(worker::stop)
        exits.forEach { it.get(30, SECONDS) }

        assertThat(run("Exit").code).isEqualTo(42)
    }

    @Test
    fun `a program reads console input line by line, and the end of input reads as an empty line`() {
        val output = StringBuffer()
        val prompted = CompletableFuture<Unit>()
        val exited = CompletableFuture<Pair<Long?, String?>>()
        val id =
            worker.start(
                RunWorkerProcess.Request(listOf(lib.path), "Ask", "run", emptyList(), directory.toString()),
                object : RunWorkerProcess.Listener {
                    override fun output(text: String) {
                        output.append(text)
                        if ("Name? " in output) prompted.complete(Unit)
                    }

                    override fun exited(
                        code: Long?,
                        error: String?,
                    ) {
                        exited.complete(code to error)
                    }
                },
            )
        prompted.get(60, SECONDS)
        worker.input(id, "Ada Lovelace ✓\n")
        worker.endInput(id)
        val (code, error) = exited.get(60, SECONDS)

        assertThat(error).isNull()
        assertThat(code).isEqualTo(0)
        assertThat(output.toString()).contains("Hello, Ada Lovelace ✓!").contains("next=[]")
    }

    @Test
    fun `stop ends a program waiting for input, and the worker serves the next run`() {
        val output = StringBuffer()
        val waiting = CompletableFuture<Unit>()
        val exited = CompletableFuture<Long?>()
        val id =
            worker.start(
                RunWorkerProcess.Request(listOf(lib.path), "Waiter", "run", emptyList(), directory.toString()),
                object : RunWorkerProcess.Listener {
                    override fun output(text: String) {
                        output.append(text)
                        if ("waiting" in output) waiting.complete(Unit)
                    }

                    override fun exited(
                        code: Long?,
                        error: String?,
                    ) {
                        exited.complete(code)
                    }
                },
            )
        waiting.get(60, SECONDS)
        worker.stop(id)
        exited.get(30, SECONDS)

        assertThat(run("Exit").code).isEqualTo(42)
    }

    @Test
    fun `input typed while the module is still building reaches the program`() {
        CompilerTestSupport.configure()
        val ask = source("Ask.x", ASK.trimIndent())
        val output = StringBuffer()
        val exited = CompletableFuture<RunExitedParams>()
        val client =
            object : XtcLanguageClient, LanguageClient by mock(LanguageClient::class.java) {
                override fun runOutput(params: RunOutputParams) {
                    output.append(params.text)
                }

                override fun runExited(params: RunExitedParams) {
                    exited.complete(params)
                }
            }
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("Ask", ask)))
            EmbeddedRuns(adapter, { client }).use { runs ->
                val started = runs.run(RunParams(module = "Ask", runId = "typed-early"))
                // typed right away: until the build finishes and the program starts, the server holds it
                assertThat(runs.input(RunInputParams("typed-early", "Grace\n", end = true))).isTrue()
                assertThat(started.get(60, SECONDS).runId).isEqualTo("typed-early")
                val exit = exited.get(60, SECONDS)

                assertThat(exit.error).isNull()
                assertThat(output.toString()).contains("Hello, Grace!").contains("next=[]")
                assertThat(runs.tracked()).isZero()
                assertThat(runs.input(RunInputParams("typed-early", "late\n"))).isFalse()
                assertThat(runs.input(RunInputParams("no-such-run", "text\n"))).isFalse()
            }
        }
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
    fun `a build compiles the editor's unsaved text, not the file on disk`() {
        CompilerTestSupport.configure()
        val app = source("App.x", "module App { Int run() = 1; }")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("App", app)))
            adapter.compile(app, "module App { Int run() = 7; }")
            val build = adapter.buildAsync("App", rebuild = false).get(60, SECONDS)

            assertThat(build.succeeded).describedAs(build.diagnostics.toString()).isTrue()
            assertThat(run("App", modulePath = build.modulePath).code).isEqualTo(7)
        }
    }

    @Test
    fun `unchanged modules are reused, an editor analysis counts, and rebuild compiles everything`() {
        CompilerTestSupport.configure()
        val library = source("Library.x", "module Library { static Int value() = 7; }")
        val app = source("App.x", "module App { package lib import Library; Int run() = lib.value(); }")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(
                listOf(XdkSourceModule("Library", library), XdkSourceModule("App", app, setOf("Library"))),
            )

            fun reused(rebuild: Boolean = false) =
                adapter
                    .buildAsync("App", rebuild)
                    .get(60, SECONDS)
                    .also {
                        assertThat(it.succeeded).describedAs(it.diagnostics.toString()).isTrue()
                    }.modules
                    .associate { it.name to it.reused }

            assertThat(reused()).containsExactly(entry("Library", false), entry("App", false))
            assertThat(reused()).containsExactly(entry("Library", true), entry("App", true))
            // the editor's analysis of the changed library already compiled it; its consumer compiles again
            adapter.compile(library, "module Library { static Int value() = 8; }")
            assertThat(reused()).containsExactly(entry("Library", true), entry("App", false))
            assertThat(reused(rebuild = true)).containsExactly(entry("Library", false), entry("App", false))
        }
    }

    @Test
    fun `the manifest records each module's compiled sources and dependency revisions`() {
        CompilerTestSupport.configure()
        val libraryText = "module Library { static Int value() = 7; }"
        val library = source("Library.x", libraryText)
        val app = source("App.x", "module App { package lib import Library; Int run() = lib.value(); }")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(
                listOf(XdkSourceModule("Library", library), XdkSourceModule("App", app, setOf("Library"))),
            )
            val build = adapter.buildAsync("App", rebuild = false).get(60, SECONDS)
            val revisions = build.modules.associate { it.name to it.revision }
            val manifest =
                JsonParser
                    .parseString(directory.resolve("build/xtc/embedded/manifest.json").toFile().readText())
                    .asJsonObject["modules"]
                    .asJsonArray
                    .associateBy { it.asJsonObject["name"].asString }
                    .mapValues { it.value.asJsonObject }

            assertThat(manifest.keys).containsExactly("Library", "App")
            assertThat(manifest.getValue("Library")["sources"].asJsonObject[library].asString)
                .isEqualTo(XdkBuild.sha256(libraryText))
            assertThat(manifest.getValue("Library")["revision"].asString).isEqualTo(revisions["Library"])
            assertThat(manifest.getValue("App")["dependencies"].asJsonObject["Library"].asString).isEqualTo(revisions["Library"])
        }
    }

    @Test
    fun `a module that no longer compiles leaves no earlier artifact to run`() {
        CompilerTestSupport.configure()
        val app = source("App.x", "module App { Int run() = 1; }")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(listOf(XdkSourceModule("App", app)))
            val artifact = directory.resolve("build/xtc/embedded/lib/App.xtc").toFile()
            assertThat(adapter.buildAsync("App", rebuild = false).get(60, SECONDS).succeeded).isTrue()
            assertThat(artifact).isFile()

            adapter.compile(app, "module App { Int run() = \"text\"; }")
            assertThat(adapter.buildAsync("App", rebuild = false).get(60, SECONDS).succeeded).isFalse()
            assertThat(artifact).doesNotExist()
        }
    }

    @Test
    fun `only modules with a callable run method are runnable targets with a Run lens`() {
        CompilerTestSupport.configure()
        val libraryText = "module Library { static Int value() = 7; }"
        val appText = "module App { void run() {} }"
        // the runtime calls only methods, so a run function is no entry point
        val utilityText = "module Utility { static void run() {} }"
        val library = source("Library.x", libraryText)
        val app = source("App.x", appText)
        val utility = source("Utility.x", utilityText)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(
                listOf(XdkSourceModule("Library", library), XdkSourceModule("App", app), XdkSourceModule("Utility", utility)),
            )
            assertThat(adapter.compile(library, libraryText).success).isTrue()
            assertThat(adapter.compile(app, appText).success).isTrue()
            assertThat(adapter.compile(utility, utilityText).success).isTrue()

            assertThat(adapter.runnableTargets().map { it.module }).containsExactly("App")
            assertThat(adapter.getCodeLensesAsync(app, false).get(30, SECONDS).mapNotNull { it.command?.command })
                .contains("xtc.runModule")
            assertThat(adapter.getCodeLensesAsync(library, false).get(30, SECONDS).mapNotNull { it.command?.command })
                .doesNotContain("xtc.runModule")
        }
    }

    @Test
    fun `a run method an annotation contributes gives its module a Run lens, and the module runs`() {
        CompilerTestSupport.configure()
        val contributionsText =
            """
            module Contributions {
                annotation Runnable into module {
                    void run() {
                        @Inject Console console;
                        console.print("run from an annotation");
                    }
                }
            }
            """.trimIndent()
        val annotatedText =
            """
            @Runnable
            module Annotated {
                package contributions import Contributions;
                import contributions.Runnable;
            }
            """.trimIndent()
        val contributions = source("Contributions.x", contributionsText)
        val annotated = source("Annotated.x", annotatedText)
        val output = StringBuffer()
        val exited = CompletableFuture<RunExitedParams>()
        val client =
            object : XtcLanguageClient, LanguageClient by mock(LanguageClient::class.java) {
                override fun runOutput(params: RunOutputParams) {
                    output.append(params.text)
                }

                override fun runExited(params: RunExitedParams) {
                    exited.complete(params)
                }
            }
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(
                listOf(XdkSourceModule("Contributions", contributions), XdkSourceModule("Annotated", annotated, setOf("Contributions"))),
            )
            assertThat(adapter.compile(contributions, contributionsText).success).isTrue()
            assertThat(adapter.compile(annotated, annotatedText).success).isTrue()

            // the module declares no run method; its annotation does, and the module declaring the annotation is no program
            assertThat(adapter.runnableTargets().map { it.module }).containsExactly("Annotated")
            assertThat(adapter.getCodeLensesAsync(annotated, false).get(30, SECONDS).mapNotNull { it.command?.command })
                .contains("xtc.runModule")
            EmbeddedRuns(adapter, { client }).use { runs ->
                assertThat(runs.run(RunParams(module = "Annotated", runId = "contributed")).get(60, SECONDS).runId).isEqualTo("contributed")
                assertThat(exited.get(60, SECONDS).error).isNull()
                assertThat(output.toString()).contains("run from an annotation")
            }
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
        modulePath: List<String> = listOf(lib.path),
        event: () -> Unit = {},
    ): Outcome {
        val output = StringBuffer()
        val exited = CompletableFuture<Pair<Long?, String?>>()
        worker.start(
            RunWorkerProcess.Request(modulePath, module, method, args, directory.toString()),
            object : RunWorkerProcess.Listener {
                override fun output(text: String) {
                    event()
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

    private companion object {
        const val FOREVER =
            """
            module Forever {
                void run() {
                    @Inject Console console;
                    console.print("started");
                    // runs until stopped: an empty while (True) is rejected as non-terminating
                    for (Int count = 0; count >= 0; ++count) {}
                }
            }
            """

        const val ASK =
            """
            module Ask {
                void run() {
                    @Inject Console console;
                    String name = console.readLine("Name? ");
                    console.print($"Hello, {name}!");
                    String next = console.readLine();
                    console.print($"next=[{next}]");
                }
            }
            """
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
