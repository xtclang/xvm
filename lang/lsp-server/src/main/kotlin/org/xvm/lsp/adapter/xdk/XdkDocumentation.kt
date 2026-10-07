package org.xvm.lsp.adapter.xdk

import org.xvm.lsp.adapter.CodeAction
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.adapter.TextEdit
import org.xvm.lsp.adapter.WorkspaceEdit

/** Comment-only edits at compiler-established written headers; no syntax or signature guessing. */
internal object XdkDocumentation {
    fun actions(
        uri: String,
        text: String,
        selection: Range,
        model: SemanticModel,
    ): List<CodeAction> {
        val start = SemanticModel.Position(selection.start.line, selection.start.column)
        val end = SemanticModel.Position(selection.end.line, selection.end.column)
        val newline = Regex("\r\n|\r|\n").find(text)?.value ?: "\n"
        return model.symbols
            .mapNotNull { symbol ->
                val header = symbol.headerStart ?: return@mapNotNull null
                val name = symbol.declaration ?: return@mapNotNull null
                if (symbol.declarationSource != model.sourceName || symbol.documentation != null ||
                    start >= name.end || end < header
                ) {
                    return@mapNotNull null
                }
                val offset = XdkRename.offset(text, header) ?: return@mapNotNull null
                val lineStart = XdkRename.offset(text, header.copy(column = 0)) ?: return@mapNotNull null
                val indent = text.substring(lineStart, offset)
                // Preserve same-line siblings and ambiguous adjacent block comments, including empty docs.
                if (indent.any { it != ' ' && it != '\t' } || text.take(lineStart).trimEnd().endsWith("*/")) return@mapNotNull null
                val lines =
                    buildList {
                        add("/**")
                        add(" * TODO: add description.")
                        symbol.signature?.parameters?.forEach { parameter ->
                            parameter.name?.takeUnless { it == "_" }?.let { add(" * @param $it TODO") }
                        }
                        symbol.signature?.returns?.forEach { _ -> add(" * @return TODO") }
                        add(" */")
                    }
                val at = Position(header.line, 0)
                CodeAction(
                    "Generate documentation comment",
                    CodeAction.CodeActionKind.SOURCE,
                    edit =
                        WorkspaceEdit(
                            mapOf(uri to listOf(TextEdit(Range(at, at), lines.joinToString(newline, postfix = newline) { indent + it }))),
                            versioned = true,
                        ),
                )
            }.distinct()
    }
}
