package org.xvm.lsp.server

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentEdit
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEditCapabilities
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.slf4j.LoggerFactory
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
 * against the workspace's ecstasy, unsaved edits included, its definitions go to the workspace's sources rather than
 * the bundled copies, the status names the libraries the workspace replaces, and embedded builds are refused; and a
 * rename of a method of the workspace's ecstasy is proven over both and a module that depends on json, edits json's
 * call too, one of json's compiles json alone, as what depends on json writes neither name nor uses a type whose
 * members change, and an edit that leaves json's API as it was compiles json alone. Heavy: each case compiles ecstasy
 * from source, and a rename's proof compiles it again after the edit, which needs a 2 GB heap
 * (XdkLibrarySources.PROOF_HEAP_BYTES).
 */
@Heavy
class XdkRepositoryWorkspaceTest {
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

            assertThat(server.languageServiceStatus().awaited()["replacedLibraries"] as List<*>)
                .contains("ecstasy.xtclang.org", "json.xtclang.org")
                .doesNotContain("mack.xtclang.org")
            assertThatThrownBy { server.build(BuildParams()).awaited() }.hasStackTraceContaining("own modules replace bundled libraries")
        }
    }

    @Test
    fun `a rename of a method of the workspace's ecstasy edits json's call of it, and one of json's compiles json alone`() {
        val root = File(requireNotNull(System.getProperty("xtc.composite.root")) { "xtc.composite.root is not set" })
        val workspace = directory.toRealPath().toFile()
        listOf("lib_ecstasy", "lib_json").forEach { library ->
            root.resolve("$library/src/main").copyRecursively(workspace.resolve("$library/src/main"))
        }
        val string = workspace.resolve("lib_ecstasy/src/main/x/ecstasy/text/String.x")
        val input = workspace.resolve("lib_json/src/main/x/json/DocInput.x")

        fun withMember(
            text: String,
            member: String,
        ) = text.substring(0, text.trimEnd().lastIndexOf('}')) + "    $member\n}\n"
        // a method only the workspace's String has, which json calls: a rename that the bundled ecstasy would refuse
        string.writeText(withMember(string.readText(), "Int workspaceOnly() = 1;"))
        input.writeText(withMember(input.readText(), "void probe() { Int n = \"x\".workspaceOnly(); }"))
        // a module that depends on json, as the repository's web and xenia do
        workspace.resolve("jsonUse.x").writeText("module jsonUse {\n    package json import json.xtclang.org;\n}\n")
        val published = CopyOnWriteArrayList<PublishDiagnosticsParams>()
        val client = mock(LanguageClient::class.java)
        doAnswer { call ->
            published.add(call.getArgument(0))
            null
        }.`when`(client).publishDiagnostics(any())

        val lines =
            logged {
                XtcLanguageServer(XdkAdapter()).use { server ->
                    server.connect(client)
                    server
                        .initialize(
                            editorInitializeParams().apply {
                                workspaceFolders = listOf(WorkspaceFolder(workspace.toURI().toString(), "xdk"))
                                // a rename's edit is versioned, which a client takes as document changes, as both editors do
                                capabilities.workspace.workspaceEdit = WorkspaceEditCapabilities().apply { documentChanges = true }
                            },
                        ).awaited()
                    listOf(string, input).forEach {
                        server.textDocumentService.didOpen(
                            DidOpenTextDocumentParams(TextDocumentItem(it.toURI().toString(), "ecstasy", 1, it.readText())),
                        )
                    }
                    awaitAsserted {
                        val report = published.lastOrNull { it.uri == input.toURI().toString() }
                        assertThat(report).describedAs(published.toString()).isNotNull()
                        assertThat(report!!.diagnostics).isEmpty()
                    }

                    // as an editor renames: prepareRename, which starts the compile that the proof starts from, then
                    // the rename, which compiles after the edit only
                    fun rename(
                        file: File,
                        name: String,
                        newName: String,
                    ): List<TextDocumentEdit> {
                        val text = file.readText()
                        val line = text.lines().indexOfFirst { "$name()" in it }
                        val at = Position(line, text.lines()[line].indexOf(name))
                        val document = TextDocumentIdentifier(file.toURI().toString())
                        server.textDocumentService.prepareRename(PrepareRenameParams(document, at)).awaited()
                        val edit =
                            requireNotNull(server.textDocumentService.rename(RenameParams(document, at, newName)).awaited()) {
                                "No rename of $name"
                            }
                        return edit.documentChanges.map { it.left }
                    }
                    val changes = rename(string, "workspaceOnly", "workspaceRenamed")
                    assertThat(changes.map { File(URI(it.textDocument.uri)).canonicalFile })
                        .containsExactlyInAnyOrder(string.canonicalFile, input.canonicalFile)
                    assertThat(changes.flatMap { it.edits }).allMatch { it.left.newText == "workspaceRenamed" }

                    // a rename in json, of the same revision, whose compile is kept
                    val probed = rename(input, "probe", "probed")
                    assertThat(probed.map { File(URI(it.textDocument.uri)).canonicalFile }).containsExactly(input.canonicalFile)

                    // an edit in a method's body, which leaves json's API as it was; the next revision's compile, which
                    // a code action request makes and which starts from the one before, compiles json alone
                    val edited = input.readText().replace("\"x\".workspaceOnly()", "\"y\".workspaceOnly()")
                    server.textDocumentService.didChange(
                        DidChangeTextDocumentParams(
                            VersionedTextDocumentIdentifier(input.toURI().toString(), 2),
                            listOf(TextDocumentContentChangeEvent(edited)),
                        ),
                    )
                    server.textDocumentService
                        .codeAction(
                            CodeActionParams(
                                TextDocumentIdentifier(input.toURI().toString()),
                                Range(Position(0, 0), Position(0, 0)),
                                CodeActionContext(emptyList(), listOf(CodeActionKind.RefactorInline)).apply {
                                    triggerKind = CodeActionTriggerKind.Invoked
                                },
                            ),
                        ).awaited()
                }
            }
        val queries = lines.filter { it.startsWith("project query: kind=") }
        queries.forEach(::println)
        // prepareRename's compile of ecstasy, json and jsonUse; the proof of the rename in ecstasy compiles them all
        // again, as each uses String, whose members it renames; the proof of the one in json compiles json alone:
        // jsonUse writes neither name and uses no type whose members the rename changes, though json's API changes
        assertThat(queries.map { "${it.substringBefore(" in ")} ${Regex("compiles=[0-9]+").find(it)?.value}" })
            .containsExactly(
                "project query: kind=prepare_rename uri=String.x outcome=completed compiles=3",
                "project query: kind=rename uri=String.x outcome=completed compiles=3",
                "project query: kind=rename uri=DocInput.x outcome=completed compiles=1",
            )
        assertThat(lines).contains(
            "rename: of the 1 modules that depend on the edited ones, the proof compiles 0 after the edit; the others write " +
                "neither probe nor probed nor use a type whose members it renames",
        )
        // the body edit's revision compiles json alone: jsonUse compiles against json's API as it did
        assertThat(lines.filter { it.startsWith("code actions: uri=DocInput.x") })
            .singleElement()
            .asString()
            .contains("outcome=answered", "compiles=base 1 + proofs 0 cached-base=false")
        // json's artifact names the workspace's ecstasy's classes, which it holds only as fingerprints, and its API is
        // read all the same
        assertThat(lines).noneMatch { "cannot be read alone" in it }
    }

    /** The lines that the server's loggers log at INFO while [body] runs. */
    private fun logged(body: () -> Unit): List<String> {
        val lines = CopyOnWriteArrayList<String>()
        val logger = LoggerFactory.getLogger("org.xvm.lsp") as Logger
        val appender =
            object : AppenderBase<ILoggingEvent>() {
                override fun append(event: ILoggingEvent) {
                    lines += event.formattedMessage
                }
            }.apply { start() }
        logger.addAppender(appender)
        try {
            body()
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
        return lines
    }
}
