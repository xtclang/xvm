package org.xtclang.tooling.model

/*
 * =============================================================================
 * DSL FRAMEWORK TYPES - TO BE IMPLEMENTED
 * =============================================================================
 *
 * This file uses a Kotlin DSL for defining language models. The following types
 * and functions must be implemented in the `org.xtclang.tooling.model` package
 * to make this DSL work.
 *
 * -----------------------------------------------------------------------------
 * ENTRY POINT FUNCTION
 * -----------------------------------------------------------------------------
 *
 * fun language(
 *     name: String,
 *     fileExtensions: List<String>,
 *     scopeName: String,
 *     block: LanguageModelBuilder.() -> Unit
 * ): LanguageModel
 *
 * Creates a new language model with the given metadata and DSL configuration.
 *
 * -----------------------------------------------------------------------------
 * BUILDER CLASSES
 * -----------------------------------------------------------------------------
 *
 * class LanguageModelBuilder {
 *     // Collected data
 *     val scopes: MutableList<ScopeDefinition>
 *     val keywords: MutableList<String>
 *     val contextKeywords: MutableList<String>
 *     val tokens: MutableList<TokenRule>
 *     val operators: MutableList<OperatorDefinition>
 *     val punctuation: MutableList<PunctuationDefinition>
 *     val concepts: MutableList<ConceptDefinition>
 *
 *     // DSL functions (see below for signatures)
 *     fun scope(name: String, block: ScopeBuilder.() -> Unit)
 *     fun keywords(vararg words: String)
 *     fun contextKeywords(vararg words: String)
 *     fun token(name: String, pattern: String, textMateScope: String)
 *     fun operator(symbol: String, precedence: Int, assoc: Associativity, category: OperatorCategory)
 *     fun punctuation(symbol: String, name: String)
 *     fun concept(name: String, block: ConceptBuilder.() -> Unit)
 *     fun abstractConcept(name: String, block: ConceptBuilder.() -> Unit)
 * }
 *
 * class ScopeBuilder {
 *     var textMate: String      // TextMate scope name (e.g., "keyword.control.xtc")
 *     var intellij: String      // IntelliJ TextAttributesKey name (e.g., "KEYWORD")
 *     var eclipse: String       // Eclipse color key (e.g., "keyword")
 *     var semanticToken: String // LSP semantic token type (e.g., "keyword")
 * }
 *
 * class ConceptBuilder {
 *     fun extends(parentConcept: String)
 *     fun property(name: String, type: String, default: String? = null, optional: Boolean = false)
 *     fun child(name: String, type: String, cardinality: Cardinality = Cardinality.REQUIRED)
 *     fun children(name: String, type: String)
 *     fun reference(name: String, type: String, optional: Boolean = false)
 *     fun syntax(pattern: String)  // Regex for grammar generation
 * }
 *
 * -----------------------------------------------------------------------------
 * ENUMS
 * -----------------------------------------------------------------------------
 *
 * enum class Associativity { LEFT, RIGHT, NONE }
 *
 * enum class OperatorCategory {
 *     ASSIGNMENT,      // =, +=, -=, etc.
 *     ARITHMETIC,      // +, -, *, /, %
 *     COMPARISON,      // ==, !=, <, >, <=, >=, <=>
 *     LOGICAL,         // &&, ||, !, ^^
 *     BITWISE,         // &, |, ^, ~, <<, >>, >>>
 *     MEMBER_ACCESS,   // ., ?.
 *     OTHER            // ->, ?:, .., etc.
 * }
 *
 * enum class Cardinality { REQUIRED, OPTIONAL, ZERO_OR_MORE }
 *
 * -----------------------------------------------------------------------------
 * DATA CLASSES (output of builders)
 * -----------------------------------------------------------------------------
 *
 * data class LanguageModel(
 *     val name: String,
 *     val fileExtensions: List<String>,
 *     val scopeName: String,
 *     val scopes: List<ScopeDefinition>,
 *     val keywords: List<String>,
 *     val contextKeywords: List<String>,
 *     val tokens: List<TokenRule>,
 *     val operators: List<OperatorDefinition>,
 *     val punctuation: List<PunctuationDefinition>,
 *     val concepts: List<ConceptDefinition>
 * )
 *
 * data class ScopeDefinition(
 *     val name: String,
 *     val textMate: String,
 *     val intellij: String,
 *     val eclipse: String,
 *     val semanticToken: String?
 * )
 *
 * data class TokenRule(
 *     val name: String,
 *     val pattern: String,        // Regex pattern
 *     val textMateScope: String
 * )
 *
 * data class OperatorDefinition(
 *     val symbol: String,
 *     val precedence: Int,        // 1 = lowest (binds last), 15 = highest
 *     val associativity: Associativity,
 *     val category: OperatorCategory
 * )
 *
 * data class PunctuationDefinition(
 *     val symbol: String,
 *     val name: String            // Token name (e.g., "COLON", "L_PAREN")
 * )
 *
 * data class ConceptDefinition(
 *     val name: String,
 *     val isAbstract: Boolean,
 *     val parentConcept: String?,
 *     val properties: List<PropertyDefinition>,
 *     val children: List<ChildDefinition>,
 *     val references: List<ReferenceDefinition>,
 *     val syntaxPattern: String?
 * )
 *
 * data class PropertyDefinition(
 *     val name: String,
 *     val type: String,
 *     val default: String?,
 *     val optional: Boolean
 * )
 *
 * data class ChildDefinition(
 *     val name: String,
 *     val type: String,
 *     val cardinality: Cardinality
 * )
 *
 * data class ReferenceDefinition(
 *     val name: String,
 *     val type: String,
 *     val optional: Boolean
 * )
 *
 * =============================================================================
 * END OF DSL FRAMEWORK TYPES
 * =============================================================================
 */

/**
 * Complete XTC (Ecstasy) Language Model Definition
 *
 * This model is derived from analysis of the XVM repository:
 * - javatools/src/main/java/org/xvm/compiler/Token.java (Token IDs and keywords)
 * - javatools/src/main/java/org/xvm/compiler/Lexer.java (Lexical analysis)
 * - javatools/src/main/java/org/xvm/compiler/Parser.java (Recursive descent parser)
 * - javatools/src/main/java/org/xvm/compiler/ast/ (all AST node classes)
 * - lib_ecstasy/src/main/x/ (XTC source files)
 *
 * This serves as a single source of truth for generating:
 * - TextMate grammars for VS Code
 * - JFlex lexer specifications for IntelliJ
 * - Tree-sitter grammars
 * - LSP semantic token mappings
 * - Eclipse syntax coloring
 */

// language() - DSL entry point function (see LanguageModelBuilder above)
// Returns: LanguageModel data class containing all parsed definitions
val xtcLanguage = language(
    name = "Ecstasy",
    fileExtensions = listOf("x", "xtc"),
    scopeName = "source.xtc"
) {
    // =========================================================================
    // Scopes - Map language elements to editor-specific styling
    // =========================================================================
    //
    // scope() - Defines how a semantic element maps to different editor color schemes
    // Parameters:
    //   name: String - Internal scope identifier
    //   block: ScopeBuilder.() -> Unit - Configuration lambda
    //
    // ScopeBuilder properties:
    //   textMate: String - TextMate scope name (https://macromates.com/manual/en/language_grammars)
    //   intellij: String - IntelliJ DefaultLanguageHighlighterColors key
    //   eclipse: String - Eclipse IPreferenceStore color key
    //   semanticToken: String - LSP SemanticTokenTypes (https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#semanticTokenTypes)
    //   vim: String - Vim highlight group (see :help group-name)
    //   emacs: String - Emacs font-lock face (see M-x describe-face)
    //   treeSitter: String - Tree-sitter capture name (see https://tree-sitter.github.io/tree-sitter/syntax-highlighting)

    scope("keyword") {
        textMate = "keyword.control.xtc"
        intellij = "KEYWORD"
        eclipse = "keyword"
        semanticToken = "keyword"
        vim = "Keyword"
        emacs = "font-lock-keyword-face"
        treeSitter = "@keyword"
    }

    scope("keyword.declaration") {
        textMate = "keyword.declaration.xtc"
        intellij = "KEYWORD"
        eclipse = "keyword"
        semanticToken = "keyword"
        vim = "Keyword"
        emacs = "font-lock-keyword-face"
        treeSitter = "@keyword"
    }

    scope("keyword.modifier") {
        textMate = "storage.modifier.xtc"
        intellij = "KEYWORD"
        eclipse = "keyword"
        semanticToken = "modifier"
        vim = "StorageClass"
        emacs = "font-lock-builtin-face"
        treeSitter = "@keyword.modifier"
    }

    scope("type") {
        textMate = "entity.name.type.xtc"
        intellij = "CLASS_NAME"
        eclipse = "class"
        semanticToken = "type"
        vim = "Type"
        emacs = "font-lock-type-face"
        treeSitter = "@type"
    }

    scope("type.builtin") {
        textMate = "support.type.builtin.xtc"
        intellij = "DEFAULT_KEYWORD"
        eclipse = "keyword"
        semanticToken = "type"
        vim = "Type"
        emacs = "font-lock-type-face"
        treeSitter = "@type.builtin"
    }

    scope("function") {
        textMate = "entity.name.function.xtc"
        intellij = "FUNCTION_DECLARATION"
        eclipse = "methodDeclaration"
        semanticToken = "function"
        vim = "Function"
        emacs = "font-lock-function-name-face"
        treeSitter = "@function"
    }

    scope("variable") {
        textMate = "variable.other.xtc"
        intellij = "LOCAL_VARIABLE"
        eclipse = "localVariable"
        semanticToken = "variable"
        vim = "Identifier"
        emacs = "font-lock-variable-name-face"
        treeSitter = "@variable"
    }

    scope("parameter") {
        textMate = "variable.parameter.xtc"
        intellij = "PARAMETER"
        eclipse = "parameterVariable"
        semanticToken = "parameter"
        vim = "Identifier"
        emacs = "font-lock-variable-name-face"
        treeSitter = "@variable.parameter"
    }

    scope("property") {
        textMate = "variable.other.property.xtc"
        intellij = "INSTANCE_FIELD"
        eclipse = "field"
        semanticToken = "property"
        vim = "Identifier"
        emacs = "font-lock-variable-name-face"
        treeSitter = "@variable.member"
    }

    scope("string") {
        textMate = "string.quoted.double.xtc"
        intellij = "STRING"
        eclipse = "string"
        semanticToken = "string"
        vim = "String"
        emacs = "font-lock-string-face"
        treeSitter = "@string"
    }

    scope("string.template") {
        textMate = "string.interpolated.xtc"
        intellij = "STRING"
        eclipse = "string"
        semanticToken = "string"
        vim = "String"
        emacs = "font-lock-string-face"
        treeSitter = "@string"
    }

    scope("number") {
        textMate = "constant.numeric.xtc"
        intellij = "NUMBER"                     // DefaultLanguageHighlighterColors.NUMBER
        eclipse = "number"
        semanticToken = "number"                // LSP SemanticTokenTypes.Number
        vim = "Number"
        emacs = "font-lock-constant-face"
        treeSitter = "@number"
    }

    scope("comment") {
        textMate = "comment.line.double-slash.xtc"
        intellij = "LINE_COMMENT"               // DefaultLanguageHighlighterColors.LINE_COMMENT
        eclipse = "singleLineComment"
        semanticToken = "comment"               // LSP SemanticTokenTypes.Comment
        vim = "Comment"
        emacs = "font-lock-comment-face"
        treeSitter = "@comment"
    }

    scope("comment.block") {
        textMate = "comment.block.xtc"
        intellij = "BLOCK_COMMENT"              // DefaultLanguageHighlighterColors.BLOCK_COMMENT
        eclipse = "multiLineComment"
        semanticToken = "comment"
        vim = "Comment"
        emacs = "font-lock-comment-face"
        treeSitter = "@comment"
    }

    scope("comment.doc") {
        textMate = "comment.block.documentation.xtc"
        intellij = "DOC_COMMENT"                // DefaultLanguageHighlighterColors.DOC_COMMENT
        eclipse = "javadoc"
        semanticToken = "comment"
        vim = "SpecialComment"
        emacs = "font-lock-doc-face"
        treeSitter = "@comment.documentation"
    }

    scope("annotation") {
        textMate = "storage.type.annotation.xtc"
        intellij = "ANNOTATION_NAME"            // DefaultLanguageHighlighterColors.METADATA
        eclipse = "annotation"
        semanticToken = "decorator"             // LSP SemanticTokenTypes.Decorator
        vim = "PreProc"
        emacs = "font-lock-preprocessor-face"
        treeSitter = "@attribute"
    }

    scope("operator") {
        textMate = "keyword.operator.xtc"
        intellij = "OPERATION_SIGN"             // DefaultLanguageHighlighterColors.OPERATION_SIGN
        eclipse = "operator"
        semanticToken = "operator"              // LSP SemanticTokenTypes.Operator
        vim = "Operator"
        emacs = "font-lock-builtin-face"
        treeSitter = "@operator"
    }

    scope("punctuation") {
        textMate = "punctuation.xtc"
        intellij = "COMMA"                      // DefaultLanguageHighlighterColors.COMMA
        eclipse = "operator"
        vim = "Delimiter"
        emacs = "font-lock-punctuation-face"
        treeSitter = "@punctuation.delimiter"
    }

    scope("constant") {
        textMate = "constant.language.xtc"
        intellij = "KEYWORD"
        eclipse = "keyword"
        semanticToken = "enumMember"            // LSP SemanticTokenTypes.EnumMember
        vim = "Constant"
        emacs = "font-lock-constant-face"
        treeSitter = "@constant.builtin"
    }

    // =========================================================================
    // Keywords - Derived from Token.java Id enum
    // =========================================================================
    //
    // keywords(category, ...) - Registers reserved keywords with semantic category
    // contextKeywords(category, ...) - Registers context-sensitive keywords with category
    // Source: Token.java

    // Control flow keywords
    keywords(KeywordCategory.CONTROL,
        "if", "else", "switch", "case", "default",
        "for", "while", "do",
        "break", "continue", "return"
    )

    // Exception handling keywords
    keywords(KeywordCategory.EXCEPTION,
        "try", "catch", "finally", "throw", "using",
        "assert", "assert:rnd", "assert:arg", "assert:bounds", "assert:TODO",
        "assert:once", "assert:test", "assert:debug"
    )

    // Declaration keywords (reserved)
    keywords(KeywordCategory.DECLARATION,
        "construct", "function", "typedef", "import"
    )

    // Modifier keywords (reserved)
    keywords(KeywordCategory.MODIFIER,
        "public", "protected", "private", "static", "immutable", "conditional"
    )

    // Other reserved keywords
    keywords(KeywordCategory.OTHER,
        "is", "as", "new", "void", "TODO", "annotation"
    )

    // Declaration keywords (context-sensitive)
    contextKeywords(KeywordCategory.DECLARATION,
        "module", "package", "class", "interface", "mixin", "service",
        "const", "enum", "struct", "val", "var"
    )

    // Type relation keywords
    contextKeywords(KeywordCategory.TYPE_RELATION,
        "extends", "implements", "delegates", "incorporates", "into"
    )

    // Modifier keywords (context-sensitive)
    contextKeywords(KeywordCategory.MODIFIER,
        "allow", "avoid", "prefer", "desired", "required", "optional",
        "embedded", "inject"
    )

    // Special reference keywords
    contextKeywords(KeywordCategory.OTHER,
        "this", "super", "outer",
        "this:class", "this:module", "this:private", "this:protected",
        "this:public", "this:service", "this:struct", "this:target"
    )

    // =========================================================================
    // Built-in Types
    // =========================================================================

    builtinTypes(
        // Primitive types
        "Bit", "Boolean", "Byte", "Char",

        // Numeric types
        "Int", "Int8", "Int16", "Int32", "Int64", "Int128", "IntN",
        "UInt", "UInt8", "UInt16", "UInt32", "UInt64", "UInt128", "UIntN",
        "Dec", "Dec32", "Dec64", "Dec128", "DecN",
        "Float", "Float8e4", "Float8e5", "Float16", "Float32", "Float64", "Float128", "FloatN",
        "BFloat16",

        // Text types
        "String", "Char",

        // Core types
        "Object", "Enum", "Exception", "Const", "Service", "Module", "Package",

        // Collection types
        "Array", "List", "Set", "Map", "Range", "Interval", "Tuple",

        // Function types
        "Function", "Method", "Property",

        // Reflection types
        "Type", "Class",

        // Capability types
        "Nullable", "Orderable", "Hashable", "Stringable",
        "Iterator", "Iterable", "Collection", "Sequence",

        // Special
        "Void", "Null", "True", "False"
    )

    // =========================================================================
    // Token Rules - Regular expressions for lexical analysis
    // =========================================================================
    //
    // token() - Defines a lexical token rule
    // Parameters:
    //   name: String - Token type name (used in generated lexers)
    //   pattern: String - Regex pattern to match
    //   textMateScope: String - TextMate scope for syntax highlighting
    // Source: Token.java and Lexer.java

    // Built-in types (common Ecstasy types from lib_ecstasy)
    token("BUILTIN_TYPE",
        "\\b(Bit|Boolean|Byte|Char|Dec|Dec32|Dec64|Dec128|DecN|" +
        "Float|Float8e4|Float8e5|Float16|Float32|Float64|Float128|FloatN|BFloat16|" +
        "Int|Int8|Int16|Int32|Int64|Int128|IntN|" +
        "UInt|UInt8|UInt16|UInt32|UInt64|UInt128|UIntN|" +
        "Nibble|String|Object|Enum|Exception|Const|Service|Module|Package|" +
        "Array|List|Set|Map|Range|Interval|Tuple|Function|Method|Property|Type|Class|" +
        "Nullable|Orderable|Hashable|Stringable|Iterator|Iterable|Collection|Sequence|" +
        "Date|Time|TimeOfDay|TimeZone|Duration|Version|Path)\\b",
        "support.type.builtin.xtc"
    )

    // Boolean literals
    token("BOOLEAN", "\\b(True|False)\\b", "constant.language.boolean.xtc")

    // Null literal
    token("NULL", "\\bNull\\b", "constant.language.null.xtc")

    // Annotations (@ followed by identifier)
    token("ANNOTATION", "@[a-zA-Z_][a-zA-Z0-9_]*", "storage.type.annotation.xtc")

    // Doc comments (/** ... */)
    token("DOC_COMMENT", "/\\*\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", "comment.block.documentation.xtc")

    // Block comments (/* ... */)
    token("BLOCK_COMMENT", "/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", "comment.block.xtc")

    // Line comments (// ...)
    token("LINE_COMMENT", "//.*$", "comment.line.double-slash.xtc")

    // Template/interpolated strings ($"...")
    token("TEMPLATE_STRING", "\\$\"(?:[^\"\\\\]|\\\\.)*\"", "string.interpolated.xtc")

    // Binary file inclusion (#"...")
    token("BINARY_FILE", "#\"(?:[^\"\\\\]|\\\\.)*\"", "string.quoted.binary.xtc")

    // Regular strings ("...")
    token("STRING", "\"(?:[^\"\\\\]|\\\\.)*\"", "string.quoted.double.xtc")

    // Multi-line strings (|"..." and raw strings)
    token("MULTILINE_STRING", "\\|\"[\\s\\S]*?\"", "string.quoted.multi.xtc")

    // Character literals ('x')
    token("CHAR", "'(?:[^'\\\\]|\\\\.)'", "string.quoted.single.xtc")

    // Hex numbers (0x...)
    token("HEX_NUMBER", "0[xX][0-9a-fA-F][0-9a-fA-F_]*", "constant.numeric.hex.xtc")

    // Binary numbers (0b...)
    token("BINARY_NUMBER", "0[bB][01][01_]*", "constant.numeric.binary.xtc")

    // Version literals (v:1.2.3)
    token("VERSION", "v:[0-9][0-9.a-zA-Z_-]*", "constant.other.version.xtc")

    // Date literal (YYYY-MM-DD)
    token("DATE", "\\d{4}-\\d{2}-\\d{2}", "constant.other.date.xtc")

    // Time literal (HH:MM:SS.sss)
    token("TIME", "\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?", "constant.other.time.xtc")

    // Duration literal (P1DT2H3M or PT1H30M)
    token("DURATION", "P(?:\\d+[YMWD])*(?:T(?:\\d+[HMS])*)?", "constant.other.duration.xtc")

    // Float/decimal numbers
    token("FLOAT_NUMBER",
        "[0-9][0-9_]*\\.[0-9][0-9_]*(?:[eE][+-]?[0-9]+)?|[0-9][0-9_]*[eE][+-]?[0-9]+",
        "constant.numeric.float.xtc"
    )

    // Integer numbers (with optional underscore separators)
    token("INT_NUMBER", "[0-9][0-9_]*", "constant.numeric.integer.xtc")

    // Identifiers
    token("IDENTIFIER", "[a-zA-Z_][a-zA-Z0-9_]*", "variable.other.xtc")

    // =========================================================================
    // Operators - Derived from Token.java Id enum
    // =========================================================================
    //
    // operator() - Defines an operator with precedence information
    // Parameters:
    //   symbol: String - The operator symbol
    //   precedence: Int - Binding strength (1=lowest/binds last, 15=highest/binds first)
    //   assoc: Associativity - LEFT, RIGHT, or NONE (see Associativity enum)
    //   category: OperatorCategory - Classification for styling (see OperatorCategory enum)
    //
    // Associativity enum: { LEFT, RIGHT, NONE }
    //   LEFT: a op b op c = (a op b) op c
    //   RIGHT: a op b op c = a op (b op c)
    //   NONE: Cannot chain
    //
    // OperatorCategory enum: { ASSIGNMENT, ARITHMETIC, COMPARISON, LOGICAL, BITWISE, MEMBER_ACCESS, OTHER }

    // Assignment operators (precedence 1, lowest - binds last)
    operator("=", 1, Associativity.RIGHT, OperatorCategory.ASSIGNMENT)
    operator("+=", 1, Associativity.RIGHT, OperatorCategory.ASSIGNMENT)
    operator("-=", 1, Associativity.RIGHT, OperatorCategory.ASSIGNMENT)
    operator("*=", 1, Associativity.RIGHT, OperatorCategory.ASSIGNMENT)
    operator("/=", 1, Associativity.RIGHT, OperatorCategory.ASSIGNMENT)
    operator("%=", 1, Associativity.RIGHT, OperatorCategory.ASSIGNMENT)
    operator("&=", 1, Associativity.RIGHT, OperatorCategory.ASSIGNMENT)
    operator("|=", 1, Associativity.RIGHT, OperatorCategory.ASSIGNMENT)
    operator("^=", 1, Associativity.RIGHT, OperatorCategory.ASSIGNMENT)
    operator("<<=", 1, Associativity.RIGHT, OperatorCategory.ASSIGNMENT)
    operator(">>=", 1, Associativity.RIGHT, OperatorCategory.ASSIGNMENT)
    operator(">>>=", 1, Associativity.RIGHT, OperatorCategory.ASSIGNMENT)
    operator(":=", 1, Associativity.RIGHT, OperatorCategory.ASSIGNMENT)    // Conditional assign
    operator("?=", 1, Associativity.RIGHT, OperatorCategory.ASSIGNMENT)    // Not-null assign
    operator("?:=", 1, Associativity.RIGHT, OperatorCategory.ASSIGNMENT)   // Elvis assign
    operator("&&=", 1, Associativity.RIGHT, OperatorCategory.ASSIGNMENT)   // Logical AND assign
    operator("||=", 1, Associativity.RIGHT, OperatorCategory.ASSIGNMENT)   // Logical OR assign

    // Ternary / Elvis (precedence 2)
    operator("?:", 2, Associativity.RIGHT, OperatorCategory.OTHER)         // Elvis operator

    // Logical OR (precedence 3)
    operator("||", 3, Associativity.LEFT, OperatorCategory.LOGICAL)

    // Logical XOR (precedence 3)
    operator("^^", 3, Associativity.LEFT, OperatorCategory.LOGICAL)

    // Logical AND (precedence 4)
    operator("&&", 4, Associativity.LEFT, OperatorCategory.LOGICAL)

    // Bitwise OR (precedence 5)
    operator("|", 5, Associativity.LEFT, OperatorCategory.BITWISE)

    // Bitwise XOR (precedence 6)
    operator("^", 6, Associativity.LEFT, OperatorCategory.BITWISE)

    // Bitwise AND (precedence 7)
    operator("&", 7, Associativity.LEFT, OperatorCategory.BITWISE)

    // Equality (precedence 8)
    operator("==", 8, Associativity.LEFT, OperatorCategory.COMPARISON)
    operator("!=", 8, Associativity.LEFT, OperatorCategory.COMPARISON)

    // Relational / comparison (precedence 9)
    operator("<", 9, Associativity.LEFT, OperatorCategory.COMPARISON)
    operator("<=", 9, Associativity.LEFT, OperatorCategory.COMPARISON)
    operator(">", 9, Associativity.LEFT, OperatorCategory.COMPARISON)
    operator(">=", 9, Associativity.LEFT, OperatorCategory.COMPARISON)
    operator("<=>", 9, Associativity.LEFT, OperatorCategory.COMPARISON)    // Spaceship/comparison

    // Range operators (precedence 10)
    operator("..", 10, Associativity.LEFT, OperatorCategory.OTHER)         // Inclusive range
    operator(">..", 10, Associativity.LEFT, OperatorCategory.OTHER)        // Exclusive start
    operator("..<", 10, Associativity.LEFT, OperatorCategory.OTHER)        // Exclusive end
    operator(">..<", 10, Associativity.LEFT, OperatorCategory.OTHER)       // Exclusive range

    // Shift (precedence 11)
    operator("<<", 11, Associativity.LEFT, OperatorCategory.BITWISE)
    operator(">>", 11, Associativity.LEFT, OperatorCategory.BITWISE)
    operator(">>>", 11, Associativity.LEFT, OperatorCategory.BITWISE)      // Unsigned shift

    // Additive (precedence 12)
    operator("+", 12, Associativity.LEFT, OperatorCategory.ARITHMETIC)
    operator("-", 12, Associativity.LEFT, OperatorCategory.ARITHMETIC)

    // Multiplicative (precedence 13)
    operator("*", 13, Associativity.LEFT, OperatorCategory.ARITHMETIC)
    operator("/", 13, Associativity.LEFT, OperatorCategory.ARITHMETIC)
    operator("%", 13, Associativity.LEFT, OperatorCategory.ARITHMETIC)
    operator("/%", 13, Associativity.LEFT, OperatorCategory.ARITHMETIC)    // Divmod

    // Unary (precedence 14)
    operator("!", 14, Associativity.RIGHT, OperatorCategory.LOGICAL)       // Logical NOT
    operator("~", 14, Associativity.RIGHT, OperatorCategory.BITWISE)       // Bitwise NOT
    operator("++", 14, Associativity.RIGHT, OperatorCategory.ARITHMETIC)   // Pre/post increment
    operator("--", 14, Associativity.RIGHT, OperatorCategory.ARITHMETIC)   // Pre/post decrement

    // Member access / navigation (precedence 15, highest)
    operator(".", 15, Associativity.LEFT, OperatorCategory.MEMBER_ACCESS)
    operator("?.", 15, Associativity.LEFT, OperatorCategory.MEMBER_ACCESS) // Safe navigation

    // Special operators
    operator("->", 15, Associativity.RIGHT, OperatorCategory.OTHER)        // Lambda arrow
    operator("<-", 15, Associativity.RIGHT, OperatorCategory.OTHER)        // Assignment expression
    operator("_", 0, Associativity.NONE, OperatorCategory.OTHER)           // Wildcard/any

    // Async operator
    operator("^(", 15, Associativity.LEFT, OperatorCategory.OTHER)         // Async invocation

    // =========================================================================
    // Punctuation/Delimiters - From Token.java
    // =========================================================================
    //
    // punctuation() - Defines a punctuation/delimiter token
    // Parameters:
    //   symbol: String - The punctuation character(s)
    //   name: String - Token name for generated code (e.g., "COLON", "L_PAREN")

    punctuation(":", "COLON")
    punctuation(";", "SEMICOLON")
    punctuation(",", "COMMA")
    punctuation("(", "L_PAREN")
    punctuation(")", "R_PAREN")
    punctuation("{", "L_CURLY")
    punctuation("}", "R_CURLY")
    punctuation("[", "L_SQUARE")
    punctuation("]", "R_SQUARE")
    punctuation("@", "AT")
    punctuation("?", "COND")
    punctuation("./", "DIR_CUR")       // Current directory path
    punctuation("../", "DIR_PARENT")   // Parent directory path
    punctuation("$", "STR_FILE")       // String file inclusion
    punctuation("#", "BIN_FILE")       // Binary file inclusion

    // =========================================================================
    // AST Concepts - Derived from org.xvm.compiler.ast.* classes
    // =========================================================================
    //
    // concept() - Defines a concrete AST node type
    // abstractConcept() - Defines an abstract AST node type (cannot be instantiated)
    //
    // Parameters:
    //   name: String - The concept name (becomes class name in generated code)
    //   block: ConceptBuilder.() -> Unit - Configuration lambda
    //
    // ConceptBuilder methods:
    //   extends(parentConcept: String) - Inherit from another concept
    //   property(name, type, default?, optional?) - Scalar property
    //   child(name, type, cardinality?) - Single child node
    //   children(name, type) - List of child nodes (zero or more)
    //   reference(name, type, optional?) - Reference to another node
    //   syntax(pattern: String) - Regex pattern for grammar generation
    //
    // Cardinality enum: { REQUIRED, OPTIONAL, ZERO_OR_MORE }

    // ----- Source File Structure -----

    concept("SourceFile") {
        property("path", "String")
        child("module", "ModuleDeclaration", Cardinality.OPTIONAL)
        children("imports", "ImportStatement")
        children("types", "TypeDeclaration")
    }

    concept("ModuleDeclaration") {
        property("qualifiedName", "String")
        property("simpleName", "String")
        children("annotations", "Annotation")
        children("packages", "PackageDeclaration")
        children("types", "TypeDeclaration")
        syntax("\\b(module)\\s+([a-zA-Z_][a-zA-Z0-9_.]*)")
    }

    concept("PackageDeclaration") {
        property("name", "String")
        property("isImport", "Boolean", default = "false")
        property("importedModule", "String", optional = true)
        children("types", "TypeDeclaration")
        syntax("\\b(package)\\s+([a-zA-Z_][a-zA-Z0-9_]*)")
    }

    // From ImportStatement.java
    concept("ImportStatement") {
        property("qualifiedName", "String")
        property("alias", "String", optional = true)
        property("isWildcard", "Boolean", default = "false")
        syntax("\\b(import)\\s+([a-zA-Z_][a-zA-Z0-9_.]*)")
    }

    // ----- Type Declarations (from TypeCompositionStatement.java) -----

    abstractConcept("TypeDeclaration") {
        property("name", "String")
        property("visibility", "String", default = "public")
        children("annotations", "Annotation")
        children("typeParameters", "TypeParameter")
    }

    concept("ClassDeclaration") {
        extends("TypeDeclaration")
        property("isAbstract", "Boolean", default = "false")
        property("isStatic", "Boolean", default = "false")
        reference("superclass", "TypeExpression", optional = true)
        children("implements", "TypeExpression")
        children("incorporates", "IncorporatesClause")
        children("members", "ClassMember")
        syntax("\\b(class)\\s+([A-Z][a-zA-Z0-9_]*)")
    }

    concept("InterfaceDeclaration") {
        extends("TypeDeclaration")
        children("extends", "TypeExpression")
        children("members", "InterfaceMember")
        syntax("\\b(interface)\\s+([A-Z][a-zA-Z0-9_]*)")
    }

    concept("MixinDeclaration") {
        extends("TypeDeclaration")
        reference("into", "TypeExpression", optional = true)
        children("extends", "TypeExpression")
        children("members", "ClassMember")
        syntax("\\b(mixin)\\s+([A-Z][a-zA-Z0-9_]*)")
    }

    concept("ServiceDeclaration") {
        extends("TypeDeclaration")
        reference("superclass", "TypeExpression", optional = true)
        children("implements", "TypeExpression")
        children("members", "ClassMember")
        syntax("\\b(service)\\s+([A-Z][a-zA-Z0-9_]*)")
    }

    concept("ConstDeclaration") {
        extends("TypeDeclaration")
        reference("superclass", "TypeExpression", optional = true)
        children("implements", "TypeExpression")
        children("members", "ClassMember")
        syntax("\\b(const)\\s+([A-Z][a-zA-Z0-9_]*)")
    }

    concept("EnumDeclaration") {
        extends("TypeDeclaration")
        children("implements", "TypeExpression")
        children("values", "EnumValue")
        children("members", "ClassMember")
        syntax("\\b(enum)\\s+([A-Z][a-zA-Z0-9_]*)")
    }

    concept("EnumValue") {
        property("name", "String")
        children("arguments", "Expression")
    }

    // From TypedefStatement.java
    concept("TypedefDeclaration") {
        extends("TypeDeclaration")
        child("targetType", "TypeExpression")
        syntax("\\b(typedef)\\s+([A-Z][a-zA-Z0-9_]*)")
    }

    // ----- Class Members -----

    abstractConcept("ClassMember") {
        property("name", "String")
        property("visibility", "String", default = "public")
        children("annotations", "Annotation")
    }

    // Placeholder for interface members (similar structure)
    abstractConcept("InterfaceMember") {
        property("name", "String")
        property("visibility", "String", default = "public")
        children("annotations", "Annotation")
    }

    // From PropertyDeclarationStatement.java
    concept("PropertyDeclaration") {
        extends("ClassMember")
        child("type", "TypeExpression")
        property("isReadOnly", "Boolean", default = "false")
        property("isStatic", "Boolean", default = "false")
        child("initializer", "Expression", Cardinality.OPTIONAL)
    }

    // From MethodDeclarationStatement.java
    concept("MethodDeclaration") {
        extends("ClassMember")
        property("isAbstract", "Boolean", default = "false")
        property("isStatic", "Boolean", default = "false")
        children("typeParameters", "TypeParameter")
        children("returnTypes", "TypeExpression")
        children("parameters", "Parameter")
        child("body", "StatementBlock", Cardinality.OPTIONAL)
        syntax("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(")
    }

    concept("ConstructorDeclaration") {
        extends("ClassMember")
        property("kind", "String")  // "construct" or "finally"
        children("parameters", "Parameter")
        child("body", "StatementBlock", Cardinality.OPTIONAL)
        syntax("\\b(construct|finally)\\s*\\(")
    }

    // From Parameter.java
    concept("Parameter") {
        property("name", "String")
        child("type", "TypeExpression")
        property("isVariadic", "Boolean", default = "false")
        child("defaultValue", "Expression", Cardinality.OPTIONAL)
    }

    concept("TypeParameter") {
        property("name", "String")
        reference("constraint", "TypeExpression", optional = true)
    }

    // ----- Type Expressions (from org.xvm.compiler.ast.*TypeExpression.java) -----

    abstractConcept("TypeExpression") {}

    // From NamedTypeExpression.java
    concept("NamedType") {
        extends("TypeExpression")
        property("name", "String")
        children("typeArguments", "TypeExpression")
        property("isNullable", "Boolean", default = "false")
    }

    // From FunctionTypeExpression.java
    concept("FunctionType") {
        extends("TypeExpression")
        children("parameterTypes", "TypeExpression")
        children("returnTypes", "TypeExpression")
    }

    // From TupleTypeExpression.java
    concept("TupleType") {
        extends("TypeExpression")
        children("elementTypes", "TypeExpression")
    }

    // From BiTypeExpression.java (union types)
    concept("UnionType") {
        extends("TypeExpression")
        children("types", "TypeExpression")
    }

    // From ArrayTypeExpression.java
    concept("ArrayType") {
        extends("TypeExpression")
        child("elementType", "TypeExpression")
        property("dimensionCount", "Int", default = "1")
    }

    // From NullableTypeExpression.java
    concept("NullableType") {
        extends("TypeExpression")
        child("baseType", "TypeExpression")
    }

    // From AnnotatedTypeExpression.java
    concept("AnnotatedType") {
        extends("TypeExpression")
        children("annotations", "Annotation")
        child("baseType", "TypeExpression")
    }

    // From DecoratedTypeExpression.java - immutable, etc.
    concept("DecoratedType") {
        extends("TypeExpression")
        property("decorator", "String")  // "immutable", etc.
        child("baseType", "TypeExpression")
    }

    // ----- Statements (from org.xvm.compiler.ast.*Statement.java) -----

    abstractConcept("Statement") {}

    // From StatementBlock.java
    concept("StatementBlock") {
        extends("Statement")
        children("statements", "Statement")
    }

    // From VariableDeclarationStatement.java
    concept("VariableDeclaration") {
        extends("Statement")
        child("type", "TypeExpression")
        property("name", "String")
        child("initializer", "Expression", Cardinality.OPTIONAL)
        property("isVal", "Boolean", default = "true")
    }

    // From IfStatement.java
    concept("IfStatement") {
        extends("Statement")
        child("condition", "Expression")
        child("thenBranch", "Statement")
        child("elseBranch", "Statement", Cardinality.OPTIONAL)
    }

    // From SwitchStatement.java
    concept("SwitchStatement") {
        extends("Statement")
        child("expression", "Expression")
        children("cases", "CaseClause")
    }

    // From ForStatement.java
    concept("ForStatement") {
        extends("Statement")
        child("initializer", "Statement", Cardinality.OPTIONAL)
        child("condition", "Expression", Cardinality.OPTIONAL)
        child("update", "Expression", Cardinality.OPTIONAL)
        child("body", "Statement")
    }

    // From ForEachStatement.java
    concept("ForEachStatement") {
        extends("Statement")
        child("variableType", "TypeExpression")
        property("variableName", "String")
        child("iterable", "Expression")
        child("body", "Statement")
    }

    // From WhileStatement.java
    concept("WhileStatement") {
        extends("Statement")
        child("condition", "Expression")
        child("body", "Statement")
    }

    // From TryStatement.java
    concept("TryStatement") {
        extends("Statement")
        children("resources", "Expression")
        child("tryBlock", "StatementBlock")
        children("catchClauses", "CatchClause")
        child("finallyBlock", "StatementBlock", Cardinality.OPTIONAL)
    }

    // From ReturnStatement.java
    concept("ReturnStatement") {
        extends("Statement")
        children("values", "Expression")
    }

    // From AssertStatement.java
    concept("AssertStatement") {
        extends("Statement")
        child("condition", "Expression")
        child("message", "Expression", Cardinality.OPTIONAL)
        property("kind", "String", default = "assert")  // assert, assert:arg, assert:bounds, etc.
    }

    // From BreakStatement.java
    concept("BreakStatement") {
        extends("Statement")
        property("label", "String", optional = true)
    }

    // From ContinueStatement.java
    concept("ContinueStatement") {
        extends("Statement")
        property("label", "String", optional = true)
    }

    // From AssignmentStatement.java
    concept("AssignmentStatement") {
        extends("Statement")
        child("target", "Expression")
        property("operator", "String")  // =, +=, -=, etc.
        child("value", "Expression")
    }

    // From ExpressionStatement.java
    concept("ExpressionStatement") {
        extends("Statement")
        child("expression", "Expression")
    }

    // From LabeledStatement.java
    concept("LabeledStatement") {
        extends("Statement")
        property("label", "String")
        child("statement", "Statement")
    }

    // ----- Expressions (from org.xvm.compiler.ast.*Expression.java) -----

    abstractConcept("Expression") {}

    // From LiteralExpression.java
    concept("LiteralExpression") {
        extends("Expression")
        property("value", "String")
        property("kind", "String")  // INT, STRING, CHAR, BOOLEAN, NULL, etc.
    }

    // From NameExpression.java
    concept("IdentifierExpression") {
        extends("Expression")
        property("name", "String")
    }

    // From BiExpression.java
    concept("BinaryExpression") {
        extends("Expression")
        child("left", "Expression")
        property("operator", "String")
        child("right", "Expression")
    }

    // From PrefixExpression.java, UnaryMinusExpression.java, UnaryPlusExpression.java,
    // UnaryComplementExpression.java
    concept("UnaryExpression") {
        extends("Expression")
        property("operator", "String")
        child("operand", "Expression")
        property("isPrefix", "Boolean", default = "true")
    }

    // From InvocationExpression.java
    concept("InvocationExpression") {
        extends("Expression")
        child("target", "Expression")
        children("typeArguments", "TypeExpression")
        children("arguments", "Expression")
    }

    // From NewExpression.java
    concept("NewExpression") {
        extends("Expression")
        child("type", "TypeExpression")
        children("arguments", "Expression")
    }

    // From LambdaExpression.java
    concept("LambdaExpression") {
        extends("Expression")
        children("parameters", "Parameter")
        child("body", "Expression")  // or StatementBlock for block lambdas
    }

    // From TernaryExpression.java
    concept("TernaryExpression") {
        extends("Expression")
        child("condition", "Expression")
        child("thenExpr", "Expression")
        child("elseExpr", "Expression")
    }

    // From ElvisExpression.java
    concept("ElvisExpression") {
        extends("Expression")
        child("value", "Expression")
        child("otherwise", "Expression")
    }

    // From ArrayAccessExpression.java
    concept("ArrayAccessExpression") {
        extends("Expression")
        child("array", "Expression")
        children("indices", "Expression")
    }

    // From TupleExpression.java
    concept("TupleExpression") {
        extends("Expression")
        children("elements", "Expression")
    }

    // From ListExpression.java
    concept("ListExpression") {
        extends("Expression")
        children("elements", "Expression")
    }

    // From MapExpression.java
    concept("MapExpression") {
        extends("Expression")
        children("entries", "MapEntry")
    }

    concept("MapEntry") {
        child("key", "Expression")
        child("value", "Expression")
    }

    // From TemplateExpression.java (string interpolation)
    concept("TemplateExpression") {
        extends("Expression")
        children("parts", "Expression")  // alternating strings and expressions
    }

    // From IsExpression.java
    concept("IsExpression") {
        extends("Expression")
        child("value", "Expression")
        child("type", "TypeExpression")
    }

    // From AsExpression.java
    concept("AsExpression") {
        extends("Expression")
        child("value", "Expression")
        child("type", "TypeExpression")
    }

    // From ThrowExpression.java
    concept("ThrowExpression") {
        extends("Expression")
        child("exception", "Expression")
    }

    // From SwitchExpression.java
    concept("SwitchExpression") {
        extends("Expression")
        child("selector", "Expression")
        children("cases", "CaseClause")
    }

    // From ParenthesizedExpression.java
    concept("ParenthesizedExpression") {
        extends("Expression")
        child("inner", "Expression")
    }

    // From NotNullExpression.java (postfix !)
    concept("NotNullExpression") {
        extends("Expression")
        child("inner", "Expression")
    }

    // From SequentialAssignExpression.java (++/-- pre/post)
    concept("SequentialAssignExpression") {
        extends("Expression")
        child("target", "Expression")
        property("operator", "String")  // ++ or --
        property("isPrefix", "Boolean")
    }

    // ----- Comparison & Relational Expressions (from org.xvm.compiler.ast) -----

    // From CmpExpression.java - comparison operations (<, >, <=, >=, <=>)
    // The spaceship operator (<=>) returns Ordered (Less, Equal, Greater)
    concept("ComparisonExpression") {
        extends("Expression")
        child("left", "Expression")
        property("operator", "String")  // <, >, <=, >=, <=>
        child("right", "Expression")
    }

    // From CmpChainExpression.java - chained comparisons (a < b < c)
    // XTC allows comparison chaining: 0 <= x < 10 means (0 <= x) && (x < 10)
    concept("ComparisonChainExpression") {
        extends("Expression")
        children("operands", "Expression")
        property("operators", "List<String>")  // List of comparison operators
    }

    // From CondOpExpression.java - conditional operators (&&, ||, ^^)
    // Short-circuit evaluation for logical operators
    concept("ConditionalOperatorExpression") {
        extends("Expression")
        child("left", "Expression")
        property("operator", "String")  // &&, ||, ^^
        child("right", "Expression")
    }

    // From RelOpExpression.java - relational operations
    // Used for type relations and other relational checks
    concept("RelationalExpression") {
        extends("Expression")
        child("left", "Expression")
        property("operator", "String")
        child("right", "Expression")
    }

    // From ElseExpression.java - else-if expression chains
    // Supports expression form of if-else-if chains
    concept("ElseExpression") {
        extends("Expression")
        child("condition", "Expression")
        child("thenExpr", "Expression")
        child("elseExpr", "Expression", Cardinality.OPTIONAL)
    }

    // From ConvertExpression.java - implicit type conversions
    // Compiler-generated conversion between compatible types
    concept("ConvertExpression") {
        extends("Expression")
        child("source", "Expression")
        child("targetType", "TypeExpression")
        property("isImplicit", "Boolean", default = "true")
    }

    // From DelegatingExpression.java (abstract) - delegation pattern
    // Base for expressions that delegate to another expression
    abstractConcept("DelegatingExpression") {
        extends("Expression")
        child("delegate", "Expression")
    }

    // From FileExpression.java - file inclusion expressions
    // Handles $"path" (string file) and #"path" (binary file)
    concept("FileExpression") {
        extends("Expression")
        property("path", "String")
        property("isBinary", "Boolean", default = "false")  // # vs $
        property("resolvedPath", "String", optional = true)
    }

    // From IgnoredNameExpression.java - the wildcard _ expression
    // Used in assignments where a value is discarded: (_, y) = tuple
    concept("IgnoredNameExpression") {
        extends("Expression")
        // No properties - represents the _ wildcard
    }

    // From LabeledExpression.java - labeled expressions for flow control
    // Allows labels on expressions: LABEL: expr
    concept("LabeledExpression") {
        extends("Expression")
        property("label", "String")
        child("expression", "Expression")
    }

    // From NonBindingExpression.java - expressions that don't bind names
    // Used for expressions in declaration contexts that shouldn't bind
    concept("NonBindingExpression") {
        extends("Expression")
        child("expression", "Expression")
    }

    // From PackExpression.java - tuple packing
    // Packs multiple values into a tuple: (a, b, c)
    concept("PackExpression") {
        extends("Expression")
        children("elements", "Expression")
    }

    // From StatementExpression.java - statement used as expression
    // Wraps a statement block to be used in expression context
    concept("StatementExpression") {
        extends("Expression")
        child("block", "StatementBlock")
    }

    // From SyntheticExpression.java (abstract) - compiler-generated expressions
    // Base for expressions generated during compilation, not present in source
    abstractConcept("SyntheticExpression") {
        extends("Expression")
        property("origin", "String", optional = true)  // What generated this
    }

    // From ToIntExpression.java - conversion to integer
    // Specialized conversion for integer types
    concept("ToIntExpression") {
        extends("Expression")
        child("source", "Expression")
        property("targetIntType", "String", default = "Int")  // Int, Int8, Int16, etc.
    }

    // From TraceExpression.java - debugging trace expressions
    // TODO: assert expression for debugging/tracing
    concept("TraceExpression") {
        extends("Expression")
        child("expression", "Expression")
        property("message", "String", optional = true)
    }

    // From UnpackExpression.java - tuple unpacking
    // Unpacks a tuple into multiple values: (a, b) = tuple
    concept("UnpackExpression") {
        extends("Expression")
        child("tuple", "Expression")
        children("targets", "Expression")  // LValue targets for unpacked values
    }

    // ----- Additional Type Expressions (from org.xvm.compiler.ast) -----

    // From ModuleTypeExpression.java - extends NamedTypeExpression
    // Represents a module as a type (module.Type syntax)
    concept("ModuleType") {
        extends("NamedType")
        property("moduleName", "String")
    }

    // From KeywordTypeExpression.java - keyword-based types
    // Handles void, immutable, etc. as type expressions
    concept("KeywordType") {
        extends("TypeExpression")
        property("keyword", "String")  // void, Immutable, etc.
    }

    // From VariableTypeExpression.java - type variables/parameters
    // Used in generic contexts: T, K, V in class Foo<T, K, V>
    concept("TypeVariableType") {
        extends("TypeExpression")
        property("name", "String")
        reference("definition", "TypeParameter", optional = true)
    }

    // From BadTypeExpression.java - error recovery type
    // Placeholder for malformed type expressions during error recovery
    concept("BadType") {
        extends("TypeExpression")
        property("errorMessage", "String", optional = true)
    }

    // ----- Additional Statements (from org.xvm.compiler.ast) -----

    // From GotoStatement.java (abstract) - jump statements base
    // Abstract base for break, continue with labels
    abstractConcept("GotoStatement") {
        extends("Statement")
        property("targetLabel", "String", optional = true)
    }

    // From MultipleLValueStatement.java - multiple assignment targets
    // Handles (a, b, c) = expression patterns
    concept("MultipleLValueStatement") {
        extends("Statement")
        children("targets", "Expression")  // LValue expressions
        child("value", "Expression")
    }

    // From ConditionalStatement.java - conditional declarations
    // if (Type name := expr) { use name }
    concept("ConditionalStatement") {
        extends("Statement")
        child("declaration", "VariableDeclaration")
        child("condition", "Expression")
        child("thenBranch", "Statement")
        child("elseBranch", "Statement", Cardinality.OPTIONAL)
    }

    // From ComponentStatement.java - component declarations
    // Base for module-level component declarations
    concept("ComponentStatement") {
        extends("Statement")
        property("name", "String")
        property("visibility", "String", default = "public")
        children("annotations", "Annotation")
    }

    // From DoStatement.java - do-while loop (variant of WhileStatement)
    concept("DoWhileStatement") {
        extends("Statement")
        child("body", "Statement")
        child("condition", "Expression")
    }

    // ----- Supporting Concepts -----

    concept("Annotation") {
        property("name", "String")
        children("arguments", "Expression")
    }

    // From CompositionNode.java - composition clauses (extends, implements, etc.)
    // Abstract base for type composition elements
    abstractConcept("CompositionClause") {
        property("keyword", "String")  // extends, implements, delegates, incorporates, into
    }

    // Extends clause - class Foo extends Bar
    concept("ExtendsClause") {
        extends("CompositionClause")
        child("superType", "TypeExpression")
    }

    // Implements clause - class Foo implements Bar, Baz
    concept("ImplementsClause") {
        extends("CompositionClause")
        children("interfaces", "TypeExpression")
    }

    // Delegates clause - class Foo delegates Bar to prop
    concept("DelegatesClause") {
        extends("CompositionClause")
        child("delegateType", "TypeExpression")
        property("delegateProperty", "String")
    }

    // Incorporates clause - class Foo incorporates Mixin
    concept("IncorporatesClause") {
        extends("CompositionClause")
        child("mixin", "TypeExpression")
        children("arguments", "Expression")
        property("isConditional", "Boolean", default = "false")
    }

    // Into clause - mixin Foo into Bar
    concept("IntoClause") {
        extends("CompositionClause")
        child("targetType", "TypeExpression")
    }

    // From CaseStatement.java
    concept("CaseClause") {
        children("labels", "Expression")
        property("isDefault", "Boolean", default = "false")
        children("statements", "Statement")
    }

    // From CatchStatement.java
    concept("CatchClause") {
        child("exceptionType", "TypeExpression")
        property("variableName", "String")
        child("body", "StatementBlock")
    }

    // From VersionOverride.java
    concept("VersionOverride") {
        property("versionSpec", "String")
        child("statement", "Statement")
    }

    // From AnonInnerClass.java - anonymous inner class structure
    // Represents new Interface() { ... } or new Class(args) { ... }
    concept("AnonymousInnerClass") {
        child("baseType", "TypeExpression")
        children("arguments", "Expression")
        children("members", "ClassMember")
    }

    // ----- Struct Declaration (missing from previous) -----

    concept("StructDeclaration") {
        extends("TypeDeclaration")
        children("implements", "TypeExpression")
        children("members", "ClassMember")
        syntax("\\b(struct)\\s+([A-Z][a-zA-Z0-9_]*)")
    }

    // =========================================================================
    // Token Classification (for lexer generation)
    // =========================================================================
    //
    // These are the literal token types from Token.java that the lexer produces.
    // Each has a specific format and is used for numeric/temporal literals.

    // ----- Literal Token Types -----
    // Note: These represent the *typed* literals in XTC, e.g., 123i8, 3.14f32

    // Integer literal types (suffixed: 123i8, 456i16, etc.)
    token("LIT_INT8", "[0-9][0-9_]*[iI]8", "constant.numeric.integer.int8.xtc")
    token("LIT_INT16", "[0-9][0-9_]*[iI]16", "constant.numeric.integer.int16.xtc")
    token("LIT_INT32", "[0-9][0-9_]*[iI]32", "constant.numeric.integer.int32.xtc")
    token("LIT_INT64", "[0-9][0-9_]*[iI]64", "constant.numeric.integer.int64.xtc")
    token("LIT_INT128", "[0-9][0-9_]*[iI]128", "constant.numeric.integer.int128.xtc")
    token("LIT_INTN", "[0-9][0-9_]*[iI][nN]", "constant.numeric.integer.intn.xtc")

    // Unsigned integer literal types (suffixed: 123u8, 456u16, etc.)
    token("LIT_UINT8", "[0-9][0-9_]*[uU]8", "constant.numeric.integer.uint8.xtc")
    token("LIT_UINT16", "[0-9][0-9_]*[uU]16", "constant.numeric.integer.uint16.xtc")
    token("LIT_UINT32", "[0-9][0-9_]*[uU]32", "constant.numeric.integer.uint32.xtc")
    token("LIT_UINT64", "[0-9][0-9_]*[uU]64", "constant.numeric.integer.uint64.xtc")
    token("LIT_UINT128", "[0-9][0-9_]*[uU]128", "constant.numeric.integer.uint128.xtc")
    token("LIT_UINTN", "[0-9][0-9_]*[uU][nN]", "constant.numeric.integer.uintn.xtc")

    // Decimal literal types (suffixed: 3.14d32, 2.718d64, etc.)
    token("LIT_DEC32", "[0-9][0-9_]*\\.[0-9][0-9_]*[dD]32", "constant.numeric.decimal.dec32.xtc")
    token("LIT_DEC64", "[0-9][0-9_]*\\.[0-9][0-9_]*[dD]64", "constant.numeric.decimal.dec64.xtc")
    token("LIT_DEC128", "[0-9][0-9_]*\\.[0-9][0-9_]*[dD]128", "constant.numeric.decimal.dec128.xtc")
    token("LIT_DECN", "[0-9][0-9_]*\\.[0-9][0-9_]*[dD][nN]", "constant.numeric.decimal.decn.xtc")

    // Float literal types (suffixed: 3.14f32, 2.718f64, etc.)
    token("LIT_FLOAT8E4", "[0-9][0-9_]*\\.?[0-9]*[fF]8[eE]4", "constant.numeric.float.float8e4.xtc")
    token("LIT_FLOAT8E5", "[0-9][0-9_]*\\.?[0-9]*[fF]8[eE]5", "constant.numeric.float.float8e5.xtc")
    token("LIT_FLOAT16", "[0-9][0-9_]*\\.?[0-9]*[fF]16", "constant.numeric.float.float16.xtc")
    token("LIT_FLOAT32", "[0-9][0-9_]*\\.?[0-9]*[fF]32", "constant.numeric.float.float32.xtc")
    token("LIT_FLOAT64", "[0-9][0-9_]*\\.?[0-9]*[fF]64", "constant.numeric.float.float64.xtc")
    token("LIT_FLOAT128", "[0-9][0-9_]*\\.?[0-9]*[fF]128", "constant.numeric.float.float128.xtc")
    token("LIT_FLOATN", "[0-9][0-9_]*\\.?[0-9]*[fF][nN]", "constant.numeric.float.floatn.xtc")
    token("LIT_BFLOAT16", "[0-9][0-9_]*\\.?[0-9]*[bB][fF]16", "constant.numeric.float.bfloat16.xtc")

    // Bit and Nibble literals
    token("LIT_BIT", "0|1", "constant.numeric.bit.xtc")
    token("LIT_NIBBLE", "0[xX][0-9a-fA-F]", "constant.numeric.nibble.xtc")

    // Path literals (for file system paths)
    token("LIT_PATH", "\\.?\\./[a-zA-Z0-9_./-]+", "string.other.path.xtc")

    // =========================================================================
    // Common Standard Library Annotations (from lib_ecstasy)
    // =========================================================================
    //
    // These are NOT language keywords but annotations defined in lib_ecstasy.
    // They are recognized by the compiler through the annotation mechanism.
    // The syntax highlighting treats them as annotations (@annotation scope).
    //
    // Note: These are documented here for completeness but are NOT part of
    // the core language grammar. They're library types that generators may
    // want to recognize for enhanced highlighting or IDE features.

    // ----- Property & Access Control Annotations -----
    // @RO - Read-only property (compiler enforces no writes)
    // @WO - Write-only property (rare, for special cases)
    // @Lazy - Lazy initialization (computed on first access)
    // @Atomic - Atomic access guarantees
    // @Volatile - Volatile memory access semantics

    // ----- Dependency Injection Annotations -----
    // @Inject - Dependency injection marker (service/container injects value)
    //   Example: @Inject Console console;
    //   The container/service provides the Console instance at runtime.

    // ----- Method & Class Modifiers -----
    // @Abstract - Abstract method or class (must be overridden)
    // @Override - Method overrides a parent method
    // @Final - Cannot be overridden or extended
    // @Synchronized - Synchronized access (implicit lock)

    // ----- Lifecycle & Initialization -----
    // @Default - Provides a default implementation
    // @Op - Operator overload marker (e.g., @Op("+") add)

    // ----- Conditional Compilation -----
    // @Concurrent - For concurrent execution contexts
    // @Test - Test method marker (for test frameworks)

    // ----- Documentation Annotations -----
    // @Deprecated - Marks deprecated API
    // @SoftDeprecated - Soft deprecation (warning only)

    // The annotation() concept (defined above) handles all annotations
    // uniformly in the AST. The semantic meaning comes from lib_ecstasy.
    //
    // For syntax highlighting:
    //   - All @Identifier tokens use the "annotation" scope
    //   - No special handling needed for specific annotation names
    //   - IDEs may provide enhanced completion/validation for known annotations

    // =========================================================================
    // Compiler Infrastructure (Non-AST nodes used during compilation)
    // =========================================================================
    //
    // These are not AST nodes but compiler infrastructure classes that manage
    // the compilation process. They are documented here for completeness.
    //
    // Context.java - Compilation context holding scope, module, and state
    //   - Not an AST node, but provides name resolution and type checking context
    //   - Used by: Parser, all AST nodes during resolve() and validate()
    //
    // NameResolver.java - Name resolution helper
    //   - Resolves identifiers to their declarations
    //   - Handles scope hierarchy traversal
    //   - Used by: NameExpression, TypeExpression
    //
    // StageMgr.java - Compilation stage management
    //   - Tracks compilation phases: Parse, Resolve, Validate, Emit
    //   - Ensures proper ordering of compilation steps
    //
    // CaseManager.java - Switch case management
    //   - Handles switch statement analysis
    //   - Checks for exhaustiveness, duplicate cases
    //   - Used by: SwitchStatement, SwitchExpression
}
