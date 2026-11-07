package org.xtclang.idea

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** Makes read-only Ecstasy sources recognizable before users attempt an edit or refactoring. */
class XtcEditorTabTitleProvider : EditorTabTitleProvider {
    override fun getEditorTabTitle(
        project: Project,
        file: VirtualFile,
    ): String? = if (file.extension == "x" && !file.isWritable) "${file.name} (locked)" else null
}
