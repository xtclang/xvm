; Tree-sitter highlights for Ecstasy
; Generated from XTC language model

; Comments
(line_comment) @comment
(block_comment) @comment
(doc_comment) @comment.documentation

; Control flow keywords
"if" @keyword
"else" @keyword
"switch" @keyword
"case" @keyword
"default" @keyword
"for" @keyword
"while" @keyword
"do" @keyword
"break" @keyword
"continue" @keyword
"return" @keyword

; Exception keywords
"try" @keyword
"catch" @keyword
"finally" @keyword
"throw" @keyword
"using" @keyword
"assert" @keyword

; Declaration keywords
"construct" @keyword
"function" @keyword
"typedef" @keyword
"import" @keyword
"module" @keyword
"package" @keyword
"class" @keyword
"interface" @keyword
"mixin" @keyword
"service" @keyword
"const" @keyword
"enum" @keyword
"struct" @keyword
"val" @keyword
"var" @keyword

; Modifiers
(visibility_modifier) @keyword.modifier
"static" @keyword.modifier
"immutable" @keyword.modifier
"conditional" @keyword.modifier
"allow" @keyword.modifier
"avoid" @keyword.modifier
"prefer" @keyword.modifier
"desired" @keyword.modifier
"required" @keyword.modifier
"optional" @keyword.modifier
"embedded" @keyword.modifier
"inject" @keyword.modifier

; Type relation keywords
"extends" @keyword
"implements" @keyword
"delegates" @keyword
"incorporates" @keyword
"into" @keyword

; Operators
[
  "=" "+=" "-=" "*=" "/=" "%=" "&=" "|="
  "^=" "<<=" ">>=" ">>>=" ":=" "?=" "?:=" "&&="
  "||=" "?:" "||" "^^" "&&" "|" "^" "&"
  "==" "!=" "<" "<=" ">" ">=" "<=>" ".."
  ">.." "..<" ">..<" "<<" ">>" ">>>" "+" "-"
  "*" "/" "%" "/%" "!" "~" "++" "--"
  "." "?." "->" "<-" "_" "^("
] @operator

; Punctuation
[":" ";" ","] @punctuation.delimiter
["(" ")" "{" "}" "[" "]" "<" ">"] @punctuation.bracket

; Literals
(integer_literal) @number
(float_literal) @number.float
(string_literal) @string
(template_string_literal) @string
(char_literal) @character
(boolean_literal) @constant.builtin
(null_literal) @constant.builtin

; Types
(type_name) @type
(type_parameter (identifier) @type.parameter)

; Built-in types
((type_name) @type.builtin
  (#match? @type.builtin "^(Bit|Boolean|Byte|Char|Int|Int8|Int16|Int32|Int64|Int128|IntN|UInt|UInt8|UInt16|UInt32|UInt64|UInt128|UIntN|Dec|Dec32|Dec64|Dec128|DecN|Float|Float8e4|Float8e5|Float16|Float32|Float64|Float128|FloatN|BFloat16|String|Char|Object|Enum|Exception|Const|Service|Module|Package|Array|List|Set|Map|Range|Interval|Tuple|Function|Method|Property|Type|Class|Nullable|Orderable|Hashable|Stringable|Iterator|Iterable|Collection|Sequence|Void|Null|True|False)$"))

; Functions
(method_declaration
  name: (identifier) @function)
(constructor_declaration
  ("construct") @constructor)
(call_expression
  function: (identifier) @function.call)
(call_expression
  function: (member_expression
    property: (identifier) @function.call))

; Variables
(identifier) @variable
(parameter name: (identifier) @variable.parameter)
(property_declaration name: (identifier) @variable.member)

; Definitions
(module_declaration name: (qualified_name) @namespace)
(package_declaration name: (identifier) @namespace)
(class_declaration name: (type_name) @type.definition)
(interface_declaration name: (type_name) @type.definition)
(mixin_declaration name: (type_name) @type.definition)
(service_declaration name: (type_name) @type.definition)
(const_declaration name: (type_name) @type.definition)
(enum_declaration name: (type_name) @type.definition)

; Annotations
(annotation "@" @punctuation.special)
(annotation name: (identifier) @attribute)
