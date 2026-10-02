package org.xtclang.idea.lsp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.psi.PsiManager
import com.redhat.devtools.lsp4ij.LanguageServerItem
import com.redhat.devtools.lsp4ij.LanguageServerWrapper
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import java.net.URI
import java.util.concurrent.CompletableFuture

// TODO LSP4IJ: UP19 — reconnect open descendants when their directory moves or is renamed.
// Upstream onFileRenameAfter handles only the event's exact file. Remove this bridge once X162
// passes Move/Undo/Redo and subsequent unsaved edits without stale document connections.
internal class DirectoryDocumentMoves(
    private val wrapper: LanguageServerWrapper,
    private val disposed: () -> Boolean,
) : BulkFileListener {
    override fun before(events: List<VFileEvent>) {
        if (closed()) return
        // VFS invokes this under the write lock, before VirtualFile paths change. Capture the old
        // URI now; after the event the same VirtualFile already reports its new path. Only this
        // server's documents participate; overlapping directory events retire each document once.
        val files = descendants(events) { wrapper.openedDocuments.map { it.file } }
        files.forEach { file -> wrapper.toUri(file)?.let(::disconnect) }
        files.forEach(::reconnectLater)
    }

    private fun closed() = disposed() || wrapper.isDisposed || wrapper.project.isDisposed

    private fun reconnectLater(file: VirtualFile) {
        ApplicationManager.getApplication().invokeLater {
            if (closed() || !file.isValid || !FileEditorManager.getInstance(wrapper.project).isFileOpen(file)) return@invokeLater
            val uri = wrapper.toUri(file) ?: return@invokeLater
            // Use the normal public connection API and current document buffer after the VFS
            // transaction. Do not wait for didOpen or compilation while holding the IDE write lock.
            val connection =
                ReadAction.computeBlocking<CompletableFuture<List<LanguageServerItem>>?, RuntimeException> {
                    val psi = PsiManager.getInstance(wrapper.project).findFile(file) ?: return@computeBlocking null
                    LanguageServiceAccessor
                        .getInstance(wrapper.project)
                        .getLanguageServers(psi, { it.serverWrapper === wrapper }, { it.serverWrapper === wrapper })
                } ?: return@invokeLater
            connection
                .whenComplete { _, failure ->
                    ApplicationManager.getApplication().invokeLater repair@{
                        if (closed()) return@repair
                        val open = file.isValid && FileEditorManager.getInstance(wrapper.project).isFileOpen(file)
                        if (!open || wrapper.toUri(file) != uri) {
                            // A rapid Undo/Redo or tab close can overtake asynchronous connection.
                            // Retire only that attempt's file, never a different file reusing its URI.
                            if (wrapper.getOpenedDocument(uri)?.file === file) disconnect(uri)
                            if (open) reconnectLater(file)
                        } else if (failure != null) {
                            logger.warn("Cannot reconnect moved Ecstasy document $uri", failure)
                        }
                    }
                }
        }
    }

    private fun disconnect(uri: URI) {
        // The wrapper owns map removal, synchronizer disposal, didClose and diagnostic cleanup.
        // It exposes no public equivalent. Reflect only this method, never its maps or locks.
        disconnectMethod.invoke(wrapper, uri, false)
    }

    companion object {
        private val logger = logger<DirectoryDocumentMoves>()

        internal fun descendants(
            events: List<VFileEvent>,
            openedFiles: () -> List<VirtualFile>,
        ): List<VirtualFile> {
            val directories =
                events.mapNotNull { event ->
                    when (event) {
                        is VFileMoveEvent -> event.file
                        is VFilePropertyChangeEvent -> event.file.takeIf { event.propertyName == VirtualFile.PROP_NAME }
                        else -> null
                    }?.takeIf { it.isDirectory }
                }
            if (directories.isEmpty()) return emptyList()
            return openedFiles().distinct().filter { file -> directories.any { VfsUtilCore.isAncestor(it, file, true) } }
        }

        internal val disconnectMethod by lazy {
            LanguageServerWrapper::class.java
                .getDeclaredMethod("disconnect", URI::class.java, Boolean::class.javaPrimitiveType)
                .apply { check(trySetAccessible()) { "LSP4IJ document disconnect is inaccessible" } }
        }
    }
}
