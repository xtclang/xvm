package org.xtclang.idea.lsp

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.FileRename
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.jsonrpc.Endpoint
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

class PreflightedRenamesTest {
    private val guard = PreflightedRenames()
    private val pending = CompletableFuture<Any?>()
    private val notifications = mutableListOf<Pair<String, Any?>>()
    private val delegate =
        object : Endpoint {
            override fun request(
                method: String,
                parameter: Any?,
            ): CompletableFuture<Any?> = pending

            override fun notify(
                method: String,
                parameter: Any?,
            ) {
                notifications.add(method to parameter)
            }
        }
    private val endpoint = guard.endpoint(delegate)
    private val from = Path.of("/project/Before.x")
    private val to = Path.of("/project/After.x")
    private val rename = RenameFilesParams(listOf(FileRename(from.toUri().toString(), to.toUri().toString())))

    @Test
    fun `LSP service proxy preserves synchronous ownership through workspace service dispatch`() {
        val server = ServiceEndpoints.toServiceObject(endpoint, LanguageServer::class.java)
        guard.apply(from, to) {
            assertThat(server.workspaceService.willRenameFiles(rename)).isCompletedWithValue(null)
        }
        assertThat(server.workspaceService.willRenameFiles(rename)).isSameAs(pending)
    }

    @Test
    fun `physical move no-op requests complete without reaching a blocked server`() {
        listOf(
            "file:///project/Before.x" to "file:/project/Before.x",
            "file:///project/directory/" to "file:/project/directory",
            "file:///project/name%20with%20spaces.x" to "file:/project/name%20with%20spaces.x",
        ).forEach { (old, new) ->
            val result = request(RenameFilesParams(listOf(FileRename(old, new))))
            assertThat(result).isCompletedWithValue(null)
        }
        assertThat(pending).isNotDone()
    }

    @Test
    fun `only the exact approved rename is handled inside its lexical scope`() {
        assertThat(request(rename)).isSameAs(pending)
        guard.apply(from, to) {
            assertThat(request(rename)).isCompletedWithValue(null)
            assertThat(request(RenameFilesParams(listOf(FileRename(to.toUri().toString(), from.toUri().toString())))))
                .isSameAs(pending)
            assertThat(endpoint.request("workspace/willDeleteFiles", rename)).isSameAs(pending)
            assertThat(PreflightedRenames().endpoint(delegate).request("workspace/willRenameFiles", rename)).isSameAs(pending)
        }
        assertThat(request(rename)).isSameAs(pending)
    }

    @Test
    fun `nested scopes restore their parent and exceptions retire all approval`() {
        assertThatThrownBy {
            guard.apply(from, to) {
                guard.apply(to, from) { assertThat(request(rename)).isSameAs(pending) }
                assertThat(request(rename)).isCompletedWithValue(null)
                error("VFS operation failed")
            }
        }.isInstanceOf(IllegalStateException::class.java)
        assertThat(request(rename)).isSameAs(pending)
    }

    @Test
    fun `unrelated worker requests cannot inherit an EDT approval`() {
        Executors.newSingleThreadExecutor().use { worker ->
            guard.apply(from, to) {
                val otherThread = worker.submit<CompletableFuture<*>> { request(rename) }.get()
                assertThat(otherThread).isSameAs(pending)
                assertThat(request(rename)).isCompletedWithValue(null)
            }
        }
    }

    @Test
    fun `unknown mixed malformed and meaningful URI changes retain normal validation`() {
        listOf(
            RenameFilesParams(emptyList()),
            RenameFilesParams(listOf(FileRename(from.toUri().toString(), from.toUri().toString())) + rename.files),
            RenameFilesParams(listOf(FileRename("untitled:buffer", "untitled:buffer"))),
            RenameFilesParams(listOf(FileRename("file:/project/A/../Before.x", "file:/project/Before.x"))),
            RenameFilesParams(listOf(FileRename("file:/project/a.x", "file:/project/A.x"))),
            RenameFilesParams(listOf(FileRename("file:/broken uri", "file:/broken uri"))),
        ).forEach { assertThat(request(it)).isSameAs(pending) }
        assertThat(endpoint.request("workspace/willRenameFiles", null)).isSameAs(pending)
    }

    @Test
    fun `did notifications are unchanged and forwarded requests preserve cancellation`() {
        guard.apply(from, to) {
            endpoint.notify("workspace/didRenameFiles", rename)
            endpoint.notify("workspace/didChangeWatchedFiles", rename)
        }
        assertThat(notifications).containsExactly(
            "workspace/didRenameFiles" to rename,
            "workspace/didChangeWatchedFiles" to rename,
        )
        request(rename).cancel(false)
        assertThat(pending).isCancelled()
    }

    private fun request(params: RenameFilesParams): CompletableFuture<*> = endpoint.request("workspace/willRenameFiles", params)
}
