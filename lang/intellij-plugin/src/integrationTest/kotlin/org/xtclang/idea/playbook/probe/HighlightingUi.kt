package org.xtclang.idea.playbook.probe

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateElementType

/** Inspect the installed lexical highlighter and semantic markup without changing either. */
object HighlightingUi {
    @JvmStatic
    fun lexical(
        editor: Editor,
        offset: Int,
    ): String {
        val iterator = (editor as EditorEx).highlighter.createIterator(offset)
        check(!iterator.atEnd()) { "No lexical token at $offset" }
        check(iterator.tokenType is TextMateElementType) {
            "Ecstasy must use the registered TextMate grammar, not ${iterator.tokenType}"
        }
        val attributes = iterator.textAttributes
        return "${iterator.tokenType}|${attributes.foregroundColor?.rgb}|${attributes.fontType}"
    }

    @JvmStatic
    fun semanticOverlays(
        editor: Editor,
        offset: Int,
    ): List<String> =
        (
            editor.markupModel.allHighlighters.toList() +
                DocumentMarkupModel.forDocument(editor.document, editor.project, false)?.allHighlighters.orEmpty()
        ).filter { it.isValid && offset >= it.startOffset && offset < it.endOffset }
            .mapNotNull { it.textAttributesKey?.externalName?.takeIf { name -> name.startsWith("LSP_") } }
}
