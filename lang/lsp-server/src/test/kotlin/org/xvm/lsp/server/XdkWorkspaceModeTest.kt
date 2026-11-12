package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.xvm.lsp.Heavy
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.awaitAsserted
import org.xvm.lsp.awaited
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A workspace that holds the XDK's own sources, over copies of the repository's lib_ecstasy and lib_json: json compiles
 * against the workspace's ecstasy, unsaved edits included, its definitions go to the workspace's sources rather than the
 * bundled copies, the status names the libraries the workspace replaces, and embedded builds are refused. Heavy: each
 * case compiles ecstasy from source.
 */
@Heavy
class XdkWorkspaceModeTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `json compiles against the workspace's ecstasy, and its definitions go to the workspace's sources`() {
        val root = File(requireNotNull(System.getProperty("xtc.composite.root")) { "xtc.composite.root is not set" })
        val workspace = directory.toRealPath().toFile()
        // each library's sources and the resources its file literals name
        listOf("lib_ecstasy", "lib_json").forEach { library ->
            root.resolve("$library/src/main").copyRecursively(workspace.resolve("$library/src/main"))
        }
        val string = workspace.resolve("lib_ecstasy/src/main/x/ecstasy/text/String.x")
        val input = workspace.resolve("lib_json/src/main/x/json/DocInput.x")
        val published = CopyOnWriteArrayList<PublishDiagnosticsParams>()
        val client = mock(LanguageClient::class.java)
        doAnswer { call ->
            published.add(call.getArgument(0))
            null
        }.`when`(client).publishDiagnostics(any())

        fun withMember(
            text: String,
            member: String,
        ) = text.substring(0, text.trimEnd().lastIndexOf('}')) + "    $member\n}\n"

        XtcLanguageServer(XdkAdapter()).use { server ->
            server.connect(client)
            server
                .initialize(
                    editorInitializeParams().apply { workspaceFolders = listOf(WorkspaceFolder(workspace.toURI().toString(), "xdk")) },
                ).awaited()

            fun open(
                file: File,
                text: String,
            ) = server.textDocumentService.didOpen(DidOpenTextDocumentParams(TextDocumentItem(file.toURI().toString(), "ecstasy", 1, text)))

            // a method that only the workspace's String has, in an edit not saved
            open(string, withMember(string.readText(), "Int workspaceOnly() = 1;"))
            val use = withMember(input.readText(), "void probe() { Int n = \"x\".workspaceOnly(); }")
            open(input, use)
            awaitAsserted {
                val report = published.lastOrNull { it.uri == input.toURI().toString() }
                assertThat(report).describedAs(published.toString()).isNotNull()
                assertThat(report!!.diagnostics).isEmpty()
            }

            // Int, which the bundled ecstasy has too, is the workspace's
            val line = use.lines().indexOfFirst { "workspaceOnly()" in it }
            val at = Position(line, use.lines()[line].indexOf("Int"))
            val target =
                server.textDocumentService
                    .definition(DefinitionParams(TextDocumentIdentifier(input.toURI().toString()), at))
                    .awaited()
            val uri = if (target.isLeft) target.left.single().uri else target.right.single().targetUri
            val int64 = workspace.resolve("lib_ecstasy/src/main/x/ecstasy/numbers/Int64.x")
            assertThat(File(URI(uri)).canonicalFile).isEqualTo(int64.canonicalFile)

            assertThat(server.languageServiceStatus().awaited()["xdkWorkspace"] as List<*>)
                .contains("ecstasy.xtclang.org", "json.xtclang.org")
                .doesNotContain("mack.xtclang.org")
            assertThatThrownBy { server.build(BuildParams()).awaited() }.hasStackTraceContaining("holds the XDK's own sources")
        }
    }
}
