package org.xvm.lsp.adapter

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xvm.lsp.adapter.XtcCompilerAdapter.Position
import org.xvm.lsp.adapter.XtcCompilerAdapter.Range
import org.xvm.lsp.adapter.XtcCompilerAdapter.TextEdit
import org.xvm.lsp.treesitter.XtcNode
import org.xvm.lsp.treesitter.XtcTree

/**
 * Handles all formatting concerns for the tree-sitter adapter:
 * - Document formatting (re-indentation, trailing whitespace, final newline)
 * - Range formatting (same as document but scoped to a line range)
 * - On-type formatting (auto-indent on Enter, closing brace/paren)
 *
 * Extracted from [TreeSitterAdapter] to keep that class focused on LSP
 * feature orchestration rather than formatting implementation details.
 *
 * Stateless — a single instance is shared across all formatting requests.
 */
class TreeSitterFormatter {
    private val logger: Logger = LoggerFactory.getLogger(TreeSitterFormatter::class.java)

    companion object {
        // --- AST node type sets for formatting ---

        internal val classBodyTypes =
            setOf(
                "class_body",
                "module_body",
                "package_body",
                "enum_body",
            )

        internal val blockTypes = setOf("block")

        internal val indentParentTypes =
            setOf(
                "class_body",
                "module_body",
                "package_body",
                "enum_body",
                "block",
                "case_clause",
            )

        internal val declarationTypes =
            setOf(
                "class_declaration",
                "interface_declaration",
                "mixin_declaration",
                "service_declaration",
                "const_declaration",
                "enum_declaration",
                "module_declaration",
            )

        internal val controlFlowTypes =
            setOf(
                "if_statement",
                "for_statement",
                "while_statement",
                "do_statement",
                "try_statement",
                "catch_clause",
                "using_statement",
            )

        internal val stringLiteralTypes =
            setOf(
                "string_literal",
                "multiline_literal",
                "template_literal",
                "multiline_template_literal",
            )

        internal val switchTypes =
            setOf(
                "switch_statement",
                "switch_expression",
            )

        internal val commentTypes =
            setOf(
                "doc_comment",
                "block_comment",
            )

        /** Sentinel: line is inside a string literal and must not be modified. */
        private const val SKIP_INDENT = -1
    }

    // ========================================================================
    // Document / range formatting
    // ========================================================================

    /**
     * Format an entire document: fix indentation, strip trailing whitespace, insert final newline.
     */
    fun formatDocument(
        tree: XtcTree,
        content: String,
        config: XtcFormattingConfig,
        options: XtcCompilerAdapter.FormattingOptions,
    ): List<TextEdit> = buildFormattingEdits(tree, content, config, options, startLine = 0, endLine = null)

    /**
     * Format a range of lines within the document.
     */
    fun formatRange(
        tree: XtcTree,
        content: String,
        range: Range,
        config: XtcFormattingConfig,
        options: XtcCompilerAdapter.FormattingOptions,
    ): List<TextEdit> = buildFormattingEdits(tree, content, config, options, startLine = range.start.line, endLine = range.end.line)

    /**
     * Build formatting edits for a line range (or the whole document when [endLine] is null).
     *
     * For each line, computes the correct indentation from the AST and emits an edit if
     * the actual indentation differs. Also strips trailing whitespace and inserts a final
     * newline for whole-document formatting.
     */
    private fun buildFormattingEdits(
        tree: XtcTree,
        content: String,
        config: XtcFormattingConfig,
        options: XtcCompilerAdapter.FormattingOptions,
        startLine: Int,
        endLine: Int?,
    ): List<TextEdit> =
        buildList {
            val lines = content.split("\n")
            val lastLine = endLine ?: (lines.size - 1)
            val isFullDocument = endLine == null

            for (i in startLine..minOf(lastLine, lines.size - 1)) {
                val line = lines[i]

                // Indentation fix
                val desiredIndent = computeLineIndent(tree, i, lines, config)
                if (desiredIndent != SKIP_INDENT) {
                    val currentIndent = line.takeWhile { it == ' ' }.length
                    if (desiredIndent != currentIndent && line.isNotBlank()) {
                        add(makeIndentEdit(i, currentIndent, desiredIndent))
                    }
                }

                // Trailing whitespace removal
                val trimmed = line.trimEnd()
                if (trimmed.length < line.length && (options.trimTrailingWhitespace || isFullDocument)) {
                    add(
                        TextEdit(
                            range =
                                Range(
                                    start = Position(i, trimmed.length),
                                    end = Position(i, line.length),
                                ),
                            newText = "",
                        ),
                    )
                }
            }

            // Insert final newline if missing. XTC default is true; user can override
            // via editor settings (insertFinalNewline = false).
            if (isFullDocument && options.insertFinalNewline && content.isNotEmpty() && !content.endsWith("\n")) {
                val lastIdx = lines.size - 1
                val lastCol = lines[lastIdx].length
                add(
                    TextEdit(
                        range =
                            Range(
                                start = Position(lastIdx, lastCol),
                                end = Position(lastIdx, lastCol),
                            ),
                        newText = "\n",
                    ),
                )
            }
        }.also {
            logger.info("format -> {} edits", it.size)
        }

    /**
     * Compute the correct indentation for a single line based on its AST context.
     *
     * Uses structural depth (counting indent-parent ancestors in the AST) rather than
     * reading indentation from the source. This is critical for correctly formatting
     * files that are already misindented.
     *
     * Returns [SKIP_INDENT] for lines inside string literals (must not be modified).
     * Returns 0 for blank lines.
     */
    private fun computeLineIndent(
        tree: XtcTree,
        lineIndex: Int,
        lines: List<String>,
        config: XtcFormattingConfig,
    ): Int {
        val line = lines[lineIndex]
        val trimmed = line.trimStart()

        // 1. Blank lines -> 0 indent (removes trailing whitespace on blank lines)
        if (trimmed.isEmpty()) return 0

        val firstNonWsCol = line.length - trimmed.length

        // 2. Check if inside string literal -> skip
        val node = tree.nodeAt(lineIndex, firstNonWsCol) ?: return 0
        if (isInsideStringLiteral(node)) return SKIP_INDENT

        // 3. Doc/block comment interior lines
        val commentAncestor =
            generateSequence(node) { it.parent }
                .firstOrNull { it.type in commentTypes }
        if (commentAncestor != null && commentAncestor.startLine != lineIndex) {
            // Interior or closing line of a comment, not the opening line.
            // Align " *" one space to the right of the comment's structural indent.
            return countIndentDepth(commentAncestor) * config.indentSize + 1
        }

        // 4. Closing brace -> match the opening construct
        if (trimmed.startsWith("}")) {
            val enclosingBlock =
                generateSequence(node) { it.parent }
                    .firstOrNull { it.type in blockTypes || it.type in classBodyTypes }
                    ?: return 0
            val ownerNode = enclosingBlock.parent ?: return 0
            return countIndentDepth(ownerNode) * config.indentSize
        }

        // 5. Closing paren -> match the opening paren's construct
        if (trimmed.startsWith(")")) {
            val enclosing =
                generateSequence(node) { it.parent }.firstOrNull { ancestor ->
                    val firstChild = ancestor.children.firstOrNull()
                    firstChild != null && firstChild.type == "(" && ancestor.startLine < lineIndex
                }
            if (enclosing != null) {
                return countIndentDepth(enclosing) * config.indentSize
            }
        }

        // 5b. Interior of multi-line paren construct -> continuation indent from owner
        val parenAncestor =
            generateSequence(node) { it.parent }.firstOrNull { ancestor ->
                val firstChild = ancestor.children.firstOrNull()
                firstChild != null && firstChild.type == "(" && ancestor.startLine < lineIndex
            }
        if (parenAncestor != null) {
            return countIndentDepth(parenAncestor) * config.indentSize + config.indentSize
        }

        // 6. Case labels -> same indent as switch
        if (trimmed.startsWith("case ") || trimmed.startsWith("default:") || trimmed.startsWith("default ")) {
            val switchNode =
                generateSequence(node) { it.parent }
                    .firstOrNull { it.type in switchTypes }
            if (switchNode != null) {
                return countIndentDepth(switchNode) * config.indentSize
            }
        }

        // 7. Continuation lines (extends, implements, incorporates, delegates)
        if (isContinuationLine(trimmed)) {
            val declNode =
                generateSequence(node) { it.parent }
                    .firstOrNull { it.type in declarationTypes }
            if (declNode != null) {
                return countIndentDepth(declNode) * config.indentSize + config.continuationIndentSize
            }
        }

        // 8. General case: count indent-parent ancestors for structural depth
        return countIndentDepth(node) * config.indentSize
    }

    /**
     * Count the number of indent-parent ancestors to determine structural nesting depth.
     * Includes the node itself if it is an indent parent type.
     */
    internal fun countIndentDepth(node: XtcNode): Int {
        var depth = 0
        generateSequence(node) { it.parent }.forEach { n ->
            if (n.type in indentParentTypes) depth++
        }
        return depth
    }

    // ========================================================================
    // On-type formatting (auto-indent)
    // ========================================================================

    /**
     * Handle a character typed by the user and return indentation edits.
     */
    fun onTypeFormatting(
        tree: XtcTree,
        line: Int,
        column: Int,
        ch: String,
        config: XtcFormattingConfig,
    ): List<TextEdit> =
        when (ch) {
            "\n" -> handleEnter(tree, line, config)
            "}" -> handleCloseBrace(tree, line, column, config)
            ")" -> handleCloseParen(tree, line, column, config)
            ";" -> emptyList()
            else -> emptyList()
        }

    /**
     * Handle Enter key: determine the correct indentation for the new line based on
     * what the previous line ends with and the AST context.
     */
    private fun handleEnter(
        tree: XtcTree,
        line: Int,
        config: XtcFormattingConfig,
    ): List<TextEdit> {
        val source = tree.source
        val lines = source.split("\n")

        val prevLineIndex = line - 1
        if (prevLineIndex < 0 || prevLineIndex >= lines.size) return emptyList()

        // Guard: don't adjust indentation inside string literals.
        val nodeAtCursor = tree.nodeAt(prevLineIndex, 0)
        if (nodeAtCursor != null && isInsideStringLiteral(nodeAtCursor)) return emptyList()

        val prevLine = lines[prevLineIndex]
        val prevTrimmed = prevLine.trimEnd()
        val prevIndent = prevLine.takeWhile { it == ' ' }.length

        // Doc/block comment continuation: insert " * " prefix on Enter inside comments.
        // Try multiple column positions since tree.nodeAt(line, 0) may return a node
        // outside the comment when there's leading whitespace.
        val commentEdit = handleCommentContinuation(tree, prevLineIndex, line, lines, prevTrimmed, prevIndent)
        if (commentEdit != null) return commentEdit

        val desiredIndent =
            when {
                // Continuation keyword ending with '{' -> body indent from declaration start.
                // Must be checked BEFORE the generic endsWith("{") to avoid matching as plain brace.
                isContinuationLine(prevTrimmed) && prevTrimmed.endsWith("{") -> {
                    findDeclarationIndent(tree, prevLineIndex) + config.indentSize
                }

                // Continuation keyword (extends, implements, etc.) NOT ending with '{'
                isContinuationLine(prevTrimmed) && !prevTrimmed.endsWith("{") -> {
                    findDeclarationIndent(tree, prevLineIndex) + config.continuationIndentSize
                }

                // Previous line ends with '{' -> indent one level deeper
                prevTrimmed.endsWith("{") -> {
                    prevIndent + config.indentSize
                }

                // Previous line ends with ':' inside a case_clause -> indent for case body
                prevTrimmed.endsWith(":") && isInsideCaseClause(tree, prevLineIndex) -> {
                    prevIndent + config.indentSize
                }

                // Previous line ends with '->' -> indent lambda/case expression body
                prevTrimmed.endsWith("->") -> {
                    prevIndent + config.indentSize
                }

                // Previous line ends with '}' -> maintain the brace's indent level
                prevTrimmed.endsWith("}") -> {
                    prevIndent
                }

                // Default: use AST context
                else -> {
                    computeDesiredIndent(tree, prevLineIndex, prevIndent, config.indentSize)
                }
            }

        val currentIndent =
            if (line < lines.size) {
                lines[line].takeWhile { it == ' ' }.length
            } else {
                0
            }

        if (desiredIndent == currentIndent) return emptyList()

        return listOf(makeIndentEdit(line, currentIndent, desiredIndent))
    }

    /**
     * Handle closing brace: outdent the current line to match the line where the
     * corresponding opening '{' lives.
     */
    private fun handleCloseBrace(
        tree: XtcTree,
        line: Int,
        column: Int,
        config: XtcFormattingConfig,
    ): List<TextEdit> {
        val source = tree.source
        val lines = source.split("\n")
        if (line < 0 || line >= lines.size) return emptyList()

        val currentIndent = lines[line].takeWhile { it == ' ' }.length

        // Find the '}' character position on the line and look up the AST node there.
        // LSP sends the cursor position *after* the typed character, so try both the
        // reported column and the actual '}' position on the line.
        val braceCol = lines[line].indexOf('}')
        val node =
            tree.nodeAt(line, if (braceCol >= 0) braceCol else column)
                ?: tree.nodeAt(line, column)
                ?: return emptyList()

        // Walk up to find the block or class_body that this '}' closes
        val enclosingBlock =
            generateSequence(node) { it.parent }
                .firstOrNull { it.type in blockTypes || it.type in classBodyTypes }
                ?: return emptyList()

        // Find the reference line: the construct that owns the block
        val ownerNode = enclosingBlock.parent
        val refLine =
            when (ownerNode?.type) {
                in declarationTypes,
                "method_declaration",
                "function_declaration",
                "constructor_declaration",
                -> ownerNode!!.startLine

                in controlFlowTypes -> ownerNode!!.startLine

                else -> enclosingBlock.startLine
            }

        val desiredIndent = getLineIndent(source, refLine)

        if (desiredIndent == currentIndent) return emptyList()

        return listOf(makeIndentEdit(line, currentIndent, desiredIndent))
    }

    /**
     * Handle closing parenthesis: outdent to match the line where the opening '(' lives.
     * This handles multi-line parameter lists, argument lists, and condition expressions.
     */
    private fun handleCloseParen(
        tree: XtcTree,
        line: Int,
        column: Int,
        @Suppress("UNUSED_PARAMETER") config: XtcFormattingConfig,
    ): List<TextEdit> {
        val source = tree.source
        val lines = source.split("\n")
        if (line < 0 || line >= lines.size) return emptyList()

        val currentIndent = lines[line].takeWhile { it == ' ' }.length

        // Find the ')' on this line and look up the AST node.
        val parenCol = lines[line].indexOf(')')
        val node =
            tree.nodeAt(line, if (parenCol >= 0) parenCol else column)
                ?: tree.nodeAt(line, column)
                ?: return emptyList()

        // Walk up to find a node whose opening '(' is on a different line.
        // Parenthesized constructs in tree-sitter: argument_list, parameter_list,
        // parenthesized_expression, condition, etc. We look for any ancestor that
        // starts with '(' (its first child is '(') and spans multiple lines.
        val enclosing =
            generateSequence(node) { it.parent }.firstOrNull { ancestor ->
                val firstChild = ancestor.children.firstOrNull()
                firstChild != null && firstChild.type == "(" && ancestor.startLine < line
            } ?: return emptyList()

        val desiredIndent = getLineIndent(source, enclosing.startLine)
        if (desiredIndent == currentIndent) return emptyList()

        return listOf(makeIndentEdit(line, currentIndent, desiredIndent))
    }

    /**
     * Handle Enter inside a doc comment or block comment.
     *
     * Returns a list of edits that insert " * " continuation prefix on the new line,
     * aligned with the "*" on the opening line. Returns null if not inside a comment.
     */
    private fun handleCommentContinuation(
        tree: XtcTree,
        prevLineIndex: Int,
        line: Int,
        lines: List<String>,
        prevTrimmed: String,
        prevIndent: Int,
    ): List<TextEdit>? {
        // Try multiple column positions on the previous line to find a comment node.
        // Column 0 may land outside the comment when there's leading whitespace.
        val prevLineLen = lines.getOrNull(prevLineIndex)?.length ?: 0
        val columnsToTry = listOf(0, prevIndent, (prevLineLen - 1).coerceAtLeast(0))
        val commentType =
            columnsToTry.firstNotNullOfOrNull { col ->
                val node = tree.nodeAt(prevLineIndex, col) ?: return@firstNotNullOfOrNull null
                generateSequence(node) { it.parent }
                    .firstOrNull { it.type == "doc_comment" || it.type == "block_comment" }
            } ?: return null

        // Don't continue after the closing "*/" line
        if (prevTrimmed.endsWith("*/")) return null

        // Determine the prefix to insert. The " * " aligns with the opening "/**" or "/*":
        // The opening line's indent + 1 space gives us the " *" column.
        val commentStartLine = commentType.startLine
        val commentIndent =
            if (commentStartLine in lines.indices) {
                lines[commentStartLine].takeWhile { it == ' ' }.length
            } else {
                prevIndent
            }

        val prefix = " ".repeat(commentIndent) + " * "
        val closing = " ".repeat(commentIndent) + " */"

        val currentIndent =
            if (line < lines.size) {
                lines[line].takeWhile { it == ' ' }.length
            } else {
                0
            }

        // Doc comment skeleton: when Enter is pressed right after "/**" and the next line
        // has "*/" (auto-closed), insert " * " on the cursor line and keep the closing.
        // This creates the skeleton: /** \n * |\n */
        val isOpeningLine = prevTrimmed.endsWith("/**") || prevTrimmed.endsWith("/*")
        val nextLineIsClose = line < lines.size && lines[line].trimStart().startsWith("*/")
        if (isOpeningLine && nextLineIsClose) {
            // Insert " * \n " + closing on the next line
            return listOf(
                TextEdit(
                    range =
                        Range(
                            start = Position(line, 0),
                            end = Position(line, currentIndent),
                        ),
                    newText = prefix + "\n" + closing,
                ),
            )
        }

        // Normal continuation: just insert " * " prefix
        return listOf(
            TextEdit(
                range =
                    Range(
                        start = Position(line, 0),
                        end = Position(line, currentIndent),
                    ),
                newText = prefix,
            ),
        )
    }

    // --- Helpers ---

    private fun isContinuationLine(trimmedLine: String): Boolean {
        val stripped = trimmedLine.trimStart()
        return stripped.startsWith("extends ") ||
            stripped.startsWith("implements ") ||
            stripped.startsWith("incorporates ") ||
            stripped.startsWith("delegates ")
    }

    private fun isInsideCaseClause(
        tree: XtcTree,
        lineIndex: Int,
    ): Boolean {
        val node = tree.nodeAt(lineIndex, 0) ?: return false
        return generateSequence(node) { it.parent }
            .any { it.type == "case_clause" }
    }

    internal fun isInsideStringLiteral(node: XtcNode): Boolean =
        generateSequence(node) { it.parent }
            .any { it.type in stringLiteralTypes }

    private fun findDeclarationIndent(
        tree: XtcTree,
        lineIndex: Int,
    ): Int {
        val node = tree.nodeAt(lineIndex, 0) ?: return 0
        val decl =
            generateSequence(node) { it.parent }
                .firstOrNull { it.type in declarationTypes }
                ?: return 0
        return getLineIndent(tree.source, decl.startLine)
    }

    private fun computeDesiredIndent(
        tree: XtcTree,
        prevLineIndex: Int,
        prevIndent: Int,
        indentSize: Int,
    ): Int {
        val prevLineText = tree.source.split("\n").getOrNull(prevLineIndex) ?: return prevIndent
        val lastNonSpace = prevLineText.indexOfLast { !it.isWhitespace() }
        if (lastNonSpace < 0) return prevIndent

        val node = tree.nodeAt(prevLineIndex, lastNonSpace) ?: return prevIndent

        val ancestor =
            generateSequence(node) { it.parent }
                .firstOrNull { it.type in indentParentTypes }

        return if (ancestor != null) {
            val ownerLine = ancestor.parent?.startLine ?: ancestor.startLine
            getLineIndent(tree.source, ownerLine) + indentSize
        } else {
            prevIndent
        }
    }

    private fun getLineIndent(
        source: String,
        lineNumber: Int,
    ): Int {
        val lines = source.split("\n")
        if (lineNumber < 0 || lineNumber >= lines.size) return 0
        return lines[lineNumber].takeWhile { it == ' ' }.length
    }

    private fun makeIndentEdit(
        line: Int,
        currentIndent: Int,
        desiredIndent: Int,
    ): TextEdit =
        TextEdit(
            range =
                Range(
                    start = Position(line, 0),
                    end = Position(line, currentIndent),
                ),
            newText = " ".repeat(desiredIndent),
        )
}
