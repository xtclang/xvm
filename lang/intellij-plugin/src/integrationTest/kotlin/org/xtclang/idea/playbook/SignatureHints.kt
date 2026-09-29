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
import kotlin.time.Duration.Companion.seconds

/** A snapshot of the response produced by IntelliJ's Parameter Info action. */
data class Signature(
    val label: String,
    val parameters: List<String>,
    val activeParameter: Int?,
    val documentation: String? = null,
)

/** Inspect the native action's future and visible popup; reopen only after recorded focus loss. */
fun Driver.signature(
    editor: JEditorUiComponent,
    at: Int,
    keepOpen: Boolean = false,
    inspectDocumentation: Boolean = false,
    matches: (List<Signature>) -> Boolean,
) {
    dismissPopups()
    focusEditor(editor)
    val popup = ui.x("//div[@class='ParameterInfoComponent']")
    val support =
        withContext(OnDispatcher.EDT, semantics = LockSemantics.READ_ACTION) {
            editor.editor.getCaretModel().moveToOffset(at)
            val file =
                requireNotNull(
                    service<PsiManager>(singleProject()).findFile(editor.editor.getVirtualFile())
                )
            utility(LspFileSupport::class).getSupport(file).getSignatureHelpSupport()
        }
    editor.scrollToCaretNow()
    val inspection =
        PopupInspection(this, editor) {
            invokeAction("ParameterInfo", component = editor.component)
        }
    invokeAction("ParameterInfo", component = editor.component)

    fun renderedHints() =
        ui.xx("//div[@class='ParameterInfoComponent']//div[@class='JBHtmlPane']").list().map {
            cast(it.component, ParameterHintText::class).getText()
        }
    awaitUi(
        message = "native parameter information for offset $at",
        timeout = 45.seconds,
        errorMessage = {
            val future = support.getValidLSPFuture()
            val help = future?.takeIf { it.isDone() && !it.isCompletedExceptionally() }?.get()
            val labels =
                help?.getSignatures()?.map { item ->
                    "${item.getLabel()} active=${item.getActiveParameter()} parameters=" +
                        item.getParameters().map { it.getLabel().getLeft() }
                }
            "Native future: $future; signatures=$labels activeSignature=${help?.getActiveSignature()} " +
                "global=${help?.getActiveParameter()}; rendered hints: ${renderedHints()}"
        },
    ) {
        inspection.recover()
        val future = support.getValidLSPFuture()
        if (future == null || !future.isDone() || future.isCompletedExceptionally())
            return@awaitUi false
        val help = future.get()
        val signatures =
            help?.getSignatures().orEmpty().map { item ->
                val label = item.getLabel()
                Signature(
                    label,
                    item.getParameters().map { parameter ->
                        val name = parameter.getLabel()
                        name.getLeft()
                            ?: name.getRight().let {
                                label.substring(it.getFirst(), it.getSecond())
                            }
                    },
                    item.getActiveParameter() ?: help?.getActiveParameter(),
                    if (inspectDocumentation)
                        ClientProtocol(this).copy(item.getDocumentation()).let {
                            when {
                                it.isJsonPrimitive -> it.asString
                                it.isJsonObject -> it.asJsonObject["value"]?.asString
                                else -> null
                            }
                        }
                    else null,
                )
            }
        if (!matches(signatures)) return@awaitUi false
        val rendered = renderedHints()
        if (signatures.isEmpty()) return@awaitUi rendered.isEmpty()
        val activeSignature = help?.getActiveSignature()
        rendered.size == signatures.size &&
            signatures.indices.all { index ->
                val signature = signatures[index]
                val html = rendered[index]
                val bold =
                    Regex("<b(?:\\s[^>]*)?>[\\s\\S]*?</b>")
                        .findAll(html)
                        .map { plainText(it.value) }
                        .toList()
                // LSP4IJ dims inactive overloads and only highlights enabled rows. With no
                // selected overload it enables every row; parameter overrides still apply.
                val enabled =
                    activeSignature == null || activeSignature < 0 || activeSignature == index
                val active =
                    signature.activeParameter
                        ?.takeIf { enabled }
                        ?.let { signature.parameters.getOrNull(it) }
                plainText(html)
                    .contains(
                        if (signature.parameters.isEmpty()) signature.label
                        else signature.parameters.joinToString(", ")
                    ) && bold == listOfNotNull(active)
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
