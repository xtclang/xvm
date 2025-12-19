; Tree-sitter highlight queries for Ecstasy (XTC)
; Generated from XTC language model
; 
; Used by: Zed, Neovim (nvim-treesitter), Helix, GitHub

; =============================================================================
; Keywords
; =============================================================================

; Control flow
["if" "else" "switch" "case" "default"] @keyword.conditional
["for" "while" "do" "foreach"] @keyword.repeat
["break" "continue" "return"] @keyword.return
["try" "catch" "finally" "throw"] @keyword.exception
["assert"] @keyword.debug
["using"] @keyword

; Declarations
["module" "package"] @keyword.module
["class" "interface" "mixin" "service" "const" "enum" "typedef"] @keyword.type
["import"] @keyword.import
["construct"] @keyword.function

; Modifiers
["public" "protected" "private"] @keyword.modifier
["static" "abstract" "final" "immutable"] @keyword.modifier
["extends" "implements" "incorporates" "into"] @keyword.modifier
["inject" "conditional"] @keyword.modifier

; Other keywords
["new"] @keyword.operator
["this" "super" "outer"] @variable.builtin
["is" "as"] @keyword.operator
["void"] @type.builtin

; =============================================================================
; Literals
; =============================================================================

(integer_literal) @number
(float_literal) @number.float
(hex_literal) @number
(binary_literal) @number

(string_literal) @string
(template_expression) @string
(char_literal) @character
(escape_sequence) @string.escape

(boolean_literal) @constant.builtin
(null_literal) @constant.builtin

; =============================================================================
; Types
; =============================================================================

(type_identifier) @type

; Built-in types
((type_identifier) @type.builtin
 (#any-of? @type.builtin
  "Bit" "Boolean" "Byte" "Char" "Dec" "Float"
  "Int" "Int8" "Int16" "Int32" "Int64" "Int128" "IntN"
  "UInt" "UInt8" "UInt16" "UInt32" "UInt64" "UInt128" "UIntN"
  "String" "Object" "Void" "Array" "List" "Map" "Set"
  "Tuple" "Function" "Type" "Class" "Enum" "Exception"
  "Iterator" "Iterable" "Collection" "Sequence" "Range" "Interval"
  "Nullable" "Orderable" "Hashable" "Stringable"))

; Type parameters
(type_parameter (identifier) @type.parameter)

; =============================================================================
; Functions and Methods
; =============================================================================

(method_declaration
  name: (identifier) @function.method)

(constructor_declaration) @function.constructor

(invocation_expression
  function: (identifier) @function.call)

(invocation_expression
  function: (member_access
    member: (identifier) @function.method.call))

; =============================================================================
; Variables and Parameters
; =============================================================================

(parameter
  name: (identifier) @variable.parameter)

(variable_declaration
  name: (identifier) @variable)

(property_declaration
  name: (identifier) @property)

; =============================================================================
; Comments
; =============================================================================

(line_comment) @comment
(block_comment) @comment
(doc_comment) @comment.documentation

; Doc comment tags
((doc_comment) @comment.documentation
 (#match? @comment.documentation "@\\w+"))

; =============================================================================
; Annotations
; =============================================================================

(annotation
  "@" @punctuation.special
  name: (identifier) @attribute)

; =============================================================================
; Operators
; =============================================================================

["=" "+=" "-=" "*=" "/=" "%=" "&=" "|=" "^=" "<<=" ">>=" ">>>=" ":=" "?="] @operator

["+" "-" "*" "/" "%" "++" "--"] @operator
["==" "!=" "<" "<=" ">" ">=" "<=>"] @operator
["&&" "||" "^^" "!" "&" "|" "^" "~" "<<" ">>" ">>>"] @operator
[".." "..<" "?:" "?."] @operator

["." ":"] @punctuation.delimiter
["," ";"] @punctuation.delimiter

; =============================================================================
; Brackets
; =============================================================================

["{" "}"] @punctuation.bracket
["(" ")"] @punctuation.bracket
["[" "]"] @punctuation.bracket
["<" ">"] @punctuation.bracket

; =============================================================================
; Special
; =============================================================================

(identifier) @variable

; Module names
(module_declaration
  name: (qualified_identifier) @module)

; Class names in declarations
(class_declaration
  name: (identifier) @type.definition)

(interface_declaration
  name: (identifier) @type.definition)

(enum_declaration
  name: (identifier) @type.definition)

; Enum values
(enum_value
  name: (identifier) @constant)
