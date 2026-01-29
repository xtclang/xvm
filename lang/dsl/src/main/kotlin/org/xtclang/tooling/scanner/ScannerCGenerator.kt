package org.xtclang.tooling.scanner

import java.io.File

/**
 * Generates scanner.c from ScannerSpec.
 *
 * The scanner is STATELESS - it uses valid_symbols to determine context.
 * Tree-sitter's grammar uses different external tokens for single-line vs multiline
 * templates, so the scanner checks which tokens are valid to know what to do.
 */
object ScannerCGenerator {

    fun generate(): String = buildString {
        appendLine(HEADER)
        appendLine()
        appendLine(generateEnums())
        appendLine()
        appendLine(generateLifecycleFunctions())
        appendLine()
        appendLine(generateHelperFunctions())
        appendLine()
        appendLine(generateScanFunction())
    }

    private val HEADER = """
/**
 * Tree-sitter external scanner for XTC template string literals.
 *
 * THIS FILE IS GENERATED from ScannerSpec.kt - DO NOT EDIT MANUALLY.
 *
 * Regenerate with: ./gradlew :lang:dsl:generateScannerC
 *
 * DESIGN: The scanner is STATELESS. It uses valid_symbols to determine context:
 * - SINGLELINE_* tokens valid → in single-line template ($"...")
 * - MULTILINE_* tokens valid → in multiline template ($|...|)
 *
 * Tree-sitter's grammar handles $" and $| as regular tokens.
 * The scanner only handles content and delimiters AFTER the start is matched.
 */

#include "tree_sitter/parser.h"
#include <stdbool.h>

// Debug flag - uncomment to enable debug output
// #define SCANNER_DEBUG 1

#ifdef SCANNER_DEBUG
#include <stdio.h>
#endif
    """.trimIndent()

    private fun generateEnums(): String = buildString {
        appendLine("// External token types - must match grammar.js externals array")
        appendLine("// Generated from: ${TemplateScannerSpec.tokens.joinToString { it.name }}")
        appendLine("enum TokenType {")
        TemplateScannerSpec.tokens.forEachIndexed { index, token ->
            val comma = if (index < TemplateScannerSpec.tokens.size - 1) "," else ""
            appendLine("    ${token.name}$comma")
        }
        appendLine("};")
    }

    private fun generateLifecycleFunctions(): String = """
// Create scanner (stateless - no state needed)
void *tree_sitter_xtc_external_scanner_create(void) {
    return NULL;
}

// Destroy scanner
void tree_sitter_xtc_external_scanner_destroy(void *payload) {
    (void)payload;
}

// Serialize (nothing to serialize)
unsigned tree_sitter_xtc_external_scanner_serialize(void *payload, char *buffer) {
    (void)payload;
    (void)buffer;
    return 0;
}

// Deserialize (nothing to deserialize)
void tree_sitter_xtc_external_scanner_deserialize(void *payload, const char *buffer, unsigned length) {
    (void)payload;
    (void)buffer;
    (void)length;
}
    """.trimIndent()

    private fun generateHelperFunctions(): String = """
// Helper: advance lexer
static inline void advance(TSLexer *lexer) {
    lexer->advance(lexer, false);
}

// Helper: check if at end
static inline bool at_eof(TSLexer *lexer) {
    return lexer->eof(lexer);
}

// Helper: peek current character
static inline int32_t peek(TSLexer *lexer) {
    return lexer->lookahead;
}

// Helper: check if horizontal whitespace (space or tab)
static inline bool is_hspace(int32_t c) {
    return c == ' ' || c == '\t';
}
    """.trimIndent()

    /** Escape a character for C char literal */
    private fun escapeForC(c: Char): String = when (c) {
        '\'' -> "\\'"
        '\\' -> "\\\\"
        '\n' -> "\\n"
        '\r' -> "\\r"
        '\t' -> "\\t"
        '"' -> "\\\""
        else -> c.toString()
    }

    private fun generateScanFunction(): String {
        val exprStart = escapeForC(TemplateScannerSpec.Rules.EXPR_START_CHAR)
        val exprEnd = escapeForC(TemplateScannerSpec.Rules.EXPR_END_CHAR)
        val singlelineEnd = escapeForC(TemplateScannerSpec.Rules.SINGLELINE_END_CHAR)
        val continuation = escapeForC(TemplateScannerSpec.Rules.MULTILINE_CONTINUATION_CHAR)
        val newline = escapeForC(TemplateScannerSpec.Rules.NEWLINE_CHAR)
        val backslash = escapeForC(TemplateScannerSpec.Rules.BACKSLASH_CHAR)

        return """
// Main scan function - STATELESS, uses valid_symbols to determine context
bool tree_sitter_xtc_external_scanner_scan(
    void *payload,
    TSLexer *lexer,
    const bool *valid_symbols
) {
    (void)payload;

    if (at_eof(lexer)) return false;

    // Determine context from valid_symbols
    bool in_singleline = valid_symbols[SINGLELINE_CONTENT] ||
                         valid_symbols[SINGLELINE_EXPR_START] ||
                         valid_symbols[SINGLELINE_END];

    bool in_multiline = valid_symbols[MULTILINE_CONTENT] ||
                        valid_symbols[MULTILINE_EXPR_START] ||
                        valid_symbols[MULTILINE_END];

    bool in_expr = valid_symbols[TEMPLATE_EXPR_END];

    // During error recovery, tree-sitter may set ALL external tokens valid.
    // If both single-line AND multiline tokens are valid simultaneously,
    // we're likely in error recovery - return false to let tree-sitter handle it.
    if (in_singleline && in_multiline) {
        return false;
    }

#ifdef SCANNER_DEBUG
    fprintf(stderr, "[SCANNER] char='%c'(0x%02x) single=%d multi=%d expr=%d\n",
        (peek(lexer) >= 32 && peek(lexer) < 127) ? (char)peek(lexer) : '?',
        peek(lexer), in_singleline, in_multiline, in_expr);
#endif

    // =========================================================================
    // Inside expression: only look for closing brace
    // =========================================================================
    if (in_expr && peek(lexer) == '$exprEnd') {
        advance(lexer);
        lexer->mark_end(lexer);
        lexer->result_symbol = TEMPLATE_EXPR_END;
        return true;
    }

    // =========================================================================
    // Single-line template ($"...")
    // =========================================================================
    if (in_singleline) {
        bool has_content = false;

        while (!at_eof(lexer)) {
            int32_t c = peek(lexer);

            // Expression start: {
            if (c == '$exprStart') {
                if (has_content && valid_symbols[SINGLELINE_CONTENT]) {
                    lexer->mark_end(lexer);
                    lexer->result_symbol = SINGLELINE_CONTENT;
                    return true;
                }
                if (valid_symbols[SINGLELINE_EXPR_START]) {
                    advance(lexer);
                    lexer->mark_end(lexer);
                    lexer->result_symbol = SINGLELINE_EXPR_START;
                    return true;
                }
                break;
            }

            // Template end: "
            if (c == '$singlelineEnd') {
                if (has_content && valid_symbols[SINGLELINE_CONTENT]) {
                    lexer->mark_end(lexer);
                    lexer->result_symbol = SINGLELINE_CONTENT;
                    return true;
                }
                if (valid_symbols[SINGLELINE_END]) {
                    advance(lexer);
                    lexer->mark_end(lexer);
                    lexer->result_symbol = SINGLELINE_END;
                    return true;
                }
                break;
            }

            // Escape sequence
            if (c == '$backslash') {
                advance(lexer);
                if (!at_eof(lexer)) advance(lexer);
                has_content = true;
                continue;
            }

            advance(lexer);
            has_content = true;
        }

        if (has_content && valid_symbols[SINGLELINE_CONTENT]) {
            lexer->mark_end(lexer);
            lexer->result_symbol = SINGLELINE_CONTENT;
            return true;
        }
        return false;
    }

    // =========================================================================
    // Multiline template ($|...|)
    // =========================================================================
    if (in_multiline) {
        bool has_content = false;

        while (!at_eof(lexer)) {
            int32_t c = peek(lexer);

            // Expression start: {
            if (c == '$exprStart') {
                if (has_content && valid_symbols[MULTILINE_CONTENT]) {
                    lexer->mark_end(lexer);
                    lexer->result_symbol = MULTILINE_CONTENT;
                    return true;
                }
                if (valid_symbols[MULTILINE_EXPR_START]) {
                    advance(lexer);
                    lexer->mark_end(lexer);
                    lexer->result_symbol = MULTILINE_EXPR_START;
                    return true;
                }
                break;
            }

            // Newline: check for continuation
            if (c == '$newline') {
                advance(lexer);
                has_content = true;

                // Skip horizontal whitespace
                while (!at_eof(lexer) && is_hspace(peek(lexer))) {
                    advance(lexer);
                }

                // Continuation marker |
                if (peek(lexer) == '$continuation') {
                    advance(lexer);
                    continue;  // Keep scanning content
                }

                // No continuation - template ends
                if (valid_symbols[MULTILINE_END]) {
                    lexer->mark_end(lexer);
                    lexer->result_symbol = MULTILINE_END;
                    return true;
                }
                break;
            }

            // Escape sequence
            if (c == '$backslash') {
                advance(lexer);
                if (!at_eof(lexer)) advance(lexer);
                has_content = true;
                continue;
            }

            advance(lexer);
            has_content = true;
        }

        // EOF ends multiline template
        if (at_eof(lexer) && valid_symbols[MULTILINE_END]) {
            lexer->mark_end(lexer);
            lexer->result_symbol = MULTILINE_END;
            return true;
        }

        if (has_content && valid_symbols[MULTILINE_CONTENT]) {
            lexer->mark_end(lexer);
            lexer->result_symbol = MULTILINE_CONTENT;
            return true;
        }
        return false;
    }

    return false;
}
        """.trimIndent()
    }
}

/** Main entry point for CLI */
fun main(args: Array<String>) {
    val outputPath = args.getOrNull(0)
    val generated = ScannerCGenerator.generate()

    if (outputPath != null) {
        File(outputPath).writeText(generated)
        println("Generated scanner.c at: $outputPath")
    } else {
        println(generated)
    }
}