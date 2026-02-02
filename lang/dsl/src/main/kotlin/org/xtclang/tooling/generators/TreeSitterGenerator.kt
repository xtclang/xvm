package org.xtclang.tooling.generators

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.xtclang.tooling.model.Associativity
import org.xtclang.tooling.model.KeywordCategory
import org.xtclang.tooling.model.LanguageModel
import org.xtclang.tooling.model.OperatorCategory

/**
 * Generates Tree-sitter grammar files (grammar.js) and query files (highlights.scm)
 *
 * Tree-sitter grammars are supported by:
 * - Neovim (native tree-sitter support)
 * - Helix (primary syntax engine)
 * - Zed (primary syntax engine)
 * - Emacs (via tree-sitter-langs)
 * - Atom (via tree-sitter package)
 * - GitHub (syntax highlighting in code views)
 * - Sourcegraph (code intelligence)
 * - Difftastic (structural diff tool)
 * - tree-sitter CLI (parsing and highlighting)
 * - Many LSP servers (for syntax-aware operations)
 */
class TreeSitterGenerator(
    private val model: LanguageModel,
    private val version: String = "0.0.0",
) {
    companion object {
        /** Number of spaces per indentation level in generated grammar.js */
        const val INDENT_SIZE = 4

        /** Returns indentation string for the given nesting level */
        fun indent(level: Int): String = " ".repeat(INDENT_SIZE * level)

        private val json = Json { prettyPrint = true }
    }

    /**
     * Generates the tree-sitter.json configuration file for ABI 15 support.
     *
     * This config file is required by tree-sitter CLI 0.25+ for ABI version 15.
     * It defines the grammar metadata including name, file types, and version.
     */
    fun generateConfig(): String {
        val config =
            buildJsonObject {
                putJsonArray("grammars") {
                    addJsonObject {
                        put("name", model.name.lowercase())
                        put("camelcase", model.name)
                        put("scope", "source.${model.name.lowercase()}")
                        put("path", ".")
                        putJsonArray("file-types") {
                            model.fileExtensions.forEach { add(JsonPrimitive(it)) }
                        }
                    }
                }
                putJsonObject("metadata") {
                    put("version", version)
                    put("license", "Apache-2.0")
                    put("description", "${model.name} grammar for tree-sitter")
                    putJsonObject("links") {
                        put("repository", "https://github.com/xtclang/xvm")
                    }
                }
            }
        return json.encodeToString(JsonObject.serializer(), config)
    }

    private fun loadTemplate(name: String): String =
        javaClass
            .getResourceAsStream("/templates/$name")
            ?.bufferedReader()
            ?.readText()
            ?: error("Template not found: $name")

    /**
     * Generates the Tree-sitter grammar.js file
     */
    fun generateGrammar(): String {
        val template = loadTemplate("grammar.js.template")

        val assignmentOps =
            model.operators
                .filter { it.category == OperatorCategory.ASSIGNMENT }
                .joinToString(", ") { "'${escapeJsString(it.symbol)}'" }

        val maxPrecedence = model.operators.maxOfOrNull { it.precedence } ?: 15

        val prefixUnaryOps =
            listOf("!", "~", "-", "+", "++", "--")
                .filter { sym -> model.operators.any { it.symbol == sym } }
                .joinToString(", ") { "'${escapeJsString(it)}'" }

        val postfixOps =
            listOf("++", "--", "!")
                .filter { sym -> model.operators.any { it.symbol == sym } }
                .joinToString(", ") { "'${escapeJsString(it)}'" }

        val memberAccessOps =
            model.operators
                .filter { it.category == OperatorCategory.MEMBER_ACCESS }
                .joinToString(", ") { "'${escapeJsString(it.symbol)}'" }

        val i2 = indent(2)
        val booleanRule =
            if (model.booleanLiterals.isNotEmpty()) {
                "${i2}boolean_literal: \$ => choice(${model.booleanLiterals.joinToString(", ") { "'$it'" }}),"
            } else {
                "${i2}boolean_literal: \$ => choice('True', 'False'),"
            }

        val nullRule =
            model.nullLiteral?.let {
                "${i2}null_literal: \$ => '$it',"
            } ?: "${i2}null_literal: \$ => 'Null',"

        val visibilityRule =
            if (model.visibilityKeywords.isNotEmpty()) {
                val mods = model.visibilityKeywords.joinToString(", ") { "'$it'" }
                """$i2// Visibility modifiers
$i2// Supports single visibility (public, private, protected) and dual visibility (public/private)
${i2}visibility_modifier: $ => choice(
$i2    // Single visibility
$i2    $mods,
$i2    // Dual visibility: public/private, protected/private, etc.
$i2    seq(choice($mods), '/', choice($mods)),
$i2),"""
            } else {
                "${i2}visibility_modifier: \$ => choice('public', 'private', 'protected'),"
            }

        return template
            .replace("{{LANGUAGE_NAME}}", model.name)
            .replace("{{ASSIGNMENT_OPS}}", assignmentOps)
            .replace("{{BINARY_EXPRESSION_RULES}}", generateBinaryExpressionRules())
            .replace("{{MAX_PRECEDENCE_PLUS_1}}", (maxPrecedence + 1).toString())
            .replace("{{MAX_PRECEDENCE_PLUS_2}}", (maxPrecedence + 2).toString())
            .replace("{{MAX_PRECEDENCE_PLUS_3}}", (maxPrecedence + 3).toString())
            .replace("{{PREFIX_UNARY_OPS}}", prefixUnaryOps)
            .replace("{{POSTFIX_OPS}}", postfixOps)
            .replace("{{MEMBER_ACCESS_OPS}}", memberAccessOps)
            .replace("{{BOOLEAN_LITERAL_RULE}}", booleanRule)
            .replace("{{NULL_LITERAL_RULE}}", nullRule)
            .replace("{{VISIBILITY_MODIFIER_RULE}}", visibilityRule)
    }

    /**
     * Generates the Tree-sitter highlights.scm query file
     */
    fun generateHighlights(): String {
        val template = loadTemplate("highlights.scm.template")

        val controlKeywords = model.keywordsByCategory(KeywordCategory.CONTROL)
        val controlSection =
            if (controlKeywords.isNotEmpty()) {
                buildString {
                    appendLine("; Control flow keywords")
                    controlKeywords.forEach { appendLine("\"$it\" @keyword") }
                }
            } else {
                ""
            }

        val exceptionKeywords =
            model
                .keywordsByCategory(KeywordCategory.EXCEPTION)
                .filter { !it.contains(":") }
        val exceptionSection =
            if (exceptionKeywords.isNotEmpty()) {
                buildString {
                    appendLine("; Exception keywords")
                    exceptionKeywords.forEach { appendLine("\"$it\" @keyword") }
                }
            } else {
                ""
            }

        val declarationKeywords = model.keywordsByCategory(KeywordCategory.DECLARATION)
        val declarationSection =
            if (declarationKeywords.isNotEmpty()) {
                buildString {
                    appendLine("; Declaration keywords")
                    declarationKeywords.forEach { appendLine("\"$it\" @keyword") }
                }
            } else {
                ""
            }

        val modifierKeywords = model.keywordsByCategory(KeywordCategory.MODIFIER)
        val visibilityKeywords = model.visibilityKeywords
        val modifierSection =
            if (modifierKeywords.isNotEmpty()) {
                buildString {
                    appendLine("; Modifiers")
                    if (visibilityKeywords.isNotEmpty()) {
                        appendLine("(visibility_modifier) @keyword.modifier")
                    }
                    modifierKeywords.filter { it !in visibilityKeywords }.forEach {
                        appendLine("\"$it\" @keyword.modifier")
                    }
                }
            } else {
                ""
            }

        val typeRelationKeywords = model.keywordsByCategory(KeywordCategory.TYPE_RELATION)
        val typeRelationSection =
            if (typeRelationKeywords.isNotEmpty()) {
                buildString {
                    appendLine("; Type relation keywords")
                    typeRelationKeywords.forEach { appendLine("\"$it\" @keyword") }
                }
            } else {
                ""
            }

        val operatorSymbols = model.operators.map { escapeTreeSitterSymbol(it.symbol) }
        val operatorsSection =
            operatorSymbols.chunked(8).joinToString("\n") { chunk ->
                "  ${chunk.joinToString(" ") { "\"$it\"" }}"
            }

        val delimiters =
            model.punctuation
                .filter { it.symbol in listOf(".", ",", ";", ":") }
                .map { "\"${it.symbol}\"" }
        val delimiterSection =
            if (delimiters.isNotEmpty()) {
                "[${delimiters.joinToString(" ")}] @punctuation.delimiter"
            } else {
                ""
            }

        val brackets =
            model.punctuation
                .filter { it.symbol in listOf("(", ")", "[", "]", "{", "}", "<", ">") }
                .map { "\"${it.symbol}\"" }
        val bracketSection =
            if (brackets.isNotEmpty()) {
                "[${brackets.joinToString(" ")}] @punctuation.bracket"
            } else {
                ""
            }

        val builtinSection =
            if (model.builtinTypes.isNotEmpty()) {
                """; Built-in types
((type_name) @type.builtin
  (#match? @type.builtin "^(${model.builtinTypes.joinToString("|")})$"))
"""
            } else {
                ""
            }

        return template
            .replace("{{LANGUAGE_NAME}}", model.name)
            .replace("{{CONTROL_KEYWORDS}}", controlSection)
            .replace("{{EXCEPTION_KEYWORDS}}", exceptionSection)
            .replace("{{DECLARATION_KEYWORDS}}", declarationSection)
            .replace("{{MODIFIER_KEYWORDS}}", modifierSection)
            .replace("{{TYPE_RELATION_KEYWORDS}}", typeRelationSection)
            .replace("{{OPERATORS}}", operatorsSection)
            .replace("{{PUNCTUATION_DELIMITERS}}", delimiterSection)
            .replace("{{PUNCTUATION_BRACKETS}}", bracketSection)
            .replace("{{BUILTIN_TYPES}}", builtinSection)
    }

    /**
     * Generates binary expression rules from model operators, grouped by precedence.
     * Operators at the same precedence level are combined into a choice().
     */
    private fun generateBinaryExpressionRules(): String =
        buildString {
            val binaryOps =
                model.operators.filter { op ->
                    op.category !in listOf(OperatorCategory.ASSIGNMENT, OperatorCategory.MEMBER_ACCESS) &&
                        op.symbol !in listOf("!", "~", "++", "--")
                }

            val byPrecedence = binaryOps.groupBy { it.precedence }.toSortedMap()
            val i3 = indent(3)

            byPrecedence.entries.forEachIndexed { index, (precedence, ops) ->
                val precFn =
                    when {
                        ops.all { it.associativity == Associativity.RIGHT } -> "prec.right"
                        else -> "prec.left"
                    }

                val symbols = ops.map { escapeJsString(it.symbol) }
                val choiceExpr =
                    if (symbols.size == 1) {
                        "'${symbols.first()}'"
                    } else {
                        "choice(${symbols.joinToString(", ") { "'$it'" }})"
                    }

                val comma = if (index < byPrecedence.size - 1) "," else ""
                appendLine("$i3$precFn($precedence, seq(\$._expression, $choiceExpr, \$._expression))$comma")
            }
        }.trimEnd()

    private fun escapeTreeSitterSymbol(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
