package org.xtclang.idea.lsp

import com.intellij.ide.TitledHandler
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.ui.NameSuggestionsField
import com.intellij.refactoring.ui.RefactoringDialog
import com.redhat.devtools.lsp4ij.LanguageServerWrapper
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import javax.swing.JComponent

/**
 * Request compiler proof before changing a file's physical path. IntelliJ's VFS before-event for
 * rename runs after the filesystem mutation, too late for LSP4IJ's willRenameFiles listener.
 */
class XtcFileRenameHandler : RenameHandler, TitledHandler {
    override fun getActionTitle(): String = "Rename XTC file and references"

    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
        if (CommonDataKeys.EDITOR.getData(dataContext) != null) return false
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
        val file = selectedFile(dataContext) ?: return false
        return isSourcePath(file) && server(project) != null
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) =
        invoke(project, arrayOf(file), dataContext)

    override fun invoke(
        project: Project,
        elements: Array<out PsiElement>,
        dataContext: DataContext,
    ) {
        val file = selectedFile(dataContext) ?: return
        val wrapper = server(project) ?: return
        RenameDialog(project, file, wrapper).show()
    }

    private fun selectedFile(context: DataContext): VirtualFile? {
        val files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(context)
        return if (files == null) CommonDataKeys.VIRTUAL_FILE.getData(context)
        else files.singleOrNull()
    }

    private fun isSourcePath(file: VirtualFile): Boolean =
        if (!file.isDirectory) file.extension == "x"
        else
            generateSequence(file) { it.parent }
                .any {
                    it.parent?.findChild("${it.name}.x")?.isDirectory == false
                }

    private fun server(project: Project): LanguageServerWrapper? =
        LanguageServiceAccessor.getInstance(project).startedServers.singleOrNull {
            it.serverDefinition.id == CompilerSettings.SERVER_ID &&
                it.serverCapabilitiesSync?.workspace?.fileOperations?.willRename != null
        }

    private class RenameDialog(
        project: Project,
        private val file: VirtualFile,
        private val wrapper: LanguageServerWrapper,
    ) : RefactoringDialog(project, false) {
        private val name =
            NameSuggestionsField(arrayOf(file.name), project, FileTypes.PLAIN_TEXT, null)

        init {
            title = "Rename"
            name.addDataChangedListener { validateButtons() }
            init()
        }

        override fun createCenterPanel(): JComponent = name

        override fun getPreferredFocusedComponent(): JComponent = name.focusableComponent

        override fun hasPreviewButton(): Boolean = false

        override fun areButtonsValid(): Boolean =
            name.enteredName.let {
                it.isNotBlank() &&
                    it != file.name &&
                    it != "." &&
                    it != ".." &&
                    '/' !in it &&
                    '\\' !in it &&
                    (file.isDirectory || it.endsWith(".x"))
            }

        override fun doAction() {
            val future = XtcRenameEdit.requestFileRename(wrapper, file, name.enteredName)
            close(OK_EXIT_CODE)
            object : Task.Backgroundable(project, "Renaming ${file.name}", true) {
                    override fun run(indicator: ProgressIndicator) {
                        // Completion below owns errors; this task provides cancellable progress.
                        ProgressIndicatorUtils.awaitWithCheckCanceled(
                            future.handle { _, _ -> null }
                        )
                    }

                    override fun onCancel() {
                        future.cancel(true)
                    }
                }
                .queue()
            future.whenComplete { edit, error ->
                ApplicationManager.getApplication().invokeLater {
                    if (project.isDisposed || future.isCancelled) return@invokeLater
                    val failure =
                        when {
                            error != null ->
                                "Rename failed: ${error.cause?.message ?: error.message}"
                            edit == null ->
                                "The compiler cannot safely update references for this file rename. No files were moved."
                            !edit.apply() -> "Sources or paths changed; invoke Rename again."
                            else -> null
                        }
                    failure?.let { Messages.showErrorDialog(project, it, "XTC Rename") }
                }
            }
        }
    }
}
