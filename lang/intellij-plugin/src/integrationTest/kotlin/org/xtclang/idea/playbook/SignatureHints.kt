package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.client.service
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.PsiManager
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration.Companion.seconds

/** A snapshot of the response produced by IntelliJ's Parameter Info action. */
data class Signature(
    val label: String,
    val parameters: List<String>,
    val activeParameter: Int?,
)

/** Inspect the native action's existing future, then the visible popup; never issue a second LSP request. */
fun Driver.signature(
    editor: JEditorUiComponent,
    at: Int,
    keepOpen: Boolean = false,
    matches: (List<Signature>) -> Boolean,
) {
    val popup = ui.x("//div[@class='ParameterInfoComponent']")
    val support =
        withContext(OnDispatcher.EDT, semantics = LockSemantics.READ_ACTION) {
            editor.editor.getCaretModel().moveToOffset(at)
            val file = requireNotNull(service<PsiManager>(singleProject()).findFile(editor.editor.getVirtualFile()))
            utility(LspFileSupport::class).getSupport(file).getSignatureHelpSupport()
        }
    editor.scrollToCaret()
    val previous = support.getValidLSPFuture()
    invokeAction("ParameterInfo", component = editor.component)
    waitFor("native parameter information for offset $at", 45.seconds) {
        val future = support.getValidLSPFuture()
        if (future == null || future == previous || !future.isDone() || future.isCompletedExceptionally()) return@waitFor false
        val help = future.get()
        val signatures =
            help?.getSignatures().orEmpty().map { item ->
                val label = item.getLabel()
                Signature(
                    label,
                    item.getParameters().map { parameter ->
                        val name = parameter.getLabel()
                        name.getLeft() ?: name.getRight().let { label.substring(it.getFirst(), it.getSecond()) }
                    },
                    item.getActiveParameter() ?: help?.getActiveParameter(),
                )
            }
        if (!matches(signatures)) return@waitFor false
        val rendered =
            ui
                .xx("//div[@class='ParameterInfoComponent']//div[@class='JBHtmlPane']")
                .list()
                .map { cast(it.component, ParameterHintText::class).getText() }
        if (signatures.isEmpty()) return@waitFor rendered.isEmpty()
        rendered.size == signatures.size &&
            signatures.zip(rendered).all { (signature, html) ->
                val bold = Regex("<b(?:\\s[^>]*)?>[\\s\\S]*?</b>").findAll(html).map { plainText(it.value) }.toList()
                val active = signature.activeParameter?.let { signature.parameters.getOrNull(it) }
                // LSP4IJ currently displays 'no parameters' when metadata is suppressed for an ambiguous slot.
                (signature.parameters.isEmpty() || plainText(html).contains(signature.parameters.joinToString(", "))) &&
                    bold == listOfNotNull(active)
            }
    }
    if (!keepOpen && popup.present()) invokeAction("EditorEscape", component = editor.component)
}

private fun plainText(html: String): String =
    html
        .replace(Regex("<head>[\\s\\S]*?</head>"), "")
        .replace(Regex("<[^>]+>"), "")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace(Regex("\\s+"), " ")
        .trim()
