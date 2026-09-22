package org.xvm.lsp.server

import com.google.gson.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.TypeHierarchyPrepareParams
import org.eclipse.lsp4j.TypeHierarchySubtypesParams
import org.eclipse.lsp4j.TypeHierarchySupertypesParams
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit.SECONDS

class XdkModuleServerTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `member diagnostics use their own versions and sibling edits refresh them`() {
        val (root, member) = fixture()
        Session().use { session ->
            session.open(root, root.readText(), 1)
            session.expect(member, 0, null, false)
            var mark = session.published.size
            session.open(member, member.readText(), 7)
            session.expect(member, mark, 7, false)
            mark = session.published.size
            session.change(root, root.readText().replace("Base", "Renamed"), 2)
            session.expect(member, mark, 7, true)
            session.expect(root, mark, 2, false)
            mark = session.published.size
            session.change(root, root.readText(), 3)
            session.expect(member, mark, 7, false)
            mark = session.published.size
            session.change(member, "class Child extends Base { MissingType absent; }", 8)
            session.expect(member, mark, 8, true)
            mark = session.published.size
            session.closeDocument(member)
            session.expect(member, mark, null, false)
            mark = session.published.size
            session.closeDocument(root)
            session.expect(root, mark, 3, false)
            session.expect(member, mark, null, false)
        }
    }

    @Test
    fun `file creation and deletion refresh closed module members and clear removed diagnostics`() {
        val (root, member) = fixture()
        Session().use { session ->
            session.open(root, root.readText(), 1)
            session.expect(member, 0, null, false)
            val added = directory.resolve("Project/Added.x").toFile()
            added.writeText("class Added { MissingType absent; }")
            var mark = session.published.size
            session.watched(added, FileChangeType.Created)
            session.expect(added, mark, null, true)
            mark = session.published.size
            assertThat(added.delete()).isTrue()
            session.watched(added, FileChangeType.Deleted)
            session.expect(added, mark, null, false)
        }
    }

    @Test
    fun `hierarchy data survives protocol conversion and obsolete items cannot select new declarations`() {
        val (root, member) = fixture()
        Session().use { session ->
            val capabilities =
                session.server
                    .initialize(InitializeParams())
                    .get(10, SECONDS)
                    .capabilities
            assertThat(capabilities.typeHierarchyProvider.left).isTrue()
            session.open(root, root.readText(), 1)
            val hierarchy =
                session.documents
                    .prepareTypeHierarchy(
                        TypeHierarchyPrepareParams(
                            TextDocumentIdentifier(root.toURI().toString()),
                            Position(0, root.readText().indexOf("Base")),
                        ),
                    ).get(30, SECONDS)
                    .single()
            hierarchy.data = JsonPrimitive(hierarchy.data as String)
            val child =
                session.documents
                    .typeHierarchySubtypes(TypeHierarchySubtypesParams(hierarchy))
                    .get(10, SECONDS)
                    .single()
            assertThat(child.uri).isEqualTo(member.toURI().toString())
            child.data = JsonPrimitive(child.data as String)
            val base =
                session.documents
                    .typeHierarchySupertypes(TypeHierarchySupertypesParams(child))
                    .get(10, SECONDS)
                    .single()
            assertThat(base.uri).isEqualTo(root.toURI().toString())
            session.change(root, root.readText(), 2)
            assertThat(session.documents.typeHierarchySubtypes(TypeHierarchySubtypesParams(hierarchy)).get(30, SECONDS)).isEmpty()
        }
    }

    private fun fixture(): Pair<File, File> {
        directory = directory.toRealPath()
        val root = directory.resolve("Project.x").toFile()
        root.writeText("module Project { class Base {} }")
        val member = directory.resolve("Project/Child.x").toFile()
        member.parentFile.mkdirs()
        member.writeText("class Child extends Base {}")
        return root to member
    }

    private class Session : AutoCloseable {
        val server = XtcLanguageServer(XdkAdapter())
        val documents = server.textDocumentService
        val published = CopyOnWriteArrayList<PublishDiagnosticsParams>()

        init {
            val client = mock(LanguageClient::class.java)
            doAnswer { call ->
                published.add(call.getArgument(0))
                null
            }.`when`(client).publishDiagnostics(any())
            server.connect(client)
        }

        fun open(
            file: File,
            text: String,
            version: Int,
        ) = documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(file.toURI().toString(), "xtc", version, text)))

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
        ) = server.workspaceService.didChangeWatchedFiles(DidChangeWatchedFilesParams(listOf(FileEvent(file.toURI().toString(), kind))))

        fun expect(
            file: File,
            after: Int,
            version: Int?,
            errors: Boolean,
        ) {
            await().atMost(30, SECONDS).untilAsserted {
                val matches = published.drop(after).filter { it.uri == file.toURI().toString() && it.version == version }
                assertThat(matches).isNotEmpty()
                assertThat(matches.last().diagnostics.isNotEmpty()).describedAs("Publications: %s", published.drop(after)).isEqualTo(errors)
                assertThat(matches.last().diagnostics).noneMatch { it.code?.left == "ANALYSIS-FAILED" || it.code?.left == "EMB-5" }
            }
        }

        override fun close() {
            server.shutdown().get(10, SECONDS)
        }
    }
}
