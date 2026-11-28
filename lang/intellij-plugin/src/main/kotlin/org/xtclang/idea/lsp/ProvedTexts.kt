package org.xtclang.idea.lsp

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.LSPIJUtils
import org.eclipse.lsp4j.WorkspaceEdit
import org.xvm.lsp.protocol.RenameProposal
import java.io.IOException
import java.nio.file.Files

/**
 * Whether each document that a refactoring's edit changes has, as the edit is applied, the text that the server computed
 * its changes against, which the server's proposal names by its hash ([RenameProposal.textHashes]). IntelliJ's document,
 * which the edit changes, must have that text; so must the file on the disk when the server read it there, as for a
 * document that had no buffer at the server as the refactoring was asked, not one of [read]: IntelliJ loads a document
 * from the VFS's cached content, which is older than the disk's until the VFS refreshes a file changed outside the IDE,
 * and LSP4IJ saves the closed documents that an edit changes (since 0.30.1, redhat-developer/lsp4ij#1726 and #1731; X169
 * checks their disk text after the rename, its Undo and its Redo) over what the disk has. A document whose hash the
 * proposal does not name must be one of [read], which the request's snapshot proves unchanged: a server that proves no
 * text, as one that speaks LSP alone, or an adapter that edits only the document of the rename, read no other. A
 * target that IntelliJ cannot resolve refuses the edit too. The check runs under the apply's write action, as no change
 * may come between it and the edit: it reads each changed file that the server read on the disk once, and hashes each
 * changed document's text, and that file's, once, so its cost grows with the text that the edit changes, as LSP4IJ's
 * application of the edit, which loads each of those documents, does.
 */
internal object ProvedTexts {
    fun current(
        edit: WorkspaceEdit,
        hashes: Map<String, String>,
        read: Set<Document>,
    ): Boolean {
        val manager = FileDocumentManager.getInstance()
        val uris =
            edit.changes.orEmpty().keys +
                edit.documentChanges
                    .orEmpty()
                    .filter { it.isLeft }
                    .map { it.left.textDocument.uri }
        val files = uris.distinct().associateWith(LSPIJUtils::findResourceFor)
        val changed =
            files.mapNotNull { (uri, file) ->
                hashes[uri]?.let { hash -> file?.takeUnless { proved(it, hash, read) } }
            }
        // the VFS learns of a change outside the IDE as it refreshes the file, which a rename invoked again needs, as its
        // proof then reads the disk and IntelliJ's document its cached content
        if (changed.isNotEmpty()) VfsUtil.markDirtyAndRefresh(true, false, false, *changed.toTypedArray())
        return changed.isEmpty() &&
            files.all { (uri, file) ->
                val document = file?.let(manager::getDocument)
                document != null && (uri in hashes || document in read)
            }
    }

    /**
     * Whether [file]'s document has the text that [hash] names, and so does its file on the disk when the server read it
     * there, as it read a document that is not one of [read].
     */
    private fun proved(
        file: VirtualFile,
        hash: String,
        read: Set<Document>,
    ): Boolean {
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return false
        return RenameProposal.textHash(document.immutableCharSequence) == hash &&
            (document in read || disk(file)?.let(RenameProposal::textHash) == hash)
    }

    /**
     * The text of [file] on the disk, read as the server reads it, in UTF-8 with any malformed bytes replaced and without
     * a byte order mark, which IntelliJ's document does not have either; null when it can't be read.
     */
    private fun disk(file: VirtualFile): String? =
        if (file.isInLocalFileSystem) {
            try {
                String(Files.readAllBytes(file.toNioPath()), Charsets.UTF_8).removePrefix("\uFEFF")
            } catch (_: IOException) {
                null
            }
        } else {
            null
        }
}
