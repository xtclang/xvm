package org.xvm.lsp.adapter

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/** Writes the source file `<name>.x` in this directory, and the folders it needs, and returns its canonical URI. */
internal fun Path.source(
    name: String,
    text: String,
): String =
    resolve("$name.x")
        .toFile()
        .apply {
            parentFile.mkdirs()
            writeText(text)
        }.canonicalFile
        .toURI()
        .toString()

/** The text with the edits applied, as a client applies them: at their ranges' offsets, whether lines end in LF, CRLF or CR. */
internal fun apply(
    text: String,
    edits: List<TextEdit>,
): String {
    val starts = listOf(0) + Regex("\r\n|\r|\n").findAll(text).map { it.range.last + 1 }

    fun offset(position: Position) = starts[position.line] + position.column
    return edits.sortedByDescending { offset(it.range.start) }.fold(text) { result, edit ->
        result.replaceRange(offset(edit.range.start), offset(edit.range.end), edit.newText)
    }
}

/** The text of the document at [uri] with the workspace edit's changes to it applied. */
internal fun apply(
    text: String,
    edit: WorkspaceEdit,
    uri: String,
): String = apply(text, edit.changes.getValue(uri))

/** Applies the workspace edit to the files on disk, as a client does: its text edits, then its file moves and the [requested] ones. */
internal fun apply(
    edit: WorkspaceEdit,
    requested: Map<String, String> = emptyMap(),
) {
    edit.changes.forEach { (uri, edits) ->
        val path = Path.of(URI(uri))
        Files.writeString(path, apply(Files.readString(path), edits))
    }
    (edit.renames + requested).forEach { (from, to) -> Files.move(Path.of(URI(from)), Path.of(URI(to))) }
}
