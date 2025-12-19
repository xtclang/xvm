package org.xtclang.tooling.generators

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
class TreeSitterGenerator(private val model: LanguageModel) {

    /**
     * Generates the Tree-sitter grammar.js file
     */
    fun generateGrammar(): String = buildString {
        appendLine("/**")
        appendLine(" * Tree-sitter grammar for ${model.name}")
        appendLine(" * Generated from XTC language model")
        appendLine(" */")
        appendLine()
        appendLine("module.exports = grammar({")
        appendLine("  name: 'xtc',")
        appendLine()
        appendLine("  extras: $ => [")
        appendLine("    /\\s/,")
        appendLine("    $.comment,")
        appendLine("  ],")
        appendLine()
        appendLine("  externals: $ => [],")
        appendLine()
        appendLine("  conflicts: $ => [],")
        appendLine()
        appendLine("  word: $ => $.identifier,")
        appendLine()
        appendLine("  rules: {")

        // Source file
        appendLine("    source_file: $ => repeat($._definition),")
        appendLine()

        // Definitions
        appendLine("    _definition: $ => choice(")
        appendLine("      $.module_declaration,")
        appendLine("      $.package_declaration,")
        appendLine("      $.import_statement,")
        appendLine("      $.class_declaration,")
        appendLine("      $.interface_declaration,")
        appendLine("      $.mixin_declaration,")
        appendLine("      $.service_declaration,")
        appendLine("      $.const_declaration,")
        appendLine("      $.enum_declaration,")
        appendLine("    ),")
        appendLine()

        // Module declaration
        appendLine("    module_declaration: $ => seq(")
        appendLine("      repeat($.annotation),")
        appendLine("      'module',")
        appendLine("      $.qualified_name,")
        appendLine("      optional($.module_body),")
        appendLine("    ),")
        appendLine()
        appendLine("    module_body: $ => seq('{', repeat($._definition), '}'),")
        appendLine()

        // Package declaration
        appendLine("    package_declaration: $ => seq(")
        appendLine("      repeat($.annotation),")
        appendLine("      'package',")
        appendLine("      $.identifier,")
        appendLine("      optional($.import_spec),")
        appendLine("      optional($.package_body),")
        appendLine("    ),")
        appendLine()
        appendLine("    package_body: $ => seq('{', repeat($._definition), '}'),")
        appendLine()

        // Import
        appendLine("    import_statement: $ => seq(")
        appendLine("      'import',")
        appendLine("      $.qualified_name,")
        appendLine("      optional(seq('as', $.identifier)),")
        appendLine("      ';',")
        appendLine("    ),")
        appendLine()
        appendLine("    import_spec: $ => seq('import', $.qualified_name),")
        appendLine()

        // Class declaration
        appendLine("    class_declaration: $ => seq(")
        appendLine("      repeat($.annotation),")
        appendLine("      optional($.visibility_modifier),")
        appendLine("      optional('static'),")
        appendLine("      optional('abstract'),")
        appendLine("      'class',")
        appendLine("      $.type_name,")
        appendLine("      optional($.type_parameters),")
        appendLine("      optional($.extends_clause),")
        appendLine("      optional($.implements_clause),")
        appendLine("      optional($.incorporates_clause),")
        appendLine("      $.class_body,")
        appendLine("    ),")
        appendLine()

        // Similar declarations
        appendLine("    interface_declaration: $ => seq(")
        appendLine("      repeat($.annotation),")
        appendLine("      optional($.visibility_modifier),")
        appendLine("      'interface',")
        appendLine("      $.type_name,")
        appendLine("      optional($.type_parameters),")
        appendLine("      optional($.extends_clause),")
        appendLine("      $.class_body,")
        appendLine("    ),")
        appendLine()

        appendLine("    mixin_declaration: $ => seq(")
        appendLine("      repeat($.annotation),")
        appendLine("      optional($.visibility_modifier),")
        appendLine("      'mixin',")
        appendLine("      $.type_name,")
        appendLine("      optional($.type_parameters),")
        appendLine("      optional($.into_clause),")
        appendLine("      optional($.extends_clause),")
        appendLine("      $.class_body,")
        appendLine("    ),")
        appendLine()

        appendLine("    service_declaration: $ => seq(")
        appendLine("      repeat($.annotation),")
        appendLine("      optional($.visibility_modifier),")
        appendLine("      'service',")
        appendLine("      $.type_name,")
        appendLine("      optional($.type_parameters),")
        appendLine("      optional($.extends_clause),")
        appendLine("      optional($.implements_clause),")
        appendLine("      $.class_body,")
        appendLine("    ),")
        appendLine()

        appendLine("    const_declaration: $ => seq(")
        appendLine("      repeat($.annotation),")
        appendLine("      optional($.visibility_modifier),")
        appendLine("      'const',")
        appendLine("      $.type_name,")
        appendLine("      optional($.type_parameters),")
        appendLine("      optional($.extends_clause),")
        appendLine("      optional($.implements_clause),")
        appendLine("      $.class_body,")
        appendLine("    ),")
        appendLine()

        appendLine("    enum_declaration: $ => seq(")
        appendLine("      repeat($.annotation),")
        appendLine("      optional($.visibility_modifier),")
        appendLine("      'enum',")
        appendLine("      $.type_name,")
        appendLine("      optional($.implements_clause),")
        appendLine("      $.enum_body,")
        appendLine("    ),")
        appendLine()

        // Class body
        appendLine("    class_body: $ => seq('{', repeat($._class_member), '}'),")
        appendLine()
        appendLine("    enum_body: $ => seq(")
        appendLine("      '{',")
        appendLine("      optional($.enum_values),")
        appendLine("      optional(seq(';', repeat($._class_member))),")
        appendLine("      '}',")
        appendLine("    ),")
        appendLine()
        appendLine("    enum_values: $ => seq($.enum_value, repeat(seq(',', $.enum_value))),")
        appendLine()
        appendLine("    enum_value: $ => seq($.identifier, optional($.arguments)),")
        appendLine()

        // Class members
        appendLine("    _class_member: $ => choice(")
        appendLine("      $.property_declaration,")
        appendLine("      $.method_declaration,")
        appendLine("      $.constructor_declaration,")
        appendLine("      $.class_declaration,")
        appendLine("      $.interface_declaration,")
        appendLine("    ),")
        appendLine()

        appendLine("    property_declaration: $ => seq(")
        appendLine("      repeat($.annotation),")
        appendLine("      optional($.visibility_modifier),")
        appendLine("      optional('static'),")
        appendLine("      $.type_expression,")
        appendLine("      $.identifier,")
        appendLine("      optional(seq('=', $._expression)),")
        appendLine("      ';',")
        appendLine("    ),")
        appendLine()

        appendLine("    method_declaration: $ => seq(")
        appendLine("      repeat($.annotation),")
        appendLine("      optional($.visibility_modifier),")
        appendLine("      optional('static'),")
        appendLine("      optional('abstract'),")
        appendLine("      $.type_expression,")
        appendLine("      $.identifier,")
        appendLine("      optional($.type_parameters),")
        appendLine("      $.parameters,")
        appendLine("      choice($.block, ';'),")
        appendLine("    ),")
        appendLine()

        appendLine("    constructor_declaration: $ => seq(")
        appendLine("      repeat($.annotation),")
        appendLine("      optional($.visibility_modifier),")
        appendLine("      choice('construct', 'finally'),")
        appendLine("      $.parameters,")
        appendLine("      $.block,")
        appendLine("    ),")
        appendLine()

        // Clauses
        appendLine("    extends_clause: $ => seq('extends', $.type_expression),")
        appendLine("    implements_clause: $ => seq('implements', commaSep1($.type_expression)),")
        appendLine("    incorporates_clause: $ => seq('incorporates', $.type_expression),")
        appendLine("    into_clause: $ => seq('into', $.type_expression),")
        appendLine()

        // Type parameters
        appendLine("    type_parameters: $ => seq('<', commaSep1($.type_parameter), '>'),")
        appendLine("    type_parameter: $ => seq($.identifier, optional(seq('extends', $.type_expression))),")
        appendLine()

        // Parameters
        appendLine("    parameters: $ => seq('(', commaSep($.parameter), ')'),")
        appendLine("    parameter: $ => seq(")
        appendLine("      $.type_expression,")
        appendLine("      $.identifier,")
        appendLine("      optional(seq('=', $._expression)),")
        appendLine("    ),")
        appendLine()

        // Type expressions
        appendLine("    type_expression: $ => choice(")
        appendLine("      $.type_name,")
        appendLine("      $.generic_type,")
        appendLine("      $.nullable_type,")
        appendLine("      $.function_type,")
        appendLine("      $.tuple_type,")
        appendLine("    ),")
        appendLine()
        appendLine("    generic_type: $ => seq($.type_name, $.type_arguments),")
        appendLine("    type_arguments: $ => seq('<', commaSep1($.type_expression), '>'),")
        appendLine("    nullable_type: $ => seq($.type_expression, '?'),")
        appendLine("    function_type: $ => seq('function', $.type_expression, $.parameters),")
        appendLine("    tuple_type: $ => seq('(', commaSep($.type_expression), ')'),")
        appendLine()

        // Statements
        appendLine("    block: $ => seq('{', repeat($._statement), '}'),")
        appendLine()
        appendLine("    _statement: $ => choice(")
        appendLine("      $.block,")
        appendLine("      $.variable_declaration,")
        appendLine("      $.if_statement,")
        appendLine("      $.for_statement,")
        appendLine("      $.while_statement,")
        appendLine("      $.do_statement,")
        appendLine("      $.switch_statement,")
        appendLine("      $.try_statement,")
        appendLine("      $.return_statement,")
        appendLine("      $.break_statement,")
        appendLine("      $.continue_statement,")
        appendLine("      $.assert_statement,")
        appendLine("      $.expression_statement,")
        appendLine("    ),")
        appendLine()

        // Statement implementations
        appendLine("    variable_declaration: $ => seq(")
        appendLine("      choice('val', 'var'),")
        appendLine("      optional($.type_expression),")
        appendLine("      $.identifier,")
        appendLine("      optional(seq('=', $._expression)),")
        appendLine("      ';',")
        appendLine("    ),")
        appendLine()

        appendLine("    if_statement: $ => seq(")
        appendLine("      'if',")
        appendLine("      '(',")
        appendLine("      $._expression,")
        appendLine("      ')',")
        appendLine("      $._statement,")
        appendLine("      optional(seq('else', $._statement)),")
        appendLine("    ),")
        appendLine()

        appendLine("    for_statement: $ => seq(")
        appendLine("      'for',")
        appendLine("      '(',")
        appendLine("      choice(")
        appendLine("        seq($.type_expression, $.identifier, ':', $._expression),  // foreach")
        appendLine("        seq(optional($._expression), ';', optional($._expression), ';', optional($._expression)),")
        appendLine("      ),")
        appendLine("      ')',")
        appendLine("      $._statement,")
        appendLine("    ),")
        appendLine()

        appendLine("    while_statement: $ => seq('while', '(', $._expression, ')', $._statement),")
        appendLine("    do_statement: $ => seq('do', $._statement, 'while', '(', $._expression, ')', ';'),")
        appendLine()

        appendLine("    switch_statement: $ => seq(")
        appendLine("      'switch',")
        appendLine("      '(',")
        appendLine("      $._expression,")
        appendLine("      ')',")
        appendLine("      '{',")
        appendLine("      repeat($.case_clause),")
        appendLine("      '}',")
        appendLine("    ),")
        appendLine()
        appendLine("    case_clause: $ => seq(")
        appendLine("      choice(seq('case', $._expression), 'default'),")
        appendLine("      ':',")
        appendLine("      repeat($._statement),")
        appendLine("    ),")
        appendLine()

        appendLine("    try_statement: $ => seq(")
        appendLine("      'try',")
        appendLine("      optional(seq('(', commaSep1($._expression), ')')),")
        appendLine("      $.block,")
        appendLine("      repeat($.catch_clause),")
        appendLine("      optional(seq('finally', $.block)),")
        appendLine("    ),")
        appendLine()
        appendLine("    catch_clause: $ => seq(")
        appendLine("      'catch',")
        appendLine("      '(',")
        appendLine("      $.type_expression,")
        appendLine("      $.identifier,")
        appendLine("      ')',")
        appendLine("      $.block,")
        appendLine("    ),")
        appendLine()

        appendLine("    return_statement: $ => seq('return', optional(commaSep1($._expression)), ';'),")
        appendLine("    break_statement: $ => seq('break', optional($.identifier), ';'),")
        appendLine("    continue_statement: $ => seq('continue', optional($.identifier), ';'),")
        appendLine("    assert_statement: $ => seq('assert', $._expression, optional(seq(',', $._expression)), ';'),")
        appendLine("    expression_statement: $ => seq($._expression, ';'),")
        appendLine()

        // Expressions
        appendLine("    _expression: $ => choice(")
        appendLine("      $.assignment_expression,")
        appendLine("      $.ternary_expression,")
        appendLine("      $.binary_expression,")
        appendLine("      $.unary_expression,")
        appendLine("      $.postfix_expression,")
        appendLine("      $.call_expression,")
        appendLine("      $.member_expression,")
        appendLine("      $.index_expression,")
        appendLine("      $.new_expression,")
        appendLine("      $.lambda_expression,")
        appendLine("      $.parenthesized_expression,")
        appendLine("      $.identifier,")
        appendLine("      $._literal,")
        appendLine("    ),")
        appendLine()

        // Assignment expression - operators from model
        val assignmentOps = model.operators
            .filter { it.category == OperatorCategory.ASSIGNMENT }
            .map { escapeJsString(it.symbol) }
        appendLine("    assignment_expression: $ => prec.right(1, seq($._expression, choice(${assignmentOps.joinToString(", ") { "'$it'" }}), $._expression)),")
        appendLine("    ternary_expression: $ => prec.right(2, seq($._expression, '?', $._expression, ':', $._expression)),")
        appendLine()

        // Binary expression - generate from model operators grouped by precedence
        appendLine("    binary_expression: $ => choice(")
        generateBinaryExpressionRules()
        appendLine("    ),")
        appendLine()

        // Unary operators - prefix operators from model (those that make sense as prefix)
        val prefixUnaryOps = listOf("!", "~", "-", "+", "++", "--")
            .filter { sym -> model.operators.any { it.symbol == sym } }
            .map { escapeJsString(it) }
        val maxPrecedence = model.operators.maxOfOrNull { it.precedence } ?: 15
        appendLine("    unary_expression: $ => prec.right(${maxPrecedence + 1}, seq(choice(${prefixUnaryOps.joinToString(", ") { "'$it'" }}), $._expression)),")

        // Postfix operators
        val postfixOps = listOf("++", "--", "!")
            .filter { sym -> model.operators.any { it.symbol == sym } }
            .map { escapeJsString(it) }
        appendLine("    postfix_expression: $ => prec.left(${maxPrecedence + 1}, seq($._expression, choice(${postfixOps.joinToString(", ") { "'$it'" }}))),")
        appendLine()

        // Member access - operators from model
        val memberAccessOps = model.operators
            .filter { it.category == OperatorCategory.MEMBER_ACCESS }
            .map { escapeJsString(it.symbol) }
        appendLine("    call_expression: $ => prec.left(${maxPrecedence + 2}, seq($._expression, optional($.type_arguments), $.arguments)),")
        appendLine("    member_expression: $ => prec.left(${maxPrecedence + 2}, seq($._expression, choice(${memberAccessOps.joinToString(", ") { "'$it'" }}), $.identifier)),")
        appendLine("    index_expression: $ => prec.left(${maxPrecedence + 2}, seq($._expression, '[', commaSep1($._expression), ']')),")
        appendLine()

        appendLine("    new_expression: $ => seq('new', $.type_expression, $.arguments),")
        appendLine("    lambda_expression: $ => seq(")
        appendLine("      choice($.identifier, $.parameters),")
        appendLine("      '->',")
        appendLine("      choice($._expression, $.block),")
        appendLine("    ),")
        appendLine("    parenthesized_expression: $ => seq('(', $._expression, ')'),")
        appendLine()

        appendLine("    arguments: $ => seq('(', commaSep($._expression), ')'),")
        appendLine()

        // Literals
        appendLine("    _literal: $ => choice(")
        appendLine("      $.integer_literal,")
        appendLine("      $.float_literal,")
        appendLine("      $.string_literal,")
        appendLine("      $.template_string_literal,")
        appendLine("      $.char_literal,")
        appendLine("      $.boolean_literal,")
        appendLine("      $.null_literal,")
        appendLine("      $.list_literal,")
        appendLine("      $.map_literal,")
        appendLine("    ),")
        appendLine()

        appendLine("    integer_literal: $ => token(choice(")
        appendLine("      /[0-9][0-9_]*/,")
        appendLine("      /0[xX][0-9a-fA-F][0-9a-fA-F_]*/,")
        appendLine("      /0[bB][01][01_]*/,")
        appendLine("    )),")
        appendLine()
        appendLine("    float_literal: $ => /[0-9][0-9_]*\\.[0-9][0-9_]*([eE][+-]?[0-9]+)?/,")
        appendLine()
        appendLine("    string_literal: $ => /\"([^\"\\\\]|\\\\.)*\"/,")
        appendLine("    template_string_literal: $ => /\\$\"([^\"\\\\]|\\\\.)*\"/,")
        appendLine("    char_literal: $ => /'([^'\\\\]|\\\\.)'/,")
        appendLine()
        appendLine("    boolean_literal: $ => choice('True', 'False'),")
        appendLine("    null_literal: $ => 'Null',")
        appendLine()
        appendLine("    list_literal: $ => seq('[', commaSep($._expression), ']'),")
        appendLine("    map_literal: $ => seq('[', commaSep($.map_entry), ']'),")
        appendLine("    map_entry: $ => seq($._expression, '=', $._expression),")
        appendLine()

        // Identifiers and names
        appendLine("    identifier: $ => /[a-zA-Z_][a-zA-Z0-9_]*/,")
        appendLine("    type_name: $ => /[A-Z][a-zA-Z0-9_]*/,")
        appendLine("    qualified_name: $ => sep1($.identifier, '.'),")
        appendLine()

        // Annotations
        appendLine("    annotation: $ => seq('@', $.identifier, optional($.arguments)),")
        appendLine()

        // Visibility
        appendLine("    visibility_modifier: $ => choice('public', 'protected', 'private'),")
        appendLine()

        // Comments
        appendLine("    comment: $ => choice(")
        appendLine("      $.line_comment,")
        appendLine("      $.block_comment,")
        appendLine("      $.doc_comment,")
        appendLine("    ),")
        appendLine()
        appendLine("    line_comment: $ => token(seq('//', /.*/)),")
        appendLine("    block_comment: $ => token(seq('/*', /[^*]*\\*+([^/*][^*]*\\*+)*/, '/')),")
        appendLine("    doc_comment: $ => token(seq('/**', /[^*]*\\*+([^/*][^*]*\\*+)*/, '/')),")
        appendLine("  },")
        appendLine("});")
        appendLine()

        // Helper functions
        appendLine("function sep1(rule, separator) {")
        appendLine("  return seq(rule, repeat(seq(separator, rule)));")
        appendLine("}")
        appendLine()
        appendLine("function commaSep(rule) {")
        appendLine("  return optional(commaSep1(rule));")
        appendLine("}")
        appendLine()
        appendLine("function commaSep1(rule) {")
        appendLine("  return seq(rule, repeat(seq(',', rule)));")
        appendLine("}")
    }

    /**
     * Generates the Tree-sitter highlights.scm query file
     */
    fun generateHighlights(): String = buildString {
        appendLine("; Tree-sitter highlights for ${model.name}")
        appendLine("; Generated from XTC language model")
        appendLine()

        // Comments
        appendLine("; Comments")
        appendLine("(line_comment) @comment")
        appendLine("(block_comment) @comment")
        appendLine("(doc_comment) @comment.documentation")
        appendLine()

        // Control flow keywords from model
        val controlKeywords = model.keywordsByCategory(KeywordCategory.CONTROL)
        if (controlKeywords.isNotEmpty()) {
            appendLine("; Control flow keywords")
            for (kw in controlKeywords) {
                appendLine("\"$kw\" @keyword")
            }
            appendLine()
        }

        // Exception keywords from model
        val exceptionKeywords = model.keywordsByCategory(KeywordCategory.EXCEPTION)
            .filter { !it.contains(":") }
        if (exceptionKeywords.isNotEmpty()) {
            appendLine("; Exception keywords")
            for (kw in exceptionKeywords) {
                appendLine("\"$kw\" @keyword")
            }
            appendLine()
        }

        // Declaration keywords from model
        val declarationKeywords = model.keywordsByCategory(KeywordCategory.DECLARATION)
        if (declarationKeywords.isNotEmpty()) {
            appendLine("; Declaration keywords")
            for (kw in declarationKeywords) {
                appendLine("\"$kw\" @keyword")
            }
            appendLine()
        }

        // Modifier keywords from model
        val modifierKeywords = model.keywordsByCategory(KeywordCategory.MODIFIER)
        if (modifierKeywords.isNotEmpty()) {
            appendLine("; Modifiers")
            appendLine("(visibility_modifier) @keyword.modifier")
            for (kw in modifierKeywords.filter { it !in listOf("public", "protected", "private") }) {
                appendLine("\"$kw\" @keyword.modifier")
            }
            appendLine()
        }

        // Type relation keywords from model
        val typeRelationKeywords = model.keywordsByCategory(KeywordCategory.TYPE_RELATION)
        if (typeRelationKeywords.isNotEmpty()) {
            appendLine("; Type relation keywords")
            for (kw in typeRelationKeywords) {
                appendLine("\"$kw\" @keyword")
            }
            appendLine()
        }

        // Operators from model
        appendLine("; Operators")
        appendLine("[")
        val operatorSymbols = model.operators.map { escapeTreeSitterSymbol(it.symbol) }
        // Group operators by line for readability
        operatorSymbols.chunked(8).forEach { chunk ->
            appendLine("  ${chunk.joinToString(" ") { "\"$it\"" }}")
        }
        appendLine("] @operator")
        appendLine()

        // Punctuation from model
        appendLine("; Punctuation")
        val delimiters = model.punctuation
            .filter { it.symbol in listOf(".", ",", ";", ":") }
            .map { "\"${it.symbol}\"" }
        if (delimiters.isNotEmpty()) {
            appendLine("[${delimiters.joinToString(" ")}] @punctuation.delimiter")
        }
        val brackets = model.punctuation
            .filter { it.symbol in listOf("(", ")", "[", "]", "{", "}", "<", ">") }
            .map { "\"${it.symbol}\"" }
        if (brackets.isNotEmpty()) {
            appendLine("[${brackets.joinToString(" ")}] @punctuation.bracket")
        }
        appendLine()

        // Literals
        appendLine("; Literals")
        appendLine("(integer_literal) @number")
        appendLine("(float_literal) @number.float")
        appendLine("(string_literal) @string")
        appendLine("(template_string_literal) @string")
        appendLine("(char_literal) @character")
        appendLine("(boolean_literal) @constant.builtin")
        appendLine("(null_literal) @constant.builtin")
        appendLine()

        // Types
        appendLine("; Types")
        appendLine("(type_name) @type")
        appendLine("(type_parameter (identifier) @type.parameter)")
        appendLine()

        // Built-in types from model
        if (model.builtinTypes.isNotEmpty()) {
            appendLine("; Built-in types")
            appendLine("((type_name) @type.builtin")
            appendLine("  (#match? @type.builtin \"^(${model.builtinTypes.joinToString("|")})$\"))")
            appendLine()
        }

        // Functions
        appendLine("; Functions")
        appendLine("(method_declaration")
        appendLine("  name: (identifier) @function)")
        appendLine("(constructor_declaration")
        appendLine("  (\"construct\") @constructor)")
        appendLine("(call_expression")
        appendLine("  function: (identifier) @function.call)")
        appendLine("(call_expression")
        appendLine("  function: (member_expression")
        appendLine("    property: (identifier) @function.call))")
        appendLine()

        // Variables
        appendLine("; Variables")
        appendLine("(identifier) @variable")
        appendLine("(parameter name: (identifier) @variable.parameter)")
        appendLine("(property_declaration name: (identifier) @variable.member)")
        appendLine()

        // Modules and classes
        appendLine("; Definitions")
        appendLine("(module_declaration name: (qualified_name) @namespace)")
        appendLine("(package_declaration name: (identifier) @namespace)")
        appendLine("(class_declaration name: (type_name) @type.definition)")
        appendLine("(interface_declaration name: (type_name) @type.definition)")
        appendLine("(mixin_declaration name: (type_name) @type.definition)")
        appendLine("(service_declaration name: (type_name) @type.definition)")
        appendLine("(const_declaration name: (type_name) @type.definition)")
        appendLine("(enum_declaration name: (type_name) @type.definition)")
        appendLine()

        // Annotations
        appendLine("; Annotations")
        appendLine("(annotation \"@\" @punctuation.special)")
        appendLine("(annotation name: (identifier) @attribute)")
    }

    private fun escapeTreeSitterSymbol(s: String): String = buildString {
        for (c in s) {
            when (c) {
                '"', '\\' -> {
                    append('\\')
                    append(c)
                }
                else -> append(c)
            }
        }
    }

    private fun escapeJsString(s: String): String = buildString {
        for (c in s) {
            when (c) {
                '\'', '\\' -> {
                    append('\\')
                    append(c)
                }
                else -> append(c)
            }
        }
    }

    /**
     * Generates binary expression rules from model operators, grouped by precedence.
     * Operators at the same precedence level are combined into a choice().
     */
    private fun StringBuilder.generateBinaryExpressionRules() {
        // Get binary operators (exclude assignment, member access, and unary-only operators)
        val binaryOps = model.operators.filter { op ->
            op.category !in listOf(OperatorCategory.ASSIGNMENT, OperatorCategory.MEMBER_ACCESS) &&
                op.symbol !in listOf("!", "~", "++", "--") // exclude unary-only
        }

        // Group by precedence
        val byPrecedence = binaryOps.groupBy { it.precedence }
            .toSortedMap() // Sort by precedence (lowest first = binds last)

        byPrecedence.entries.forEachIndexed { index, (precedence, ops) ->
            val precFn = when {
                ops.all { it.associativity == Associativity.RIGHT } -> "prec.right"
                else -> "prec.left"
            }

            val symbols = ops.map { escapeJsString(it.symbol) }
            val choiceExpr = if (symbols.size == 1) {
                "'${symbols.first()}'"
            } else {
                "choice(${symbols.joinToString(", ") { "'$it'" }})"
            }

            val comma = if (index < byPrecedence.size - 1) "," else ""
            appendLine("      $precFn($precedence, seq(\$._expression, $choiceExpr, \$._expression))$comma")
        }
    }
}