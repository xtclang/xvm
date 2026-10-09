package org.xtclang.idea.playbook

import com.intellij.driver.sdk.invokeAction
import kotlin.time.Duration.Companion.seconds

internal fun ParityScenarios.documentationCases() {
    case("X278") { data ->
        val source = data.string("source")
        val expected = data.string("expected")
        val document = open(data.string("file"), source)
        configure(listOf(SharedScenarios.SourceModule("Documentation", document.uri, emptyList())))
        clean(document)
        with(driver) { quickFix(document.editor, document.at(data.string("anchor")), data.string("title")) }
        awaitUi("documentation matches the compiler signature", 30.seconds) { document.text == expected }
        clean(document)
        val at = ParityWorkspace.position(document.text, document.at(data.string("anchor")))
        val actions =
            query(
                "textDocument/codeAction",
                document,
                extra =
                    mapOf(
                        "range" to mapOf("start" to at, "end" to at),
                        "context" to mapOf("diagnostics" to emptyList<Any>()),
                    ),
            ).rows()
        check(actions.none { it.string("title") == data.string("title") })
        listOf("\$Undo" to source, "\$Redo" to expected, "\$Undo" to source).forEach { (action, text) ->
            with(driver) {
                focusEditor(document.editor)
                invokeAction(action, now = false, component = document.editor.component)
            }
            awaitUi("$action restores documentation", 30.seconds) { document.text == text }
            clean(document)
        }
    }
}
