package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS
import java.util.jar.JarFile

/** Runs the production launcher in child JVMs; fallback cleanup must not hide a failure to exit. */
class LspProcessLifecycleTest {
    @TempDir
    lateinit var directory: Path

    enum class Termination {
        EOF_BEFORE_INITIALIZE,
        EOF_AFTER_OPEN,
        SHUTDOWN_THEN_EOF,
        EXIT_WITHOUT_SHUTDOWN,
        SHUTDOWN_AND_EXIT,
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("terminations")
    fun `server terminates when its client disconnects`(
        backend: String,
        termination: Termination,
    ) {
        val jar = Path.of(requireNotNull(System.getProperty("xtc.lsp.jar")) { "Run the Gradle test task" })
        // Override only build selection, exercising every shipped backend from the packaged classes.
        // The resource directory precedes the JAR, avoiding an extra distribution build per backend.
        val resources = Files.createDirectory(directory.resolve("resources"))
        val properties = Properties()
        JarFile(jar.toFile()).use { archive ->
            archive.getInputStream(archive.getJarEntry("lsp-version.properties")).use { properties.load(it) }
        }
        properties.setProperty("lsp.adapter", backend)
        Files.newOutputStream(resources.resolve("lsp-version.properties")).use { properties.store(it, null) }
        val workspace = Files.createDirectory(directory.resolve("workspace"))
        val source = "module Lifecycle { Int value=1; }"
        val file = Files.writeString(workspace.resolve("Lifecycle.x"), source)
        val stderr = directory.resolve("stderr.log")
        val process =
            ProcessBuilder(
                ProcessHandle
                    .current()
                    .info()
                    .command()
                    .orElseThrow(),
                "--enable-native-access=ALL-UNNAMED",
                "-Duser.home=$directory",
                "-cp",
                "$resources${File.pathSeparator}$jar",
                "org.xvm.lsp.server.XtcLanguageServerLauncherKt",
            ).redirectError(stderr.toFile()).start()
        val executor = Executors.newVirtualThreadPerTaskExecutor()
        val diagnostics = LinkedBlockingQueue<PublishDiagnosticsParams>()
        val client =
            object : LanguageClient {
                override fun publishDiagnostics(params: PublishDiagnosticsParams) {
                    diagnostics.add(params)
                }

                override fun telemetryEvent(value: Any?) = Unit

                override fun showMessage(params: MessageParams) = Unit

                override fun showMessageRequest(params: ShowMessageRequestParams): CompletableFuture<MessageActionItem> =
                    CompletableFuture.completedFuture(null)

                override fun logMessage(params: MessageParams) = Unit

                override fun configuration(params: ConfigurationParams): CompletableFuture<List<Any>> =
                    CompletableFuture.completedFuture(params.items.map { emptyMap<String, Any>() })
            }
        try {
            val launcher = LSPLauncher.createClientLauncher(client, process.inputStream, process.outputStream, executor) { it }
            launcher.startListening()
            val server = launcher.remoteProxy
            if (termination != Termination.EOF_BEFORE_INITIALIZE) {
                server
                    .initialize(
                        InitializeParams().apply {
                            capabilities = ClientCapabilities()
                            workspaceFolders = listOf(WorkspaceFolder(workspace.toUri().toString(), "Lifecycle"))
                        },
                    ).get(20, SECONDS)
                server.initialized(InitializedParams())
                server.textDocumentService.didOpen(
                    DidOpenTextDocumentParams(TextDocumentItem(file.toUri().toString(), "xtc", 1, source)),
                )
                assertThat(diagnostics.poll(20, SECONDS)).describedAs("Server must have processed the document").isNotNull()
            }
            val shutdown = termination in setOf(Termination.SHUTDOWN_THEN_EOF, Termination.SHUTDOWN_AND_EXIT)
            if (shutdown) server.shutdown().get(20, SECONDS)
            when (termination) {
                Termination.EXIT_WITHOUT_SHUTDOWN, Termination.SHUTDOWN_AND_EXIT -> server.exit()
                else -> process.outputStream.close()
            }
            assertThat(process.waitFor(10, SECONDS))
                .describedAs("$backend survived $termination; pid=${process.pid()}\n${Files.readString(stderr)}")
                .isTrue()
            assertThat(process.exitValue()).isEqualTo(if (shutdown) 0 else 1)
            assertThat(Files.readString(stderr)).doesNotContain("falling back to mock")
        } finally {
            // Only after the exit assertion: never count forcibly reaping a leaked child as success.
            if (process.isAlive) process.destroyForcibly()
            check(process.waitFor(10, SECONDS)) { "Could not reap test server ${process.pid()}" }
            process.outputStream.close()
            process.inputStream.close()
            executor.shutdownNow()
        }
    }

    companion object {
        @JvmStatic
        fun terminations(): List<Arguments> =
            listOf("treesitter", "mock").flatMap { backend ->
                Termination.entries.map { Arguments.of(backend, it) }
            }
    }
}
