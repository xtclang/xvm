package org.xtclang.idea.lsp

import org.eclipse.lsp4j.FileRename
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.jsonrpc.Endpoint
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/** Connection-local, lexically scoped proof for the synchronous VFS portion of a guarded edit. */
internal class PreflightedRenames {
    private data class Move(
        val from: Path,
        val to: Path,
    )

    private val applying = ScopedValue.newInstance<Move>()

    fun apply(
        from: Path,
        to: Path,
        change: () -> Unit,
    ) {
        ScopedValue.where(applying, Move(from, to)).run(change)
    }

    // TODO LSP4IJ: UP03 — VFS preflight blocks the EDT even for an already approved edit.
    // Its Move listener also sends oldPath -> oldPath. Those requests need no server edit.
    // Keep real unowned renames and all notifications intact; remove when upstream accepts
    // transaction ownership and correct move targets without waiting under the write lock.
    fun endpoint(delegate: Endpoint): Endpoint =
        object : Endpoint {
            override fun request(
                method: String,
                parameter: Any?,
            ): CompletableFuture<*> =
                if (method == "workspace/willRenameFiles" && handled(parameter)) {
                    CompletableFuture.completedFuture<Any?>(null)
                } else {
                    delegate.request(method, parameter)
                }

            override fun notify(
                method: String,
                parameter: Any?,
            ) = delegate.notify(method, parameter)
        }

    private fun handled(parameter: Any?): Boolean {
        val files = (parameter as? RenameFilesParams)?.files ?: return false
        if (files.isEmpty()) return false
        return files.all { file ->
            val move = localMove(file) ?: return@all false
            move.from == move.to || (applying.isBound && move == applying.get())
        }
    }

    private fun localMove(file: FileRename): Move? =
        try {
            // Path equality ignores a directory's optional trailing slash and file:/ vs file:///.
            // Do not resolve symlinks, fold case or normalize '..': different spellings can carry
            // meaning, and non-local URIs belong to the server's normal validation path.
            val from = URI.create(file.oldUri)
            val to = URI.create(file.newUri)
            if (from.scheme != "file" || to.scheme != "file") null else Move(Path.of(from), Path.of(to))
        } catch (_: IllegalArgumentException) {
            null
        }
}
