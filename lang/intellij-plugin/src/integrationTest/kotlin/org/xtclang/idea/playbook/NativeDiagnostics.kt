package org.xtclang.idea.playbook

import com.google.gson.Gson
import com.intellij.driver.client.Remote
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Editor
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent

/** Captures one painted editor diagnostic for severity, message and range assertions. */
data class Diagnostic(
    val severity: String,
    val description: String,
    val start: Int,
)

/**
 * Export immutable values: the driver's object descriptions evaluate cancellable lazy quick fixes.
 */
fun JEditorUiComponent.installedDiagnostics(): List<Diagnostic> =
    driver.withContext(OnDispatcher.EDT, semantics = LockSemantics.READ_ACTION) {
        val json = driver.utility(EditorDiagnostics::class).read(editor, driver.singleProject())
        Gson().fromJson(json, Array<Diagnostic>::class.java).toList()
    }

/** Reads diagnostics actually painted in the IDE rather than querying the server independently. */
@Remote("org.xtclang.idea.playbook.probe.EditorDiagnostics", plugin = "org.xtclang.playbook.probe")
interface EditorDiagnostics {
    fun read(
        editor: Editor,
        project: Project,
    ): String
}
