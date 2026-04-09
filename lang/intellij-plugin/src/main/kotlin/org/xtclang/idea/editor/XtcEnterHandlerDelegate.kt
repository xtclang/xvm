package org.xtclang.idea.editor

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

/**
 * Repairs IntelliJ's local brace-split behavior for `.x` files when Enter is pressed
 * after a declaration ending in `{`. LSP on-type formatting does not reliably receive
 * the first Enter in this path, so we normalize the editor-local result here using the
 * current IntelliJ code style settings.
 */
class XtcEnterHandlerDelegate : EnterHandlerDelegateAdapter() {
    private val logger = logger<XtcEnterHandlerDelegate>()

    override fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        caretOffset: Ref<Int>,
        caretAdvance: Ref<Int>,
        dataContext: DataContext,
        originalHandler: EditorActionHandler?,
    ): EnterHandlerDelegate.Result {
        if (isXtcFile(file)) {
            logger.warn(
                "preprocessEnter: file=${file.virtualFile?.path} " +
                    "caretOffset=${caretOffset.get()} caretAdvance=${caretAdvance.get()}",
            )
            scheduleRepairAfterEnter(file, editor)
        }
        return EnterHandlerDelegate.Result.Continue
    }

    override fun postProcessEnter(
        file: PsiFile,
        editor: Editor,
        dataContext: DataContext,
    ): EnterHandlerDelegate.Result {
        if (!isXtcFile(file)) {
            return EnterHandlerDelegate.Result.Continue
        }

        val document = editor.document
        val caretModel = editor.caretModel
        val caretOffset = caretModel.offset
        val currentLine = document.getLineNumber(caretOffset)
        logger.warn(
            "postProcessEnter: file=${file.virtualFile?.path} caretOffset=$caretOffset line=$currentLine " +
                "prev='${safeLine(document, currentLine - 1)}' " +
                "current='${safeLine(document, currentLine)}' " +
                "next='${safeLine(document, currentLine + 1)}'",
        )

        val fix =
            when {
                isCompactEmptyBlockSplit(document, currentLine) -> {
                    rewriteEmptyBlock(document, currentLine - 1, currentLine, currentLine + 1)
                }

                isClosingBraceCaretAfterBadSplit(document, currentLine) -> {
                    rewriteEmptyBlock(document, currentLine - 2, currentLine - 1, currentLine)
                }

                else -> {
                    null
                }
            }

        if (fix == null) {
            return EnterHandlerDelegate.Result.Continue
        }

        val (declarationLine, bodyLine, closingLine) = fix
        val declarationIndent = lineIndent(document, declarationLine)
        val bodyIndent = declarationIndent + indentUnit(file)

        replaceLineWithIndent(document, bodyLine, bodyIndent)
        replaceLineWithIndent(document, closingLine, declarationIndent, suffix = "}")

        val bodyCaretOffset = document.getLineStartOffset(bodyLine) + bodyIndent.length
        caretModel.moveToOffset(bodyCaretOffset)
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
        PsiDocumentManager.getInstance(file.project).commitDocument(document)

        logger.info(
            "postProcessEnter: repaired empty-block split " +
                "declarationLine=$declarationLine bodyLine=$bodyLine closingLine=$closingLine " +
                "declarationIndent='${visualizeIndent(declarationIndent)}' bodyIndent='${visualizeIndent(bodyIndent)}'",
        )

        return EnterHandlerDelegate.Result.Stop
    }

    private fun rewriteEmptyBlock(
        document: com.intellij.openapi.editor.Document,
        declarationLine: Int,
        bodyLine: Int,
        closingLine: Int,
    ): Triple<Int, Int, Int>? {
        if (declarationLine < 0 || bodyLine < 0 || closingLine >= document.lineCount) {
            return null
        }
        val declarationText = lineText(document, declarationLine).trimEnd()
        val bodyText = lineText(document, bodyLine)
        val closingText = lineText(document, closingLine)
        if (!declarationText.endsWith("{")) {
            return null
        }
        if (bodyText.isNotBlank()) {
            return null
        }
        if (closingText.trim() != "}") {
            return null
        }
        return Triple(declarationLine, bodyLine, closingLine)
    }

    private fun isCompactEmptyBlockSplit(
        document: com.intellij.openapi.editor.Document,
        currentLine: Int,
    ): Boolean {
        if (currentLine <= 0 || currentLine + 1 >= document.lineCount) {
            return false
        }
        val previousText = lineText(document, currentLine - 1).trimEnd()
        val currentText = lineText(document, currentLine)
        val nextText = lineText(document, currentLine + 1)
        return previousText.endsWith("{") && currentText.isBlank() && nextText.trim() == "}"
    }

    private fun isClosingBraceCaretAfterBadSplit(
        document: com.intellij.openapi.editor.Document,
        currentLine: Int,
    ): Boolean {
        if (currentLine < 2) {
            return false
        }
        val declarationText = lineText(document, currentLine - 2).trimEnd()
        val bodyText = lineText(document, currentLine - 1)
        val closingText = lineText(document, currentLine)
        return declarationText.endsWith("{") && bodyText.isBlank() && closingText.trim() == "}"
    }

    private fun lineText(
        document: com.intellij.openapi.editor.Document,
        line: Int,
    ): String {
        val start = document.getLineStartOffset(line)
        val end = document.getLineEndOffset(line)
        return document.getText(
            com.intellij.openapi.util
                .TextRange(start, end),
        )
    }

    private fun lineIndent(
        document: com.intellij.openapi.editor.Document,
        line: Int,
    ): String = lineText(document, line).takeWhile { it == ' ' || it == '\t' }

    private fun indentUnit(file: PsiFile): String {
        val indentOptions = CodeStyle.getIndentOptions(file)
        val indentSize = indentOptions.INDENT_SIZE.coerceAtLeast(1)
        return if (indentOptions.USE_TAB_CHARACTER) {
            val tabs = indentSize / indentOptions.TAB_SIZE.coerceAtLeast(1)
            val spaces = indentSize % indentOptions.TAB_SIZE.coerceAtLeast(1)
            buildString {
                append("\t".repeat(tabs.coerceAtLeast(1)))
                append(" ".repeat(spaces))
            }
        } else {
            " ".repeat(indentSize)
        }
    }

    private fun replaceLineWithIndent(
        document: com.intellij.openapi.editor.Document,
        line: Int,
        indent: String,
        suffix: String = "",
    ) {
        val start = document.getLineStartOffset(line)
        val end = document.getLineEndOffset(line)
        document.replaceString(start, end, indent + suffix)
    }

    private fun isXtcFile(file: PsiFile): Boolean = file.virtualFile?.extension == "x"

    private fun scheduleRepairAfterEnter(
        file: PsiFile,
        editor: Editor,
    ) {
        val project = file.project
        ApplicationManager.getApplication().invokeLater {
            if (editor.isDisposed) {
                return@invokeLater
            }
            WriteCommandAction.runWriteCommandAction(project) {
                val document = editor.document
                val currentLine = document.getLineNumber(editor.caretModel.offset)
                val fix =
                    when {
                        isCompactEmptyBlockSplit(document, currentLine) -> {
                            rewriteEmptyBlock(document, currentLine - 1, currentLine, currentLine + 1)
                        }

                        isClosingBraceCaretAfterBadSplit(document, currentLine) -> {
                            rewriteEmptyBlock(document, currentLine - 2, currentLine - 1, currentLine)
                        }

                        else -> {
                            null
                        }
                    }
                if (fix == null) {
                    logger.warn(
                        "deferredRepair: no fix at line=$currentLine " +
                            "prev='${safeLine(document, currentLine - 1)}' " +
                            "current='${safeLine(document, currentLine)}' " +
                            "next='${safeLine(document, currentLine + 1)}'",
                    )
                    return@runWriteCommandAction
                }

                val (declarationLine, bodyLine, closingLine) = fix
                val declarationIndent = lineIndent(document, declarationLine)
                val bodyIndent = declarationIndent + indentUnit(file)

                replaceLineWithIndent(document, bodyLine, bodyIndent)
                replaceLineWithIndent(document, closingLine, declarationIndent, suffix = "}")

                val bodyCaretOffset = document.getLineStartOffset(bodyLine) + bodyIndent.length
                editor.caretModel.moveToOffset(bodyCaretOffset)
                editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
                PsiDocumentManager.getInstance(project).commitDocument(document)

                logger.warn(
                    "deferredRepair: repaired empty-block split " +
                        "declarationLine=$declarationLine bodyLine=$bodyLine closingLine=$closingLine " +
                        "declarationIndent='${visualizeIndent(declarationIndent)}' " +
                        "bodyIndent='${visualizeIndent(bodyIndent)}'",
                )
            }
        }
    }

    private fun safeLine(
        document: com.intellij.openapi.editor.Document,
        line: Int,
    ): String =
        if (line < 0 || line >= document.lineCount) {
            "<out-of-range>"
        } else {
            lineText(document, line)
        }

    private fun visualizeIndent(indent: String): String = indent.replace("\t", "\\t").replace(" ", "·")
}
