package org.xtclang.idea.lsp

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LSPIJUtils
import org.eclipse.lsp4j.WorkspaceEdit

/**
 * Whether a refactoring's edit reaches a closed document with unsaved changes, or a target IntelliJ cannot resolve:
 * the server proved the edit against the files on disk, so such a buffer was no input to its proof, and the edit is
 * refused. The closed documents that the edit changes are saved by LSP4IJ since 0.30.1 (redhat-developer/lsp4ij#1726
 * and #1731); X169 checks their disk text after the rename, its Undo and its Redo.
 */
internal object UnsavedClosedTargets {
    fun refuse(
        project: Project,
        edit: WorkspaceEdit,
    ): Boolean {
        val manager = FileDocumentManager.getInstance()
        val editors = FileEditorManager.getInstance(project)
        val uris =
            edit.changes.orEmpty().keys +
                edit.documentChanges
                    .orEmpty()
                    .filter { it.isLeft }
                    .map { it.left.textDocument.uri }
        return uris.distinct().any { uri ->
            val file = LSPIJUtils.findResourceFor(uri) ?: return@any true
            if (editors.isFileOpen(file)) return@any false
            val document = manager.getDocument(file) ?: return@any true
            manager.isDocumentUnsaved(document)
        }
    }
}
