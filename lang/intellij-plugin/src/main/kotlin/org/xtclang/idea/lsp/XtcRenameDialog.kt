package org.xtclang.idea.lsp

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.refactoring.ui.NameSuggestionsField
import com.intellij.refactoring.ui.RefactoringDialog
import javax.swing.JComponent

/**
 * A rename dialog with a single name field and no preview. Subclasses validate the entered name
 * and perform the rename.
 *
 * Kotlin initializes a subclass's own properties after this class's initializer runs, and
 * [RefactoringDialog.init] can call back into overridden members. Subclasses therefore call
 * [initDialog] from their own initializer, once their state is set.
 */
internal abstract class XtcRenameDialog(
    project: Project,
    initialName: String,
    editor: Editor?,
) : RefactoringDialog(project, false) {
    protected val name = NameSuggestionsField(arrayOf(initialName), project, FileTypes.PLAIN_TEXT, editor)

    /** Finish constructing the dialog; call once the subclass's own properties are initialized. */
    protected fun initDialog() {
        title = "Rename"
        name.addDataChangedListener { validateButtons() }
        init()
    }

    override fun createCenterPanel(): JComponent = name

    override fun getPreferredFocusedComponent(): JComponent = name.focusableComponent

    override fun hasPreviewButton(): Boolean = false
}
