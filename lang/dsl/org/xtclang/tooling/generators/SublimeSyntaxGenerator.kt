package org.xtclang.tooling.generators

import org.xtclang.tooling.model.KeywordCategory
import org.xtclang.tooling.model.LanguageModel
import org.xtclang.tooling.model.OperatorCategory

/**
 * Generates Sublime Text syntax files (.sublime-syntax)
 *
 * Sublime syntax is a YAML-based format used by:
 * - Sublime Text
 * - bat (command-line cat clone with syntax highlighting)
 * - Any tool using the syntect library
 *
 * This format is more powerful than TextMate grammars and supports
 * context-based parsing with push/pop/set operations.
 */
class SublimeSyntaxGenerator(private val model: LanguageModel) {

    fun generate(): String = buildString {
        appendLine("%YAML 1.2")
        appendLine("---")
        appendLine("# ${model.name} language syntax for Sublime Text / bat")
        appendLine("# Generated from XTC language model DSL")
        appendLine("name: ${model.name}")
        appendLine("file_extensions:")
        model.fileExtensions.forEach { ext ->
            appendLine("  - $ext")
        }
        appendLine("scope: ${model.scopeName}")
        appendLine()
        appendLine("contexts:")

        // Main context
        appendLine("  main:")
        appendLine("    - include: comments")
        appendLine("    - include: strings")
        appendLine("    - include: numbers")
        appendLine("    - include: annotations")
        appendLine("    - include: keywords")
        appendLine("    - include: types")
        appendLine("    - include: operators")
        appendLine()

        // Comments context
        generateCommentsContext()

        // Strings context
        generateStringsContext()

        // Numbers context
        generateNumbersContext()

        // Annotations context
        generateAnnotationsContext()

        // Keywords context
        generateKeywordsContext()

        // Types context
        generateTypesContext()

        // Operators context
        generateOperatorsContext()
    }

    private fun StringBuilder.generateCommentsContext() {
        appendLine("  comments:")
        appendLine("    # Doc comments")
        appendLine("    - match: '/\\*\\*'")
        appendLine("      scope: punctuation.definition.comment.begin.xtc")
        appendLine("      push:")
        appendLine("        - meta_scope: comment.block.documentation.xtc")
        appendLine("        - match: '@\\w+'")
        appendLine("          scope: keyword.other.documentation.xtc")
        appendLine("        - match: '\\*/'")
        appendLine("          scope: punctuation.definition.comment.end.xtc")
        appendLine("          pop: true")
        appendLine("    # Block comments")
        appendLine("    - match: '/\\*'")
        appendLine("      scope: punctuation.definition.comment.begin.xtc")
        appendLine("      push:")
        appendLine("        - meta_scope: comment.block.xtc")
        appendLine("        - match: '\\*/'")
        appendLine("          scope: punctuation.definition.comment.end.xtc")
        appendLine("          pop: true")
        appendLine("    # Line comments")
        appendLine("    - match: '//'")
        appendLine("      scope: punctuation.definition.comment.xtc")
        appendLine("      push:")
        appendLine("        - meta_scope: comment.line.double-slash.xtc")
        appendLine("        - match: $")
        appendLine("          pop: true")
        appendLine()
    }

    private fun StringBuilder.generateStringsContext() {
        appendLine("  strings:")
        appendLine("    # Template strings")
        appendLine("    - match: '\\$\"'")
        appendLine("      scope: punctuation.definition.string.begin.xtc")
        appendLine("      push:")
        appendLine("        - meta_scope: string.interpolated.xtc")
        appendLine("        - match: '\\\\.'")
        appendLine("          scope: constant.character.escape.xtc")
        appendLine("        - match: '\\{'")
        appendLine("          scope: punctuation.section.interpolation.begin.xtc")
        appendLine("          push:")
        appendLine("            - meta_scope: meta.interpolation.xtc")
        appendLine("            - match: '\\}'")
        appendLine("              scope: punctuation.section.interpolation.end.xtc")
        appendLine("              pop: true")
        appendLine("            - include: main")
        appendLine("        - match: '\"'")
        appendLine("          scope: punctuation.definition.string.end.xtc")
        appendLine("          pop: true")
        appendLine("    # Regular strings")
        appendLine("    - match: '\"'")
        appendLine("      scope: punctuation.definition.string.begin.xtc")
        appendLine("      push:")
        appendLine("        - meta_scope: string.quoted.double.xtc")
        appendLine("        - match: '\\\\.'")
        appendLine("          scope: constant.character.escape.xtc")
        appendLine("        - match: '\"'")
        appendLine("          scope: punctuation.definition.string.end.xtc")
        appendLine("          pop: true")
        appendLine("    # Character literals")
        appendLine("    - match: \"'\"")
        appendLine("      scope: punctuation.definition.string.begin.xtc")
        appendLine("      push:")
        appendLine("        - meta_scope: string.quoted.single.xtc")
        appendLine("        - match: '\\\\.'")
        appendLine("          scope: constant.character.escape.xtc")
        appendLine("        - match: \"'\"")
        appendLine("          scope: punctuation.definition.string.end.xtc")
        appendLine("          pop: true")
        appendLine()
    }

    private fun StringBuilder.generateNumbersContext() {
        appendLine("  numbers:")
        appendLine("    # Hex")
        appendLine("    - match: '\\b0[xX][0-9a-fA-F][0-9a-fA-F_]*\\b'")
        appendLine("      scope: constant.numeric.hex.xtc")
        appendLine("    # Binary")
        appendLine("    - match: '\\b0[bB][01][01_]*\\b'")
        appendLine("      scope: constant.numeric.binary.xtc")
        appendLine("    # Float")
        appendLine("    - match: '\\b[0-9][0-9_]*\\.[0-9][0-9_]*([eE][+-]?[0-9]+)?\\b'")
        appendLine("      scope: constant.numeric.float.xtc")
        appendLine("    # Integer")
        appendLine("    - match: '\\b[0-9][0-9_]*\\b'")
        appendLine("      scope: constant.numeric.integer.xtc")
        appendLine()
    }

    private fun StringBuilder.generateAnnotationsContext() {
        appendLine("  annotations:")
        appendLine("    - match: '@[a-zA-Z_][a-zA-Z0-9_]*'")
        appendLine("      scope: storage.type.annotation.xtc")
        appendLine()
    }

    private fun StringBuilder.generateKeywordsContext() {
        appendLine("  keywords:")

        // Control flow keywords
        val controlKeywords = model.keywordsByCategory(KeywordCategory.CONTROL) +
                model.keywordsByCategory(KeywordCategory.EXCEPTION).filter { !it.contains(":") }
        if (controlKeywords.isNotEmpty()) {
            appendLine("    # Control flow")
            appendLine("    - match: '\\b(${controlKeywords.joinToString("|")})\\b'")
            appendLine("      scope: keyword.control.xtc")
        }

        // Declaration keywords
        val declarationKeywords = model.keywordsByCategory(KeywordCategory.DECLARATION)
        if (declarationKeywords.isNotEmpty()) {
            appendLine("    # Declarations")
            appendLine("    - match: '\\b(${declarationKeywords.joinToString("|")})\\b'")
            appendLine("      scope: keyword.declaration.xtc")
        }

        // Modifier keywords
        val modifierKeywords = model.keywordsByCategory(KeywordCategory.MODIFIER) +
                model.keywordsByCategory(KeywordCategory.TYPE_RELATION)
        if (modifierKeywords.isNotEmpty()) {
            appendLine("    # Modifiers")
            appendLine("    - match: '\\b(${modifierKeywords.joinToString("|")})\\b'")
            appendLine("      scope: storage.modifier.xtc")
        }

        // Other keywords
        val otherKeywords = model.keywordsByCategory(KeywordCategory.OTHER)
        if (otherKeywords.isNotEmpty()) {
            appendLine("    # Other keywords")
            appendLine("    - match: '\\b(${otherKeywords.joinToString("|")})\\b'")
            appendLine("      scope: keyword.other.xtc")
        }

        // Boolean constants from model
        val booleans = model.booleanLiterals
        if (booleans.isNotEmpty()) {
            appendLine("    # Boolean constants")
            appendLine("    - match: '\\b(${booleans.joinToString("|")})\\b'")
            appendLine("      scope: constant.language.boolean.xtc")
        }

        // Null constant from model
        val nullLit = model.nullLiteral
        if (nullLit != null) {
            appendLine("    # Null")
            appendLine("    - match: '\\b$nullLit\\b'")
            appendLine("      scope: constant.language.null.xtc")
        }
        appendLine()
    }

    private fun StringBuilder.generateTypesContext() {
        appendLine("  types:")

        // Built-in types from model
        val builtinTypes = model.builtinTypes
        if (builtinTypes.isNotEmpty()) {
            appendLine("    # Built-in types")
            appendLine("    - match: '\\b(${builtinTypes.joinToString("|")})\\b'")
            appendLine("      scope: support.type.builtin.xtc")
        }

        // Type names (PascalCase)
        appendLine("    # Type names (PascalCase)")
        appendLine("    - match: '\\b[A-Z][a-zA-Z0-9_]*\\b'")
        appendLine("      scope: entity.name.type.xtc")
        appendLine()
    }

    private fun StringBuilder.generateOperatorsContext() {
        appendLine("  operators:")

        // Generate operator patterns from the language model by category
        // Categories match OperatorCategory enum in LanguageModel.kt
        val categoryToScope: Map<OperatorCategory, String> = mapOf(
            OperatorCategory.COMPARISON to "keyword.operator.comparison.xtc",
            OperatorCategory.ASSIGNMENT to "keyword.operator.assignment.xtc",
            OperatorCategory.LOGICAL to "keyword.operator.logical.xtc",
            OperatorCategory.BITWISE to "keyword.operator.bitwise.xtc",
            OperatorCategory.ARITHMETIC to "keyword.operator.arithmetic.xtc",
            OperatorCategory.MEMBER_ACCESS to "keyword.operator.access.xtc",
            OperatorCategory.OTHER to "keyword.operator.other.xtc"  // includes range, elvis, etc.
        )

        for ((category, scope) in categoryToScope) {
            val ops = operatorsByCategory(category)
            if (ops.isNotEmpty()) {
                appendLine("    # ${category.name.lowercase().replaceFirstChar { it.uppercase() }}")
                appendLine("    - match: '${ops.joinToString("|")}'")
                appendLine("      scope: $scope")
            }
        }
    }

    private fun operatorsByCategory(category: OperatorCategory): List<String> =
        model.operators
            .filter { it.category == category }
            .map { escapeRegex(it.symbol) }
            .sortedByDescending { it.length }  // Match longest first

    private fun escapeRegex(s: String): String = buildString {
        for (c in s) {
            when (c) {
                '.', '*', '+', '?', '|', '\\', '^', '$', '(', ')', '[', ']', '{', '}' -> {
                    append('\\')
                    append(c)
                }
                else -> append(c)
            }
        }
    }
}