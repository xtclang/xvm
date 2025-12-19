package org.xtclang.tooling.generators

import org.xtclang.tooling.model.*

/**
 * Generates Tree-sitter grammar (grammar.js)
 * 
 * Tree-sitter is used by:
 * - Zed (primary)
 * - Neovim (via nvim-treesitter)
 * - Helix
 * - Emacs (via tree-sitter integration)
 * - GitHub (syntax highlighting and code navigation)
 * - Many more
 */
class TreeSitterGenerator(private val model: LanguageModel) {
    
    fun generate(): String = buildString {
        appendLine("""
/**
 * Tree-sitter grammar for ${model.name}
 * 
 * Generated from XTC language model
 * 
 * To build:
 *   npm install
 *   npx tree-sitter generate
 *   npx tree-sitter build-wasm
 */
module.exports = grammar({
    name: '${model.name.lowercase()}',
    
    // External scanner for complex tokens (strings, comments)
    externals: $ => [
        $._string_content,
        $._template_content,
    ],
    
    // Tokens that can appear anywhere (whitespace, comments)
    extras: $ => [
        /\s/,
        $.line_comment,
        $.block_comment,
    ],
    
    // Word token for keyword extraction
    word: $ => $.identifier,
    
    // Conflicts that need explicit precedence
    conflicts: $ => [
        [$.primary_expression, $.type_expression],
        [$._type_identifier, $.identifier],
    ],
    
    // Inline rules (won't create nodes in the tree)
    inline: $ => [
        $._declaration,
        $._statement,
        $._expression,
    ],
    
    rules: {
        // =====================================================================
        // Source File
        // =====================================================================
        
        source_file: $ => seq(
            optional($.module_declaration),
            repeat($.import_statement),
            repeat($._declaration)
        ),
        
        // =====================================================================
        // Module and Package
        // =====================================================================
        
        module_declaration: $ => seq(
            repeat($.annotation),
            'module',
            $.qualified_identifier,
            optional($.version_clause),
            $.declaration_body
        ),
        
        package_declaration: $ => seq(
            repeat($.annotation),
            'package',
            $.identifier,
            optional(seq('import', $.qualified_identifier)),
            $.declaration_body
        ),
        
        import_statement: $ => seq(
            'import',
            $.qualified_identifier,
            optional(seq('as', $.identifier)),
            ';'
        ),
        
        version_clause: $ => seq(
            '.', 'v', ':', /[0-9]+(\.[0-9]+)*/
        ),
        
        // =====================================================================
        // Declarations
        // =====================================================================
        
        _declaration: $ => choice(
            $.class_declaration,
            $.interface_declaration,
            $.mixin_declaration,
            $.service_declaration,
            $.const_declaration,
            $.enum_declaration,
            $.typedef_declaration,
            $.package_declaration
        ),
        
        class_declaration: $ => seq(
            repeat($.annotation),
            repeat($._modifier),
            'class',
            $._type_identifier,
            optional($.type_parameters),
            optional($.extends_clause),
            optional($.implements_clause),
            optional($.incorporates_clause),
            $.class_body
        ),
        
        interface_declaration: $ => seq(
            repeat($.annotation),
            repeat($._modifier),
            'interface',
            $._type_identifier,
            optional($.type_parameters),
            optional($.extends_clause),
            $.interface_body
        ),
        
        mixin_declaration: $ => seq(
            repeat($.annotation),
            repeat($._modifier),
            'mixin',
            $._type_identifier,
            optional($.type_parameters),
            optional($.into_clause),
            optional($.extends_clause),
            optional($.implements_clause),
            $.class_body
        ),
        
        service_declaration: $ => seq(
            repeat($.annotation),
            repeat($._modifier),
            'service',
            $._type_identifier,
            optional($.type_parameters),
            optional($.extends_clause),
            optional($.implements_clause),
            $.class_body
        ),
        
        const_declaration: $ => seq(
            repeat($.annotation),
            repeat($._modifier),
            'const',
            $._type_identifier,
            optional($.type_parameters),
            optional($.extends_clause),
            optional($.implements_clause),
            $.class_body
        ),
        
        enum_declaration: $ => seq(
            repeat($.annotation),
            repeat($._modifier),
            'enum',
            $._type_identifier,
            optional($.implements_clause),
            $.enum_body
        ),
        
        typedef_declaration: $ => seq(
            repeat($.annotation),
            repeat($._modifier),
            'typedef',
            $._type_identifier,
            optional($.type_parameters),
            'as',
            $.type_expression,
            ';'
        ),
        
        // =====================================================================
        // Clauses
        // =====================================================================
        
        extends_clause: $ => seq(
            'extends',
            commaSep1($.type_expression)
        ),
        
        implements_clause: $ => seq(
            'implements',
            commaSep1($.type_expression)
        ),
        
        incorporates_clause: $ => seq(
            'incorporates',
            commaSep1($.incorporates_item)
        ),
        
        incorporates_item: $ => seq(
            optional('conditional'),
            $.type_expression,
            optional($.argument_list)
        ),
        
        into_clause: $ => seq(
            'into',
            $.type_expression
        ),
        
        // =====================================================================
        // Bodies
        // =====================================================================
        
        declaration_body: $ => seq(
            '{',
            repeat($._declaration),
            '}'
        ),
        
        class_body: $ => seq(
            '{',
            repeat($._class_member),
            '}'
        ),
        
        interface_body: $ => seq(
            '{',
            repeat($._interface_member),
            '}'
        ),
        
        enum_body: $ => seq(
            '{',
            optional(seq(
                commaSep1($.enum_value),
                optional(',')
            )),
            optional(seq(';', repeat($._class_member))),
            '}'
        ),
        
        enum_value: $ => seq(
            $.identifier,
            optional($.argument_list),
            optional($.class_body)
        ),
        
        // =====================================================================
        // Members
        // =====================================================================
        
        _class_member: $ => choice(
            $.property_declaration,
            $.method_declaration,
            $.constructor_declaration,
            $._declaration
        ),
        
        _interface_member: $ => choice(
            $.property_declaration,
            $.method_signature
        ),
        
        property_declaration: $ => seq(
            repeat($.annotation),
            repeat($._modifier),
            $.type_expression,
            $.identifier,
            optional(seq('=', $._expression)),
            optional($.property_body),
            ';'
        ),
        
        property_body: $ => seq(
            '{',
            optional($.getter),
            optional($.setter),
            '}'
        ),
        
        getter: $ => seq(
            'get',
            choice(';', $.block)
        ),
        
        setter: $ => seq(
            'set',
            choice(';', $.block)
        ),
        
        method_declaration: $ => seq(
            repeat($.annotation),
            repeat($._modifier),
            optional('conditional'),
            $.return_types,
            $.identifier,
            optional($.type_parameters),
            $.parameter_list,
            choice(';', $.block)
        ),
        
        method_signature: $ => seq(
            repeat($.annotation),
            repeat($._modifier),
            optional('conditional'),
            $.return_types,
            $.identifier,
            optional($.type_parameters),
            $.parameter_list,
            ';'
        ),
        
        constructor_declaration: $ => seq(
            repeat($.annotation),
            repeat($._modifier),
            choice('construct', 'finally'),
            $.parameter_list,
            choice(';', $.block)
        ),
        
        return_types: $ => choice(
            'void',
            $.type_expression,
            $.tuple_type
        ),
        
        // =====================================================================
        // Parameters and Type Parameters
        // =====================================================================
        
        parameter_list: $ => seq(
            '(',
            optional(commaSep($.parameter)),
            ')'
        ),
        
        parameter: $ => seq(
            repeat($.annotation),
            $.type_expression,
            $.identifier,
            optional(seq('=', $._expression))
        ),
        
        type_parameters: $ => seq(
            '<',
            commaSep1($.type_parameter),
            '>'
        ),
        
        type_parameter: $ => seq(
            $.identifier,
            optional($.type_constraint)
        ),
        
        type_constraint: $ => seq(
            'extends',
            $.type_expression
        ),
        
        type_arguments: $ => seq(
            '<',
            commaSep1($.type_expression),
            '>'
        ),
        
        // =====================================================================
        // Types
        // =====================================================================
        
        type_expression: $ => prec.left(choice(
            $._type_identifier,
            $.qualified_type,
            $.nullable_type,
            $.function_type,
            $.tuple_type,
            $.array_type,
            $.immutable_type,
            $.parameterized_type
        )),
        
        _type_identifier: $ => alias($.identifier, $.type_identifier),
        
        qualified_type: $ => seq(
            $.type_expression,
            '.',
            $._type_identifier
        ),
        
        nullable_type: $ => seq(
            $.type_expression,
            '?'
        ),
        
        function_type: $ => seq(
            'function',
            $.type_expression,
            $.parameter_list
        ),
        
        tuple_type: $ => seq(
            '(',
            commaSep1($.type_expression),
            ')'
        ),
        
        array_type: $ => seq(
            $.type_expression,
            '[',
            ']'
        ),
        
        immutable_type: $ => seq(
            'immutable',
            $.type_expression
        ),
        
        parameterized_type: $ => seq(
            $.type_expression,
            $.type_arguments
        ),
        
        // =====================================================================
        // Statements
        // =====================================================================
        
        _statement: $ => choice(
            $.block,
            $.variable_declaration,
            $.expression_statement,
            $.if_statement,
            $.switch_statement,
            $.for_statement,
            $.foreach_statement,
            $.while_statement,
            $.do_statement,
            $.try_statement,
            $.return_statement,
            $.throw_statement,
            $.assert_statement,
            $.break_statement,
            $.continue_statement,
            $.using_statement
        ),
        
        block: $ => seq(
            '{',
            repeat($._statement),
            '}'
        ),
        
        variable_declaration: $ => seq(
            $.type_expression,
            $.identifier,
            optional(seq('=', $._expression)),
            ';'
        ),
        
        expression_statement: $ => seq(
            $._expression,
            ';'
        ),
        
        if_statement: $ => prec.right(seq(
            'if',
            '(',
            $._expression,
            ')',
            $._statement,
            optional(seq('else', $._statement))
        )),
        
        switch_statement: $ => seq(
            'switch',
            '(',
            $._expression,
            ')',
            '{',
            repeat($.case_clause),
            '}'
        ),
        
        case_clause: $ => seq(
            choice(
                seq('case', commaSep1($._expression), ':'),
                seq('default', ':')
            ),
            repeat($._statement)
        ),
        
        for_statement: $ => seq(
            'for',
            '(',
            optional($._expression),
            ';',
            optional($._expression),
            ';',
            optional($._expression),
            ')',
            $._statement
        ),
        
        foreach_statement: $ => seq(
            choice('for', 'foreach'),
            '(',
            $.type_expression,
            $.identifier,
            ':',
            $._expression,
            ')',
            $._statement
        ),
        
        while_statement: $ => seq(
            'while',
            '(',
            $._expression,
            ')',
            $._statement
        ),
        
        do_statement: $ => seq(
            'do',
            $._statement,
            'while',
            '(',
            $._expression,
            ')',
            ';'
        ),
        
        try_statement: $ => seq(
            'try',
            optional($.resource_specification),
            $.block,
            repeat($.catch_clause),
            optional($.finally_clause)
        ),
        
        resource_specification: $ => seq(
            '(',
            commaSep1($.resource),
            ')'
        ),
        
        resource: $ => seq(
            $.type_expression,
            $.identifier,
            '=',
            $._expression
        ),
        
        catch_clause: $ => seq(
            'catch',
            '(',
            $.type_expression,
            $.identifier,
            ')',
            $.block
        ),
        
        finally_clause: $ => seq(
            'finally',
            $.block
        ),
        
        return_statement: $ => seq(
            'return',
            optional(commaSep1($._expression)),
            ';'
        ),
        
        throw_statement: $ => seq(
            'throw',
            $._expression,
            ';'
        ),
        
        assert_statement: $ => seq(
            choice('assert', 'assert:rnd', 'assert:arg', 'assert:bounds', 
                   'assert:todo', 'assert:once', 'assert:test', 'assert:dbg'),
            $._expression,
            optional(seq(',', $._expression)),
            ';'
        ),
        
        break_statement: $ => seq(
            'break',
            optional($.identifier),
            ';'
        ),
        
        continue_statement: $ => seq(
            'continue',
            optional($.identifier),
            ';'
        ),
        
        using_statement: $ => seq(
            'using',
            '(',
            $.resource,
            ')',
            $._statement
        ),
        
        // =====================================================================
        // Expressions
        // =====================================================================
        
        _expression: $ => choice(
            $.primary_expression,
            $.unary_expression,
            $.binary_expression,
            $.ternary_expression,
            $.assignment_expression,
            $.lambda_expression
        ),
        
        primary_expression: $ => choice(
            $.literal,
            $.identifier,
            $.this_expression,
            $.super_expression,
            $.parenthesized_expression,
            $.new_expression,
            $.invocation_expression,
            $.member_access,
            $.index_expression,
            $.list_expression,
            $.map_expression,
            $.tuple_expression,
            $.template_expression,
            $.switch_expression
        ),
        
        literal: $ => choice(
            $.integer_literal,
            $.float_literal,
            $.string_literal,
            $.char_literal,
            $.boolean_literal,
            $.null_literal
        ),
        
        integer_literal: $ => token(choice(
            /0[xX][0-9a-fA-F][0-9a-fA-F_]*/,
            /0[bB][01][01_]*/,
            /[0-9][0-9_]*/
        )),
        
        float_literal: $ => token(
            /[0-9][0-9_]*\.[0-9][0-9_]*([eE][+-]?[0-9]+)?/
        ),
        
        string_literal: $ => seq(
            '"',
            repeat(choice(
                $._string_content,
                $.escape_sequence
            )),
            '"'
        ),
        
        template_expression: $ => seq(
            '$"',
            repeat(choice(
                $._template_content,
                $.escape_sequence,
                $.template_substitution
            )),
            '"'
        ),
        
        template_substitution: $ => seq(
            '{',
            $._expression,
            '}'
        ),
        
        escape_sequence: $ => token.immediate(/\\[nrtbf\\"'\\]|\\u[0-9a-fA-F]{4}/),
        
        char_literal: $ => seq(
            "'",
            choice(
                /[^'\\]/,
                $.escape_sequence
            ),
            "'"
        ),
        
        boolean_literal: $ => choice('True', 'False'),
        
        null_literal: $ => 'Null',
        
        this_expression: $ => seq(
            'this',
            optional(seq(':', $.type_expression))
        ),
        
        super_expression: $ => seq(
            'super',
            optional(seq(':', $.type_expression))
        ),
        
        parenthesized_expression: $ => seq(
            '(',
            $._expression,
            ')'
        ),
        
        new_expression: $ => seq(
            'new',
            $.type_expression,
            $.argument_list,
            optional($.class_body)
        ),
        
        invocation_expression: $ => prec.left(15, seq(
            $._expression,
            optional($.type_arguments),
            $.argument_list
        )),
        
        member_access: $ => prec.left(15, seq(
            $._expression,
            choice('.', '?.'),
            $.identifier
        )),
        
        index_expression: $ => prec.left(15, seq(
            $._expression,
            '[',
            commaSep1($._expression),
            ']'
        )),
        
        argument_list: $ => seq(
            '(',
            optional(commaSep($.argument)),
            ')'
        ),
        
        argument: $ => seq(
            optional(seq($.identifier, '=')),
            $._expression
        ),
        
        unary_expression: $ => choice(
            prec.right(14, seq(choice('!', '~', '-', '+', '++', '--'), $._expression)),
            prec.left(14, seq($._expression, choice('++', '--')))
        ),
        
        binary_expression: $ => choice(
            // Multiplicative
            prec.left(13, seq($._expression, choice('*', '/', '%', '/%'), $._expression)),
            // Additive
            prec.left(12, seq($._expression, choice('+', '-'), $._expression)),
            // Shift
            prec.left(11, seq($._expression, choice('<<', '>>', '>>>'), $._expression)),
            // Range
            prec.left(10, seq($._expression, choice('..', '..<'), $._expression)),
            // Relational
            prec.left(9, seq($._expression, choice('<', '<=', '>', '>=', '<=>'), $._expression)),
            // Equality
            prec.left(8, seq($._expression, choice('==', '!='), $._expression)),
            // Bitwise AND
            prec.left(7, seq($._expression, '&', $._expression)),
            // Bitwise XOR
            prec.left(6, seq($._expression, '^', $._expression)),
            // Bitwise OR
            prec.left(5, seq($._expression, '|', $._expression)),
            // Logical AND
            prec.left(4, seq($._expression, '&&', $._expression)),
            // Logical OR/XOR
            prec.left(3, seq($._expression, choice('||', '^^'), $._expression)),
            // Elvis
            prec.right(2, seq($._expression, '?:', $._expression)),
            // Type check
            prec.left(9, seq($._expression, choice('is', 'as'), $.type_expression))
        ),
        
        ternary_expression: $ => prec.right(2, seq(
            $._expression,
            '?',
            $._expression,
            ':',
            $._expression
        )),
        
        assignment_expression: $ => prec.right(1, seq(
            $._expression,
            choice('=', '+=', '-=', '*=', '/=', '%=', '&=', '|=', '^=', 
                   '<<=', '>>=', '>>>=', ':=', '?='),
            $._expression
        )),
        
        lambda_expression: $ => seq(
            choice(
                $.identifier,
                seq('(', optional(commaSep($.lambda_parameter)), ')')
            ),
            '->',
            choice($._expression, $.block)
        ),
        
        lambda_parameter: $ => seq(
            optional($.type_expression),
            $.identifier
        ),
        
        list_expression: $ => seq(
            optional(seq($.type_expression, ':')),
            '[',
            optional(commaSep($._expression)),
            ']'
        ),
        
        map_expression: $ => seq(
            optional(seq($.type_expression, ':')),
            '[',
            optional(commaSep($.map_entry)),
            ']'
        ),
        
        map_entry: $ => seq(
            $._expression,
            '=',
            $._expression
        ),
        
        tuple_expression: $ => seq(
            '(',
            commaSep1($._expression),
            ',',
            optional($._expression),
            ')'
        ),
        
        switch_expression: $ => seq(
            'switch',
            optional(seq('(', $._expression, ')')),
            '{',
            repeat($.switch_expression_arm),
            '}'
        ),
        
        switch_expression_arm: $ => seq(
            choice(
                seq('case', commaSep1($._expression)),
                'default'
            ),
            '->',
            choice($._expression, $.block),
            optional(';')
        ),
        
        // =====================================================================
        // Modifiers and Annotations
        // =====================================================================
        
        _modifier: $ => choice(
            'public', 'protected', 'private',
            'static', 'abstract', 'final', 'immutable'
        ),
        
        annotation: $ => seq(
            '@',
            $.identifier,
            optional($.argument_list)
        ),
        
        // =====================================================================
        // Identifiers
        // =====================================================================
        
        identifier: $ => /[a-zA-Z_][a-zA-Z0-9_]*/,
        
        qualified_identifier: $ => sep1($.identifier, '.'),
        
        // =====================================================================
        // Comments
        // =====================================================================
        
        line_comment: $ => token(seq('//', /.*/)),
        
        block_comment: $ => token(seq(
            '/*',
            /[^*]*\*+([^/*][^*]*\*+)*/,
            '/'
        )),
    }
});

// Helper functions
function commaSep(rule) {
    return optional(commaSep1(rule));
}

function commaSep1(rule) {
    return seq(rule, repeat(seq(',', rule)));
}

function sep1(rule, separator) {
    return seq(rule, repeat(seq(separator, rule)));
}
""".trimIndent())
    }
    
    /**
     * Generate highlights.scm for Tree-sitter queries
     */
    fun generateHighlightsQuery(): String = buildString {
        appendLine("; Highlights query for ${model.name}")
        appendLine()
        
        appendLine("; Keywords")
        appendLine("${keywordsList("control")} @keyword")
        appendLine("${keywordsList("declaration")} @keyword.declaration")  
        appendLine("${keywordsList("modifier")} @keyword.modifier")
        appendLine()
        
        appendLine("; Types")
        appendLine("(type_identifier) @type")
        appendLine("(parameterized_type (type_identifier) @type)")
        appendLine()
        
        appendLine("; Functions and methods")
        appendLine("(method_declaration name: (identifier) @function.method)")
        appendLine("(constructor_declaration) @function.constructor")
        appendLine("(invocation_expression (identifier) @function.call)")
        appendLine("(invocation_expression (member_access (identifier) @function.method.call))")
        appendLine()
        
        appendLine("; Variables and parameters")
        appendLine("(parameter name: (identifier) @variable.parameter)")
        appendLine("(variable_declaration name: (identifier) @variable)")
        appendLine("(property_declaration name: (identifier) @property)")
        appendLine()
        
        appendLine("; Literals")
        appendLine("(integer_literal) @number")
        appendLine("(float_literal) @number.float")
        appendLine("(string_literal) @string")
        appendLine("(template_expression) @string")
        appendLine("(char_literal) @character")
        appendLine("(boolean_literal) @constant.builtin")
        appendLine("(null_literal) @constant.builtin")
        appendLine("(escape_sequence) @string.escape")
        appendLine()
        
        appendLine("; Comments")
        appendLine("(line_comment) @comment")
        appendLine("(block_comment) @comment")
        appendLine()
        
        appendLine("; Annotations")
        appendLine("(annotation \"@\" @punctuation.special)")
        appendLine("(annotation (identifier) @attribute)")
        appendLine()
        
        appendLine("; Operators")
        appendLine("[\"=\" \"+\" \"-\" \"*\" \"/\" \"%\" \"!\" \"~\" \"&\" \"|\" \"^\" \"<\" \">\" \"?\" \":\"] @operator")
        appendLine("[\"==\" \"!=\" \"<=\" \">=\" \"&&\" \"||\" \"++\" \"--\" \"<<\" \">>\" \"?.\" \"?:\" \"..\"] @operator")
        appendLine()
        
        appendLine("; Punctuation")
        appendLine("[\"{\" \"}\"] @punctuation.bracket")
        appendLine("[\"(\" \")\"] @punctuation.bracket")
        appendLine("[\"[\" \"]\"] @punctuation.bracket")
        appendLine("[\",\" \";\"] @punctuation.delimiter")
        appendLine("\".\" @punctuation.delimiter")
    }
    
    private fun keywordsList(category: String): String {
        val keywords = when (category) {
            "control" -> listOf(
                "if", "else", "switch", "case", "default",
                "for", "while", "do", "foreach",
                "break", "continue", "return",
                "try", "catch", "finally", "throw",
                "assert", "using"
            )
            "declaration" -> listOf(
                "module", "package", "class", "interface", "mixin", "service",
                "const", "enum", "typedef", "import", "construct"
            )
            "modifier" -> listOf(
                "public", "protected", "private", "static",
                "abstract", "final", "immutable",
                "extends", "implements", "incorporates", "into", "inject", "conditional"
            )
            else -> emptyList()
        }
        return "[${keywords.joinToString(" ") { "\"$it\"" }}]"
    }
    
    /**
     * Generate package.json for the Tree-sitter grammar
     */
    fun generatePackageJson(): String = """
{
  "name": "tree-sitter-${model.name.lowercase()}",
  "version": "1.0.0",
  "description": "Tree-sitter grammar for ${model.name}",
  "main": "bindings/node",
  "keywords": [
    "tree-sitter",
    "parser",
    "${model.name.lowercase()}"
  ],
  "repository": {
    "type": "git",
    "url": "https://github.com/xtclang/tree-sitter-${model.name.lowercase()}"
  },
  "author": "xtclang.org",
  "license": "MIT",
  "dependencies": {
    "nan": "^2.17.0"
  },
  "devDependencies": {
    "tree-sitter-cli": "^0.20.8"
  },
  "scripts": {
    "build": "tree-sitter generate && tree-sitter build-wasm",
    "test": "tree-sitter test",
    "parse": "tree-sitter parse"
  },
  "tree-sitter": [
    {
      "scope": "${model.scopeName}",
      "file-types": ${model.fileExtensions.map { "\"$it\"" }},
      "highlights": "queries/highlights.scm",
      "injection-regex": "^${model.name.lowercase()}$"
    }
  ]
}
""".trimIndent()
}
