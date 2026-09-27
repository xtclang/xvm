package org.xtclang.idea.playbook.probe

import com.google.gson.Gson
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.project.Project

/** Loaded only in the disposable test IDE; never included in the distributed XTC plugin. */
object EditorDiagnostics {
    @JvmStatic
    fun read(
        editor: Editor,
        project: Project,
    ): String {
        val document = DocumentMarkupModel.forDocument(editor.document, project, false)
        val diagnostics =
            (editor.markupModel.allHighlighters.asSequence() + document?.allHighlighters.orEmpty().asSequence())
                .mapNotNull(HighlightInfo::fromRangeHighlighter)
                .filter { it.severity.name in setOf("ERROR", "WARNING") }
                .map {
                    mapOf(
                        "severity" to it.severity.name,
                        "description" to it.description.orEmpty(),
                        "start" to it.startOffset,
                    )
                }.distinct()
                .toList()
        return Gson().toJson(diagnostics)
    }
}
