package org.xtclang.idea.lsp

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.MoveHandlerDelegate
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.ui.DocumentAdapter
import com.redhat.devtools.lsp4ij.internal.CancellationSupport
import java.awt.BorderLayout
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

/** Community Move entry point: the compiler sees every old path before VFS changes anything. */
class XtcFileMoveHandler : MoveHandlerDelegate() {
    // TODO LSP4IJ: remove this bridge when native Move preflights willRenameFiles and applies
    // both URI parents and names in one version-checked undo command (shared X130 acceptance).
    override fun canMove(
        elements: Array<out PsiElement>,
        targetContainer: PsiElement?,
        reference: PsiReference?,
    ): Boolean =
        elements.isNotEmpty() &&
            elements.all { element ->
                file(element)?.let(XtcFileOperations::isSourcePath) == true
            } &&
            XtcFileOperations.server(elements.first().project) != null &&
            (targetContainer == null || targetContainer is PsiDirectory)

    override fun isValidTarget(target: PsiElement?, sources: Array<out PsiElement>): Boolean =
        target is PsiDirectory

    override fun doMove(
        project: Project,
        elements: Array<out PsiElement>,
        targetContainer: PsiElement?,
        callback: MoveCallback?,
    ) {
        val files = elements.map { file(it) ?: return }
        MoveDialog(project, files, (targetContainer as? PsiDirectory)?.virtualFile, callback).show()
    }

    private fun file(element: PsiElement): VirtualFile? =
        when (element) {
            is PsiFile -> element.virtualFile
            is PsiDirectory -> element.virtualFile
            else -> null
        }

    private class MoveDialog(
        project: Project,
        private val files: List<VirtualFile>,
        target: VirtualFile?,
        private val callback: MoveCallback?,
    ) : RefactoringDialog(project, false) {
        private val destination =
            TextFieldWithBrowseButton().apply {
                text = (target ?: files.first().parent).path
                addBrowseFolderListener(
                    project,
                    FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                )
                addDocumentListener(
                    object : DocumentAdapter() {
                        override fun textChanged(event: DocumentEvent) = validateButtons()
                    }
                )
            }

        init {
            title = "Move Ecstasy Sources"
            init()
        }

        override fun createCenterPanel(): JComponent =
            JPanel(BorderLayout()).apply {
                add(JLabel("Destination directory:"), BorderLayout.NORTH)
                add(destination, BorderLayout.CENTER)
            }

        override fun getPreferredFocusedComponent(): JComponent = destination.textField

        override fun hasPreviewButton(): Boolean = false

        override fun areButtonsValid(): Boolean = targets()?.let(FileMoveTargets::valid) == true

        private fun targets(): Map<Path, Path>? = runCatching {
            val directory = Path.of(destination.text).toAbsolutePath().normalize()
            files.associate { Path.of(it.path) to directory.resolve(it.name) }
        }
            .getOrNull()

        override fun doAction() {
            val targets = targets() ?: return
            if (!FileMoveTargets.valid(targets)) return
            // Resolve an existing target for the later VFS move; never create directories before
            // proof.
            val wrapper = XtcFileOperations.server(project) ?: return
            val requested = files.associateWith { targets.getValue(Path.of(it.path)) }
            val cancellation = CancellationSupport()
            val future =
                CompletableFuture.runAsync {
                        requireNotNull(
                            LocalFileSystem.getInstance()
                                .refreshAndFindFileByNioFile(targets.values.first().parent)
                        ) {
                            "Destination directory no longer exists"
                        }
                    }
                    .thenCompose {
                        cancellation.checkCanceled()
                        cancellation.execute(XtcRenameEdit.requestFileMoves(wrapper, requested))
                    }
            future.whenComplete { _, _ -> if (future.isCancelled) cancellation.cancel() }
            close(OK_EXIT_CODE)
            XtcFileOperations.submit(project, "Move Ecstasy sources", future) {
                callback?.refactoringCompleted()
            }
        }
    }
}
