package org.xtclang.tooling.scanner

/**
 * Scanner specification DSL - THE SINGLE SOURCE OF TRUTH.
 *
 * This defines the template string scanner logic in a declarative way.
 * Generators produce both Kotlin and C implementations from this spec.
 *
 * IMPORTANT: Tree-sitter's valid_symbols array tells us what tokens are expected.
 * We use this to determine context rather than maintaining internal state, because:
 * 1. Tree-sitter handles $" and $| as regular tokens (not external)
 * 2. When tree-sitter matches $", it doesn't call the scanner
 * 3. The scanner is only called when tree-sitter expects external tokens
 *
 * Context is inferred from valid_symbols:
 * - valid[TEMPLATE_CONTENT] || valid[TEMPLATE_EXPR_START] || valid[TEMPLATE_END] → inside template
 * - valid[TEMPLATE_EXPR_END] → inside embedded expression
 */

/** Token types emitted by the scanner - order must match grammar.js externals array */
enum class TokenType {
    // Note: Template START ($" and $|) is a regular token, not external
    // This allows tree-sitter's lexer to see and match it
    TEMPLATE_CONTENT,         // String content between expressions
    TEMPLATE_EXPR_START,      // { - start of embedded expression
    TEMPLATE_EXPR_END,        // } - end of embedded expression
    TEMPLATE_END,             // " - end of template
}

/**
 * Scanner behavior rules - what to do when certain tokens are expected.
 * The generator uses this to produce code that checks valid_symbols.
 */
object TemplateScannerSpec {

    val tokens = TokenType.entries

    /**
     * Scanner logic based on valid_symbols (what tree-sitter expects).
     *
     * IMPORTANT: During error recovery, tree-sitter may set ALL external tokens valid.
     * We detect this and return false to avoid consuming content incorrectly.
     *
     * When inside a template (TEMPLATE_CONTENT/TEMPLATE_EXPR_START/TEMPLATE_END valid,
     * but NOT TEMPLATE_EXPR_END - that's mutually exclusive):
     * 1. Try to scan content (characters until { or ")
     * 2. If at {, emit TEMPLATE_EXPR_START
     * 3. If at ", emit TEMPLATE_END
     *
     * When inside an expression (TEMPLATE_EXPR_END valid):
     * 1. If at }, emit TEMPLATE_EXPR_END
     * 2. Otherwise return false (let tree-sitter parse the expression)
     */
    object Rules {
        // Template start sequences
        const val TEMPLATE_START_SEQ = "\$\""
        const val MULTILINE_START_SEQ = "\$|"

        // Stop chars for content scanning - { starts expression, " ends template
        const val CONTENT_STOP_CHARS = "{\""

        // Characters that start/end expressions and templates
        const val EXPR_START_CHAR = '{'
        const val EXPR_END_CHAR = '}'
        const val TEMPLATE_END_CHAR = '"'
    }
}
