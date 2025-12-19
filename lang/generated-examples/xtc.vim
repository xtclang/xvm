" Vim syntax file for Ecstasy (XTC)
" Language: Ecstasy
" Generated from XTC language model

if exists('b:current_syntax')
  finish
endif

let s:cpo_save = &cpo
set cpo&vim

" Keywords - Control flow
syn keyword xtcControl if else switch case default
syn keyword xtcControl for while do foreach
syn keyword xtcControl break continue return
syn keyword xtcControl try catch finally throw using assert

" Keywords - Declarations
syn keyword xtcDeclaration module package class interface mixin service
syn keyword xtcDeclaration const enum typedef import construct

" Keywords - Modifiers
syn keyword xtcModifier public protected private static
syn keyword xtcModifier abstract final immutable
syn keyword xtcModifier extends implements incorporates into inject conditional

" Keywords - Other
syn keyword xtcKeyword new this super outer is as void

" Built-in types
syn keyword xtcType Bit Boolean Byte Char Dec Float
syn keyword xtcType Int Int8 Int16 Int32 Int64 Int128 IntN
syn keyword xtcType UInt UInt8 UInt16 UInt32 UInt64 UInt128 UIntN
syn keyword xtcType String Object Void Array List Map Set
syn keyword xtcType Tuple Function Type Class Enum Exception
syn keyword xtcType Iterator Iterable Collection Sequence Range Interval
syn keyword xtcType Nullable Orderable Hashable Stringable

" Literals
syn keyword xtcBoolean True False
syn keyword xtcNull Null

" Numbers
syn match xtcNumber '\<\d[\d_]*\>'
syn match xtcNumber '\<0[xX][0-9a-fA-F][0-9a-fA-F_]*\>'
syn match xtcNumber '\<0[bB][01][01_]*\>'
syn match xtcFloat '\<\d[\d_]*\.\d[\d_]*\([eE][+-]\?\d\+\)\?\>'

" Strings
syn region xtcString start='"' skip='\\.' end='"' contains=xtcEscape
syn region xtcTemplateString start='\$"' skip='\\.' end='"' contains=xtcEscape,xtcTemplateExpr
syn match xtcCharacter "'[^'\\]'"
syn match xtcCharacter "'\\[nrtbf\\\"']'"
syn match xtcCharacter "'\\u[0-9a-fA-F]\{4}'"

" Escape sequences
syn match xtcEscape '\\[nrtbf\\"'0]' contained
syn match xtcEscape '\\u[0-9a-fA-F]\{4}' contained

" Template expressions
syn region xtcTemplateExpr matchgroup=xtcTemplateBrace start='{' end='}' contained contains=TOP

" Comments
syn region xtcComment start='//' end='$' contains=xtcTodo
syn region xtcComment start='/\*' end='\*/' contains=xtcTodo,xtcComment
syn region xtcDocComment start='/\*\*' end='\*/' contains=xtcTodo,xtcDocTag

" Doc comment tags
syn match xtcDocTag '@\w\+' contained

" TODO markers
syn keyword xtcTodo TODO FIXME XXX HACK NOTE contained

" Annotations
syn match xtcAnnotation '@[A-Za-z_][A-Za-z0-9_]*'

" Type names (PascalCase identifiers)
syn match xtcTypeName '\<[A-Z][A-Za-z0-9_]*\>'

" Function calls
syn match xtcFunctionCall '\<[a-z_][A-Za-z0-9_]*\s*(' contains=xtcFunctionName
syn match xtcFunctionName '\<[a-z_][A-Za-z0-9_]*' contained

" Operators
syn match xtcOperator '[+\-*/%&|^~<>=!?:.]'
syn match xtcOperator '&&\|||\|\^\^'
syn match xtcOperator '==\|!=\|<=\|>=\|<=>'
syn match xtcOperator '<<\|>>\|>>>'
syn match xtcOperator '\.\.\|\.\.<'
syn match xtcOperator '?:\|?\.'
syn match xtcOperator '+=\|-=\|\*=\|/=\|%=\|&=\||=\|\^='

" Brackets
syn match xtcBracket '[{}()\[\]<>]'

" Highlighting
hi def link xtcControl Keyword
hi def link xtcDeclaration Keyword
hi def link xtcModifier StorageClass
hi def link xtcKeyword Keyword
hi def link xtcType Type
hi def link xtcTypeName Type
hi def link xtcBoolean Boolean
hi def link xtcNull Constant
hi def link xtcNumber Number
hi def link xtcFloat Float
hi def link xtcString String
hi def link xtcTemplateString String
hi def link xtcCharacter Character
hi def link xtcEscape SpecialChar
hi def link xtcTemplateBrace Special
hi def link xtcComment Comment
hi def link xtcDocComment SpecialComment
hi def link xtcDocTag Special
hi def link xtcTodo Todo
hi def link xtcAnnotation PreProc
hi def link xtcFunctionName Function
hi def link xtcOperator Operator
hi def link xtcBracket Delimiter

let b:current_syntax = 'xtc'

let &cpo = s:cpo_save
unlet s:cpo_save
