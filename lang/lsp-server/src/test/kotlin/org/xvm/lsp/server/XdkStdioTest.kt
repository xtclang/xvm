package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/** Process tests consume the actual fat JAR, including its manifest, resources and logging setup. */
@Tag("compiler-stdio")
class XdkStdioTest {
    @TempDir
    lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `bundled compiler handles edits and shutdown without an external XDK`(invalidXdkHome: Boolean) {
        Session(packagedJar(), directory, invalidXdkHome).use { session ->
            session.initialize()
            session.open(BROKEN)
            val errors = session.diagnosticsAt(1).diagnostics
            assertThat(errors).isNotEmpty()
            assertThat(errors.map { it.code.left }).doesNotContain("XDK-UNAVAILABLE", "ANALYSIS-FAILED", "EMB-5")
            assertThat(errors.any { it.message.left.contains("missing") }).isTrue()

            for (version in 2..101) {
                session.change(if (version == 101) VALID else BROKEN, version)
            }
            assertThat(session.diagnosticsAt(101).diagnostics).isEmpty()
            val symbols =
                session.await(
                    session.server.textDocumentService.documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(URI))),
                )
            assertThat(symbols).isNotEmpty()

            session.server.textDocumentService.didClose(DidCloseTextDocumentParams(TextDocumentIdentifier(URI)))
            assertThat(session.diagnosticsAt(101).diagnostics).isEmpty()
            session.open(VALID)
            assertThat(session.diagnosticsAt(1).diagnostics).isEmpty()

            for (version in 2..20) session.change(BROKEN, version)
            session.shutdownAndExit()
        }
    }

    @Test
    fun `a missing bundled bootstrap reports an analysis failure`() {
        val damaged = directory.resolve("damaged.jar")
        JarFile(packagedJar().toFile()).use { original ->
            assertThat(original.getJarEntry(BOOTSTRAP)).isNotNull()
            JarOutputStream(Files.newOutputStream(damaged)).use { output ->
                for (entry in original.entries()) {
                    if (entry.name == BOOTSTRAP) continue
                    output.putNextEntry(JarEntry(entry.name))
                    original.getInputStream(entry).use { it.copyTo(output) }
                    output.closeEntry()
                }
            }
        }
        Session(damaged, directory).use { session ->
            session.initialize()
            session.open(VALID)
            val errors = session.diagnosticsAt(1).diagnostics
            assertThat(errors.map { it.code.left }).containsExactly("ANALYSIS-FAILED")
            session.shutdownAndExit()
        }
        assertThat(Files.readString(directory.resolve("stderr.log"))).contains("Bundled XDK resource is missing: javatools_turtle.xtc")
    }

    @Test
    fun `exit without shutdown terminates with failure status`() {
        Session(packagedJar(), directory).use { session ->
            session.initialize()
            session.server.exit()
            session.expectExit(1)
        }
    }

    private fun packagedJar(): Path {
        val jar = Path.of(requireNotNull(System.getProperty("xtc.lsp.jar")) { "Run the compilerStdioTest Gradle task" })
        JarFile(jar.toFile()).use { archive ->
            val properties = Properties()
            archive.getInputStream(archive.getJarEntry("lsp-version.properties")).use { properties.load(it) }
            assertThat(properties.getProperty("lsp.adapter"))
                .describedAs("compilerStdioTest requires -Plsp.adapter=compiler")
                .isIn("compiler", "xtc", "full")
        }
        return jar
    }

    private class Session(
        jar: Path,
        directory: Path,
        invalidXdkHome: Boolean = false,
    ) : AutoCloseable {
        private val stderr = directory.resolve("stderr.log")
        private val published = LinkedBlockingQueue<PublishDiagnosticsParams>()
        private val executor = Executors.newVirtualThreadPerTaskExecutor()
        private val process =
            ProcessBuilder(
                ProcessHandle
                    .current()
                    .info()
                    .command()
                    .orElseThrow(),
                "-ea",
                "-Duser.home=$directory",
                "-jar",
                jar.toString(),
            ).apply {
                directory(directory.toFile())
                environment().remove("XDK_HOME")
                if (invalidXdkHome) environment()["XDK_HOME"] = directory.resolve("absent-xdk").toString()
                redirectError(stderr.toFile())
            }.start()
        private val client =
            object : LanguageClient {
                override fun publishDiagnostics(params: PublishDiagnosticsParams) {
                    published.add(params)
                }

                override fun telemetryEvent(value: Any?) = Unit

                override fun showMessage(params: MessageParams) = Unit

                override fun showMessageRequest(params: ShowMessageRequestParams): CompletableFuture<MessageActionItem> =
                    CompletableFuture.completedFuture(null)

                override fun logMessage(params: MessageParams) = Unit

                override fun configuration(params: ConfigurationParams): CompletableFuture<List<Any>> =
                    CompletableFuture.completedFuture(params.items.map { emptyMap<String, Any>() })
            }
        private val launcher =
            try {
                LSPLauncher.createClientLauncher(client, process.inputStream, process.outputStream, executor) { it }
            } catch (e: Exception) {
                process.destroyForcibly()
                process.waitFor(10, SECONDS)
                executor.shutdownNow()
                throw e
            }
        private val listening = launcher.startListening()
        val server = launcher.remoteProxy

        fun initialize() {
            val initialized = await(server.initialize(InitializeParams().apply { capabilities = ClientCapabilities() }))
            assertThat(initialized.capabilities.definitionProvider.left).isTrue()
            server.initialized(InitializedParams())
        }

        fun open(content: String) = server.textDocumentService.didOpen(DidOpenTextDocumentParams(TextDocumentItem(URI, "xtc", 1, content)))

        fun change(
            content: String,
            version: Int,
        ) = server.textDocumentService.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(URI, version),
                listOf(TextDocumentContentChangeEvent(content)),
            ),
        )

        fun diagnosticsAt(version: Int): PublishDiagnosticsParams {
            val deadline = System.nanoTime() + SECONDS.toNanos(30)
            while (true) {
                val remaining = deadline - System.nanoTime()
                val publication = if (remaining > 0) published.poll(remaining, NANOSECONDS) else null
                checkNotNull(publication) { "No diagnostics for version $version. ${log()}" }
                assertThat(publication.uri).isEqualTo(URI)
                assertThat(publication.version).isLessThanOrEqualTo(version)
                if (publication.version == version) return publication
            }
        }

        fun <T> await(future: Future<T>): T =
            try {
                future.get(30, SECONDS)
            } catch (e: TimeoutException) {
                throw AssertionError("LSP request timed out. ${log()}", e)
            }

        fun shutdownAndExit() {
            await(server.shutdown())
            server.exit()
            expectExit(0)
        }

        fun expectExit(status: Int) {
            assertThat(process.waitFor(10, SECONDS)).describedAs("Server did not exit. ${log()}").isTrue()
            assertThat(process.exitValue()).describedAs(log()).isEqualTo(status)
        }

        private fun log(): String = Files.readString(stderr).takeLast(8192)

        override fun close() {
            try {
                if (process.isAlive) process.destroyForcibly()
                check(process.waitFor(10, SECONDS)) { "Could not terminate test server. ${log()}" }
            } finally {
                listening.cancel(true)
                executor.shutdownNow()
                process.outputStream.close()
                process.inputStream.close()
            }
        }
    }

    private companion object {
        const val URI = "file:///Stdio.x"
        const val VALID = "module Stdio { Int run() { Int value = 1; return value; } }"
        const val BROKEN = "module Stdio { Int run() { return missing; } }"
        const val BOOTSTRAP = "org/xvm/lsp/xdk/javatools_turtle.xtc"
    }
}
