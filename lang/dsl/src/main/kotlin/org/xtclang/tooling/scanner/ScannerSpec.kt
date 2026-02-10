package org.xtclang.tooling.scanner

/*
 * Scanner specification DSL - THE SINGLE SOURCE OF TRUTH.
 *
 * This defines the template string scanner logic in a declarative way.
 * Generators produce C implementation from this spec.
 *
 * DESIGN: The scanner is STATELESS - it uses valid_symbols to determine context.
 * Tree-sitter's grammar distinguishes single-line vs multiline templates by using
 * DIFFERENT external tokens for each. The scanner checks which tokens are valid
 * to know what type of template content to scan.
 *
 * Grammar structure:
 *   template_string_literal: `$"` + singleline_content* + singleline_end
 *   multiline_template_literal: $| + multiline_content* + multiline_end
 *
 * The `$"` and `$|` are regular grammar tokens (not external).
 * This allows tree-sitter's lexer to match them correctly.
 */

/** Token types emitted by the scanner - order must match grammar.js externals array */
enum class TokenType {
    // Single-line template tokens ($"...")
    SINGLELINE_CONTENT, // Content in $"..." template
    SINGLELINE_EXPR_START, // { in $"..." template
    SINGLELINE_END, // " end of $"..." template

    // Multiline template tokens ($|...|)
    MULTILINE_CONTENT, // Content in $|...| template
    MULTILINE_EXPR_START, // { in $|...| template
    MULTILINE_END, // End of multiline (no | continuation)

    // Shared token for expression end
    TEMPLATE_EXPR_END, // } - end of embedded expression (both types)

    // Statement block tokens for {{...}} patterns in templates
    // These handle blocks with arbitrary XTC code including nested braces
    MULTILINE_STMT_BLOCK, // {{...}} in multiline template - entire block content
    SINGLELINE_STMT_BLOCK, // {{...}} in single-line template - entire block content

    // TODO freeform text after 'TODO' keyword: consumes " message text" to end of line
    // The 'TODO' keyword is matched by tree-sitter's internal lexer.
    // This external token matches the TEXT that follows (space + message).
    // Together: 'TODO' (internal) + TODO_FREEFORM_TEXT (external) = freeform TODO
    TODO_FREEFORM_TEXT,

    // TODO freeform text that stops at ';' (for expression context in switch expressions)
    // Used when the ';' is needed as explicit terminator after the TODO text.
    // If no ';' before EOL, this token fails (forcing switch_statement interpretation).
    TODO_FREEFORM_UNTIL_SEMI,
}

/**
 * Scanner behavior rules and constants.
 */
object TemplateScannerSpec {
    val tokens = TokenType.entries

    /**
     * Scanner logic - determined by which tokens are valid:
     *
     * 1. If SINGLELINE_CONTENT or SINGLELINE_EXPR_START or SINGLELINE_END valid:
     *    → In single-line template: scan until '{' or `"`, handle escapes
     *
     * 2. If MULTILINE_CONTENT or MULTILINE_EXPR_START or MULTILINE_END valid:
     *    → In multiline template: scan until '{' or newline-without-continuation
     *
     * 3. If TEMPLATE_EXPR_END valid and see }:
     *    → End of embedded expression
     */
    object Rules {
        const val EXPR_START_CHAR = '{'
        const val EXPR_END_CHAR = '}'
        const val SINGLELINE_END_CHAR = '"'
        const val MULTILINE_CONTINUATION_CHAR = '|'
        const val NEWLINE_CHAR = '\n'
        const val BACKSLASH_CHAR = '\\'
    }
}
