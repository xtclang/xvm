
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
    "<" TypeExpression ">" "?"
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

#
# compilation unit
#

CompilationUnit
	ImportStatements-opt TypeCompositionStatement

ImportStatements
	ImportStatement
	ImportStatements ImportStatement

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
    "mixin"

Compositions
    ConditionalComposition
    Compositions ConditionalComposition

# while parsing is of a generic Expression, there are only a few expression forms that are permitted:
# 1. StringLiteral "." "defined"
# 2. QualifiedName "." "present"
# 3. QualifiedName "." "versionMatches" "(" VersionLiteral ")"
# 4. Any of 1-3 and 5 negated using "!"
# 5. Any two of 1-5 combined using "&", "&&", "|", or "||"
ConditionalComposition
    IfComposition
    Composition

IfComposition
    "if" "(" Expression ")" "{" Compositions "}" ElseComposition-opt

ElseComposition
    "else" IfComposition
    "else" "{" Compositions "}"

Composition
    "extends" TypeExpression ArgumentList-opt
    "implements" TypeExpression
    "delegates" TypeExpression "(" Expression ")"
    "incorporates" IncorporatesFinish
    "into" TypeExpression
    ImportClause QualifiedName VersionRequirement-opt
    "default" "(" Expression ")"

IncorporatesFinish
    "conditional" QualifiedName TypeParameterList ArgumentList-opt
    TypeExpression ArgumentList-opt

ImportClause
    "import"
    "import:embedded"
    "import:required"
    "import:desired"
    "import:optional"

VersionRequirement
    Version VersionOverrides-opt

VersionOverrides
    VersionOverride
    VersionOverrides "," VersionOverride

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
    "v:" NoWhitespace StringLiteral

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

# note: EnumBody is only valid (and is not actually optional) for the "enum" category, but that
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
    ConditionalTypeCompositionComponent
    TypeCompositionComponents ConditionalTypeCompositionComponent

ConditionalTypeCompositionComponent
    IfTypeCompositionComponent
    TypeCompositionComponent

# see special notes on ConditionalComposition related to the Expression
IfTypeCompositionComponent
    "if" "(" Expression ")" "{" TypeCompositionComponents "}" ElseTypeCompositionComponent-opt

ElseTypeCompositionComponent
    "else" IfTypeCompositionComponent
    "else" "{" TypeCompositionComponents "}"

TypeCompositionComponent
    AccessModifier-opt TypeDefStatement
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
    MethodModifiers-opt TypeParameterList-opt MethodIdentity ParameterList MethodDeclarationFinish

MethodModifiers
    MethodModifier
    MethodModifiers MethodModifier

MethodModifier
    Modifier
    Annotation

MethodIdentity
    "construct"
    "conditional"-opt ReturnList Name RedundantReturnSpecifier-opt

ReturnList
    "void"
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
	VariableDeclaration ";"
	Assignment ";"
    LabeledStatement
    AssertStatement
    BreakStatement
    ContinueStatement
    "do" StatementBlock "while" "(" ConditionList ")" ";"
    ForStatement
    IfStatement
	ImportStatement
	ReturnStatement
    SwitchStatement
    TryStatement
	TypeDefStatement
    "using" ResourceDeclaration StatementBlock
    "while" "(" ConditionList ")" StatementBlock
    WithStatement
    StatementBlock
	Expression ";"      // for parsing purposes (compilation will only allow specific expression forms)

PropertyDeclarationStatement
    "static" TypeExpression Name PropertyDeclarationFinish-opt

StatementBlock
    "{" Statements "}"

Statements
    Statement
    Statements Statement

VariableDeclaration
    VariableTypeExpression Name VariableInitializerFinish-opt
    "(" OptionalDeclarationList "," OptionalDeclaration ")" "=" Expression

VariableInitializerFinish
    "=" Expression

OptionalDeclarationList
    OptionalDeclaration
    OptionalDeclarationList "," OptionalDeclaration

OptionalDeclaration
    Assignable
    VariableTypeExpression Name

VariableTypeExpression
    "val"
    "var"
    TypeExpression

Assignment
    Assignee AssignmentOperator Expression

Assignee
    Assignable
    "(" AssignableList "," Assignable ")"

AssignableList
    Assignable
    AssignableList "," Assignable

# Assignable turns out to be just an Expression that meets certain requirements, i.e. one that ends
# with a Name or an ArrayIndexes
Assignable
    Name
    TernaryExpression "." Name
    TernaryExpression ArrayIndexes

AssignmentOperator
    "="                 // straight assignment
    "*="                // multiply-assign
    "/="                // divide-assign
    "%="                // modulo-assign
    "+="                // add-assign
    "-="                // subtract-assign
    "<<="               // shift-left-assign
    ">>="               // shift-right-assign
    ">>>="              // unsigned-shift-right-assign
    "&="                // and-assign
    "&&="               // and-assign (short-circuiting)
    "|="                // or-assign
    "||="               // or-assign (short-circuiting)
    "^="                // xor-assign
    "?:="               // elvis-assign (assigns only if the LVal is null)
    ":="                // conditional assign (RVal must be @Conditional; assigns starting with 2nd tuple field iff expression is true)

LabeledStatement
    Name ":" Statement

AssertStatement
    AssertInstruction ConditionList-opt ";"

AssertInstruction
    "assert"
    "assert:once"
    "assert:test"
    "assert:debug"
    "assert:always"

ForStatement
    "for" "(" ForCondition ")" StatementBlock

ForCondition
    VariableInitializationList-opt ";" ConditionList-opt ";" VariableModificationList-opt
    OptionalDeclaration ":" Expression
    (OptionalDeclarationList, OptionalDeclaration) ":" Expression

VariableInitializationList
    VariableInitializer
    VariableInitializationList "," VariableInitializer

VariableInitializer
    OptionalDeclaration "=" Expression
    "(" OptionalDeclarationList "," OptionalDeclaration ")" "=" Expression

VariableModificationList
    VariableModification
    VariableModificationList "," VariableModification

VariableModification
    Assignment
    Expression    # note: expression must have side-effects (i.e. not a constant)

IfStatement
    "if" "(" ConditionList ")" StatementBlock ElseStatement-opt

ConditionList
    Condition
    ConditionList, Condition

Condition
    TernaryExpression
    OptionalDeclaration ":" Expression
    ( OptionalDeclarationList, OptionalDeclaration ) ":" Expression

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
    switch "(" SwitchCondition-opt ")" "{" SwitchBlocks "}"

SwitchCondition
    SwitchConditionExpression
    SwitchCondition "," SwitchConditionExpression

SwitchConditionExpression
    VariableInitializer
    Expression

SwitchBlocks
    SwitchBlock
    SwitchBlocks SwitchBlock

# the SwitchBlockFinish is required unless the SwitchBlock does not complete (e.g. ends with a "throw")
SwitchBlock
    SwitchLabels Statements SwitchBlockFinish-opt

SwitchLabels
    SwitchLabel
    SwitchLabels SwitchLabel

# 1) for a SwitchStatement with a SwitchCondition, each "case" expression must be a
#    "constant expression", i.e. compiler has to be able to determine the value (or a constant that
#    points to a value that is constant at run-time, e.g. a property constant for a static property)
# 2) for a SwitchStatement without a SwitchCondition, each "case" expression must be of type Boolean
#    and is not required to be a constant
# 3) for a SwitchStatement with a SwitchCondition, a case may specify a list of values, which is
#    semantically identical to having that same number of "case" labels each with one of those values.
# 4) for a SwitchStatement with multiple SwitchConditionExpressions in the SwitchCondition or with
#    a single SwitchConditionExpression of a tuple type, each "case" value must be either:
#    (a) a parenthesized list of expressions (a compatible tuple constant), or
#    (b) a constant expression of a compatible tuple type
# 5) each "case" expression may be any of:
#    (a) the type of the corresponding expression (or tuple field value) in the SwitchCondition;
#    (b) an Interval of that type; or
#    (c) the wild-card "_" (compiled as the "blackhole" constant)
#    a CaseExpressionList of all wild-cards is semantically equivalent to the use of a "default"
#    label, and would predictably conflict with the same if both were specified.
SwitchLabel
    "case" CaseOptionList ":"
    "default" ":"

SwitchBlockFinish:
    BreakStatement
    ContinueStatement

BreakStatement:
    "break" Name-opt ";"

ContinueStatement:
    "continue" Name-opt ";"

CaseOptionList:
    CaseOption
    CaseOptionList "," CaseOption

CaseOption:
    "(" CaseExpressionList "," CaseExpression ")"
    SafeCaseExpression

CaseExpressionList:
    CaseExpression
    CaseExpressionList "," CaseExpression

CaseExpression:
    "_"
    Expression

# parse for "case TernaryExpression:" because Expression parsing looks for a possible trailing ':'
SafeCaseExpression:
    "_"
    TernaryExpression

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
    "typedef" TypeExpression "as"-opt Name ";"

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
#   .is             postfix type comparison
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
#   /%
#
#   +               additive                  4     left to right
#   -
#
#   << >>           shift                     5     left to right
#   >>>
#
#   ..              range/interval            6     left to right
#
#   <  <=           relational                7     left to right
#   >  >=
#   <=>             order
#   as              type assertion
#   is              type comparison
#   instanceof      type comparison
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
    RelationalExpression "is" TypeExpression

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

# see comment on primary expression to understand why type parameter list needs to be parsed as part
# of name
PostfixExpression
    PrimaryExpression
    PostfixExpression "++"
    PostfixExpression "--"
    PostfixExpression "(" Arguments-opt ")"
    PostfixExpression ArrayDims                                 # TODO REVIEW - is this correct? (does it imply that the expression is a type expression?)
    PostfixExpression ArrayIndexes
    PostfixExpression NoWhitespace "?"
    PostfixExpression "." "&"-opt Name TypeParameterTypeList-opt
    PostfixExpression ".new" TypeExpression "(" Arguments-opt ")"
    PostfixExpression ".as" "(" TypeExpression ")"
    PostfixExpression ".is" "(" TypeExpression ")"

ArrayDims
    "[" DimIndicators-opt "]"

DimIndicators
    DimIndicator
    DimIndicators "," DimIndicator

DimIndicator
    "?"

ArrayIndexes
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
    "new" TypeExpression "(" Arguments-opt ")" AnonClassBody-opt
    "throw" TernaryExpression
    "T0D0" TodoFinish-opt
    "&"-opt "construct"-opt QualifiedName TypeParameterTypeList-opt
    StatementExpression
    SwitchExpression
    LambdaExpression
    "_"
    Literal

AnonClassBody
    "{" TypeCompositionComponents "}"

# a statement expression is a lambda with an implicit "()->" preamble and with an implicit "()"
# trailing invocation, i.e. it is a block of statements that executes, and at the end, it must
# returns a value (it can not just be an expression, like lambda would support)
StatementExpression
    StatementBlock

SwitchExpression
    switch "(" SwitchCondition-opt ")" "{" SwitchExpressionBlocks "}"

SwitchExpressionBlocks
    SwitchExpressionBlock
    SwitchExpressionBlocks SwitchExpressionBlock

SwitchExpressionBlock
    SwitchLabels Expression ;

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
    NoWhitespace "(" Expression ")"

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
    VersionLiteral
    CustomLiteral
    # TODO need an external (file) literal

# TODO unformatted text: $"text"
# TODO type literal from text: T:$"text" / T:"text"
StringLiteral
    "$"-opt "\"" CharacterString-opt "\""
    "$"-opt FreeformLiteral

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

# TODO replace "{}" with "[]" in literals?
TupleLiteral
    "(" ExpressionList "," Expression ")"
    "Tuple" NoWhitespace TypeParameterTypeList-opt NoWhitespace ":(" ExpressionList-opt ")"
    "Tuple" NoWhitespace TypeParameterTypeList-opt NoWhitespace ":{" ExpressionList-opt "}"

ListLiteral
    "[" ExpressionList-opt "]"
    "List" NoWhitespace TypeParameterTypeList-opt NoWhitespace ":{" ExpressionList-opt "}"

MapLiteral
    "Map" NoWhitespace TypeParameterTypeList-opt NoWhitespace ":{" Entries-opt "}"

Entries
    Entry
    Entries "," Entry

Entry
    Expression "=" Expression

# TODO version literal from text: Version:"2.0" / v:"2.0"
VersionLiteral
    "v" ":{" Version "}"
    "Version" ":{" Version "}"

CustomLiteral
    TypeExpression NoWhitespace ":{" Expression "}"


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
#           U+2500
# U+256D ╭─────╮ U+256E
# U+2502 │     │ U+2502
# U+2570 ╰─────╯ U+256F
#           U+2500
#
FreeformLiteral
    FreeformTop FreeformLines FreeformBottom

FreeformTop
    Whitespace-opt FreeformUpperLeft NoWhitespace FreeformHorizontals NoWhitespace FreeformUpperRight Whitespace-opt LineTerminator

FreeformLines
    FreeformLine
    FreeformLines FreeformLine

FreeformLine
    Whitespace-opt FreeformVertical FreeformChars FreeformLineEnd

FreeformLineEnd
    FreeformVertical Whitespace-opt LineTerminator
    "\" NoWhitespace LineTerminator

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

# '+' creates a union of two types; '-' creates a difference of two types
UnionedTypeExpression
    IntersectingTypeExpression
    UnionedTypeExpression + IntersectingTypeExpression
    UnionedTypeExpression - IntersectingTypeExpression

IntersectingTypeExpression
    NonBiTypeExpression
    IntersectingTypeExpression | NonBiTypeExpression

NonBiTypeExpression
    "(" TypeExpression ")"
    AnnotatedTypeExpression
    NamedTypeExpression
    FunctionTypeExpression
    NonBiTypeExpression NoWhitespace "?"
    NonBiTypeExpression ArrayDims
    NonBiTypeExpression ArrayIndexes             # ArrayIndexes is not consumed by this construction
    NonBiTypeExpression "..."
    "conditional" NonBiTypeExpression
    "immutable" NonBiTypeExpression

AnnotatedTypeExpression
    Annotation TypeExpression

NamedTypeExpression
    QualifiedName TypeAccessModifier-opt NoAutoNarrowModifier-opt TypeParameterTypeList-opt

TypeAccessModifier
    NoWhitespace ":" NoWhitespace AccessModifier

NoAutoNarrowModifier
    NoWhitespace "!"

# Note: in the case that the name precedes the ParameterTypeList, the token
#       stream is re-ordered such that the name is deposited into the stream
#       after the ParameterTypeList, and is not consumed by this construction
FunctionTypeExpression
    "function" ReturnList Name-opt "(" TypeExpressionList-opt ")"

