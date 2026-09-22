package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.TypeHierarchyPrepareParams
import org.eclipse.lsp4j.TypeHierarchySubtypesParams
import org.eclipse.lsp4j.TypeHierarchySupertypesParams
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
            session.verifySemantics(VALID, "value", "Int")

            session.server.textDocumentService.didClose(DidCloseTextDocumentParams(TextDocumentIdentifier(URI)))
            assertThat(session.diagnosticsAt(101).diagnostics).isEmpty()
            session.open(REOPENED)
            assertThat(session.diagnosticsAt(1).diagnostics).isEmpty()
            session.verifySemantics(REOPENED, "label", "String")

            for (version in 2..20) session.change(BROKEN, version)
            session.shutdownAndExit()
        }
    }

    @Test
    fun `packaged module diagnostics cross file definitions and hierarchy round trip over stdio`() {
        directory = directory.toRealPath()
        val root = directory.resolve("Multi.x").toFile()
        val member = directory.resolve("Multi/Child.x").toFile()
        val source = "module Multi { class Base {} Child make() = new Child(); }"
        root.writeText(source)
        member.parentFile.mkdirs()
        member.writeText("class Child extends Base {}")
        Session(packagedJar(), directory).use { session ->
            session.initialize()
            val rootId = TextDocumentIdentifier(root.toURI().toString())
            val memberId = TextDocumentIdentifier(member.toURI().toString())
            val documents = session.server.textDocumentService
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(rootId.uri, "xtc", 1, source)))
            assertThat(session.diagnosticsFor(memberId.uri, null).diagnostics).isEmpty()
            val definition =
                session
                    .await(
                        documents.definition(DefinitionParams(rootId, Position(0, source.indexOf("Child")))),
                    ).left
                    .single()
            assertThat(definition.uri).isEqualTo(memberId.uri)
            val base =
                session
                    .await(
                        documents.prepareTypeHierarchy(TypeHierarchyPrepareParams(rootId, Position(0, source.indexOf("Base")))),
                    ).single()
            val child = session.await(documents.typeHierarchySubtypes(TypeHierarchySubtypesParams(base))).single()
            assertThat(child.uri).isEqualTo(memberId.uri)
            assertThat(
                session.await(documents.typeHierarchySupertypes(TypeHierarchySupertypesParams(child))).single().uri,
            ).isEqualTo(rootId.uri)
            documents.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(memberId.uri, "xtc", 7, "class Child extends Base { MissingType absent; }")),
            )
            assertThat(session.diagnosticsFor(memberId.uri, 7).diagnostics).anyMatch { it.code.left == "COMPILER-38" }
            documents.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(memberId.uri, 8),
                    listOf(TextDocumentContentChangeEvent(member.readText())),
                ),
            )
            assertThat(session.diagnosticsFor(memberId.uri, 8).diagnostics).isEmpty()
            assertThat(session.await(documents.typeHierarchySubtypes(TypeHierarchySubtypesParams(base)))).isEmpty()
            session.shutdownAndExit()
        }
    }

    @Test
    fun `an invalid packaged backend setting fails startup explicitly`() {
        val invalid = directory.resolve("invalid-setting.jar")
        JarFile(packagedJar().toFile()).use { original ->
            JarOutputStream(Files.newOutputStream(invalid)).use { output ->
                for (entry in original.entries()) {
                    output.putNextEntry(JarEntry(entry.name))
                    original.getInputStream(entry).use { input ->
                        if (entry.name == "lsp-version.properties") {
                            Properties().apply {
                                load(input)
                                setProperty("lsp.adapter", "compielr")
                                store(output, null)
                            }
                        } else {
                            input.copyTo(output)
                        }
                    }
                    output.closeEntry()
                }
            }
        }
        Session(invalid, directory).use { it.expectExit(1) }
        assertThat(Files.readString(directory.resolve("stderr.log")))
            .contains("Unknown lsp.adapter 'compielr'; expected treesitter, compiler or mock")
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
            assertThat(initialized.capabilities.hoverProvider.left).isTrue()
            assertThat(initialized.capabilities.referencesProvider.left).isTrue()
            assertThat(initialized.capabilities.documentHighlightProvider.left).isTrue()
            assertThat(initialized.capabilities.completionProvider).isNull()
            assertThat(initialized.capabilities.renameProvider).isNull()
            assertThat(initialized.capabilities.signatureHelpProvider).isNull()
            server.initialized(InitializedParams())
        }

        fun verifySemantics(
            content: String,
            name: String,
            type: String,
        ) {
            val document = TextDocumentIdentifier(URI)
            val declaration = content.indexOf(name)
            val reference = content.lastIndexOf(name)
            val cursor = Position(0, reference)
            val declaredRange = Range(Position(0, declaration), Position(0, declaration + name.length))
            val usedRange = Range(cursor, Position(0, reference + name.length))
            val service = server.textDocumentService
            val hover = await(service.hover(HoverParams(document, cursor)))
            assertThat(hover.contents.right.value).contains(type)
            val definitions = await(service.definition(DefinitionParams(document, cursor))).left
            assertThat(definitions.map { it.uri }).containsExactly(URI)
            assertThat(definitions.map { it.range }).containsExactly(declaredRange)
            val references = await(service.references(ReferenceParams(document, cursor, ReferenceContext(true))))
            assertThat(references.map { it.uri }).containsOnly(URI)
            assertThat(references.map { it.range }).containsExactly(declaredRange, usedRange)
            val highlights = await(service.documentHighlight(DocumentHighlightParams(document, cursor)))
            assertThat(highlights.map { it.range }).containsExactly(declaredRange, usedRange)
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

        fun diagnosticsFor(
            uri: String,
            version: Int?,
        ): PublishDiagnosticsParams {
            val deadline = System.nanoTime() + SECONDS.toNanos(30)
            while (true) {
                val remaining = deadline - System.nanoTime()
                val publication = if (remaining > 0) published.poll(remaining, NANOSECONDS) else null
                checkNotNull(publication) { "No diagnostics for $uri version $version. ${log()}" }
                if (publication.uri == uri && publication.version == version) return publication
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
        const val REOPENED = "module Stdio { String run() { String label = \"ok\"; return label; } }"
        const val BROKEN = "module Stdio { Int run() { return missing; } }"
        const val BOOTSTRAP = "org/xvm/lsp/xdk/javatools_turtle.xtc"
    }
}
