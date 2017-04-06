
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

# note: not currently implemented // TODO
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
    "<" TypeParameterTypes ">"

TypeParameterTypes
    TypeParameterType
    TypeParameterTypes "," TypeParameterType

TypeParameterType
    TypeExpression

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
	AliasStatements-opt TypeDeclaration

AliasStatements
	AliasStatement
	AliasStatements AliasStatement

AliasStatement
	ImportStatement
	TypeDefStatement

#
# type compositions
#

TypeComposition
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
    TypeComposition
    PropertyDeclaration
    MethodDeclaration
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

MethodDeclaration
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
    "(" Returns ")"

RedundantReturnSpecifier
    "<" Returns ">"

Returns
    Return
    Returns "," Return

Return
    TypeExpression Name-opt                           // TODO name???

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

StatementBlock
    "{" Statements "}"

Statements
    Statement
    Statements Statement

Statement
    StatementBlock
	ImportStatement
	TypeDefStatement
	AssignmentStatement
	VariableDeclarationStatement
	ExpressionStatement
	ReturnStatement

ImportStatement
    "import" QualifiedName ImportAlias-opt ";"

ImportAlias
    "as" Name

TypeDefStatement
    "typedef" TypeExpression Name ";"

AssignmentStatement
    Assignable AssignmentOperator Expression ";"

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

VariableDeclarationStatement
    TypeExpression Name VariableDeclarationFinish-opt

VariableDeclarationFinish
    "=" Expression ";"

PropertyDeclarationStatement
    "static" TypeExpression Name PropertyDeclarationFinish-opt

ExpressionStatement
    Expression ";"

ReturnStatement
    "return" TupleLiteral-opt ";"
    "return" ExpressionList-opt ";"

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
    ElvisExpression Whitespace "?" TernaryExpression ":" TernaryExpression

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
    AndExpression
    BitXorExpression ^ AndExpression

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
    RelationalExpression "instanceof" TypeExpression
    RelationalExpression "as" TypeExpression

RangeExpression
    ShiftExpression
    ShiftExpression ".." ShiftExpression

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
    "new" TypeExpression ArgumentList

PostfixExpression
    PrimaryExpression
    PostfixExpression "++"
    PostfixExpression "--"
    PostfixExpression ArgumentList
    PostfixExpression ArrayDims
    PostfixExpression ArrayIndex
    PostfixExpression NoWhitespace "?"
    PostfixExpression "." Name
    PostfixExpression ".new" ArgumentList
    PostfixExpression ".instanceof" "(" TypeExpression ")"
    PostfixExpression ".as" "(" TypeExpression ")"

# Note: A parenthesized Expression, a TupleLiteral, and a LambdaExpression share a parse path
PrimaryExpression
    Name
    Literal
    LambdaExpression
    "(" Expression ")"
    "TODO" TodoMessage-opt

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
    FreeformLiteral
    CustomLiteral

# Whitespace allowed
BinaryLiteral
    "Binary:{" Nibbles-opt "}"

Nibbles
    Nibble
    Nibbles Nibble

Nibble: one of ...
    "0" "1" "2" "3" "4" "5" "6" "7" "8" "9" "A" "a" "B" "b" "C" "c" "D" "d" "E" "e" "F" "f"

TupleLiteral
    "(" ExpressionList "," Expression ")"
    "Tuple:(" ExpressionList-opt ")"
    "Tuple:{" ExpressionList-opt "}"

ListLiteral
    "{" ExpressionList-opt "}"
    "List:{" ExpressionList-opt "}"

MapLiteral
    "Map:{" Entries-opt "}"

Entries
    Entry
    Entries "," Entry

Entry
    Expression "=" Expression

# (deferred idea)
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
    ╔═════════════════════╗
    ║This could be any    ║
    ║freeform text that   ║
    ║could be inside of an║
    ║Ecstasy source file  ║
    ╚═════════════════════╝

CustomLiteral
    Class NoWhitespace ":" NoWhitespace StringLiteral
    Class NoWhitespace ":{" FreeformLiteral "}"

ExpressionList
    Expression
    ExpressionList "," Expression

TodoMessage
    "(" Expression ")"

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
    Expression
    StatementBlock

#
# types
#

TypeExpression
    UnionedTypeExpression

UnionedTypeExpression
    IntersectingTypeExpression
    IntersectingTypeExpression + IntersectingTypeExpression

IntersectingTypeExpression
    NonBiTypeExpression
    NonBiTypeExpression | NonBiTypeExpression

NonBiTypeExpression
    "(" TypeExpression ")"
    AnnotatedTypeExpression
    NamedTypeExpression
    FunctionTypeExpression
    NonBiTypeExpression "?"
    NonBiTypeExpression ArrayDim
    NonBiTypeExpression "..."
    "conditional" NonBiTypeExpression
    "immutable" NonBiTypeExpression

AnnotatedTypeExpression
    Annotation TypeExpression

NamedTypeExpression
    QualifiedName TypeParameterTypeList-opt

FunctionTypeExpression
    "function" ReturnList FunctionTypeFinish

FunctionTypeFinish
    Name ParameterList
    ParameterList Name
