package org.xtclang.idea.editor

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class XtcEditorStartupActivity : ProjectActivity {
    private val logger = logger<XtcEditorStartupActivity>()

    override suspend fun execute(project: Project) {
        val delegates =
            EnterHandlerDelegate.EP_NAME.extensionList
                .map { it.javaClass.name }
                .sorted()
        logger.warn(
            "EnterHandlerDelegate registry for project '${project.name}': " +
                "${delegates.joinToString(", ")}",
        )
    }
}
