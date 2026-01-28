package org.xtclang.tooling.scanner

/**
 * Generates scanner.c from ScannerSpec.
 *
 * This ensures the C scanner is always in sync with the Kotlin specification.
 * All scanner logic comes from TemplateScannerSpec - no hardcoded C logic here.
 *
 * Template start ($" and $|) is a regular token in the grammar.
 * The scanner only handles content/delimiters AFTER tree-sitter matches the start.
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
 * Template start ($" and $|) is handled by tree-sitter's regular lexer.
 * This scanner handles content/delimiters AFTER the start is matched.
 *
 * The scanner uses valid_symbols to know when we're in a template:
 * - valid[TEMPLATE_CONTENT] || valid[TEMPLATE_EXPR_START] || valid[TEMPLATE_END] -> in template
 * - valid[TEMPLATE_EXPR_END] -> in expression
 */

#include "tree_sitter/parser.h"
#include <stdbool.h>
#include <stdio.h>

// Debug flag - uncomment to enable debug output
// #define SCANNER_DEBUG 1
    """.trimIndent()

    private fun generateEnums(): String = buildString {
        // Token types enum - must match grammar.js externals array order
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
// Create scanner state (stateless - uses valid_symbols for context)
void *tree_sitter_xtc_external_scanner_create(void) {
    return NULL;
}

// Destroy scanner state
void tree_sitter_xtc_external_scanner_destroy(void *payload) {
    (void)payload;
}

// Serialize scanner state
unsigned tree_sitter_xtc_external_scanner_serialize(void *payload, char *buffer) {
    (void)payload;
    (void)buffer;
    return 0;
}

// Deserialize scanner state
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

// Helper: check if char is a stop character for content scanning
// Generated from: CONTENT_STOP_CHARS = "${TemplateScannerSpec.Rules.CONTENT_STOP_CHARS}"
static inline bool is_stop_char(int32_t c) {
    return c == '${TemplateScannerSpec.Rules.EXPR_START_CHAR}' ||
           c == '${TemplateScannerSpec.Rules.TEMPLATE_END_CHAR}';
}
    """.trimIndent()

    /**
     * Generate the main scan function using valid_symbols for context.
     */
    private fun generateScanFunction(): String = buildString {
        appendLine("// Main scan function - uses valid_symbols to determine context")
        appendLine("bool tree_sitter_xtc_external_scanner_scan(")
        appendLine("    void *payload,")
        appendLine("    TSLexer *lexer,")
        appendLine("    const bool *valid_symbols")
        appendLine(") {")
        appendLine("    (void)payload;")
        appendLine()
        appendLine("    if (at_eof(lexer)) return false;")
        appendLine()

        appendLine("#ifdef SCANNER_DEBUG")
        appendLine("    fprintf(stderr, \"[SCANNER] col=%u char='%c'(0x%02x) valid=[%d,%d,%d,%d]\\n\",")
        appendLine("        lexer->get_column(lexer),")
        appendLine("        (peek(lexer) >= 32 && peek(lexer) < 127) ? (char)peek(lexer) : '?',")
        appendLine("        peek(lexer),")
        appendLine("        valid_symbols[TEMPLATE_CONTENT], valid_symbols[TEMPLATE_EXPR_START],")
        appendLine("        valid_symbols[TEMPLATE_EXPR_END], valid_symbols[TEMPLATE_END]);")
        appendLine("#endif")
        appendLine()

        // Check if we're in a template (tree-sitter expects content/start/end)
        appendLine("    // Check if we're in template context (after tree-sitter matched $\" or $|)")
        appendLine("    bool in_template = valid_symbols[TEMPLATE_CONTENT] ||")
        appendLine("                       valid_symbols[TEMPLATE_EXPR_START] ||")
        appendLine("                       valid_symbols[TEMPLATE_END];")
        appendLine()

        // Check if we're in expression context
        appendLine("    // Check if we're at end of expression")
        appendLine("    bool in_expr = valid_symbols[TEMPLATE_EXPR_END];")
        appendLine()

        appendLine("    if (in_template) {")
        appendLine("        // First, try to scan content (chars until stop chars)")
        appendLine("        bool has_content = false;")
        appendLine("        while (!at_eof(lexer)) {")
        appendLine("            int32_t c = peek(lexer);")
        appendLine("            if (is_stop_char(c)) break;")
        appendLine("            // Handle escape sequences")
        appendLine("            if (c == '\\\\') {")
        appendLine("                advance(lexer);")
        appendLine("                if (!at_eof(lexer)) advance(lexer);")
        appendLine("                has_content = true;")
        appendLine("                continue;")
        appendLine("            }")
        appendLine("            advance(lexer);")
        appendLine("            has_content = true;")
        appendLine("        }")
        appendLine()
        appendLine("        if (has_content && valid_symbols[TEMPLATE_CONTENT]) {")
        appendLine("            lexer->mark_end(lexer);")
        appendLine("            lexer->result_symbol = TEMPLATE_CONTENT;")
        appendLine("            return true;")
        appendLine("        }")
        appendLine()
        appendLine("        // At a stop char - check what it is")
        appendLine("        int32_t c = peek(lexer);")
        appendLine()
        appendLine("        // Expression start: ${TemplateScannerSpec.Rules.EXPR_START_CHAR}")
        appendLine("        if (c == '${TemplateScannerSpec.Rules.EXPR_START_CHAR}' && valid_symbols[TEMPLATE_EXPR_START]) {")
        appendLine("            advance(lexer);")
        appendLine("            lexer->mark_end(lexer);")
        appendLine("            lexer->result_symbol = TEMPLATE_EXPR_START;")
        appendLine("            return true;")
        appendLine("        }")
        appendLine()
        appendLine("        // Template end: ${escapeChar(TemplateScannerSpec.Rules.TEMPLATE_END_CHAR)}")
        appendLine("        if (c == '${escapeChar(TemplateScannerSpec.Rules.TEMPLATE_END_CHAR)}' && valid_symbols[TEMPLATE_END]) {")
        appendLine("            advance(lexer);")
        appendLine("            lexer->mark_end(lexer);")
        appendLine("            lexer->result_symbol = TEMPLATE_END;")
        appendLine("            return true;")
        appendLine("        }")
        appendLine("    }")
        appendLine()

        appendLine("    if (in_expr) {")
        appendLine("        // Inside expression: only match } to end it")
        appendLine("        if (peek(lexer) == '${TemplateScannerSpec.Rules.EXPR_END_CHAR}' && valid_symbols[TEMPLATE_EXPR_END]) {")
        appendLine("            advance(lexer);")
        appendLine("            lexer->mark_end(lexer);")
        appendLine("            lexer->result_symbol = TEMPLATE_EXPR_END;")
        appendLine("            return true;")
        appendLine("        }")
        appendLine("    }")
        appendLine()

        appendLine("    return false;")
        appendLine("}")
    }

    private fun escapeChar(c: Char): String = when (c) {
        '\'' -> "\\'"
        '\\' -> "\\\\"
        '\n' -> "\\n"
        '\r' -> "\\r"
        '\t' -> "\\t"
        '"' -> "\\\""
        else -> c.toString()
    }
}

/** Main entry point for CLI */
fun main(args: Array<String>) {
    val outputPath = args.getOrNull(0)
    val generated = ScannerCGenerator.generate()

    if (outputPath != null) {
        java.io.File(outputPath).writeText(generated)
        println("Generated scanner.c at: $outputPath")
    } else {
        println(generated)
    }
}