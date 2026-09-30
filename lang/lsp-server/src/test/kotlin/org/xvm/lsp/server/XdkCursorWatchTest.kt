package org.xvm.lsp.server

import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit.SECONDS
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter

class XdkCursorWatchTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `directory creation preserves an incomplete cursor only when effective inputs match`(
        changed: Boolean
    ) {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        val root =
            directory.resolve("App.x").toFile().apply { writeText("module App { void run() {} }") }
        val member =
            directory.resolve("App/Box.x").toFile().apply {
                parentFile.mkdirs()
                writeText("class Box {}")
            }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val support = EmbeddingSupport.instance()
        val adapter =
            XdkAdapter(
                { source, repository, errors -> support.compileModule(source, repository, errors) },
                { sources, repository, errors ->
                    support.compileModule(sources, repository, errors)
                },
                { source, _, cursor, repository, errors ->
                    entered.countDown()
                    check(release.await(20, SECONDS))
                    support.analyzeIncomplete(source, cursor, repository, errors)
                },
            )
        val server = XtcLanguageServer(adapter)
        server.connect(mock(LanguageClient::class.java))
        try {
            val uri = root.toURI().toString()
            val overlay =
                "module App { void run() { String text = \"hello\"; Int size = text.si; } }"
            val document = TextDocumentIdentifier(uri)
            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, "xtc", 1, overlay))
            )
            server.textDocumentService
                .documentSymbol(DocumentSymbolParams(document))
                .get(30, SECONDS)
            val cursor =
                server.textDocumentService.completion(
                    CompletionParams(
                        document,
                        Position(0, overlay.indexOf("text.si") + "text.si".length),
                    )
                )
            assertThat(entered.await(20, SECONDS)).isTrue()
            if (changed) member.writeText("class Box { Int added = 1; }")
            server.workspaceService.didChangeWatchedFiles(
                DidChangeWatchedFilesParams(
                    listOf(
                        FileEvent(directory.toUri().toString(), FileChangeType.Created),
                        FileEvent(root.toURI().toString(), FileChangeType.Created),
                    )
                )
            )
            release.countDown()
            if (changed) {
                val failure = assertThrows<ExecutionException> { cursor.get(30, SECONDS) }
                val error = failure.cause as ResponseErrorException
                assertThat(error.responseError.code)
                    .isEqualTo(ResponseErrorCode.ContentModified.value)
            } else assertThat(cursor.get(30, SECONDS).left.map { it.label }).contains("size")
        } finally {
            release.countDown()
            server.shutdown().get(20, SECONDS)
        }
    }
}
