package org.xtclang.idea.playbook.probe

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateElementType
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

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

    /** Paint the editor's window into a PNG, for inspecting the installed color scheme. */
    @JvmStatic
    fun screenshot(
        editor: Editor,
        file: String,
    ) {
        val window = SwingUtilities.getWindowAncestor(editor.contentComponent)
        val image = BufferedImage(window.width, window.height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            window.paintAll(graphics)
        } finally {
            graphics.dispose()
        }
        check(ImageIO.write(image, "png", Path.of(file).toFile()))
    }
}
