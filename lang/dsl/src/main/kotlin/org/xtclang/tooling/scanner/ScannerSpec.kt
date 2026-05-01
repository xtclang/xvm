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

    // Single `>` used as the closer of type arguments / type parameters /
    // angle-bracket type lists. The point of routing this through the external
    // scanner is to break tree-sitter's greedy `>>` tokenization in nested
    // type-argument contexts -- e.g. `Function<<Int, String>, <Int>>` where
    // the inner `>` closes the inner list and the next `>` closes the outer.
    // The internal lexer would otherwise consume `>>` as a single right-shift
    // operator before the parser could disambiguate.
    //
    // This token only fires when the parser actively asks for it (i.e. it is
    // in `valid_symbols`), which happens only inside type-position rules.
    // In expression context the internal lexer keeps tokenizing `>>` / `>>>` /
    // `>=` / `>>=` / `>>>=` as single tokens unchanged.
    TYPE_GT,

    // Newline / multiline-template continuation. Declared in `extras` so the
    // parser silently skips it. The scanner emits this whenever peek is
    // `\n`; if we're inside an interpolation expression of a multiline
    // template, the scanner additionally consumes the trailing `   |`
    // continuation marker so the `|` is not lexed as a bitwise-OR operator.
    // Listed FIRST in extras so it is consulted before the internal `/\s/`
    // regex; the longer match wins.
    MULTILINE_CONTINUATION,

    // Opening `(` of a tuple_assignment, e.g. `(i1, Int i2) = expr;`. The
    // scanner peeks past the matching `)` looking for `=` (and not `==`/`=>`)
    // before any `;` / `{` / `}` / EOF; if found, it consumes the `(` and
    // emits this token. The grammar's `tuple_assignment` rule starts with
    // this token instead of a literal `(`, so the LR parser commits to
    // tuple_assignment deterministically at the `(` shift, even when the
    // tuple's content shape would otherwise admit a `tuple_type`
    // (variable_declaration) or `parenthesized_expression` interpretation.
    //
    // This token is only requested at statement start (where
    // tuple_assignment lives in the `_statement` choice list). At every
    // other position it stays out of valid_symbols, so the scanner is a
    // no-op and the internal lexer keeps tokenizing `(` normally.
    TUPLE_ASSIGN_LPAREN,
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
