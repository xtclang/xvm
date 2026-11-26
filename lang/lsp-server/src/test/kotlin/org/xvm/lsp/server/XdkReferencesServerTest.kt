package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.hookedAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.awaitUntil
import org.xvm.lsp.awaited
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Checks Find References at the server boundary when a change that the request is not about ends the graph query it
 * waits on: the client did not cancel the request, so it is answered from the sources as they are then.
 */
class XdkReferencesServerTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `the editor's release of a document outside the graph answers Find References from the sources as they are`() {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        // as X251 writes them: a root, which the editor opens, and a closed consumer of it; and the document of an
        // earlier case, outside the graph, which the editor still has open
        val files = SOURCES.mapValues { (name, text) -> directory.resolve("$name.x").toFile().apply { writeText(text) } }
        val hold = AtomicBoolean()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val adapter =
            hookedAdapter {
                if (hold.compareAndSet(true, false)) {
                    entered.countDown()
                    check(release.awaited())
                }
            }
        val server = XtcLanguageServer(adapter)
        server.connect(mock(LanguageClient::class.java))
        try {
            server
                .initialize(
                    InitializeParams().apply {
                        capabilities = ClientCapabilities().apply { textDocument = editorInitializeParams().capabilities.textDocument }
                    },
                ).awaited()
            val uris = files.mapValues { it.value.toURI().toString() }
            server.replaceCompilerSourceModules(
                listOf(
                    XdkSourceModule("Node0", uris.getValue("Node0")),
                    XdkSourceModule("Consumer", uris.getValue("Consumer"), setOf("Node0")),
                ),
            )
            val documents = server.textDocumentService
            listOf("Node0", "Other").forEach { name ->
                documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uris.getValue(name), "xtc", 1, SOURCES.getValue(name))))
                documents.documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uris.getValue(name)))).awaited()
            }
            // the graph's diagnostics, which an editor pulls, compile the closed consumer first, and the compile holds
            hold.set(true)
            adapter.workspaceDiagnosticsAsync()
            assertThat(entered.awaited()).isTrue()
            val at = Position(1, SOURCES.getValue("Node0").lines()[1].indexOf("Box"))
            val references =
                documents.references(
                    ReferenceParams(TextDocumentIdentifier(uris.getValue("Node0")), at, ReferenceContext(true)),
                )
            awaitUntil("Find References queues its graph query behind the held compile") {
                @Suppress("UNCHECKED_CAST")
                (adapter.compilerQueueSnapshot()["queuedJobs"] as List<String>).any { "project-REFERENCES" in it }
            }
            // then the editor releases the other document, as VS Code releases one that no editor shows some minutes after
            // it opened it: nothing of the graph changes
            documents.didClose(DidCloseTextDocumentParams(TextDocumentIdentifier(uris.getValue("Other"))))
            release.countDown()
            assertThat(references.awaited().map { Path.of(URI(it.uri)) })
                .containsExactlyInAnyOrder(files.getValue("Node0").toPath(), files.getValue("Consumer").toPath())
        } finally {
            release.countDown()
            server.shutdown().awaited()
        }
    }

    private companion object {
        val SOURCES =
            mapOf(
                "Node0" to "module Node0 {\n    class Box {}\n}\n",
                "Consumer" to "module Consumer {\n    package lib import Node0;\n    class Child extends lib.Box {}\n}\n",
                "Other" to "module Other {\n    class Extract {}\n}\n",
            )
    }
}
