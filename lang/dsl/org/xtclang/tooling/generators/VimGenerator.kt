package org.xtclang.tooling.generators

import org.xtclang.tooling.model.KeywordCategory
import org.xtclang.tooling.model.LanguageModel
import org.xtclang.tooling.model.OperatorCategory

/**
 * Generates Vim syntax highlighting files (.vim)
 *
 * Vim syntax files are supported by:
 * - Vim (native support)
 * - Neovim (native support, also supports tree-sitter)
 * - GVim (GUI Vim)
 * - MacVim
 * - IdeaVim (IntelliJ plugin)
 * - VSCodeVim (VS Code plugin)
 * - evil-mode (Emacs Vim emulation)
 * - Kakoune (partial Vim syntax compatibility)
 * - Many terminal editors with Vim mode
 *
 * Vim syntax files use specific syntax matching commands:
 * - syn keyword: For keyword matching
 * - syn match: For pattern matching
 * - syn region: For multi-line constructs
 * - hi def link: For linking syntax groups to highlight groups
 */
class VimGenerator(private val model: LanguageModel) {

    fun generate(): String = buildString {
        appendLine("\" Vim syntax file for ${model.name}")
        appendLine("\" Language: ${model.name}")
        appendLine("\" Generated from XTC language model")
        appendLine()
        appendLine("if exists('b:current_syntax')")
        appendLine("  finish")
        appendLine("endif")
        appendLine()
        appendLine("let s:cpo_save = &cpo")
        appendLine("set cpo&vim")
        appendLine()

        // Control flow keywords from model
        val controlKeywords = model.keywordsByCategory(KeywordCategory.CONTROL)
        if (controlKeywords.isNotEmpty()) {
            appendLine("\" Keywords - Control flow")
            appendLine("syn keyword xtcControl ${controlKeywords.joinToString(" ")}")
            appendLine()
        }

        // Exception handling keywords from model
        val exceptionKeywords = model.keywordsByCategory(KeywordCategory.EXCEPTION)
            .filter { !it.contains(":") }  // Skip assert:* variants for keyword matching
        if (exceptionKeywords.isNotEmpty()) {
            appendLine("\" Keywords - Exception handling")
            appendLine("syn keyword xtcException ${exceptionKeywords.joinToString(" ")}")
            appendLine()
        }

        // Declaration keywords from model
        val declarationKeywords = model.keywordsByCategory(KeywordCategory.DECLARATION)
        if (declarationKeywords.isNotEmpty()) {
            appendLine("\" Keywords - Declarations")
            appendLine("syn keyword xtcDeclaration ${declarationKeywords.joinToString(" ")}")
            appendLine()
        }

        // Modifier keywords from model
        val modifierKeywords = model.keywordsByCategory(KeywordCategory.MODIFIER)
        if (modifierKeywords.isNotEmpty()) {
            appendLine("\" Keywords - Modifiers")
            appendLine("syn keyword xtcModifier ${modifierKeywords.joinToString(" ")}")
            appendLine()
        }

        // Type relation keywords from model
        val typeRelationKeywords = model.keywordsByCategory(KeywordCategory.TYPE_RELATION)
        if (typeRelationKeywords.isNotEmpty()) {
            appendLine("\" Keywords - Type relations")
            appendLine("syn keyword xtcTypeRelation ${typeRelationKeywords.joinToString(" ")}")
            appendLine()
        }

        // Other keywords from model
        val otherKeywords = model.keywordsByCategory(KeywordCategory.OTHER)
            .filter { !it.contains(":") }  // Skip this:* variants
        if (otherKeywords.isNotEmpty()) {
            appendLine("\" Keywords - Other")
            appendLine("syn keyword xtcKeyword ${otherKeywords.joinToString(" ")}")
            appendLine()
        }

        // Built-in types from model
        if (model.builtinTypes.isNotEmpty()) {
            appendLine("\" Built-in types")
            appendLine("syn keyword xtcType ${model.builtinTypes.joinToString(" ")}")
            appendLine()
        }

        // Boolean constants from model
        val booleans = model.booleanLiterals
        if (booleans.isNotEmpty()) {
            appendLine("\" Boolean constants")
            appendLine("syn keyword xtcBoolean ${booleans.joinToString(" ")}")
            appendLine()
        }

        // Null constant from model
        val nullLit = model.nullLiteral
        if (nullLit != null) {
            appendLine("\" Null constant")
            appendLine("syn keyword xtcNull $nullLit")
            appendLine()
        }

        // Numbers - patterns from token rules
        appendLine("\" Numbers")
        appendLine("syn match xtcNumber '\\<\\d[\\d_]*\\>'")
        appendLine("syn match xtcNumber '\\<0[xX][0-9a-fA-F][0-9a-fA-F_]*\\>'")
        appendLine("syn match xtcNumber '\\<0[bB][01][01_]*\\>'")
        appendLine("syn match xtcFloat '\\<\\d[\\d_]*\\.\\d[\\d_]*\\([eE][+-]\\?\\d\\+\\)\\?\\>'")
        appendLine()

        // Strings
        appendLine("\" Strings")
        appendLine("syn region xtcString start='\"' skip='\\\\.' end='\"' contains=xtcEscape")
        appendLine("syn region xtcTemplateString start='\\$\"' skip='\\\\.' end='\"' contains=xtcEscape,xtcTemplateExpr")
        appendLine("syn match xtcCharacter \"'[^'\\\\]'\"")
        appendLine("syn match xtcCharacter \"'\\\\[nrtbf\\\\\\\"']'\"")
        appendLine("syn match xtcCharacter \"'\\\\u[0-9a-fA-F]\\{4}'\"")
        appendLine()

        // Escape sequences
        appendLine("\" Escape sequences")
        appendLine("syn match xtcEscape '\\\\[nrtbf\\\\\"'0]' contained")
        appendLine("syn match xtcEscape '\\\\u[0-9a-fA-F]\\{4}' contained")
        appendLine()

        // Template expressions
        appendLine("\" Template expressions")
        appendLine("syn region xtcTemplateExpr matchgroup=xtcTemplateBrace start='{' end='}' contained contains=TOP")
        appendLine()

        // Comments
        appendLine("\" Comments")
        appendLine("syn region xtcComment start='//' end='$' contains=xtcTodo")
        appendLine("syn region xtcComment start='/\\*' end='\\*/' contains=xtcTodo,xtcComment")
        appendLine("syn region xtcDocComment start='/\\*\\*' end='\\*/' contains=xtcTodo,xtcDocTag")
        appendLine()

        // Doc comment tags
        appendLine("\" Doc comment tags")
        appendLine("syn match xtcDocTag '@\\w\\+' contained")
        appendLine()

        // TODO markers
        appendLine("\" TODO markers")
        appendLine("syn keyword xtcTodo TODO FIXME XXX HACK NOTE contained")
        appendLine()

        // Annotations
        appendLine("\" Annotations")
        appendLine("syn match xtcAnnotation '@[A-Za-z_][A-Za-z0-9_]*'")
        appendLine()

        // Type names
        appendLine("\" Type names (PascalCase identifiers)")
        appendLine("syn match xtcTypeName '\\<[A-Z][A-Za-z0-9_]*\\>'")
        appendLine()

        // Function calls
        appendLine("\" Function calls")
        appendLine("syn match xtcFunctionCall '\\<[a-z_][A-Za-z0-9_]*\\s*(' contains=xtcFunctionName")
        appendLine("syn match xtcFunctionName '\\<[a-z_][A-Za-z0-9_]*' contained")
        appendLine()

        // Operators from model
        appendLine("\" Operators")
        generateOperatorPatterns()
        appendLine()

        // Punctuation from model
        appendLine("\" Punctuation")
        val punctSymbols = model.punctuation.map { it.symbol }
            .filter { it.length == 1 && it[0] in "{}()[]<>" }
            .joinToString("")
        appendLine("syn match xtcBracket '[$punctSymbols]'")
        appendLine()

        // Highlighting links
        appendLine("\" Highlighting")
        appendLine("hi def link xtcControl Keyword")
        appendLine("hi def link xtcException Exception")
        appendLine("hi def link xtcDeclaration Keyword")
        appendLine("hi def link xtcModifier StorageClass")
        appendLine("hi def link xtcTypeRelation Keyword")
        appendLine("hi def link xtcKeyword Keyword")
        appendLine("hi def link xtcType Type")
        appendLine("hi def link xtcTypeName Type")
        appendLine("hi def link xtcBoolean Boolean")
        appendLine("hi def link xtcNull Constant")
        appendLine("hi def link xtcNumber Number")
        appendLine("hi def link xtcFloat Float")
        appendLine("hi def link xtcString String")
        appendLine("hi def link xtcTemplateString String")
        appendLine("hi def link xtcCharacter Character")
        appendLine("hi def link xtcEscape SpecialChar")
        appendLine("hi def link xtcTemplateBrace Special")
        appendLine("hi def link xtcComment Comment")
        appendLine("hi def link xtcDocComment SpecialComment")
        appendLine("hi def link xtcDocTag Special")
        appendLine("hi def link xtcTodo Todo")
        appendLine("hi def link xtcAnnotation PreProc")
        appendLine("hi def link xtcFunctionName Function")
        appendLine("hi def link xtcOperator Operator")
        appendLine("hi def link xtcBracket Delimiter")
        appendLine()

        appendLine("let b:current_syntax = 'xtc'")
        appendLine()
        appendLine("let &cpo = s:cpo_save")
        appendLine("unlet s:cpo_save")
    }

    private fun StringBuilder.generateOperatorPatterns() {
        // Single-character operators
        appendLine("syn match xtcOperator '[+\\-*/%&|^~<>=!?:.]'")

        // Multi-character operators by category from model
        val logicalOps = model.operators
            .filter { it.category == OperatorCategory.LOGICAL && it.symbol.length > 1 }
            .map { escapeVimPattern(it.symbol) }
        if (logicalOps.isNotEmpty()) {
            appendLine("syn match xtcOperator '${logicalOps.joinToString("\\|")}'")
        }

        val comparisonOps = model.operators
            .filter { it.category == OperatorCategory.COMPARISON && it.symbol.length > 1 }
            .map { escapeVimPattern(it.symbol) }
        if (comparisonOps.isNotEmpty()) {
            appendLine("syn match xtcOperator '${comparisonOps.joinToString("\\|")}'")
        }

        val bitwiseOps = model.operators
            .filter { it.category == OperatorCategory.BITWISE && it.symbol.length > 1 }
            .map { escapeVimPattern(it.symbol) }
        if (bitwiseOps.isNotEmpty()) {
            appendLine("syn match xtcOperator '${bitwiseOps.joinToString("\\|")}'")
        }

        // Range operators from model
        val rangeOps = model.operators
            .filter { it.symbol.contains("..") }
            .map { escapeVimPattern(it.symbol) }
        if (rangeOps.isNotEmpty()) {
            appendLine("syn match xtcOperator '${rangeOps.joinToString("\\|")}'")
        }

        // Elvis and safe navigation from model
        val memberAccessOps = model.operators
            .filter { it.category == OperatorCategory.MEMBER_ACCESS && it.symbol.length > 1 }
            .map { escapeVimPattern(it.symbol) }
        if (memberAccessOps.isNotEmpty()) {
            appendLine("syn match xtcOperator '${memberAccessOps.joinToString("\\|")}'")
        }

        // Compound assignment from model
        val assignmentOps = model.operators
            .filter { it.category == OperatorCategory.ASSIGNMENT && it.symbol.length > 1 }
            .map { escapeVimPattern(it.symbol) }
        if (assignmentOps.isNotEmpty()) {
            appendLine("syn match xtcOperator '${assignmentOps.joinToString("\\|")}'")
        }
    }

    private fun escapeVimPattern(s: String): String = buildString {
        for (c in s) {
            when (c) {
                '\\', '/', '|', '^', '$', '.', '*', '+', '?', '[', ']', '(', ')' -> {
                    append('\\')
                    append(c)
                }
                else -> append(c)
            }
        }
    }
}
