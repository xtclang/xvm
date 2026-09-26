package org.xvm.lsp.server

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.Awaitility.await
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS

class CompilerConfigurationTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `explicit null restores automatic discovery through JSON and host maps`() {
        assertThat(CompilerConfiguration.automatic(mapOf("sourceModules" to null))).isTrue()
        val settings = JsonParser.parseString("""{"xtc":{"compiler":{"sourceModules":null}}}""")
        assertThat(CompilerConfiguration.automatic(CompilerConfiguration.changed(settings))).isTrue()
        assertThat(CompilerConfiguration.automatic(emptyMap<String, Any>())).isFalse()
    }

    @Test
    fun `strict configuration accepts maps and wire JSON while preserving absent versus empty`() {
        val workspace = directory.toUri().toString()
        for (raw in listOf(CONFIG, Gson().toJsonTree(CONFIG))) {
            val modules = requireNotNull(CompilerConfiguration.modules(raw, listOf(workspace)))
            assertThat(modules.map { it.name }).containsExactly("Library", "Consumer")
            assertThat(modules.last().dependencies).containsExactly("Library")
            assertThat(modules.first().uri).isEqualTo(
                directory
                    .resolve("Library.x")
                    .toFile()
                    .canonicalFile
                    .toURI()
                    .toString(),
            )
        }
        assertThat(CompilerConfiguration.modules(null, emptyList())).isNull()
        assertThat(CompilerConfiguration.modules(emptyMap<String, Any>(), emptyList())).isNull()
        assertThat(CompilerConfiguration.modules(mapOf("sourceModules" to emptyList<Any>()), emptyList())).isEmpty()
        for (raw in listOf(
            mapOf("sourceModules" to 1),
            mapOf("sourceModules" to listOf(1)),
            mapOf("sourceModules" to listOf(mapOf("name" to 1, "uri" to "Library.x"))),
            mapOf("sourceModules" to listOf(mapOf("name" to "Library", "uri" to "https://example.org/Library.x"))),
        )) {
            assertThatThrownBy { CompilerConfiguration.modules(raw, listOf(workspace)) }.isInstanceOf(IllegalArgumentException::class.java)
        }
        assertThatThrownBy { CompilerConfiguration.modules(CONFIG, listOf(workspace, "file:///other/")) }
            .hasMessageContaining("exactly one workspace")
    }

    @Test
    fun `initialization and settings updates rebuild unchanged editor documents`() {
        Session().use { session ->
            session.open()
            session.expect(false)
            session.configure(mapOf("sourceModules" to emptyList<Any>()))
            session.expect(true)
            session.configure(CONFIG)
            session.expect(false)
            val before = session.adapter.getCachedResult(session.uri)
            session.configure(CONFIG)
            assertThat(session.adapter.getCachedResult(session.uri)).isEqualTo(before)
            session.server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(
                    TextDocumentItem(
                        session.libraryUri,
                        "xtc",
                        1,
                        "module Library { static String value()=\"text\"; }",
                    ),
                ),
            )
            session.expect(true)
            session.server.textDocumentService.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(session.libraryUri, 2),
                    listOf(TextDocumentContentChangeEvent(LIBRARY)),
                ),
            )
            session.expect(false)
        }
    }

    @Test
    fun `cyclic and malformed updates report a problem without retiring the working graph`() {
        Session().use { session ->
            session.open()
            session.expect(false)
            session.configure(
                mapOf(
                    "sourceModules" to
                        listOf(
                            mapOf("name" to "Library", "uri" to "Library.x", "dependencies" to listOf("Consumer")),
                            mapOf("name" to "Consumer", "uri" to "Consumer.x", "dependencies" to listOf("Library")),
                        ),
                ),
            )
            session.configure(mapOf("sourceModules" to "bad"))
            assertThat(session.messages).hasSize(2)
            assertThat(session.messages.first().message).contains("Cyclic", "retained")
            assertThat(session.adapter.compile(session.uri, CONSUMER).success).isTrue()
        }
    }

    @Test
    fun `late configuration replies and replies after shutdown cannot replace newer settings`() {
        Session(pull = true).use { session ->
            session.open()
            session.expect(false)
            session.server.initialized(InitializedParams())
            val old = requireNotNull(session.requests.poll(10, SECONDS))
            session.server.workspaceService.didChangeConfiguration(DidChangeConfigurationParams(emptyMap<String, Any>()))
            val latest = requireNotNull(session.requests.poll(10, SECONDS))
            latest.complete(listOf(mapOf("sourceModules" to emptyList<Any>())))
            session.expect(true)
            old.complete(listOf(CONFIG))
            assertThat(session.adapter.compile(session.uri, CONSUMER).success).isFalse()
            session.server.workspaceService.didChangeConfiguration(DidChangeConfigurationParams(emptyMap<String, Any>()))
            val superseded = requireNotNull(session.requests.poll(10, SECONDS))
            session.server.workspaceService.didChangeConfiguration(DidChangeConfigurationParams(mapOf("xtc" to "invalid")))
            superseded.complete(listOf(CONFIG))
            assertThat(session.adapter.compile(session.uri, CONSUMER).success).isFalse()
            assertThat(session.messages).hasSize(1)
            session.server.workspaceService.didChangeConfiguration(DidChangeConfigurationParams(emptyMap<String, Any>()))
            val closing = requireNotNull(session.requests.poll(10, SECONDS))
            session.close()
            closing.complete(listOf(CONFIG))
            assertThat(session.messages).hasSize(1)
        }
    }

    private inner class Session(
        pull: Boolean = false,
    ) : AutoCloseable {
        val adapter = XdkAdapter()
        val server = XtcLanguageServer(adapter)
        val published = CopyOnWriteArrayList<PublishDiagnosticsParams>()
        val messages = CopyOnWriteArrayList<MessageParams>()
        val requests = LinkedBlockingQueue<CompletableFuture<List<Any>>>()
        val uri =
            directory
                .resolve("Consumer.x")
                .toFile()
                .canonicalFile
                .toURI()
                .toString()
        val libraryUri =
            directory
                .resolve("Library.x")
                .toFile()
                .canonicalFile
                .toURI()
                .toString()

        init {
            directory.resolve("Library.x").toFile().writeText(LIBRARY)
            directory.resolve("Consumer.x").toFile().writeText(CONSUMER)
            val client = mock(LanguageClient::class.java)
            doAnswer {
                published.add(it.getArgument(0))
                null
            }.`when`(client).publishDiagnostics(any())
            doAnswer {
                messages.add(it.getArgument(0))
                null
            }.`when`(client).showMessage(any())
            doAnswer { call ->
                if (call
                        .getArgument<ConfigurationParams>(0)
                        .items
                        .single()
                        .section == CompilerConfiguration.SECTION
                ) {
                    CompletableFuture<List<Any>>().also(requests::add)
                } else {
                    CompletableFuture.completedFuture(listOf(emptyMap<String, Any>()))
                }
            }.`when`(client).configuration(any())
            server.connect(client)
            server
                .initialize(
                    InitializeParams().apply {
                        workspaceFolders = listOf(WorkspaceFolder(directory.toUri().toString(), "test"))
                        initializationOptions = mapOf(CompilerConfiguration.INITIALIZATION_KEY to CONFIG)
                        capabilities =
                            ClientCapabilities().apply { workspace = WorkspaceClientCapabilities().apply { configuration = pull } }
                    },
                ).get(20, SECONDS)
        }

        fun open() = server.textDocumentService.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 7, CONSUMER)))

        fun configure(config: Any) =
            server.workspaceService.didChangeConfiguration(
                DidChangeConfigurationParams(
                    mapOf("xtc" to mapOf("compiler" to config)),
                ),
            )

        fun expect(errors: Boolean) {
            await().atMost(30, SECONDS).untilAsserted {
                val last = published.lastOrNull { it.uri == uri }
                assertThat(last).isNotNull()
                assertThat(last!!.version).isEqualTo(7)
                assertThat(last.diagnostics.isNotEmpty()).isEqualTo(errors)
            }
        }

        override fun close() {
            server.shutdown().get(20, SECONDS)
        }
    }

    private companion object {
        const val LIBRARY = "module Library { static Int value()=1; }"
        const val CONSUMER = "module Consumer { package lib import Library; Int run()=lib.value(); }"
        val CONFIG =
            mapOf(
                "sourceModules" to
                    listOf(
                        mapOf("name" to "Library", "uri" to "Library.x"),
                        mapOf("name" to "Consumer", "uri" to "Consumer.x", "dependencies" to listOf("Library")),
                    ),
            )
    }
}
