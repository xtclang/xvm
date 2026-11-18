package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.awaitility.Awaitility.await
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.awaited
import java.nio.file.Path

/**
 * A client that changed a file on disk waits for the server to hear of it: the server's status tells when it received
 * each file's latest watched-file event, once it has acted on it.
 */
class ReceivedFileChangesTest {
    @Test
    fun `the status tells when a watched file's change arrived, once the server has rediscovered the workspace`(
        @TempDir directory: Path,
    ) {
        CompilerTestSupport.configure()
        val workspace = directory.toRealPath().toFile()
        XtcLanguageServer(XdkAdapter()).use { server ->
            server.connect(mock(LanguageClient::class.java))
            server
                .initialize(
                    InitializeParams().apply {
                        workspaceFolders = listOf(WorkspaceFolder(workspace.toURI().toString(), "workspace"))
                    },
                ).awaited()
            server.initialized(InitializedParams())

            val uri =
                workspace
                    .resolve("Greetings.x")
                    .apply { writeText("module Greetings {}\n") }
                    .toURI()
                    .toString()
            val before = System.currentTimeMillis()
            server.workspaceService.didChangeWatchedFiles(DidChangeWatchedFilesParams(listOf(FileEvent(uri, FileChangeType.Created))))
            val after = System.currentTimeMillis()

            await().untilAsserted {
                val received = server.languageServiceStatus().awaited()["watchedFileChanges"] as Map<*, *>
                assertThat(received[uri] as Long?).isNotNull().isBetween(before, after)
            }
        }
    }

    @Test
    fun `the latest event of a file replaces its earlier one, and the oldest files go beyond the limit`() {
        val changes = ReceivedFileChanges(limit = 2)
        changes.received(listOf("a", "b"), 1)
        changes.received(listOf("a"), 2)
        assertThat(changes.snapshot()).containsExactly(entry("b", 1L), entry("a", 2L))
        changes.received(listOf("c"), 3)
        assertThat(changes.snapshot()).containsExactly(entry("a", 2L), entry("c", 3L))
    }
}
