/**
 * Tree-sitter grammar for Ecstasy
 * Generated from XTC language model
 */

module.exports = grammar({
  name: 'xtc',

  extras: $ => [
    /\s/,
    $.comment,
  ],

  externals: $ => [],

  conflicts: $ => [],

  word: $ => $.identifier,

  rules: {
    source_file: $ => repeat($._definition),

    _definition: $ => choice(
      $.module_declaration,
      $.package_declaration,
      $.import_statement,
      $.class_declaration,
      $.interface_declaration,
      $.mixin_declaration,
      $.service_declaration,
      $.const_declaration,
      $.enum_declaration,
    ),

    module_declaration: $ => seq(
      repeat($.annotation),
      'module',
      $.qualified_name,
      optional($.module_body),
    ),

    module_body: $ => seq('{', repeat($._definition), '}'),

    package_declaration: $ => seq(
      repeat($.annotation),
      'package',
      $.identifier,
      optional($.import_spec),
      optional($.package_body),
    ),

    package_body: $ => seq('{', repeat($._definition), '}'),

    import_statement: $ => seq(
      'import',
      $.qualified_name,
      optional(seq('as', $.identifier)),
      ';',
    ),

    import_spec: $ => seq('import', $.qualified_name),

    class_declaration: $ => seq(
      repeat($.annotation),
      optional($.visibility_modifier),
      optional('static'),
      optional('abstract'),
      'class',
      $.type_name,
      optional($.type_parameters),
      optional($.extends_clause),
      optional($.implements_clause),
      optional($.incorporates_clause),
      $.class_body,
    ),

    interface_declaration: $ => seq(
      repeat($.annotation),
      optional($.visibility_modifier),
      'interface',
      $.type_name,
      optional($.type_parameters),
      optional($.extends_clause),
      $.class_body,
    ),

    mixin_declaration: $ => seq(
      repeat($.annotation),
      optional($.visibility_modifier),
      'mixin',
      $.type_name,
      optional($.type_parameters),
      optional($.into_clause),
      optional($.extends_clause),
      $.class_body,
    ),

    service_declaration: $ => seq(
      repeat($.annotation),
      optional($.visibility_modifier),
      'service',
      $.type_name,
      optional($.type_parameters),
      optional($.extends_clause),
      optional($.implements_clause),
      $.class_body,
    ),

    const_declaration: $ => seq(
      repeat($.annotation),
      optional($.visibility_modifier),
      'const',
      $.type_name,
      optional($.type_parameters),
      optional($.extends_clause),
      optional($.implements_clause),
      $.class_body,
    ),

    enum_declaration: $ => seq(
      repeat($.annotation),
      optional($.visibility_modifier),
      'enum',
      $.type_name,
      optional($.implements_clause),
      $.enum_body,
    ),

    class_body: $ => seq('{', repeat($._class_member), '}'),

    enum_body: $ => seq(
      '{',
      optional($.enum_values),
      optional(seq(';', repeat($._class_member))),
      '}',
    ),

    enum_values: $ => seq($.enum_value, repeat(seq(',', $.enum_value))),

    enum_value: $ => seq($.identifier, optional($.arguments)),

    _class_member: $ => choice(
      $.property_declaration,
      $.method_declaration,
      $.constructor_declaration,
      $.class_declaration,
      $.interface_declaration,
    ),

    property_declaration: $ => seq(
      repeat($.annotation),
      optional($.visibility_modifier),
      optional('static'),
      $.type_expression,
      $.identifier,
      optional(seq('=', $._expression)),
      ';',
    ),

    method_declaration: $ => seq(
      repeat($.annotation),
      optional($.visibility_modifier),
      optional('static'),
      optional('abstract'),
      $.type_expression,
      $.identifier,
      optional($.type_parameters),
      $.parameters,
      choice($.block, ';'),
    ),

    constructor_declaration: $ => seq(
      repeat($.annotation),
      optional($.visibility_modifier),
      choice('construct', 'finally'),
      $.parameters,
      $.block,
    ),

    extends_clause: $ => seq('extends', $.type_expression),
    implements_clause: $ => seq('implements', commaSep1($.type_expression)),
    incorporates_clause: $ => seq('incorporates', $.type_expression),
    into_clause: $ => seq('into', $.type_expression),

    type_parameters: $ => seq('<', commaSep1($.type_parameter), '>'),
    type_parameter: $ => seq($.identifier, optional(seq('extends', $.type_expression))),

    parameters: $ => seq('(', commaSep($.parameter), ')'),
    parameter: $ => seq(
      $.type_expression,
      $.identifier,
      optional(seq('=', $._expression)),
    ),

    type_expression: $ => choice(
      $.type_name,
      $.generic_type,
      $.nullable_type,
      $.function_type,
      $.tuple_type,
    ),

    generic_type: $ => seq($.type_name, $.type_arguments),
    type_arguments: $ => seq('<', commaSep1($.type_expression), '>'),
    nullable_type: $ => seq($.type_expression, '?'),
    function_type: $ => seq('function', $.type_expression, $.parameters),
    tuple_type: $ => seq('(', commaSep($.type_expression), ')'),

    block: $ => seq('{', repeat($._statement), '}'),

    _statement: $ => choice(
      $.block,
      $.variable_declaration,
      $.if_statement,
      $.for_statement,
      $.while_statement,
      $.do_statement,
      $.switch_statement,
      $.try_statement,
      $.return_statement,
      $.break_statement,
      $.continue_statement,
      $.assert_statement,
      $.expression_statement,
    ),

    variable_declaration: $ => seq(
      choice('val', 'var'),
      optional($.type_expression),
      $.identifier,
      optional(seq('=', $._expression)),
      ';',
    ),

    if_statement: $ => seq(
      'if',
      '(',
      $._expression,
      ')',
      $._statement,
      optional(seq('else', $._statement)),
    ),

    for_statement: $ => seq(
      'for',
      '(',
      choice(
        seq($.type_expression, $.identifier, ':', $._expression),  // foreach
        seq(optional($._expression), ';', optional($._expression), ';', optional($._expression)),
      ),
      ')',
      $._statement,
    ),

    while_statement: $ => seq('while', '(', $._expression, ')', $._statement),
    do_statement: $ => seq('do', $._statement, 'while', '(', $._expression, ')', ';'),

    switch_statement: $ => seq(
      'switch',
      '(',
      $._expression,
      ')',
      '{',
      repeat($.case_clause),
      '}',
    ),

    case_clause: $ => seq(
      choice(seq('case', $._expression), 'default'),
      ':',
      repeat($._statement),
    ),

    try_statement: $ => seq(
      'try',
      optional(seq('(', commaSep1($._expression), ')')),
      $.block,
      repeat($.catch_clause),
      optional(seq('finally', $.block)),
    ),

    catch_clause: $ => seq(
      'catch',
      '(',
      $.type_expression,
      $.identifier,
      ')',
      $.block,
    ),

    return_statement: $ => seq('return', optional(commaSep1($._expression)), ';'),
    break_statement: $ => seq('break', optional($.identifier), ';'),
    continue_statement: $ => seq('continue', optional($.identifier), ';'),
    assert_statement: $ => seq('assert', $._expression, optional(seq(',', $._expression)), ';'),
    expression_statement: $ => seq($._expression, ';'),

    _expression: $ => choice(
      $.assignment_expression,
      $.ternary_expression,
      $.binary_expression,
      $.unary_expression,
      $.postfix_expression,
      $.call_expression,
      $.member_expression,
      $.index_expression,
      $.new_expression,
      $.lambda_expression,
      $.parenthesized_expression,
      $.identifier,
      $._literal,
    ),

    assignment_expression: $ => prec.right(1, seq($._expression, choice('=', '+=', '-=', '*=', '/=', '%=', '&=', '|=', '^=', '<<=', '>>=', '>>>=', ':=', '?=', '?:=', '&&=', '||='), $._expression)),
    ternary_expression: $ => prec.right(2, seq($._expression, '?', $._expression, ':', $._expression)),

    binary_expression: $ => choice(
      prec.left(0, seq($._expression, '_', $._expression)),
      prec.right(2, seq($._expression, '?:', $._expression)),
      prec.left(3, seq($._expression, choice('||', '^^'), $._expression)),
      prec.left(4, seq($._expression, '&&', $._expression)),
      prec.left(5, seq($._expression, '|', $._expression)),
      prec.left(6, seq($._expression, '^', $._expression)),
      prec.left(7, seq($._expression, '&', $._expression)),
      prec.left(8, seq($._expression, choice('==', '!='), $._expression)),
      prec.left(9, seq($._expression, choice('<', '<=', '>', '>=', '<=>'), $._expression)),
      prec.left(10, seq($._expression, choice('..', '>..', '..<', '>..<'), $._expression)),
      prec.left(11, seq($._expression, choice('<<', '>>', '>>>'), $._expression)),
      prec.left(12, seq($._expression, choice('+', '-'), $._expression)),
      prec.left(13, seq($._expression, choice('*', '/', '%', '/%'), $._expression)),
      prec.left(15, seq($._expression, choice('->', '<-', '^('), $._expression))
    ),

    unary_expression: $ => prec.right(16, seq(choice('!', '~', '-', '+', '++', '--'), $._expression)),
    postfix_expression: $ => prec.left(16, seq($._expression, choice('++', '--', '!'))),

    call_expression: $ => prec.left(17, seq($._expression, optional($.type_arguments), $.arguments)),
    member_expression: $ => prec.left(17, seq($._expression, choice('.', '?.'), $.identifier)),
    index_expression: $ => prec.left(17, seq($._expression, '[', commaSep1($._expression), ']')),

    new_expression: $ => seq('new', $.type_expression, $.arguments),
    lambda_expression: $ => seq(
      choice($.identifier, $.parameters),
      '->',
      choice($._expression, $.block),
    ),
    parenthesized_expression: $ => seq('(', $._expression, ')'),

    arguments: $ => seq('(', commaSep($._expression), ')'),

    _literal: $ => choice(
      $.integer_literal,
      $.float_literal,
      $.string_literal,
      $.template_string_literal,
      $.char_literal,
      $.boolean_literal,
      $.null_literal,
      $.list_literal,
      $.map_literal,
    ),

    integer_literal: $ => token(choice(
      /[0-9][0-9_]*/,
      /0[xX][0-9a-fA-F][0-9a-fA-F_]*/,
      /0[bB][01][01_]*/,
    )),

    float_literal: $ => /[0-9][0-9_]*\.[0-9][0-9_]*([eE][+-]?[0-9]+)?/,

    string_literal: $ => /"([^"\\]|\\.)*"/,
    template_string_literal: $ => /\$"([^"\\]|\\.)*"/,
    char_literal: $ => /'([^'\\]|\\.)'/,

    boolean_literal: $ => choice('True', 'False'),
    null_literal: $ => 'Null',

    list_literal: $ => seq('[', commaSep($._expression), ']'),
    map_literal: $ => seq('[', commaSep($.map_entry), ']'),
    map_entry: $ => seq($._expression, '=', $._expression),

    identifier: $ => /[a-zA-Z_][a-zA-Z0-9_]*/,
    type_name: $ => /[A-Z][a-zA-Z0-9_]*/,
    qualified_name: $ => sep1($.identifier, '.'),

    annotation: $ => seq('@', $.identifier, optional($.arguments)),

    visibility_modifier: $ => choice('public', 'protected', 'private'),

    comment: $ => choice(
      $.line_comment,
      $.block_comment,
      $.doc_comment,
    ),

    line_comment: $ => token(seq('//', /.*/)),
    block_comment: $ => token(seq('/*', /[^*]*\*+([^/*][^*]*\*+)*/, '/')),
    doc_comment: $ => token(seq('/**', /[^*]*\*+([^/*][^*]*\*+)*/, '/')),
  },
});

function sep1(rule, separator) {
  return seq(rule, repeat(seq(separator, rule)));
}

function commaSep(rule) {
  return optional(commaSep1(rule));
}

function commaSep1(rule) {
  return seq(rule, repeat(seq(',', rule)));
}
