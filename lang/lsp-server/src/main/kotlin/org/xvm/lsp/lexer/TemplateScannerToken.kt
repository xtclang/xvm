package org.xvm.lsp.lexer

/**
 * Token types emitted by the template scanner.
 *
 * These correspond to the external tokens that tree-sitter's grammar will reference.
 */
enum class TemplateTokenType {
    /** Start of template string: `$"` */
    TEMPLATE_START,

    /** Start of multiline template: `$|` */
    TEMPLATE_MULTILINE_START,

    /** String content between expressions (may be empty) */
    TEMPLATE_CONTENT,

    /** Start of embedded expression: `{` */
    TEMPLATE_EXPR_START,

    /** End of embedded expression: `}` */
    TEMPLATE_EXPR_END,

    /** End of template string: `"` (or end of multiline) */
    TEMPLATE_END,

    /** Error token (unterminated string, etc.) */
    ERROR,
}

/**
 * Immutable token representing a lexical unit within a template string.
 *
 * @property type The token type
 * @property startOffset Start position in source (inclusive)
 * @property endOffset End position in source (exclusive)
 * @property value Optional string value (for TEMPLATE_CONTENT)
 */
data class TemplateScannerToken(
    val type: TemplateTokenType,
    val startOffset: Int,
    val endOffset: Int,
    val value: String? = null,
) {
    val length: Int get() = endOffset - startOffset

    companion object {
        fun templateStart(
            start: Int,
            end: Int,
        ) = TemplateScannerToken(TemplateTokenType.TEMPLATE_START, start, end)

        fun templateMultilineStart(
            start: Int,
            end: Int,
        ) = TemplateScannerToken(TemplateTokenType.TEMPLATE_MULTILINE_START, start, end)

        fun content(
            start: Int,
            end: Int,
            value: String,
        ) = TemplateScannerToken(TemplateTokenType.TEMPLATE_CONTENT, start, end, value)

        fun exprStart(
            start: Int,
            end: Int,
        ) = TemplateScannerToken(TemplateTokenType.TEMPLATE_EXPR_START, start, end)

        fun exprEnd(
            start: Int,
            end: Int,
        ) = TemplateScannerToken(TemplateTokenType.TEMPLATE_EXPR_END, start, end)

        fun templateEnd(
            start: Int,
            end: Int,
        ) = TemplateScannerToken(TemplateTokenType.TEMPLATE_END, start, end)

        fun error(
            start: Int,
            end: Int,
            message: String,
        ) = TemplateScannerToken(TemplateTokenType.ERROR, start, end, message)
    }
}
