package org.xtclang.idea.lsp

import com.intellij.ide.TitledHandler
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.ui.NameSuggestionsField
import com.intellij.refactoring.ui.RefactoringDialog
import com.redhat.devtools.lsp4ij.LanguageServerWrapper
import javax.swing.JComponent

/**
 * Request compiler proof before changing a file's physical path. IntelliJ's VFS before-event for
 * rename runs after the filesystem mutation, too late for LSP4IJ's willRenameFiles listener.
 */
class XtcFileRenameHandler :
    RenameHandler,
    TitledHandler {
    // TODO LSP4IJ: move willRenameFiles preflight before the physical mutation; its current VFS
    // before-event is already too late. Then this host-specific preflight can be removed.
    override fun getActionTitle(): String = "Rename Ecstasy file and references"

    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
        if (CommonDataKeys.EDITOR.getData(dataContext) != null) return false
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
        val file = selectedFile(dataContext) ?: return false
        return XtcFileOperations.isSourcePath(file) && XtcFileOperations.server(project) != null
    }

    override fun invoke(
        project: Project,
        editor: Editor,
        file: PsiFile,
        dataContext: DataContext,
    ) = invoke(project, arrayOf(file), dataContext)

    override fun invoke(
        project: Project,
        elements: Array<out PsiElement>,
        dataContext: DataContext,
    ) {
        val file = selectedFile(dataContext) ?: return
        val wrapper = XtcFileOperations.server(project) ?: return
        RenameDialog(project, file, wrapper).show()
    }

    private fun selectedFile(context: DataContext): VirtualFile? {
        val files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(context)
        return if (files == null) {
            CommonDataKeys.VIRTUAL_FILE.getData(context)
        } else {
            files.singleOrNull()
        }
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
            XtcFileOperations.submit(project, "Rename ${file.name}", future)
        }
    }
}
