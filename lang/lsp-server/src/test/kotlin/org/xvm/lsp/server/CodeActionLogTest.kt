package org.xvm.lsp.server

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CodeActionCapabilities
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionKindCapabilities
import org.eclipse.lsp4j.CodeActionLiteralSupportCapabilities
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.WorkspaceEditCapabilities
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.slf4j.LoggerFactory
import org.xvm.lsp.HANG_SECONDS
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit.SECONDS

/**
 * The server's log has one line per code action request as it ends, with the module compiles it ran, and one per project
 * query that compiles, as it has one per embedded build; the compiles behind them were in the trace file alone.
 */
class CodeActionLogTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `each code action request and each project query that compiles logs its compiles`() {
        CompilerTestSupport.configure()
        val workspace = directory.toRealPath().toFile()
        val file = workspace.resolve("App.x").apply { writeText(SOURCE) }
        val uri = file.toURI().toString()
        val lines =
            logged {
                XtcLanguageServer(XdkAdapter()).use { server ->
                    server.connect(mock(LanguageClient::class.java))
                    server.initialize(parameters(workspace.toURI().toString())).get(HANG_SECONDS, SECONDS)
                    server.initialized(InitializedParams())
                    val service = server.textDocumentService
                    service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, SOURCE)))
                    // `input + 2`, which two extractions prove, as three removals of unused imports and their cleanup do
                    val selection = Range(Position(9, 15), Position(9, 24))
                    repeat(2) {
                        service
                            .codeAction(
                                CodeActionParams(
                                    TextDocumentIdentifier(uri),
                                    selection,
                                    CodeActionContext(emptyList()).apply { triggerKind = CodeActionTriggerKind.Invoked },
                                ),
                            ).get(HANG_SECONDS, SECONDS)
                    }
                    // a workspace's symbols, which the open file's compile has, so a query that compiles nothing logs
                    // nothing, then a rename of run, which compiles the project as it is and as renamed
                    val symbols = server.workspaceService.symbol(WorkspaceSymbolParams("run")).get(HANG_SECONDS, SECONDS)
                    assertThat(symbols.left.map { it.name }).containsExactly("run")
                    service.rename(RenameParams(TextDocumentIdentifier(uri), Position(7, 9), "go")).get(HANG_SECONDS, SECONDS)
                }
            }
        // the revision's compile, App, then three import proofs, the cleanup's and two extractions; then all of them cached
        assertThat(lines.filter { it.startsWith("code actions:") }).containsExactly(
            "code actions: uri=App.x range=9:15-9:24 trigger=invoked only=[] outcome=answered actions=6 compiles=base 1 + proofs 6 cached-base=false",
            "code actions: uri=App.x range=9:15-9:24 trigger=invoked only=[] outcome=answered actions=6 compiles=base 0 + proofs 0 cached-base=true",
        )
        assertThat(lines.filter { it.startsWith("project query:") }).containsExactly(
            "project query: kind=rename uri=App.x outcome=completed compiles=2",
        )
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

    private fun parameters(folder: String) =
        InitializeParams().apply {
            workspaceFolders = listOf(WorkspaceFolder(folder, "App"))
            capabilities =
                ClientCapabilities().apply {
                    textDocument =
                        TextDocumentClientCapabilities().apply {
                            codeAction =
                                CodeActionCapabilities().apply {
                                    codeActionLiteralSupport =
                                        CodeActionLiteralSupportCapabilities(
                                            CodeActionKindCapabilities(listOf("quickfix", "refactor", "source")),
                                        )
                                }
                        }
                    // the actions' edits are versioned
                    workspace =
                        WorkspaceClientCapabilities().apply { workspaceEdit = WorkspaceEditCapabilities().apply { documentChanges = true } }
                }
        }

    private companion object {
        /** Three imports that every module has implicitly, so each is unused, and a selection, `input + 2`, to extract. */
        val SOURCE =
            """
            module App {
                import ecstasy.text.StringBuffer;
                import ecstasy.maps.HashMap;
                import ecstasy.maps.ListMap;

                StringBuffer buffer() = new StringBuffer();

                Int run(Int input) {
                    Int unused = input + 1;
                    return input + 2;
                }
            }
            """.trimIndent() + "\n"
    }
}
