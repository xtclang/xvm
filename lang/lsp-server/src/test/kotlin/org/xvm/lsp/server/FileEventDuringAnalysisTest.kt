package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS

/**
 * A watcher event that arrives while the first compile of a document is running must not retire the
 * requests waiting on that compile when the compile already read the reported files.
 */
class FileEventDuringAnalysisTest {
    @Test
    fun `a watcher event for files the first compile already read keeps the pending request`(
        @TempDir directory: Path,
    ) {
        CompilerTestSupport.configure()
        val workspace = directory.toRealPath().toFile()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val support = EmbeddingSupport.instance()
        val adapter =
            XdkAdapter(
                { source, repository, errors -> support.compileModule(source, repository, errors) },
                { sources, repository, errors ->
                    if (entered.count > 0) {
                        entered.countDown()
                        check(release.await(20, SECONDS))
                    }
                    support.compileModule(sources, repository, errors)
                },
                { source, _, cursor, repository, errors -> support.analyzeIncomplete(source, cursor, repository, errors) },
            )
        XtcLanguageServer(adapter).use { server ->
            server.connect(mock(LanguageClient::class.java))
            server
                .initialize(
                    InitializeParams().apply {
                        workspaceFolders = listOf(WorkspaceFolder(workspace.toURI().toString(), "workspace"))
                    },
                ).get(30, SECONDS)
            server.initialized(InitializedParams())
            try {
                // as the VS Code playbook does: write a file in a new folder, then open it
                val folder = workspace.resolve("Greetings").apply { mkdirs() }
                val file = folder.resolve("Greetings.x").apply { writeText(SOURCE) }
                val uri = file.toURI().toString()
                val service = server.textDocumentService
                service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, SOURCE)))
                assertThat(entered.await(20, SECONDS)).isTrue()
                val at = SOURCE.lines()[1].indexOf("greeting") + 1
                val hover = service.hover(HoverParams(TextDocumentIdentifier(uri), Position(1, at)))

                // the watcher reports the folder and the file while the first compile is still running
                server.workspaceService.didChangeWatchedFiles(
                    DidChangeWatchedFilesParams(
                        listOf(
                            FileEvent(folder.toURI().toString(), FileChangeType.Created),
                            FileEvent(uri, FileChangeType.Created),
                        ),
                    ),
                )
                release.countDown()
                // a request retired here fails with "Document changed during analysis"
                assertThat(
                    hover
                        .get(30, SECONDS)
                        .contents.right.value,
                ).contains("greeting")
            } finally {
                release.countDown()
            }
        }
    }

    private companion object {
        val SOURCE =
            """
            module Greetings {
                String greeting() = "hello";
            }
            """.trimIndent()
    }
}
