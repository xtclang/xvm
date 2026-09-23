package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.toDependency
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit.SECONDS

class XdkProjectServerTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `unsaved dependency edits recompile an unchanged consumer and closing restores disk`() {
        val library = file("Library", LIBRARY)
        val consumer = file("Consumer", CONSUMER)
        Session(library, consumer).use { session ->
            session.open(consumer, CONSUMER, 7)
            session.expect(consumer, 0, 7, false)
            var mark = session.published.size
            session.open(library, INCOMPATIBLE, 2)
            session.expect(consumer, mark, 7, true)
            session.expect(library, mark, 2, false)
            assertThat(library.readText()).isEqualTo(LIBRARY)
            mark = session.published.size
            session.change(library, LIBRARY, 3)
            session.expect(consumer, mark, 7, false)
            val target =
                session.documents
                    .definition(
                        DefinitionParams(TextDocumentIdentifier(consumer.toURI().toString()), Position(0, CONSUMER.indexOf("value"))),
                    ).get(20, SECONDS)
                    .left
                    .single()
            assertThat(target.uri).isEqualTo(library.toURI().toString())
            mark = session.published.size
            session.change(library, INCOMPATIBLE, 4)
            session.expect(consumer, mark, 7, true)
            mark = session.published.size
            session.closeDocument(library)
            session.expect(consumer, mark, 7, false)
            mark = session.published.size
            session.open(library, INCOMPATIBLE, 1)
            session.expect(consumer, mark, 7, true)
        }
    }

    @Test
    fun `failed dependency publishes its own diagnostics and blocks old consumer facts until correction`() {
        val library = file("Library", LIBRARY)
        val consumer = file("Consumer", CONSUMER)
        Session(library, consumer).use { session ->
            session.open(consumer, CONSUMER, 7)
            session.expect(consumer, 0, 7, false)
            var mark = session.published.size
            session.open(library, "module Library { MissingType broken; }", 2)
            session.expect(library, mark, 2, true)
            session.expect(consumer, mark, 7, true, "DEPENDENCY-FAILED")
            assertThat(session.adapter.findDefinition(consumer.toURI().toString(), 0, CONSUMER.indexOf("value"))).isNull()
            mark = session.published.size
            session.change(library, LIBRARY, 3)
            session.expect(consumer, mark, 7, false)
            session.expect(library, mark, 3, false)
        }
    }

    @Test
    fun `closed dependency files are rebuilt on filesystem events including deletion and restoration`() {
        val library = file("Library", LIBRARY)
        val consumer = file("Consumer", CONSUMER)
        Session(library, consumer).use { session ->
            session.open(consumer, CONSUMER, 7)
            session.expect(consumer, 0, 7, false)
            var mark = session.published.size
            library.writeText(INCOMPATIBLE)
            session.watched(library, FileChangeType.Changed)
            session.expect(consumer, mark, 7, true)
            mark = session.published.size
            assertThat(library.delete()).isTrue()
            session.watched(library, FileChangeType.Deleted)
            session.expect(library, mark, null, true, "SOURCE-UNAVAILABLE")
            session.expect(consumer, mark, 7, true, "DEPENDENCY-FAILED")
            mark = session.published.size
            library.writeText(LIBRARY)
            session.watched(library, FileChangeType.Created)
            session.expect(consumer, mark, 7, false)
            session.expect(library, mark, null, false)
        }
    }

    @Test
    fun `unsaved and removed dependency members update consumers and clear deleted source diagnostics`() {
        val library = file("Library", LIBRARY)
        val consumer = file("Consumer", CONSUMER)
        val member = directory.resolve("Library/Extra.x").toFile()
        Session(library, consumer).use { session ->
            session.open(consumer, CONSUMER, 7)
            session.expect(consumer, 0, 7, false)
            var mark = session.published.size
            session.open(member, "class Extra { MissingType broken; }", 2)
            session.expect(member, mark, 2, true)
            session.expect(consumer, mark, 7, true, "DEPENDENCY-FAILED")
            assertThat(member.exists()).isFalse()
            mark = session.published.size
            session.closeDocument(member)
            session.expect(consumer, mark, 7, false)
            member.parentFile.mkdirs()
            member.writeText("class Extra { MissingType broken; }")
            mark = session.published.size
            session.watched(member, FileChangeType.Created)
            session.expect(member, mark, null, true)
            session.expect(consumer, mark, 7, true)
            mark = session.published.size
            assertThat(member.delete()).isTrue()
            session.watched(member, FileChangeType.Deleted)
            session.expect(consumer, mark, 7, false)
            session.expect(member, mark, null, false)
        }
    }

    @Test
    fun `transitive source changes rebuild consumers while an unrelated session stays available`() {
        val library = file("Library", LIBRARY)
        val bridge = file("Bridge", "module Bridge { package lib import Library; static Int value()=lib.value(); }")
        val consumer = file("Consumer", CONSUMER.replace("import Library", "import Bridge"))
        val unrelated = file("Unrelated", "module Unrelated { Int value=1; }")
        Session(library, consumer, bridge).use { session ->
            session.open(consumer, consumer.readText(), 7)
            session.expect(consumer, 0, 7, false)
            session.open(unrelated, unrelated.readText(), 1)
            session.expect(unrelated, 0, 1, false)
            val cached = session.adapter.getCachedResult(unrelated.toURI().toString())
            var mark = session.published.size
            session.open(library, INCOMPATIBLE, 2)
            session.expect(bridge, mark, null, true)
            session.expect(consumer, mark, 7, true, "DEPENDENCY-FAILED")
            assertThat(session.adapter.getCachedResult(unrelated.toURI().toString())).isEqualTo(cached)
            assertThat(session.published.drop(mark)).noneMatch { it.uri == unrelated.toURI().toString() }
            mark = session.published.size
            session.change(library, LIBRARY, 3)
            session.expect(consumer, mark, 7, false)
            session.expect(bridge, mark, null, false)
        }
    }

    @Test
    fun `binary replacement rebuilds open source dependencies before their consumers`() {
        CompilerTestSupport.configure()

        fun artifact(type: String) =
            EmbeddingSupport
                .instance()
                .compileModule(
                    Source("module Binary { static $type value()=" + (if (type == "Int") "1" else "\"text\"") + "; }", "file:///Binary.x"),
                    null,
                    ErrorList(),
                ).toDependency()
        val first = artifact("Int")
        val incompatible = artifact("String")
        val library = file("Library", "module Library { package base import Binary; static Int value()=base.value(); }")
        val consumer = file("Consumer", CONSUMER)
        Session(library, consumer).use { session ->
            session.server.replaceCompilerDependencies(listOf(first))
            session.open(consumer, CONSUMER, 7)
            session.expect(consumer, 0, 7, false)
            session.open(library, library.readText(), 2)
            session.expect(library, 0, 2, false)
            var mark = session.published.size
            session.server.replaceCompilerDependencies(listOf(incompatible))
            session.expect(library, mark, 2, true)
            session.expect(consumer, mark, 7, true, "DEPENDENCY-FAILED")
            mark = session.published.size
            session.server.replaceCompilerDependencies(listOf(first))
            session.expect(library, mark, 2, false)
            session.expect(consumer, mark, 7, false)
        }
    }

    @Test
    fun `closing one consumer does not clear shared dependency errors owned by another`() {
        val library = file("Library", "module Library { MissingType broken; }")
        val consumer = file("Consumer", CONSUMER)
        val other = file("Other", CONSUMER.replace("module Consumer", "module Other"))
        Session(library, consumer).use { session ->
            session.server.replaceCompilerSourceModules(
                listOf(
                    XdkSourceModule("Library", library.toURI().toString()),
                    XdkSourceModule("Consumer", consumer.toURI().toString(), setOf("Library")),
                    XdkSourceModule("Other", other.toURI().toString(), setOf("Library")),
                ),
            )
            session.open(consumer, consumer.readText(), 7)
            session.expect(consumer, 0, 7, true)
            session.open(other, other.readText(), 1)
            session.expect(other, 0, 1, true)
            val mark = session.published.size
            session.closeDocument(consumer)
            assertThat(session.published.drop(mark)).noneMatch { it.uri == library.toURI().toString() && it.diagnostics.isEmpty() }
        }
    }

    private fun file(
        name: String,
        text: String,
    ): File {
        directory = directory.toRealPath()
        return directory.resolve("$name.x").toFile().also { it.writeText(text) }
    }

    private class Session(
        library: File,
        consumer: File,
        bridge: File? = null,
    ) : AutoCloseable {
        val adapter = XdkAdapter()
        val server = XtcLanguageServer(adapter)
        val documents = server.textDocumentService
        val published = CopyOnWriteArrayList<PublishDiagnosticsParams>()

        init {
            val client = mock(LanguageClient::class.java)
            doAnswer { call ->
                published.add(call.getArgument(0))
                null
            }.`when`(client).publishDiagnostics(any())
            server.connect(client)
            server.replaceCompilerSourceModules(
                buildList {
                    add(XdkSourceModule("Library", library.toURI().toString()))
                    bridge?.let { add(XdkSourceModule("Bridge", it.toURI().toString(), setOf("Library"))) }
                    add(XdkSourceModule("Consumer", consumer.toURI().toString(), setOf(if (bridge == null) "Library" else "Bridge")))
                },
            )
        }

        fun open(
            file: File,
            text: String,
            version: Int,
        ) = documents.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(file.toURI().toString(), "xtc", version, text)),
        )

        fun change(
            file: File,
            text: String,
            version: Int,
        ) = documents.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(file.toURI().toString(), version),
                listOf(TextDocumentContentChangeEvent(text)),
            ),
        )

        fun closeDocument(file: File) = documents.didClose(DidCloseTextDocumentParams(TextDocumentIdentifier(file.toURI().toString())))

        fun watched(
            file: File,
            kind: FileChangeType,
        ) = server.workspaceService.didChangeWatchedFiles(
            DidChangeWatchedFilesParams(listOf(FileEvent(file.toURI().toString(), kind))),
        )

        fun expect(
            file: File,
            after: Int,
            version: Int?,
            errors: Boolean,
            code: String? = null,
        ) {
            await().atMost(30, SECONDS).untilAsserted {
                val last = published.drop(after).lastOrNull { it.uri == file.toURI().toString() && it.version == version }
                assertThat(last).describedAs(published.drop(after).toString()).isNotNull()
                assertThat(last!!.diagnostics.isNotEmpty()).describedAs(last.toString()).isEqualTo(errors)
                assertThat(last.diagnostics).noneMatch { it.code?.left in setOf("ANALYSIS-FAILED", "EMB-5") }
                if (code != null) assertThat(last.diagnostics).anyMatch { it.code?.left == code }
            }
        }

        override fun close() {
            server.shutdown().get(10, SECONDS)
        }
    }

    private companion object {
        const val LIBRARY = "module Library { static Int value()=1; }"
        const val INCOMPATIBLE = "module Library { static String value()=\"text\"; }"
        const val CONSUMER = "module Consumer { package lib import Library; Int run()=lib.value(); }"
    }
}
