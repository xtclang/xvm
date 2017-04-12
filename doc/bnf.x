
# question: is "<A, B>" a "tuple type of A and B"? i.e. is "(a, b)" of type "<A, B>"?


#
# misc
#

QualifiedName
    Name
    QualifiedName "." Name

Modifiers
    Modifier
    Modifiers Modifier

Modifier
    "static"
    AccessModifier
    Annotation

AccessModifier
    "public"
    "protected"
    "private"

Annotations
    Annotation
    Annotations Annotation

Annotation
    "@" NoWhitespace NamedTypeExpression NoWhitespace ArgumentList-opt

ParameterList
    "(" Parameters ")"

Parameters
    Parameter
    Parameters "," Parameter

Parameter
    TypeExpression Name DefaultValue-opt

DefaultValue
    "=" Expression

ArgumentList
    "(" Arguments-opt ")"

Arguments
    Argument
    Arguments "," Argument

Argument
    NamedArgument-opt ArgumentExpression

# note: the "?" argument allows functions to specify arguments that they are NOT binding
ArgumentExpression
    "?"
    Expression

NamedArgument
    Name "="

TypeParameterList
    "<" TypeParameters ">"

TypeParameters
    TypeParameter
    TypeParameters "," TypeParameter

TypeParameter
    Name TypeParameterConstraint-opt

TypeParameterConstraint
    "extends" TypeExpression

TypeParameterTypeList
    "<" TypeExpressionList ">"

TypeExpressionList
    TypeExpression
    TypeExpressionList "," TypeExpression

TypeVariableList
    "<" TypeVariables ">"

TypeVariables
    TypeVariable
    TypeVariables "," TypeVariable

TypeVariable
    Name

#
# compilation unit
#

CompilationUnit
	AliasStatements-opt TypeCompositionStatement

AliasStatements
	AliasStatement
	AliasStatements AliasStatement

AliasStatement
	ImportStatement
	TypeDefStatement

#
# type compositions
#

TypeCompositionStatement
    Modifiers-opt Category QualifiedName TypeParameterList-opt ParameterList-opt Compositions-opt TypeCompositionBody

Category
    "module"
    "package"
    "class"
    "interface"
    "service"
    "const"
    "enum"
    "trait"
    "mixin"

Compositions
    Composition
    Compositions Composition

Composition
    "extends" TypeExpression ArgumentList-opt
    "implements" TypeExpression
    "delegates" TypeExpression "(" Expression ")"
    "incorporates" TypeExpression ArgumentList-opt
    "into" TypeExpression
    "import" QualifiedName VersionRequirement-opt

VersionRequirement
    Version VersionOverrides-opt
    
VersionOverrides
    VersionOverride
    VersionOverrides VersionOverride
    
VersionOverride
    VersionOverrideVerb Versions

VersionOverrideVerb
    "allow"
    "avoid"
    "prefer"

Versions
    Version
    Versions, Version

# note: the StringLiteral must contain a VersionString
Version
    StringLiteral

VersionString
    VersionFinish
    VersionString . VersionFinish

VersionFinish:
    NonGAPrefix-opt DigitsNoUnderscores
    NonGAPrefix DigitsNoUnderscores-opt

NonGAPrefix:
    "dev"
    "ci"
    "alpha"
    "beta"
    "rc"

# note: EnumList is only valid (and is not actually optional) for the "enum" category, but that
# check can be deferred to a syntactic or semantic analysis phase
# note: an empty body is rare, but does occur e.g. "package x import ..", and simple classes
# with constructors specified in the type composition e.g. "const Point(Int x, Int y);"
TypeCompositionBody
    "{" EnumBody "}"
    "{" TypeCompositionComponents "}"
    ";"

EnumBody
    Enums EnumBodyFinish-opt

Enums
    Enum
    Enums "," Enum

EnumBodyFinish
    ";" TypeCompositionComponents

Enum
    Annotations-opt Name TypeParameterTypeList-opt ArgumentList-opt TypeCompositionBody-opt

TypeCompositionComponents
    TypeCompositionComponent
    TypeCompositionComponents TypeCompositionComponent

TypeCompositionComponent
    TypdefStatement
    ImportStatement
    TypeCompositionStatement
    PropertyDeclaration
    MethodDeclarationStatement
    ConstantDeclaration

#
# properties
#

PropertyDeclaration
    PropertyModifiers-opt TypeExpression Name PropertyDeclarationFinish-opt

PropertyModifiers
    PropertyModifier
    PropertyModifiers PropertyModifiers

PropertyModifier
     "static"
    PropertyAccessModifier
    Annotation

PropertyAccessModifier
    AccessModifier
    AccessModifier "/" AccessModifier

PropertyDeclarationFinish
    "=" Expression ";"
    "." Name Parameters MethodBody
    TypeCompositionBody

#
# methods
#

MethodDeclarationStatement
    MethodModifiers-opt TypeVariableList-opt NamePrecursor Name RedundantReturnSpecifier ParameterList MethodDeclarationFinish

MethodModifiers
    MethodModifier
    MethodModifiers MethodModifier

MethodModifier
    Modifier
    Annotation

NamePrecursor
    "construct"
    ReturnList

ReturnList
    SingleReturnList
    MultiReturnList

SingleReturnList
    TypeExpression

MultiReturnList
    "(" TypeExpressionList ")"

RedundantReturnSpecifier
    "<" TypeExpressionList ">"

MethodDeclarationFinish
    ;
    StatementBlock

#
# constants
#

ConstantDeclaration
    static TypeExpression Name "=" Expression;

#
# statements
#

# note: not explicitly spelling out the grammar necessary to avoid the dangling "else" problem, but
#       the approach is identical to C/Java in that the parser greedily looks for an else, causing
#       the else to be associated with the inner-most "if" that is in the parse stack
# note: no "empty statement"
Statement
    TypeCompositionStatement
    PropertyDeclarationStatement
    MethodDeclarationStatement
    StatementBlock
	VariableDeclaration ";"
	Assignment ";"
	Expression ";"
    LabeledStatement
    AssertStatement
    "break" Name-opt ";"
    "continue" Name-opt ";"
    "do" StatementBlock "while" "(" ConditionalDeclaration-opt Expression ")" ";"
    ForStatement
    IfStatement
	ImportStatement
	ReturnStatement
    SwitchStatement
    "throw" Expression ";"
    TryStatement
	TypeDefStatement
    "using" ResourceDeclaration StatementBlock
    "while" "(" ConditionalDeclaration-opt Expression ")" StatementBlock       // TODO multiple cond

PropertyDeclarationStatement
    "static" TypeExpression Name PropertyDeclarationFinish-opt

StatementBlock
    "{" Statements "}"

Statements
    Statement
    Statements Statement

VariableDeclaration
    TypeExpression Name VariableInitializerFinish-opt
    "(" MultipleOptionalDeclaration "," SingleOptionalDeclaration ")" VariableInitializerFinish

MultipleOptionalDeclaration
    SingleOptionalDeclaration
    MultipleOptionalDeclaration "," SingleOptionalDeclaration

SingleOptionalDeclaration
    TypeExpression-opt Assignable

VariableInitializerFinish
    "=" Expression

Assignment
    Assignee AssignmentOperator Expression

Assignee
    Assignable
    "(" AssignableList "," Assignable ")"

AssignableList
    Assignable
    AssignableList "," Assignable

# Assignable turns out to be just an Expression that meets certain requirements, i.e. one that ends
# with a Name or an ArrayIndex
Assignable
    Name
    Expression "." Name
    Expression ArrayIndex

AssignmentOperator
    "="
    "*="
    "/="
    "%="
    "+="
    "-="
    "<<="
    ">>="
    ">>>="
    "&="
    "^="
    "|="
    "?="

LabeledStatement
    Name ":" Statement

AssertStatement
    AssertInstruction Assertion-opt ";"

AssertInstruction
    "assert"
    "assert:once"
    "assert:test"
    "assert:debug"
    "assert:always"

Assertion
    ConditionalDeclaration-opt Expression

ForStatement
    "for" "(" ForCondition ")" StatementBlock

ForCondition
    VariableInitializationList-opt ";" Expression-opt ";" VariableModificationList-opt
    ConditionalDeclarationList

ConditionalDeclarationList
    ConditionalDeclaration Expression
    ConditionalDeclarationList "," ConditionalDeclaration Expression

VariableInitializationList
    VariableInitializer
    VariableInitializationList "," VariableInitializer

VariableInitializer
    TypeExpression-opt Name VariableInitializerFinish

VariableModificationList
    VariableModification
    VariableModificationList "," VariableModification

VariableModification
    Assignment
    Expression

IfStatement
    "if" "(" ConditionalDeclaration-opt Expression ")" StatementBlock ElseStatement-opt

ConditionalDeclaration
    TypeExpression-opt Name ":"

ElseStatement
    "else" IfStatement
    "else" StatementBlock

ImportStatement
    "import" QualifiedName ImportAlias-opt ";"

ImportAlias
    "as" Name

ReturnStatement
    "return" ReturnValue-opt ";"

ReturnValue
    TupleLiteral
    ExpressionList

SwitchStatement
    switch "(" SwitchCondition ")" "{" SwitchBlocks-opt SwitchLabels-opt "}"

SwitchCondition
    VariableInitializer
    Expression

SwitchBlocks
    SwitchBlock
    SwitchBlocks SwitchBlock

SwitchBlock
    SwitchLabels Statements

SwitchLabels
    SwitchLabel
    SwitchLabels SwitchLabel

# 1) expression must be a "constant expression", i.e. compiler has to be able to determine the value
# 2) parse for TernaryExpression; cannot parse for Exception, because it has a possible trailing ':'
SwitchLabel
    "case" TernaryExpression ":"
    "default" ":"

TryStatement
    "try" ResourceDeclaration-opt StatementBlock TryFinish

ResourceDeclaration
    "(" VariableInitializationList ")"

TryFinish
    Catches
    Catches-opt "finally" StatementBlock

Catches
    Catch
    Catches Catch

Catch
    "catch" "(" TypeExpression Name ")" StatementBlock

TypeDefStatement
    "typedef" TypeExpression Name ";"

#
# expressions
#

#   Operator        Description             Level   Associativity
#   --------------  ----------------------  -----   -------------
#   ++              post-increment            1     left to right
#   --              post-decrement
#   ()              invoke a method
#   []              access array element
#   ?               conditional
#   .               access object member
#   .new            postfix object creation
#   .as             postfix type assertion
#   .instanceof     postfix type comparison
#
#   ++              pre-increment             2     right to left
#   --              pre-decrement
#   +               unary plus
#   -               unary minus
#   !               logical NOT
#   ~               bitwise NOT
#   &               reference-of
#   new             object creation
#
#   *               multiplicative            3     left to right
#   /
#   %
#
#   + -             additive                  4     left to right
#   +               string concatenation
#
#   << >>           shift                     5     left to right
#   >>>
#
#   ..              range/interval            6     left to right
#
#   <  <=           relational                7     left to right
#   >  >=
#   <=>             order
#   instanceof      type comparison
#   as              type assertion
#
#   ==              equality                  8     left to right
#   !=
#
#   &               bitwise AND               9     left to right
#   ^               bitwise XOR              10     left to right
#   |               bitwise OR               11     left to right
#   &&              conditional AND          12     left to right
#   ||              conditional OR           13     left to right
#   ?:              conditional elvis        14     right to left
#   ? :             conditional ternary      15     right to left
#   :               conditional ELSE         16     right to left

Expression
    TernaryExpression
    TernaryExpression ":" Expression

TernaryExpression
    ElvisExpression
    ElvisExpression Whitespace "?" ElvisExpression ":" TernaryExpression

ElvisExpression
    OrExpression
    OrExpression ?: ElvisExpression

OrExpression
    AndExpression
    OrExpression || AndExpression

AndExpression
    BitOrExpression
    AndExpression && BitOrExpression

BitOrExpression
    BitXorExpression
    BitOrExpression | BitXorExpression

BitXorExpression
    BitAndExpression
    BitXorExpression ^ BitAndExpression

BitAndExpression
    EqualityExpression
    BitAndExpression & EqualityExpression

EqualityExpression
    RelationalExpression
    EqualityExpression "==" RelationalExpression
    EqualityExpression "!=" RelationalExpression

RelationalExpression
    RangeExpression
    RelationalExpression "<" RangeExpression
    RelationalExpression ">" RangeExpression
    RelationalExpression "<=" RangeExpression
    RelationalExpression ">=" RangeExpression
    RelationalExpression "<=>" RangeExpression
    RelationalExpression "as" TypeExpression
    RelationalExpression "instanceof" TypeExpression

RangeExpression
    ShiftExpression
    RangeExpression ".." ShiftExpression

ShiftExpression
    AdditiveExpression
    ShiftExpression "<<" AdditiveExpression
    ShiftExpression ">>" AdditiveExpression
    ShiftExpression ">>>" AdditiveExpression

AdditiveExpression
    MultiplicativeExpression
    AdditiveExpression "+" MultiplicativeExpression
    AdditiveExpression "-" MultiplicativeExpression

MultiplicativeExpression
    PrefixExpression
    MultiplicativeExpression "*" PrefixExpression
    MultiplicativeExpression "/" PrefixExpression
    MultiplicativeExpression "%" PrefixExpression
    MultiplicativeExpression "/%" PrefixExpression

PrefixExpression
    PostfixExpression
    "++" PrefixExpression
    "--" PrefixExpression
    "+" PrefixExpression
    "-" PrefixExpression
    "!" PrefixExpression
    "~" PrefixExpression
    "&" PrefixExpression

NewFinish
     ArgumentList-opt
     AnonClassBody-opt

AnonClassBody
    "{" TypeCompositionComponents "}"

# see comment on primary expression to understand why type parameter list needs to be parsed as part
# of name
PostfixExpression
    PrimaryExpression
    PostfixExpression "++"
    PostfixExpression "--"
    PostfixExpression ArgumentList
    PostfixExpression ArrayDims
    PostfixExpression ArrayIndex
    PostfixExpression NoWhitespace "?"
    PostfixExpression "." Name TypeParameterTypeList-opt
    PostfixExpression ".new" TypeExpression ArgumentList-opt
    PostfixExpression ".as" "(" TypeExpression ")"
    PostfixExpression ".instanceof" "(" TypeExpression ")"

ArrayDims
    ArrayDim
    ArrayDims ArrayDim

ArrayDim
    "[" DimIndicators-opt "]"

DimIndicators
    DimIndicator
    DimIndicators "," DimIndicator

DimIndicator
    "?"

ArrayIndex
    "[" ExpressionList "]"

ExpressionList
    Expression
    ExpressionList "," Expression


# Note: A parenthesized Expression, a TupleLiteral, and a LambdaExpression share a parse path
# Note: The use of QualifiedName instead of a simple Name here (which would be logical and even
#       expected since PostfixExpression takes care of the ".Name.Name" etc. suffix parsing) is
#       used to capture the case where the expression is a type expression containing type
#       parameters, and which the opening '<' of the type parameters would be parsed by the
#       RelationalExpression rule if we miss handling it here. Unfortunately, that means that the
#       TypeParameterList is parsed speculatively if the '<' opening token is encountered after
#       a name, because it could (might/will occasionally) still be a "less than sign" and not a
#       parametized type.
PrimaryExpression
    "(" Expression ")"
    "new" TypeExpression NewFinish
    "construct" QualifiedName
    QualifiedName TypeParameterTypeList-opt
    LambdaExpression
    "_"
    "TODO" TodoFinish-opt
    Literal

LambdaExpression
    LambdaInputs "->" LambdaBody

LambdaInputs
    LambdaParameterName
    LambdaInferredList
    LambdaParameterList

LambdaInferredList
    "(" LambdaParameterNames ")"

LambdaParameterNames
    LambdaParameterName
    LambdaParameterNames "," LambdaParameterName

LambdaParameterList
    "(" LambdaParameters ")"

LambdaParameters
    LambdaParameter
    LambdaParameters "," LambdaParameter

LambdaParameter
    TypeExpression LambdaParameterName

LambdaParameterName
    _
    Name

LambdaBody
    ElvisExpression
    StatementBlock

TodoFinish
    InputCharacter-not-"(" InputCharacters LineTerminator
    "(" Expression ")"

Literal
    IntLiteral
    FPDecimalLiteral
    FPBinaryLiteral
    CharLiteral
    StringLiteral
    BinaryLiteral
    TupleLiteral
    ListLiteral
    MapLiteral
    CustomLiteral

StringLiteral
    "\"" CharacterString-opt "\""
    FreeformLiteral

# all BinaryLiteral contents must be whitespace or nibbles
BinaryLiteral
    "Binary:{" AcceptableBinaryContent "}"

AcceptableBinaryContent
    StringLiteral
    FreeformLiteral
    Nibbles

Nibbles
    Nibble
    Nibbles Nibble

Nibble: one of ...
    "0" "1" "2" "3" "4" "5" "6" "7" "8" "9" "A" "a" "B" "b" "C" "c" "D" "d" "E" "e" "F" "f"

TupleLiteral
    "(" ExpressionList "," Expression ")"
    "Tuple" NoWhitespace TypeParameterTypeList-opt NoWhitespace ":(" ExpressionList-opt ")"
    "Tuple" NoWhitespace TypeParameterTypeList-opt NoWhitespace ":{" ExpressionList-opt "}"

ListLiteral
    "{" ExpressionList-opt "}"
    "List" NoWhitespace TypeParameterTypeList-opt NoWhitespace ":{" ExpressionList-opt "}"

MapLiteral
    "Map" NoWhitespace TypeParameterTypeList-opt NoWhitespace ":{" Entries-opt "}"

Entries
    Entry
    Entries "," Entry

Entry
    Expression "=" Expression

CustomLiteral
    TypeExpression NoWhitespace ":{" Expression "}"


# (deferred idea)
#   ╔═════════════════════╗
#   ║This could be any    ║
#   ║freeform text that   ║
#   ║could be inside of an║
#   ║Ecstasy source file  ║
#   ╚═════════════════════╝
#
#        U+2550
# U+2554 ╔═════╗ U+2557
# U+2551 ║     ║ U+2551
# U+255A ╚═════╝ U+255D
#        U+2550
#
#
#        U+2500
# U+256D ╭─────╮ U+256E
# U+2502 │     │ U+2502
# U+2570 ╰─────╯ U+256F
#        U+2500
#
FreeformLiteral
    FreeformTop FreeformLines FreeformBottom

FreeformTop
    Whitespace-opt FreeformUpperLeft NoWhitespace FreeformHorizontals NoWhitespace FreeformUpperRight Whitespace-opt LineTerminator

FreeformLines
    FreeformLine
    FreeformLines FreeformLine

FreeformLine
    Whitespace-opt FreeformVertical FreeformChars FreeformVertical Whitespace-opt LineTerminator

FreeformChars
    FreeformChar
    FreeformChars FreeformChars

FreeformChar
    InputCharacter except FreeFormReserved or LineTerminator

FreeformBottom
    Whitespace-opt FreeformLowerLeft NoWhitespace FreeformHorizontals NoWhitespace FreeformLowerRight

FreeFormReserved
    FreeformUpperLeft
    FreeformUpperRight
    FreeformLowerLeft
    FreeformLowerRight
    FreeformHorizontal
    FreeformVertical

FreeformUpperLeft
    U+250C  ┌
    U+250D  ┍
    U+250E  ┎
    U+250F  ┏
    U+2552  ╒
    U+2553  ╓
    U+2554  ╔
    U+256D  ╭

FreeformUpperRight
    U+2510  ┐
    U+2511  ┑
    U+2512  ┒
    U+2513  ┓
    U+2555  ╕
    U+2556  ╖
    U+2557  ╗
    U+256E  ╮

FreeformLowerLeft
    U+2514  └
    U+2515  ┕
    U+2516  ┖
    U+2517  ┗
    U+2558  ╘
    U+2559  ╙
    U+255A  ╚
    U+2570  ╰

FreeformLowerRight
    U+2518  ┘
    U+2519  ┙
    U+251A  ┚
    U+251B  ┛
    U+255B  ╛
    U+255C  ╜
    U+255D  ╝
    U+256F  ╯

FreeformHorizontals
    FreeformHorizontal
    FreeformHorizontals NoWhitespace FreeformHorizontal

FreeformHorizontal
    U+2500  ─
    U+2501  ━
    U+2504  ┄
    U+2505  ┅
    U+2508  ┈
    U+2509  ┉
    U+254C  ╌
    U+254D  ╍
    U+2550  ═

FreeformVertical
    U+2502  │
    U+2503  ┃
    U+2506  ┆
    U+2507  ┇
    U+250A  ┊
    U+250B  ┋
    U+254E  ╎
    U+254F  ╏
    U+2551  ║

#
# types
#

TypeExpression
    UnionedTypeExpression

UnionedTypeExpression
    IntersectingTypeExpression
    UnionedTypeExpression + IntersectingTypeExpression

IntersectingTypeExpression
    NonBiTypeExpression
    IntersectingTypeExpression | NonBiTypeExpression

NonBiTypeExpression
    "(" TypeExpression ")"
    AnnotatedTypeExpression
    NamedTypeExpression
    FunctionTypeExpression
    NonBiTypeExpression NoWhitespace "?"
    NonBiTypeExpression ArrayDim
    NonBiTypeExpression ArrayIndex
    NonBiTypeExpression "..."
    "conditional" NonBiTypeExpression
    "immutable" NonBiTypeExpression
    "TODO" TodoFinish-opt

AnnotatedTypeExpression
    Annotation TypeExpression

NamedTypeExpression
    QualifiedName TypeParameterTypeList-opt

FunctionTypeExpression
    "function" ReturnList FunctionTypeFinish

FunctionTypeFinish
    Name ParameterTypeList
    ParameterTypeList Name

ParameterTypeList
    "(" TypeExpressionList-opt ")"
