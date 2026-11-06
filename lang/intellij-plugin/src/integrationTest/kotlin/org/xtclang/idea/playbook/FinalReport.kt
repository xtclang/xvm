package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.utility
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import java.io.File
import java.net.URI

/**
 * Ends a wait for an editor's diagnostics that cannot come: once the report the client holds for the document's current
 * text does not do, and nothing could make another, three looks in a row. Nothing could once the server has no compile
 * queued, debouncing or running, no request it is still answering, and no current items of the document that no report
 * has given the client, and no compile was submitted between the looks. A wait for what the editor shows waits, too,
 * until IntelliJ has finished highlighting the file, after which the editor shows what it will.
 */
internal class FinalReport(
    private val driver: Driver,
    editor: JEditorUiComponent,
    private val what: String,
) {
    private val protocol = ClientProtocol(driver)
    private val file = editor.editor.getVirtualFile().getPath()
    private val path = File(file).canonicalPath
    private var looks = 0
    private var submitted: Int? = null

    /** Fails once [received], the client's report of the current text if it holds one, is final and is not [done]. */
    fun check(
        received: List<ReceivedDiagnostic>?,
        done: Boolean,
    ) = look(received != null && !done, highlighted = false) { "has $received" }

    /**
     * Fails once [received] is final, IntelliJ has finished highlighting the file, and what the editor shows of it,
     * [shown], is not [done].
     */
    fun check(
        received: List<ReceivedDiagnostic>?,
        shown: List<Diagnostic>?,
        done: Boolean,
    ) = look(received != null && shown != null && !done, highlighted = true) {
        "has $received, and the editor, its highlighting finished, shows $shown"
    }

    private fun look(
        held: Boolean,
        highlighted: Boolean,
        report: () -> String,
    ) {
        val total = if (held) quiet() else null
        val final = total != null && (!highlighted || highlightingDone())
        looks = if (final && (looks == 0 || total == submitted)) looks + 1 else 0
        submitted = total
        check(looks < 3) {
            "$what: the report of its current text is final, with nothing under way that could change it, and ${report()}"
        }
    }

    /** The compiles the server has had submitted, when nothing could change the document's diagnostics; null if not. */
    private fun quiet(): Int? {
        val status = protocol.query("xtc/languageServiceStatus", emptyMap<String, Any>()).asJsonObject
        val queue = status["compilerQueue"]?.takeIf { it.isJsonObject }?.asJsonObject
        val compiling = queue != null && listOf("queueSize", "debouncingSize", "runningSize").any { queue[it]?.asInt != 0 }
        val answering = status["openRequests"]?.takeIf { it.isJsonArray }?.asJsonArray?.size() ?: 0
        val unreported =
            status["unreportedDiagnostics"]?.takeIf { it.isJsonArray }?.asJsonArray?.any {
                // a document that is not a file, as an artifact's, is not this one
                runCatching { File(URI(it.asString)).canonicalPath }.getOrNull() == path
            } == true
        return if (compiling || answering > 0 || unreported) null else queue?.get("submittedTotal")?.asInt ?: 0
    }

    private fun highlightingDone(): Boolean =
        driver.withContext(OnDispatcher.EDT, semantics = LockSemantics.READ_ACTION) {
            utility(HighlightingDoneProbe::class).finished(singleProject(), file)
        }
}

/** Whether IntelliJ has finished highlighting a file: no daemon pass and no repaint of LSP4IJ's is pending for it. */
@Remote("org.xtclang.idea.playbook.probe.HighlightingDone", plugin = "org.xtclang.playbook.probe")
interface HighlightingDoneProbe {
    fun finished(
        project: Project,
        path: String,
    ): Boolean
}
