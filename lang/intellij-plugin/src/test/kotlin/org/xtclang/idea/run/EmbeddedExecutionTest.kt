package org.xtclang.idea.run

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xtclang.idea.lsp.BuildDiagnostic
import org.xtclang.idea.lsp.BuildPosition
import org.xtclang.idea.lsp.BuildRange
import org.xtclang.idea.lsp.BuildResponse
import org.xtclang.idea.lsp.BuiltModule
import org.xtclang.idea.lsp.DocumentDiagnostics
import org.xtclang.idea.lsp.RunExitedParams
import org.xtclang.idea.lsp.RunInputParams
import org.xtclang.idea.lsp.RunOutputParams
import org.xtclang.idea.lsp.RunParams
import org.xtclang.idea.lsp.RunResponse
import org.xtclang.idea.lsp.StopRunParams
import org.xtclang.idea.lsp.XtcLanguageServer
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.text.Charsets.ISO_8859_1
import kotlin.text.Charsets.UTF_8

/** Embedded build and run without an IDE: mode resolution, saved configurations, reports and run lifecycle. */
class EmbeddedExecutionTest {
    @Test
    fun `automatic mode builds with Gradle exactly when the project has a Gradle build`(
        @TempDir root: Path,
    ) {
        assertThat(XtcBuildSettings.resolve(XtcBuildMode.AUTOMATIC, root)).isEqualTo(XtcBuildMode.EMBEDDED)
        assertThat(XtcBuildSettings.resolve(XtcBuildMode.AUTOMATIC, null)).isEqualTo(XtcBuildMode.EMBEDDED)
        assertThat(XtcBuildSettings.resolve(XtcBuildMode.GRADLE, root)).isEqualTo(XtcBuildMode.GRADLE)
        Files.writeString(root.resolve("settings.gradle.kts"), "")
        assertThat(XtcBuildSettings.resolve(XtcBuildMode.AUTOMATIC, root)).isEqualTo(XtcBuildMode.GRADLE)
        assertThat(XtcBuildSettings.resolve(XtcBuildMode.EMBEDDED, root)).isEqualTo(XtcBuildMode.EMBEDDED)
    }

    @Test
    fun `embedded is the default, also in a project with a Gradle build, and its runs say Gradle settings do not apply`(
        @TempDir root: Path,
    ) {
        Files.writeString(root.resolve("build.gradle.kts"), "")
        val default = XtcBuildSettings.Options().mode
        assertThat(default).isEqualTo(XtcBuildMode.EMBEDDED)
        assertThat(XtcBuildSettings.resolve(default, root)).isEqualTo(XtcBuildMode.EMBEDDED)
        assertThat(XtcBuildSettings.gradleNotUsed(root)).contains("Gradle settings", "do not apply")
        assertThat(XtcBuildSettings.gradleNotUsed(root.resolve("plain"))).isNull()

        val sessions = EmbeddedRunSessions()
        val server = FakeServer(sessions)
        val handler = handler(sessions, server, XtcBuildSettings.gradleNotUsed(root))
        handler.startNotify()
        server.requests.poll(30, SECONDS)
        assertThat(handler.text()).contains("with the embedded compiler...\n", "Gradle settings")
    }

    @Test
    fun `the notice links to the settings page that chooses the mode`(
        @TempDir root: Path,
    ) {
        Files.writeString(root.resolve("build.gradle.kts"), "")
        val before = "Building with the embedded compiler...\n"
        val notice = checkNotNull(XtcBuildSettings.gradleNotUsed(root)) + "\n"
        val link = checkNotNull(BuildSettingsLink.applyFilter(notice, before.length + notice.length)).resultItems.single()
        assertThat((before + notice).substring(link.highlightStartOffset, link.highlightEndOffset))
            .isEqualTo("Settings | Build, Execution, Deployment | Ecstasy")
        assertThat(link.hyperlinkInfo).isNotNull()
        assertThat(BuildSettingsLink.applyFilter(before, before.length)).isNull()
    }

    @Test
    fun `configurations saved with useGradle follow the project unless they ran the command line`() {
        assertThat(XtcRunConfiguration.runMode(null, "true")).isEqualTo(XtcRunMode.PROJECT)
        assertThat(XtcRunConfiguration.runMode(null, null)).isEqualTo(XtcRunMode.PROJECT)
        assertThat(XtcRunConfiguration.runMode(null, "false")).isEqualTo(XtcRunMode.COMMAND_LINE)
        assertThat(XtcRunConfiguration.runMode("EMBEDDED", "false")).isEqualTo(XtcRunMode.EMBEDDED)
        assertThat(XtcRunConfiguration.runMode("unknown", null)).isEqualTo(XtcRunMode.PROJECT)
    }

    @Test
    fun `a run request names the module's source and also its name, which finds it after the source moved`() {
        val uri = "file:///work/App.x"
        assertThat(XtcRunConfiguration.embeddedRequest("App", uri, "greet", "one, two"))
            .isEqualTo(RunParams(module = uri, method = "greet", args = listOf("one", "two"), moduleName = "App"))
        // a configuration without a source sends only the name
        assertThat(XtcRunConfiguration.embeddedRequest("App", "", "", ""))
            .isEqualTo(RunParams(module = "App", method = null, args = emptyList(), moduleName = null))
    }

    @Test
    fun `build reports list module outcomes and one-based diagnostic locations`(
        @TempDir root: Path,
    ) {
        val source = root.resolve("App.x")
        val report =
            EmbeddedExecution.describe(
                BuildResponse(
                    succeeded = false,
                    modules =
                        listOf(
                            BuiltModule("Lib", "", succeeded = true, artifact = "/out/Lib.xtc"),
                            BuiltModule("App", ""),
                            BuiltModule("Tool", "", skipped = true),
                        ),
                    diagnostics =
                        listOf(
                            DocumentDiagnostics(
                                source.toUri().toString(),
                                listOf(
                                    BuildDiagnostic(BuildRange(BuildPosition(1, 4), BuildPosition(1, 9)), 1, "no such name", "COMPILER-43"),
                                    BuildDiagnostic(BuildRange(BuildPosition(0, 0), BuildPosition(0, 1)), 2, "unused"),
                                ),
                            ),
                        ),
                ),
            )
        assertThat(report).containsExactly(
            "  ✓ Lib -> /out/Lib.xtc",
            "  ✗ App",
            "  - Tool (not built: a dependency failed)",
            "$source:2:5: error: no such name [COMPILER-43]",
            "$source:1:1: warning: unused",
        )
    }

    @Test
    fun `sessions receive their own run's events until it exits or the server stops`() {
        val sessions = EmbeddedRunSessions()
        val first = Recorder()
        val second = Recorder()
        sessions.register("a", first)
        sessions.register("b", second)
        sessions.output(RunOutputParams("a", "hello"))
        sessions.output(RunOutputParams("unknown", "lost"))
        sessions.exited(RunExitedParams("a", 3, null))
        sessions.output(RunOutputParams("a", "late"))
        sessions.endAll("server stopped")
        assertThat(first.events).containsExactly("output hello", "exited 3 null")
        assertThat(second.events).containsExactly("exited null server stopped")
    }

    @Test
    fun `a run streams output and finishes with the program's exit code`() {
        val sessions = EmbeddedRunSessions()
        val server = FakeServer(sessions)
        val handler = handler(sessions, server)
        handler.startNotify()
        val request = server.requests.poll(30, SECONDS)!!
        assertThat(request.module).isEqualTo("file:///work/App.x")
        sessions.output(RunOutputParams(request.runId!!, "Hello, World!\n"))
        sessions.exited(RunExitedParams(request.runId!!, 7, null))
        assertThat(handler.waitFor(30_000)).isTrue()
        assertThat(handler.exitCode).isEqualTo(7)
        assertThat(handler.stdout()).isEqualTo("Hello, World!\n")
    }

    @Test
    fun `a failed build reports its diagnostics and runs nothing`() {
        val sessions = EmbeddedRunSessions()
        val server = FakeServer(sessions, RunResponse(null, BuildResponse(modules = listOf(BuiltModule("App", "")))))
        val handler = handler(sessions, server)
        handler.startNotify()
        assertThat(handler.waitFor(30_000)).isTrue()
        assertThat(handler.exitCode).isEqualTo(1)
        assertThat(handler.text()).contains("  ✗ App", "Build failed; nothing was run.")
    }

    @Test
    fun `stop asks the server to stop the run and waits for its exit`() {
        val sessions = EmbeddedRunSessions()
        val server = FakeServer(sessions)
        val handler = handler(sessions, server)
        handler.startNotify()
        val runId = server.requests.poll(30, SECONDS)!!.runId!!
        server.started.get(30, SECONDS)
        handler.destroyProcess()
        assertThat(server.stops.poll(30, SECONDS)?.runId).isEqualTo(runId)
        assertThat(handler.isProcessTerminated).isFalse()
        sessions.exited(RunExitedParams(runId, null, "Stopped by the user"))
        assertThat(handler.waitFor(30_000)).isTrue()
        assertThat(handler.text()).contains("Stopped by the user")
    }

    @Test
    fun `typed lines reach the server after the run request, and Send EOF ends the input`() {
        val sessions = EmbeddedRunSessions()
        val server = FakeServer(sessions)
        val handler = handler(sessions, server)
        // typed before the run request exists: it must still follow it
        handler.processInput.write("Ada ✓\n".toByteArray(UTF_8))
        handler.startNotify()
        handler.processInput.write("rest".toByteArray(UTF_8))
        handler.processInput.close()
        val runId = server.requests.poll(30, SECONDS)!!.runId!!

        assertThat(server.inputs.poll(30, SECONDS)).isEqualTo(RunInputParams(runId, "Ada ✓\n", false))
        assertThat(server.inputs.poll(30, SECONDS)).isEqualTo(RunInputParams(runId, "rest", true))
        assertThat(server.calls).containsExactly("run", "runInput", "runInput")
    }

    @Test
    fun `console input forwards each complete line in the console's encoding`() {
        val sent = mutableListOf<Pair<String?, Boolean>>()
        RunConsoleInput(ISO_8859_1) { text, end -> sent += text to end }.use { input ->
            input.write("é\nab\ncd".toByteArray(ISO_8859_1))
        }
        assertThat(sent).containsExactly("é\n" to false, "ab\n" to false, "cd" to true)
    }

    @Test
    fun `a server that stops ends the run`() {
        val sessions = EmbeddedRunSessions()
        val server = FakeServer(sessions)
        val handler = handler(sessions, server)
        handler.startNotify()
        server.requests.poll(30, SECONDS)
        server.started.get(30, SECONDS)
        sessions.endAll("The Ecstasy language server stopped")
        assertThat(handler.waitFor(30_000)).isTrue()
        assertThat(handler.exitCode).isEqualTo(1)
        assertThat(handler.text()).contains("The Ecstasy language server stopped")
    }

    private fun handler(
        sessions: EmbeddedRunSessions,
        server: FakeServer,
        notice: String? = null,
    ) = RecordingHandler(
        EmbeddedRunProcessHandler(sessions, {
            CompletableFuture.completedFuture(server.proxy)
        }, RunParams("file:///work/App.x"), "App", notice, consoleCharset = { UTF_8 }),
    )

    private class Recorder : EmbeddedRunSessions.Session {
        val events = mutableListOf<String>()

        override fun output(text: String) {
            events += "output $text"
        }

        override fun exited(
            exitCode: Long?,
            error: String?,
        ) {
            events += "exited $exitCode $error"
        }
    }

    /** The requests a run makes, answered as a language server would; everything else is unsupported. */
    private class FakeServer(
        sessions: EmbeddedRunSessions,
        private val response: RunResponse? = null,
    ) {
        val requests = LinkedBlockingQueue<RunParams>()
        val stops = LinkedBlockingQueue<StopRunParams>()
        val inputs = LinkedBlockingQueue<RunInputParams>()
        val calls = ConcurrentLinkedQueue<String>()
        val started = CompletableFuture<Unit>()

        val proxy: XtcLanguageServer =
            Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XtcLanguageServer::class.java)) { _, method, args ->
                calls += method.name
                when (method.name) {
                    "run" -> {
                        val request = args[0] as RunParams
                        requests += request
                        started.complete(Unit)
                        CompletableFuture.completedFuture(response ?: RunResponse(request.runId, BuildResponse(succeeded = true)))
                    }

                    "stopRun" -> {
                        stops += args[0] as StopRunParams
                        CompletableFuture.completedFuture(true)
                    }

                    "runInput" -> {
                        inputs += args[0] as RunInputParams
                        CompletableFuture.completedFuture(true)
                    }

                    else -> {
                        throw UnsupportedOperationException(method.name)
                    }
                }
            } as XtcLanguageServer
    }

    private class RecordingHandler(
        private val handler: EmbeddedRunProcessHandler,
    ) {
        private val texts = ConcurrentLinkedQueue<Pair<Key<*>, String>>()

        init {
            handler.addProcessListener(
                object : ProcessListener {
                    override fun onTextAvailable(
                        event: ProcessEvent,
                        outputType: Key<*>,
                    ) {
                        texts += outputType to event.text
                    }
                },
            )
        }

        val exitCode get() = handler.exitCode
        val isProcessTerminated get() = handler.isProcessTerminated
        val processInput get() = handler.processInput

        fun startNotify() = handler.startNotify()

        fun destroyProcess() = handler.destroyProcess()

        fun waitFor(millis: Long) = handler.waitFor(millis)

        fun stdout() = texts.filter { it.first == ProcessOutputTypes.STDOUT }.joinToString("") { it.second }

        fun text() = texts.joinToString("") { it.second }
    }
}
