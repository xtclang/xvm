package org.xtclang.idea.lsp

import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LSPIJUtils
import org.eclipse.lsp4j.WorkspaceEdit

// TODO LSP4IJ: UP21 — WorkspaceEdit leaves closed documents unsaved and disconnected from the
// server. Persist only this transaction's closed documents, including after native Undo/Redo.
internal class ClosedRefactoringDocuments private constructor(
    private val project: Project,
    private val documents: List<Document>,
) {
    fun beforeApply() {
        if (documents.isEmpty()) return
        UndoManager.getInstance(project).undoableActionPerformed(
            object : BasicUndoableAction() {
                override fun isGlobal() = true

                // Registered first: Undo saves after all text and path changes have been reversed.
                override fun undo() = save()

                override fun redo() = Unit
            },
        )
    }

    fun afterApply() {
        if (documents.isEmpty()) return
        save()
        UndoManager.getInstance(project).undoableActionPerformed(
            object : BasicUndoableAction() {
                override fun isGlobal() = true

                override fun undo() = Unit

                // Registered last: Redo saves after the final text and paths have been restored.
                override fun redo() = save()
            },
        )
    }

    private fun save() {
        val manager = FileDocumentManager.getInstance()
        val editors = FileEditorManager.getInstance(project)
        documents.forEach { document ->
            val file = manager.getFile(document)
            if (file != null && file.isValid && !editors.isFileOpen(file)) manager.saveDocument(document)
        }
    }

    companion object {
        /** Refuse pre-existing unsaved closed buffers: they were not inputs to the server proof. */
        fun capture(
            project: Project,
            edit: WorkspaceEdit,
        ): ClosedRefactoringDocuments? {
            val manager = FileDocumentManager.getInstance()
            val editors = FileEditorManager.getInstance(project)
            val uris =
                edit.changes.orEmpty().keys +
                    edit.documentChanges
                        .orEmpty()
                        .filter { it.isLeft }
                        .map { it.left.textDocument.uri }
            val documents =
                uris.distinct().mapNotNull { uri ->
                    val file = LSPIJUtils.findResourceFor(uri) ?: return null
                    if (editors.isFileOpen(file)) return@mapNotNull null
                    val document = manager.getDocument(file) ?: return null
                    if (manager.isDocumentUnsaved(document)) return null
                    document
                }
            return ClosedRefactoringDocuments(project, documents)
        }
    }
}
