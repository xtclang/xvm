" Vim syntax file for Ecstasy
" Language: Ecstasy
" Generated from XTC language model

if exists('b:current_syntax')
  finish
endif

let s:cpo_save = &cpo
set cpo&vim

" Keywords - Control flow
syn keyword xtcControl if else switch case default for while do break continue return

" Keywords - Exception handling
syn keyword xtcException try catch finally throw using assert

" Keywords - Declarations
syn keyword xtcDeclaration construct function typedef import module package class interface mixin service const enum struct val var

" Keywords - Modifiers
syn keyword xtcModifier public protected private static immutable conditional allow avoid prefer desired required optional embedded inject

" Keywords - Type relations
syn keyword xtcTypeRelation extends implements delegates incorporates into

" Keywords - Other
syn keyword ecstasyKeyword is as new void TODO annotation this super outer

" Built-in types
syn keyword xtcType Bit Boolean Byte Char Int Int8 Int16 Int32 Int64 Int128 IntN UInt UInt8 UInt16 UInt32 UInt64 UInt128 UIntN Dec Dec32 Dec64 Dec128 DecN Float Float8e4 Float8e5 Float16 Float32 Float64 Float128 FloatN BFloat16 String Char Object Enum Exception Const Service Module Package Array List Set Map Range Interval Tuple Function Method Property Type Class Nullable Orderable Hashable Stringable Iterator Iterable Collection Sequence Void Null True False

" Boolean constants
syn keyword xtcBoolean True False

" Null constant
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
syn match xtcOperator '\|\|\|\^\^\|&&'
syn match xtcOperator '==\|!=\|<=\|>=\|<=>'
syn match xtcOperator '<<\|>>\|>>>'
syn match xtcOperator '\.\.\|>\.\.\|\.\.<\|>\.\.<'
syn match xtcOperator '?\.'
syn match xtcOperator '+=\|-=\|\*=\|\/=\|%=\|&=\|\|=\|\^=\|<<=\|>>=\|>>>=\|:=\|?=\|?:=\|&&=\|\|\|='

" Punctuation
syn match xtcBracket '[(){}[]<>]'

" Highlighting
hi def link xtcControl Keyword
hi def link xtcException Exception
hi def link xtcDeclaration Keyword
hi def link xtcModifier StorageClass
hi def link xtcTypeRelation Keyword
hi def link ecstasyKeyword Keyword
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
