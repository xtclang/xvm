package org.xvm.compiler2.syntax;

/**
 * Enumeration of all syntax node kinds in the XVM language.
 * Used by green nodes to identify their type without instanceof checks.
 */
public enum SyntaxKind {
    // -------------------------------------------------------------------------
    // Tokens (terminals)
    // -------------------------------------------------------------------------

    // Identifiers and literals
    IDENTIFIER,
    INT_LITERAL,
    FP_LITERAL,
    CHAR_LITERAL,
    STRING_LITERAL,
    BINARY_LITERAL,
    TEMPLATE_LITERAL,

    // Operators - Arithmetic
    PLUS,           // +
    MINUS,          // -
    STAR,           // *
    SLASH,          // /
    PERCENT,        // %

    // Operators - Comparison
    EQ,             // ==
    NEQ,            // !=
    LT,             // <
    GT,             // >
    LTEQ,           // <=
    GTEQ,           // >=
    SPACESHIP,      // <=>

    // Operators - Logical
    AND,            // &&
    OR,             // ||
    NOT,            // !

    // Operators - Bitwise
    BIT_AND,        // &
    BIT_OR,         // |
    BIT_XOR,        // ^
    BIT_NOT,        // ~
    SHL,            // <<
    SHR,            // >>
    USHR,           // >>>

    // Operators - Assignment
    ASSIGN,         // =
    ADD_ASSIGN,     // +=
    SUB_ASSIGN,     // -=
    MUL_ASSIGN,     // *=
    DIV_ASSIGN,     // /=
    MOD_ASSIGN,     // %=
    AND_ASSIGN,     // &=
    OR_ASSIGN,      // |=
    XOR_ASSIGN,     // ^=
    SHL_ASSIGN,     // <<=
    SHR_ASSIGN,     // >>=
    USHR_ASSIGN,    // >>>=
    COND_ASSIGN,    // ?=
    ELVIS_ASSIGN,   // ?:=

    // Operators - Other
    COND,           // ?
    COLON,          // :
    ELVIS,          // ?:
    DOT,            // .
    DOTDOT,         // ..
    ARROW,          // ->

    // Delimiters
    LPAREN,         // (
    RPAREN,         // )
    LBRACE,         // {
    RBRACE,         // }
    LBRACKET,       // [
    RBRACKET,       // ]
    COMMA,          // ,
    SEMICOLON,      // ;

    // Keywords - declarations
    KW_MODULE,
    KW_PACKAGE,
    KW_CLASS,
    KW_INTERFACE,
    KW_MIXIN,
    KW_SERVICE,
    KW_CONST,
    KW_ENUM,

    // Keywords - modifiers
    KW_PUBLIC,
    KW_PROTECTED,
    KW_PRIVATE,
    KW_STATIC,
    KW_ABSTRACT,
    KW_FINAL,

    // Keywords - statements
    KW_IF,
    KW_ELSE,
    KW_SWITCH,
    KW_CASE,
    KW_DEFAULT,
    KW_WHILE,
    KW_DO,
    KW_FOR,
    KW_FOREACH,
    KW_RETURN,
    KW_BREAK,
    KW_CONTINUE,
    KW_THROW,
    KW_TRY,
    KW_CATCH,
    KW_FINALLY,
    KW_USING,
    KW_ASSERT,

    // Keywords - expressions
    KW_NEW,
    KW_THIS,
    KW_SUPER,
    KW_TRUE,
    KW_FALSE,
    KW_NULL,
    KW_IS,
    KW_AS,

    // Keywords - types
    KW_VOID,
    KW_VAR,
    KW_VAL,
    KW_IMMUTABLE,
    KW_CONDITIONAL,

    // Special
    EOF,
    TRIVIA_WHITESPACE,
    TRIVIA_COMMENT,
    TRIVIA_NEWLINE,
    BAD_TOKEN,

    // -------------------------------------------------------------------------
    // Expressions
    // -------------------------------------------------------------------------

    LITERAL_EXPRESSION,
    NAME_EXPRESSION,
    QUALIFIED_NAME_EXPRESSION,
    PARENTHESIZED_EXPRESSION,
    BINARY_EXPRESSION,
    UNARY_EXPRESSION,
    CONDITIONAL_EXPRESSION,
    ASSIGNMENT_EXPRESSION,
    INVOKE_EXPRESSION,
    NEW_EXPRESSION,
    ARRAY_ACCESS_EXPRESSION,
    MEMBER_ACCESS_EXPRESSION,
    CAST_EXPRESSION,
    LAMBDA_EXPRESSION,
    TUPLE_EXPRESSION,
    LIST_EXPRESSION,
    MAP_EXPRESSION,
    TEMPLATE_EXPRESSION,

    // -------------------------------------------------------------------------
    // Statements
    // -------------------------------------------------------------------------

    BLOCK_STATEMENT,
    EXPRESSION_STATEMENT,
    VARIABLE_STATEMENT,
    IF_STATEMENT,
    SWITCH_STATEMENT,
    WHILE_STATEMENT,
    DO_STATEMENT,
    FOR_STATEMENT,
    FOREACH_STATEMENT,
    RETURN_STATEMENT,
    BREAK_STATEMENT,
    CONTINUE_STATEMENT,
    THROW_STATEMENT,
    TRY_STATEMENT,
    ASSERT_STATEMENT,

    // -------------------------------------------------------------------------
    // Declarations
    // -------------------------------------------------------------------------

    MODULE_DECLARATION,
    PACKAGE_DECLARATION,
    CLASS_DECLARATION,
    INTERFACE_DECLARATION,
    MIXIN_DECLARATION,
    SERVICE_DECLARATION,
    CONST_DECLARATION,
    ENUM_DECLARATION,
    METHOD_DECLARATION,
    PROPERTY_DECLARATION,
    PARAMETER_DECLARATION,
    TYPE_PARAMETER_DECLARATION,

    // -------------------------------------------------------------------------
    // Types
    // -------------------------------------------------------------------------

    NAMED_TYPE,
    PARAMETERIZED_TYPE,
    ARRAY_TYPE,
    NULLABLE_TYPE,
    IMMUTABLE_TYPE,
    FUNCTION_TYPE,
    TUPLE_TYPE,

    // -------------------------------------------------------------------------
    // Other
    // -------------------------------------------------------------------------

    COMPILATION_UNIT,
    IMPORT_STATEMENT,
    ANNOTATION,
    ARGUMENT,
    CATCH_CLAUSE,
    FINALLY_CLAUSE,
    CASE_CLAUSE,
    ;

    /**
     * @return true if this kind represents a token (terminal)
     */
    public boolean isToken() {
        return ordinal() < LITERAL_EXPRESSION.ordinal();
    }

    /**
     * @return true if this kind represents an expression
     */
    public boolean isExpression() {
        return ordinal() >= LITERAL_EXPRESSION.ordinal()
            && ordinal() <= TEMPLATE_EXPRESSION.ordinal();
    }

    /**
     * @return true if this kind represents a statement
     */
    public boolean isStatement() {
        return ordinal() >= BLOCK_STATEMENT.ordinal()
            && ordinal() <= ASSERT_STATEMENT.ordinal();
    }

    /**
     * @return true if this kind represents a declaration
     */
    public boolean isDeclaration() {
        return ordinal() >= MODULE_DECLARATION.ordinal()
            && ordinal() <= TYPE_PARAMETER_DECLARATION.ordinal();
    }

    /**
     * @return true if this kind represents a type expression
     */
    public boolean isType() {
        return ordinal() >= NAMED_TYPE.ordinal()
            && ordinal() <= TUPLE_TYPE.ordinal();
    }

    /**
     * @return true if this kind represents trivia (whitespace, comments)
     */
    public boolean isTrivia() {
        return this == TRIVIA_WHITESPACE || this == TRIVIA_COMMENT || this == TRIVIA_NEWLINE;
    }
}
