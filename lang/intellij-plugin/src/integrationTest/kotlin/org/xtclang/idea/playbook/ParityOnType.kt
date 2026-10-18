package org.xtclang.idea.playbook

import com.google.gson.JsonObject
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Editor
import kotlin.time.Duration.Companion.seconds

/** Indentation while typing; registered after the build mode cases to keep catalog order. */
internal fun ParityScenarios.onTypeCases() {
    case("X290") { data ->
        val scenarios = data["scenarios"].rows()
        val document = open(data.string("file"), scenarios.first().string("source").replace("§", ""))
        with(driver) {
            val typing = utility(TypingProbe::class)
            val editor = document.editor
            focusEditor(editor)
            // every scenario runs, so one run reports each one that fails
            val failures = mutableListOf<String>()
            for (scenario in scenarios) {
                val name = scenario.string("name")
                val source = scenario.string("source")
                val indent = scenario.int("indent")
                replace(document, source.replace("§", ""))
                withContext(OnDispatcher.EDT) {
                    editor.editor.getCaretModel().moveToOffset(source.indexOf('§'))
                    typing.type(editor.editor, scenario.string("keys"))
                }

                fun caret() = withContext(OnDispatcher.EDT) { typing.caretOffset(editor.editor) }
                runCatching {
                    awaitUi("$name: the cursor line is indented by $indent", 10.seconds) { indentAt(document.text, caret()) == indent }
                    // the server's on-type edits for the same keystroke leave the line where it is, so no late
                    // on-type edit can still change it
                    settle(document)
                    val at = caret()
                    val options = mapOf("tabSize" to 4, "insertSpaces" to true)
                    val edits =
                        query(
                            "textDocument/onTypeFormatting",
                            document,
                            at,
                            mapOf(
                                "ch" to scenario.string("keys").takeLast(1),
                                "options" to options,
                            ),
                        )
                    val line = document.text.take(at).count { it == '\n' }
                    val after = applied(document.text, edits.rows()).lines().getOrElse(line) { "" }
                    check(
                        after.length - after.trimStart().length == indent,
                    ) { "$name: the server's on-type edits would re-indent the line" }
                }.onFailure { failures += "${it.message}\n${document.text}" }
            }
            check(failures.isEmpty()) { failures.joinToString("\n\n") }
        }
    }
}

/** The indentation of the line holding [offset]. */
private fun indentAt(
    text: String,
    offset: Int,
): Int {
    val line = text.substring(0, offset).substringAfterLast('\n') + text.substring(offset).substringBefore('\n')
    return line.length - line.trimStart().length
}

/** [text] with LSP text edits applied, last first so earlier offsets stay valid. */
private fun applied(
    text: String,
    edits: List<JsonObject>,
): String {
    val lineStarts = listOf(0) + text.indices.filter { text[it] == '\n' }.map { it + 1 }

    fun offset(position: JsonObject) = lineStarts[position.int("line")] + position.int("character")
    return edits
        .map { offset(it.getAsJsonObject("range").getAsJsonObject("start")) to it }
        .sortedByDescending { it.first }
        .fold(text) { result, (start, edit) ->
            val end = offset(edit.getAsJsonObject("range").getAsJsonObject("end"))
            result.substring(0, start) + edit.string("newText") + result.substring(end)
        }
}

/** Types through the IDE's typed and Enter actions, as key presses do; see the IDE-side probe. */
@Remote("org.xtclang.idea.playbook.probe.TypingUi", plugin = "org.xtclang.playbook.probe")
internal interface TypingProbe {
    fun type(
        editor: Editor,
        text: String,
    )

    fun caretOffset(editor: Editor): Int
}
