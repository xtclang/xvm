package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Runs the production launcher in a child JVM: the read-only copies of library sources that it writes, for a client
 * that opens them as files, are gone once it exits, whether the client ends it or disconnects.
 */
@Tag("compiler-stdio")
class LibraryCopiesCleanupTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest(name = "shutdown and exit: {0}")
    @ValueSource(booleans = [true, false])
    fun `the server deletes its library copies when it exits`(shutdown: Boolean) {
        val jar = Path.of(requireNotNull(System.getProperty("xtc.lsp.jar")) { "Run compilerStdioTest" })
        val temporary = Files.createDirectory(directory.resolve("tmp"))
        val workspace = Files.createDirectory(directory.resolve("workspace"))
        val source = "module Copies { String text = \"value\"; }"
        val uri = Files.writeString(workspace.resolve("Copies.x"), source).toUri().toString()
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
                "-Djava.io.tmpdir=$temporary",
                "-Dxtc.lsp.adapter=compiler",
                "-jar",
                jar.toString(),
            ).redirectError(stderr.toFile())
                .start()
        val executor = Executors.newVirtualThreadPerTaskExecutor()
        try {
            val launcher = LSPLauncher.createClientLauncher(client, process.inputStream, process.outputStream, executor) { it }
            launcher.startListening()
            val server = launcher.remoteProxy
            server
                .initialize(
                    InitializeParams().apply {
                        capabilities = ClientCapabilities()
                        workspaceFolders = listOf(WorkspaceFolder(workspace.toUri().toString(), "Copies"))
                    },
                ).get(20, SECONDS)
            server.initialized(InitializedParams())
            server.textDocumentService.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, source)))

            // a client without workspace/textDocumentContent opens String's declaration in a read-only copy
            val target =
                server.textDocumentService
                    .definition(DefinitionParams(TextDocumentIdentifier(uri), Position(0, source.indexOf("String"))))
                    .get(60, SECONDS)
                    .left
                    .single()
            val copy = Path.of(URI(target.uri))
            assertThat(copy).exists()
            assertThat(copy.startsWith(temporary.toRealPath())).describedAs("$copy is in $temporary").isTrue()

            if (shutdown) {
                server.shutdown().get(20, SECONDS)
                server.exit()
            } else {
                process.outputStream.close()
            }
            assertThat(process.waitFor(10, SECONDS)).describedAs("The server exits\n${Files.readString(stderr)}").isTrue()
            assertThat(Files.list(temporary).use { entries -> entries.map { it.fileName.toString() }.toList() })
                .noneMatch { it.startsWith("xtc-library-sources-") }
        } finally {
            if (process.isAlive) process.destroyForcibly()
            check(process.waitFor(10, SECONDS)) { "Could not reap test server ${process.pid()}" }
            process.outputStream.close()
            process.inputStream.close()
            executor.shutdownNow()
        }
    }

    private val client =
        object : LanguageClient {
            override fun publishDiagnostics(params: PublishDiagnosticsParams) = Unit

            override fun telemetryEvent(value: Any?) = Unit

            override fun showMessage(params: MessageParams) = Unit

            override fun showMessageRequest(params: ShowMessageRequestParams): CompletableFuture<MessageActionItem> =
                CompletableFuture.completedFuture(null)

            override fun logMessage(params: MessageParams) = Unit

            override fun configuration(params: ConfigurationParams): CompletableFuture<List<Any>> =
                CompletableFuture.completedFuture(params.items.map { emptyMap<String, Any>() })
        }
}
