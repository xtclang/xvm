package org.xtclang.idea.playbook

import com.google.gson.JsonObject
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
import org.xtclang.idea.playbook.probe.HANG_LIMIT
import java.nio.file.Files

/**
 * X361–X365, registered after X360 in catalog order: what a file's .editorconfig sets over the editor's formatting
 * settings, the formatter markers, comment reflow and trailing whitespace. X364, line endings, is VS Code's only:
 * IntelliJ applies end_of_line itself.
 */
internal fun ParityScenarios.formattingCases() {
    case("X361") { data ->
        val document = open(data.string("file"), data.string("source"))
        clean(document)
        // the editor's own settings, which the file's override
        val editor = protocol.status()["formatting"].asJsonObject
        check(editor.int("indentSize") == data.int("indentSize") && editor.int("maxLineWidth") == data.int("maxLineWidth")) {
            "The editor's formatting settings: $editor"
        }
        withEditorConfig(data) { write ->
            write(data.string("settings"))
            reformat(document, data.string("fromFile"), "the file's indent_size and max_line_length")
            replace(document, data.string("source"))
            // a later section's unset takes indent_size and max_line_length out: indent_style = tab indents by its
            // tab_width, one tab a level, where an indent_size the server ignored would leave the editor's 4 columns, and
            // the margin is the editor's
            write(data.string("unset"))
            reformat(document, data.string("fromUnset"), "unset takes indent_size and max_line_length out")
            replace(document, data.string("source"))
            write(data.string("settings"))
            val previous = codeStyle { page, project -> page.editorConfig(project, false) }
            try {
                serverFormats("editorConfig", false)
                reformat(document, data.string("fromEditor"), "with EditorConfig support off, the editor's settings")
            } finally {
                codeStyle { page, project -> page.editorConfig(project, previous) }
                serverFormats("editorConfig", previous)
            }
        }
    }

    case("X362") { data ->
        val document = open(data.string("file"), data.string("source"))
        clean(document)
        // IntelliJ has the markers on by default
        val previous = codeStyle { page, project -> page.formatterTags(project, false) }
        try {
            serverFormats("formatterTagsEnabled", false)
            reformat(document, data.string("unmarked"), "without the markers every line is formatted")
            replace(document, data.string("source"))
            codeStyle { page, project -> page.formatterTags(project, true) }
            serverFormats("formatterTagsEnabled", true)
            reformat(document, data.string("expected"), "the marked lines keep their layout, and the others are formatted")

            // as you type: the typed action, then the server's on-type formatting, which LSP4IJ asks for ;
            replace(document, data.string("typing"))
            with(driver) {
                val typing = utility(TypingProbe::class)
                val editor = document.editor
                focusEditor(editor)
                listOf("kept", "formatted").forEach { key ->
                    val at = document.at(data.string(key), data.string(key).length)
                    withContext(OnDispatcher.EDT) {
                        editor.editor.getCaretModel().moveToOffset(at)
                        typing.type(editor.editor, data.string("keys"))
                    }
                }
            }
            check(
                withinOrFail(HANG_LIMIT, "$id: typed, ; keeps the marked line and indents the line after the markers") {
                    document.text == data.string("expected")
                },
            ) { "Typed, the source is ${document.text}; ${Quiescence.stuck}" }

            // with the markers off in the Code Style and on in .editorconfig
            codeStyle { page, project -> page.formatterTags(project, false) }
            serverFormats("formatterTagsEnabled", false)
            replace(document, data.string("source"))
            withEditorConfig(data) { write ->
                write(data.string("settings"))
                reformat(document, data.string("expected"), "with the markers on in .editorconfig, the marked lines keep their layout")
            }
        } finally {
            codeStyle { page, project -> page.formatterTags(project, previous) }
            serverFormats("formatterTagsEnabled", previous)
        }
    }

    case("X363") { data ->
        val document = open(data.string("file"), data.string("source"))
        clean(document)
        check(formatting(document).isEmpty()) { "Without wrapping of comments the comment stays as it is" }
        val previous = codeStyle { page, project -> page.wrapComments(project, true) }
        try {
            serverFormats("wrapComments", true)
            reformat(
                document,
                data.string("expected"),
                "the paragraph is refilled at the margin, and the @param lines and the code keep their layout",
            )
            check(formatting(document).isEmpty()) { "A second format changes nothing" }
        } finally {
            codeStyle { page, project -> page.wrapComments(project, previous) }
            serverFormats("wrapComments", previous)
        }
    }

    case("X365") { data ->
        val document = open(data.string("file"), data.string("source"))
        clean(document)
        withEditorConfig(data) { write ->
            // IntelliJ never asks the formatter to trim trailing whitespace: true trims it, and false keeps it
            write(data.string("trim"))
            reformat(document, data.string("trimmed"), "trim_trailing_whitespace = true trims the trailing whitespace")
            replace(document, data.string("source"))
            write(data.string("keep"))
            reformat(document, data.string("kept"), "trim_trailing_whitespace = false keeps the trailing whitespace")
        }
    }
}

/** Runs [body] with the probe of the Code Style settings and the project, on the EDT, as the settings pages change them. */
private fun <T> ParityWorkspace.codeStyle(body: (LanguageServicePage, Project) -> T): T =
    with(driver) {
        val page = utility(LanguageServicePage::class)
        val project = singleProject()
        withContext(OnDispatcher.EDT) { body(page, project) }
    }

/**
 * Runs [body] with the case's .editorconfig, which it writes as [body] says, beside the case's file, and removes it again.
 * The language server reads it at each format, and no watcher of the server's reports it, so no change of it is waited
 * for; IntelliJ hears of each, as its own EditorConfig support reads the file too.
 */
private fun ParityWorkspace.withEditorConfig(
    data: JsonObject,
    body: ((String) -> Unit) -> Unit,
) {
    val file = directory.resolve(data.string("editorConfig"))
    try {
        body { text -> write(data.string("editorConfig"), text) }
    } finally {
        Files.deleteIfExists(file)
        changed(file)
    }
}

/** Reformats [document] with Reformat Code and waits until it is [expected], as [what] says. */
private fun ParityWorkspace.reformat(
    document: ParityWorkspace.Document,
    expected: String,
    what: String,
) {
    with(driver) {
        focusEditor(document.editor)
        invokeAction("ReformatCode", now = false, component = document.editor.component)
    }
    check(withinOrFail(HANG_LIMIT, "$id: Reformat Code: $what") { document.text == expected }) {
        "Reformat Code: $what: the source is ${document.text}; ${Quiescence.stuck}"
    }
}

/** The server's formatting edits for [document], with IntelliJ's options, which LSP4IJ sends: tab size and spaces. */
private fun ParityWorkspace.formatting(document: ParityWorkspace.Document): List<JsonObject> =
    query("textDocument/formatting", document, extra = mapOf("options" to mapOf("tabSize" to 4, "insertSpaces" to true))).rows()

/** Waits until the language server formats with [key] [value], as its status says, after a change of the Code Style. */
private fun ParityWorkspace.serverFormats(
    key: String,
    value: Boolean,
) {
    fun formatting() = protocol.status()["formatting"]?.takeIf { it.isJsonObject }?.asJsonObject
    check(withinOrFail(HANG_LIMIT, "$id: the language server formats with $key $value") { formatting()?.get(key)?.asBoolean == value }) {
        "The server's formatting settings: ${formatting()}; ${Quiescence.stuck}"
    }
}
