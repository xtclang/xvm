package org.xtclang.idea.playbook

import com.google.gson.JsonObject
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
import org.xtclang.idea.playbook.probe.HANG_LIMIT
import java.nio.file.Files
import java.nio.file.Path

/**
 * X361–X365, registered after X360 in catalog order: what a file's .editorconfig sets over the editor's formatting
 * settings, the formatter markers, comment reflow, line endings and trailing whitespace.
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
        reformatUnchanged(document, data, "source", "without wrapping of comments the comment stays as it is")
        val previous = codeStyle { page, project -> page.wrapComments(project, true) }
        try {
            serverFormats("wrapComments", true)
            reformat(
                document,
                data.string("expected"),
                "the paragraph is refilled at the margin, and the @param lines and the code keep their layout",
            )
            reformatUnchanged(document, data, "expected", "a second format changes nothing")
        } finally {
            codeStyle { page, project -> page.wrapComments(project, previous) }
            serverFormats("wrapComments", previous)
        }
    }

    case("X364") { data ->
        val document = open(data.string("file"), data.string("source"))
        clean(document)
        val file = directory.resolve(data.string("file"))
        check(lineSeparator(file) == "\n") { "The file has LF line endings: ${lineSeparator(file)}" }
        withEditorConfig(data) { write ->
            write(data.string("settings"))
            // the line ending that the server reads from .editorconfig, which no text edit can give a document, and which
            // the plugin gives the file as IntelliJ's File | File Properties | Line Separators does, saving it
            val expected = data.string("expected")
            reformat(document, expected.replace("\r\n", "\n"), "Reformat Code indents the module")
            check(withinOrFail(HANG_LIMIT, "$id: the file has CRLF line endings") { Files.readString(file) == expected }) {
                "The file on disk: ${Files.readString(file)}; ${Quiescence.stuck}"
            }
            check(lineSeparator(file) == "\r\n") { "IntelliJ says the file has ${lineSeparator(file)}" }
            // the conversion's save and write of the file keep the format's command, which Undo undoes
            with(driver) {
                focusEditor(document.editor)
                invokeAction("\$Undo", now = false, component = document.editor.component)
            }
            check(withinOrFail(HANG_LIMIT, "$id: Undo undoes Reformat Code") { document.text == data.string("source") }) {
                "After Undo, the source is ${document.text}; ${Quiescence.stuck}"
            }
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

/**
 * Reformats [document], as [data]'s [key] with the indentation taken out of its line that begins with [data]'s
 * `reindented`, and waits until it is [key] again: the format changes nothing else, as [what] says. A Reformat Code that
 * changes nothing cannot be told from one that has not run yet; one that puts back an indentation can.
 */
private fun ParityWorkspace.reformatUnchanged(
    document: ParityWorkspace.Document,
    data: JsonObject,
    key: String,
    what: String,
) {
    val text = data.string(key)
    val line = data.string("reindented")
    check(text.split(line).size == 2) { "$key has '$line' once" }
    replace(document, text.replace(line, line.trimStart()))
    reformat(document, text, what)
}

/** The line separator that IntelliJ gives the file at [path], as its status bar's widget shows it. */
private fun ParityWorkspace.lineSeparator(path: Path): String? =
    with(driver) { cast(requireNotNull(refresh(path)), SeparatedFile::class).getDetectedLineSeparator() }

/** A file's line separator, as IntelliJ detected it or a change of it set it. */
@Remote("com.intellij.openapi.vfs.VirtualFile")
internal interface SeparatedFile {
    fun getDetectedLineSeparator(): String?
}

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
