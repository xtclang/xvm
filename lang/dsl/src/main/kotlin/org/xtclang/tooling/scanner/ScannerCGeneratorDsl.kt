package org.xtclang.tooling.scanner

import java.io.File

/**
 * Generates scanner.c using the Kotlin C code DSL.
 *
 * This generator uses composable DSL builders for better maintainability.
 * The scanner is STATELESS - it uses valid_symbols to determine context.
 */
object ScannerCGeneratorDsl {
    fun generate(debug: Boolean = false): String =
        cFile {
            header(fileHeader(debug))
            raw(generateEnums())
            raw(generateLifecycleFunctions())
            raw(generateHelperFunctions())
            raw(generateScanFunction())
        }

    private fun fileHeader(debug: Boolean): String =
        """
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
${if (debug) "\n#define SCANNER_DEBUG 1" else ""}
#ifdef SCANNER_DEBUG
#include <stdio.h>
#endif"""

    private fun generateEnums(): String =
        cEnum("TokenType") {
            comment("External token types - must match grammar.js externals array")
            comment("Generated from: ${TemplateScannerSpec.tokens.joinToString { it.name }}")
            TemplateScannerSpec.tokens.forEach { token ->
                entry(token.name)
            }
        }

    private fun generateLifecycleFunctions(): String =
        buildString {
            // Create scanner (stateless - no state needed)
            appendLine(
                cFunction("void *", "tree_sitter_xtc_external_scanner_create") {
                    param("void", "")
                    body {
                        returnStmt("NULL")
                    }
                },
            )
            appendLine()

            // Destroy scanner
            appendLine(
                cFunction("void", "tree_sitter_xtc_external_scanner_destroy") {
                    param("void *", "payload")
                    body {
                        voidCast("payload")
                    }
                },
            )
            appendLine()

            // Serialize (nothing to serialize)
            appendLine(
                cFunction("unsigned", "tree_sitter_xtc_external_scanner_serialize") {
                    param("void *", "payload")
                    param("char *", "buffer")
                    body {
                        voidCast("payload")
                        voidCast("buffer")
                        returnStmt("0")
                    }
                },
            )
            appendLine()

            // Deserialize (nothing to deserialize)
            append(
                cFunction("void", "tree_sitter_xtc_external_scanner_deserialize") {
                    param("void *", "payload")
                    param("const char *", "buffer")
                    param("unsigned", "length")
                    body {
                        voidCast("payload")
                        voidCast("buffer")
                        voidCast("length")
                    }
                },
            )
        }

    private fun generateHelperFunctions(): String =
        buildString {
            // advance helper
            appendLine(
                cFunction("void", "advance") {
                    static()
                    inline()
                    param("TSLexer *", "lexer")
                    body {
                        line("lexer->advance(lexer, false);")
                    }
                },
            )
            appendLine()

            // at_eof helper
            appendLine(
                cFunction("bool", "at_eof") {
                    static()
                    inline()
                    param("TSLexer *", "lexer")
                    body {
                        returnStmt("lexer->eof(lexer)")
                    }
                },
            )
            appendLine()

            // peek helper
            appendLine(
                cFunction("int32_t", "peek") {
                    static()
                    inline()
                    param("TSLexer *", "lexer")
                    body {
                        returnStmt("lexer->lookahead")
                    }
                },
            )
            appendLine()

            // is_hspace helper
            appendLine(
                cFunction("bool", "is_hspace") {
                    static()
                    inline()
                    param("int32_t", "c")
                    body {
                        returnStmt("c == ' ' || c == '\\t'")
                    }
                },
            )
            appendLine()

            // skip_multiline_continuation helper
            appendLine(
                cFunction("bool", "skip_multiline_continuation") {
                    static()
                    param("TSLexer *", "lexer")
                    body {
                        comment("Skip horizontal whitespace")
                        whileBlock("!at_eof(lexer) && is_hspace(peek(lexer))") {
                            advance()
                        }
                        comment("Check for | continuation marker")
                        ifBlock("peek(lexer) == '|'") {
                            advance()
                            returnTrue()
                        }
                        returnFalse()
                    }
                },
            )
            appendLine()

            // scan_stmt_block helper
            appendLine(generateScanStmtBlock())
            appendLine()

            // TODO scanner section comment
            appendLine(
                cCode {
                    sectionComment("TODO freeform text scanner")
                    comment("Handles: TODO freeform text AFTER the 'TODO' keyword")
                    comment("The 'TODO' keyword is matched by tree-sitter's internal lexer.")
                    comment("This scanner matches \" message text\" (space + message to end of line).")
                    emptyLine()
                    comment("This mimics Java Lexer.java:877-885 which calls eatSingleLineComment() for TODO")
                    comment("when not followed by '('.")
                },
            )
            appendLine()

            // scan_todo_to_eol helper
            appendLine(
                cFunction("void", "scan_todo_to_eol") {
                    static()
                    param("TSLexer *", "lexer")
                    body {
                        whileBlock("!at_eof(lexer) && peek(lexer) != '\\n'") {
                            advance()
                        }
                    }
                },
            )
            appendLine()

            // scan_todo_freeform_text
            appendLine(generateScanTodoFreeformText())
            appendLine()

            // scan_todo_freeform_until_semi
            append(generateScanTodoFreeformUntilSemi())
        }

    private fun generateScanStmtBlock(): String =
        cFunction("bool", "scan_stmt_block") {
            static()
            param("TSLexer *", "lexer")
            param("bool", "multiline")
            body {
                comment("We've seen first {, check for second {")
                ifBlock("peek(lexer) != '{'") {
                    returnFalse()
                }
                emptyLine()

                comment("Consume the second {")
                advance()
                variable("int", "depth", "2")
                emptyLine()

                debugBlock {
                    line("fprintf(stderr, \"[SCANNER] Starting stmt block scan, depth=%d, multiline=%d\\n\", depth, multiline);")
                }
                emptyLine()

                whileBlock("!at_eof(lexer) && depth > 0") {
                    variable("int32_t", "c", "peek(lexer)")
                    emptyLine()

                    debugBlock {
                        ifBlock("c >= 32 && c < 127") {
                            line("fprintf(stderr, \"[SCANNER] stmt_block char='%c' depth=%d\\n\", (char)c, depth);")
                        }
                    }
                    emptyLine()

                    ifBlock("c == '{'") {
                        line("depth++;")
                        advance()
                    }
                    elseIfBlock("c == '}'") {
                        line("depth--;")
                        ifBlock("depth == 0") {
                            comment("Consume final } since we're returning the block content")
                            advance()
                        }
                        elseBlock {
                            advance()
                        }
                    }
                    elseIfBlock("c == '\\n' && multiline") {
                        advance()
                        comment("After newline, skip whitespace and look for | continuation")
                        call("skip_multiline_continuation", "lexer")
                    }
                    elseIfBlock("c == '\\\\'") {
                        comment("Escape sequence")
                        advance()
                        ifBlock("!at_eof(lexer)") {
                            advance()
                        }
                    }
                    elseIfBlock("c == '\\'' || c == '\"'") {
                        comment("String/char literal - consume until matching quote")
                        variable("int32_t", "quote", "c")
                        advance()
                        whileBlock("!at_eof(lexer) && peek(lexer) != quote") {
                            ifBlock("peek(lexer) == '\\\\'") {
                                advance()
                                ifBlock("!at_eof(lexer)") {
                                    advance()
                                }
                            }
                            elseBlock {
                                advance()
                            }
                        }
                        ifBlock("!at_eof(lexer)") {
                            advance()
                        }
                    }
                    elseBlock {
                        advance()
                    }
                }
                emptyLine()

                debugBlock {
                    line("fprintf(stderr, \"[SCANNER] Finished stmt block scan, depth=%d\\n\", depth);")
                }
                emptyLine()

                returnStmt("depth == 0")
            }
        }

    /**
     * Generates the common prefix for TODO freeform scanner functions.
     * Both scan_todo_freeform_text and scan_todo_freeform_until_semi share this pattern:
     * - Debug log entry
     * - Check for leading whitespace
     * - Skip whitespace
     * - Check for special characters
     */
    private fun CCodeBuilder.todoFreeformPrefix(funcName: String) {
        debugBlock {
            line("""fprintf(stderr, "[SCANNER] $funcName called, peek='%c'(0x%02x)\n",""")
            line("""    (peek(lexer) >= 32 && peek(lexer) < 127) ? (char)peek(lexer) : '?', peek(lexer));""")
        }

        comment("Check if we're at whitespace (required after TODO keyword)")
        ifBlock("at_eof(lexer) || !is_hspace(peek(lexer))") {
            debugBlock {
                line("""fprintf(stderr, "[SCANNER] $funcName: no leading space\n");""")
            }
            returnFalse()
        }
        emptyLine()

        comment("Skip whitespace")
        whileBlock("!at_eof(lexer) && is_hspace(peek(lexer))") {
            advance()
        }
        emptyLine()

        debugBlock {
            line("""fprintf(stderr, "[SCANNER] $funcName: after skip ws, peek='%c'(0x%02x)\n",""")
            line("""    (peek(lexer) >= 32 && peek(lexer) < 127) ? (char)peek(lexer) : '?', peek(lexer));""")
        }
        emptyLine()

        comment("Check what follows the whitespace")
        ifBlock("at_eof(lexer) || peek(lexer) == '(' || peek(lexer) == '\\n' || peek(lexer) == ';'") {
            debugBlock {
                line("""fprintf(stderr, "[SCANNER] $funcName: not freeform (paren/newline/semi/eof)\n");""")
            }
            returnFalse()
        }
        emptyLine()
    }

    /**
     * Generates the expression/statement block start handling common to both
     * single-line and multiline templates.
     *
     * @param exprStart The escaped character for expression start ('{')
     * @param prefix Either "SINGLELINE" or "MULTILINE" for token names
     * @param multiline Whether this is for multiline template (affects scan_stmt_block call)
     */
    private fun CCodeBuilder.templateExprBlockStart(
        exprStart: String,
        prefix: String,
        multiline: Boolean,
    ) {
        comment("Expression or statement block start: { or {{")
        ifBlock("c == '$exprStart'") {
            ifBlock("has_content && valid_symbols[${prefix}_CONTENT]") {
                emitToken("${prefix}_CONTENT")
            }
            comment("Check for statement block {{...}}")
            ifBlock("valid_symbols[${prefix}_STMT_BLOCK]") {
                advance()
                ifBlock("peek(lexer) == '$exprStart'") {
                    ifBlock("scan_stmt_block(lexer, $multiline)") {
                        emitToken("${prefix}_STMT_BLOCK")
                    }
                }
                comment("Not a statement block, but we already consumed {")
                ifBlock("valid_symbols[${prefix}_EXPR_START]") {
                    emitToken("${prefix}_EXPR_START")
                }
                breakStmt()
            }
            ifBlock("valid_symbols[${prefix}_EXPR_START]") {
                advance()
                emitToken("${prefix}_EXPR_START")
            }
            breakStmt()
        }
        emptyLine()
    }

    /**
     * Generates the escape sequence handling and default advance common to both
     * single-line and multiline templates.
     *
     * @param backslash The escaped backslash character
     */
    private fun CCodeBuilder.templateEscapeAndAdvance(backslash: String) {
        comment("Escape sequence")
        ifBlock("c == '$backslash'") {
            advance()
            ifBlock("!at_eof(lexer)") {
                advance()
            }
            assign("has_content", "true")
            continueStmt()
        }
        emptyLine()

        advance()
        assign("has_content", "true")
    }

    private fun generateScanTodoFreeformText(): String =
        cFunction("bool", "scan_todo_freeform_text") {
            static()
            param("TSLexer *", "lexer")
            body {
                todoFreeformPrefix("scan_todo_freeform_text")

                debugBlock {
                    line("""fprintf(stderr, "[SCANNER] scan_todo_freeform_text: consuming to EOL\n");""")
                }

                comment("It's freeform text - consume to end of line")
                call("scan_todo_to_eol", "lexer")
                returnTrue()
            }
        }

    private fun generateScanTodoFreeformUntilSemi(): String =
        cFunction("bool", "scan_todo_freeform_until_semi") {
            static()
            param("TSLexer *", "lexer")
            body {
                todoFreeformPrefix("scan_todo_freeform_until_semi")

                comment("Consume text until we find ';' or EOL")
                variable("bool", "found_semi", "false")
                whileBlock("!at_eof(lexer) && peek(lexer) != '\\n'") {
                    ifBlock("peek(lexer) == ';'") {
                        assign("found_semi", "true")
                        breakStmt()
                    }
                    advance()
                }
                emptyLine()

                debugBlock {
                    line("""fprintf(stderr, "[SCANNER] scan_todo_freeform_until_semi: found_semi=%d\n", found_semi);""")
                }

                comment("Only succeed if we found a ';' (so grammar can match it)")
                returnStmt("found_semi")
            }
        }

    private fun generateScanFunction(): String {
        val exprStart = escapeForC(TemplateScannerSpec.Rules.EXPR_START_CHAR)
        val exprEnd = escapeForC(TemplateScannerSpec.Rules.EXPR_END_CHAR)
        val singlelineEnd = escapeForC(TemplateScannerSpec.Rules.SINGLELINE_END_CHAR)
        val continuation = escapeForC(TemplateScannerSpec.Rules.MULTILINE_CONTINUATION_CHAR)
        val newline = escapeForC(TemplateScannerSpec.Rules.NEWLINE_CHAR)
        val backslash = escapeForC(TemplateScannerSpec.Rules.BACKSLASH_CHAR)

        return cFunction("bool", "tree_sitter_xtc_external_scanner_scan") {
            param("void *", "payload")
            param("TSLexer *", "lexer")
            param("const bool *", "valid_symbols")
            body {
                voidCast("payload")
                emptyLine()

                ifBlock("at_eof(lexer)") {
                    returnFalse()
                }
                emptyLine()

                comment("Determine context from valid_symbols")
                line("bool in_singleline = valid_symbols[SINGLELINE_CONTENT] ||")
                line("                     valid_symbols[SINGLELINE_EXPR_START] ||")
                line("                     valid_symbols[SINGLELINE_END] ||")
                line("                     valid_symbols[SINGLELINE_STMT_BLOCK];")
                emptyLine()

                line("bool in_multiline = valid_symbols[MULTILINE_CONTENT] ||")
                line("                    valid_symbols[MULTILINE_EXPR_START] ||")
                line("                    valid_symbols[MULTILINE_END] ||")
                line("                    valid_symbols[MULTILINE_STMT_BLOCK];")
                emptyLine()

                variable("bool", "in_expr", "valid_symbols[TEMPLATE_EXPR_END]")
                emptyLine()

                line("bool stmt_block_valid = valid_symbols[SINGLELINE_STMT_BLOCK] ||")
                line("                        valid_symbols[MULTILINE_STMT_BLOCK];")
                voidCast("stmt_block_valid")
                emptyLine()

                comment("During error recovery, tree-sitter may set ALL external tokens valid.")
                comment("If both single-line AND multiline tokens are valid simultaneously,")
                comment("we're likely in error recovery - return false to let tree-sitter handle it.")
                ifBlock("in_singleline && in_multiline") {
                    returnFalse()
                }
                emptyLine()

                debugBlock {
                    line("""fprintf(stderr, "[SCANNER] char='%c'(0x%02x) single=%d multi=%d expr=%d todo_text=%d todo_until_semi=%d\n",""")
                    line("""    (peek(lexer) >= 32 && peek(lexer) < 127) ? (char)peek(lexer) : '?',""")
                    line("""    peek(lexer), in_singleline, in_multiline, in_expr,""")
                    line("""    valid_symbols[TODO_FREEFORM_TEXT], valid_symbols[TODO_FREEFORM_UNTIL_SEMI]);""")
                }
                emptyLine()

                sectionComment("TODO freeform text handling")
                comment("The 'TODO' keyword is matched by tree-sitter's internal lexer.")
                comment("This scanner matches the TEXT that follows (space + message).")
                comment("When BOTH tokens are valid:")
                comment("- If there's a ';' on the line, use until_semi (stops at ';')")
                comment("- Otherwise, use freeform_text (consumes to EOL)")

                ifBlock("(valid_symbols[TODO_FREEFORM_UNTIL_SEMI] || valid_symbols[TODO_FREEFORM_TEXT]) && is_hspace(peek(lexer))") {
                    comment("Skip whitespace first (common to both)")
                    whileBlock("!at_eof(lexer) && is_hspace(peek(lexer))") {
                        advance()
                    }
                    emptyLine()

                    comment("Check what follows the whitespace")
                    ifBlock("at_eof(lexer) || peek(lexer) == '(' || peek(lexer) == '$newline' || peek(lexer) == ';'") {
                        returnFalse()
                    }
                    emptyLine()

                    comment("Now we know there's freeform text. Check if there's a ';' on this line.")
                    variable("bool", "has_semicolon", "false")
                    emptyLine()

                    comment("Scan to find ';' or newline")
                    whileBlock("!at_eof(lexer) && peek(lexer) != '$newline'") {
                        ifBlock("peek(lexer) == ';'") {
                            assign("has_semicolon", "true")
                            breakStmt()
                        }
                        advance()
                    }
                    emptyLine()

                    comment("Decide which token to return based on what's valid and what we found")
                    ifBlock("has_semicolon && valid_symbols[TODO_FREEFORM_UNTIL_SEMI]") {
                        comment("Found ';' and until_semi is valid")
                        markEnd()
                        line("lexer->result_symbol = TODO_FREEFORM_UNTIL_SEMI;")
                        debugBlock {
                            line("""fprintf(stderr, "[SCANNER] TODO: returning UNTIL_SEMI (found ;)\n");""")
                        }
                        returnTrue()
                    }
                    elseIfBlock("!has_semicolon && valid_symbols[TODO_FREEFORM_TEXT]") {
                        comment("No ';' and freeform_text is valid - we've already consumed the text")
                        markEnd()
                        line("lexer->result_symbol = TODO_FREEFORM_TEXT;")
                        debugBlock {
                            line("""fprintf(stderr, "[SCANNER] TODO: returning FREEFORM_TEXT (no ;)\n");""")
                        }
                        returnTrue()
                    }
                    elseIfBlock("has_semicolon && !valid_symbols[TODO_FREEFORM_UNTIL_SEMI] && valid_symbols[TODO_FREEFORM_TEXT]") {
                        comment("Has ';' but only freeform_text is valid - consume to EOL including ';'")
                        whileBlock("!at_eof(lexer) && peek(lexer) != '$newline'") {
                            advance()
                        }
                        markEnd()
                        line("lexer->result_symbol = TODO_FREEFORM_TEXT;")
                        debugBlock {
                            line("""fprintf(stderr, "[SCANNER] TODO: returning FREEFORM_TEXT (has ; but until_semi not valid)\n");""")
                        }
                        returnTrue()
                    }
                    returnFalse()
                }
                emptyLine()

                sectionComment("Inside expression: only look for closing brace")
                ifBlock("in_expr && peek(lexer) == '$exprEnd'") {
                    advance()
                    emitToken("TEMPLATE_EXPR_END")
                }
                emptyLine()

                // Single-line template section
                sectionComment($"""Single-line template ($"...")""")
                ifBlock("in_singleline") {
                    variable("bool", "has_content", "false")
                    emptyLine()

                    whileBlock("!at_eof(lexer)") {
                        variable("int32_t", "c", "peek(lexer)")
                        emptyLine()

                        templateExprBlockStart(exprStart, "SINGLELINE", multiline = false)

                        comment("Template end: \"")
                        ifBlock("c == '$singlelineEnd'") {
                            ifBlock("has_content && valid_symbols[SINGLELINE_CONTENT]") {
                                emitToken("SINGLELINE_CONTENT")
                            }
                            ifBlock("valid_symbols[SINGLELINE_END]") {
                                advance()
                                emitToken("SINGLELINE_END")
                            }
                            breakStmt()
                        }
                        emptyLine()

                        templateEscapeAndAdvance(backslash)
                    }
                    emptyLine()

                    ifBlock("has_content && valid_symbols[SINGLELINE_CONTENT]") {
                        emitToken("SINGLELINE_CONTENT")
                    }
                    returnFalse()
                }
                emptyLine()

                // Multiline template section
                sectionComment($"""Multiline template ($|...|)""")
                ifBlock("in_multiline") {
                    variable("bool", "has_content", "false")
                    emptyLine()

                    whileBlock("!at_eof(lexer)") {
                        variable("int32_t", "c", "peek(lexer)")
                        emptyLine()

                        templateExprBlockStart(exprStart, "MULTILINE", multiline = true)

                        comment("Newline: check for continuation")
                        ifBlock("c == '$newline'") {
                            advance()
                            assign("has_content", "true")
                            emptyLine()

                            comment("Skip horizontal whitespace")
                            whileBlock("!at_eof(lexer) && is_hspace(peek(lexer))") {
                                advance()
                            }
                            emptyLine()

                            comment("Continuation marker |")
                            ifBlock("peek(lexer) == '$continuation'") {
                                advance()
                                continueStmt()
                            }
                            emptyLine()

                            comment("No continuation - template ends")
                            ifBlock("valid_symbols[MULTILINE_END]") {
                                emitToken("MULTILINE_END")
                            }
                            breakStmt()
                        }
                        emptyLine()

                        templateEscapeAndAdvance(backslash)
                    }
                    emptyLine()

                    comment("EOF ends multiline template")
                    ifBlock("at_eof(lexer) && valid_symbols[MULTILINE_END]") {
                        emitToken("MULTILINE_END")
                    }
                    emptyLine()

                    ifBlock("has_content && valid_symbols[MULTILINE_CONTENT]") {
                        emitToken("MULTILINE_CONTENT")
                    }
                    returnFalse()
                }
                emptyLine()

                returnFalse()
            }
        }
    }

    /** Escape a character for C char literal. */
    private fun escapeForC(c: Char): String =
        when (c) {
            '\'' -> "\\'"
            '\\' -> "\\\\"
            '\n' -> "\\n"
            '\r' -> "\\r"
            '\t' -> "\\t"
            '"' -> "\\\""
            else -> c.toString()
        }
}

/** Main entry point for CLI - uses DSL-based generator. */
fun main(args: Array<String>) {
    val debug = args.contains("--debug")
    val outputPath = args.firstOrNull { !it.startsWith("--") }
    val generated = ScannerCGeneratorDsl.generate(debug = debug)
    if (outputPath == null) {
        println(generated)
        return
    }
    File(outputPath).writeText(generated)
    println("Generated scanner.c at: $outputPath" + if (debug) " (with debug enabled)" else "")
}
