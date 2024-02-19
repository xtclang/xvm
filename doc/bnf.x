
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

# note: the "_" argument allows functions to specify arguments that they are NOT binding
ArgumentExpression
    "_"
    "<" ExtendedTypeExpression ">" "_"
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
    "extends" ExtendedTypeExpression

TypeParameterTypeList
    "<" TypeExpressionList ">"

TypeExpressionList
    TypeExpressionListElement
    TypeExpressionList "," TypeExpressionListElement

TypeExpressionListElement
    TypeParameterTypeList                                       # indicates a "type sequence type"
    ExtendedTypeExpression

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
    "extends" ExtendsList
    "implements" ImplementsList
    "delegates" DelegatesList
    "incorporates" IncorporatesList
    "into" AnyTypeExpression
    "import" ImportModifier-opt QualifiedName VersionRequirement-opt ResourceProvider-opt
    "default" "(" Expression ")"

ExtendsList
    ExtendsSingle
    ExtendsList "," ExtendsSingle

ExtendsSingle
    TypeExpression ArgumentList-opt

ImplementsList
    ImplementsSingle
    ImplementsList "," ImplementsSingle

ImplementsSingle
    ExtendedTypeExpression

DelegatesList
    DelegatesSingle
    DelegatesList "," DelegatesSingle

DelegatesSingle
    AnyTypeExpression "(" Expression ")"

IncorporatesList
    IncorporatesSingle
    IncorporatesList "," IncorporatesSingle

IncorporatesSingle
    "conditional" QualifiedName TypeParameterList ArgumentList-opt
    TypeExpression ArgumentList-opt

ImportModifier
    "embedded"
    "required"
    "desired"
    "optional"

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
    Versions "," Version

Version
    VersionLiteral

ResourceProvider
    ResourceList-opt "using" NamedTypeExpression

ResourceList
    "inject" "(" ResourceListContents-opt ")"

ResourceListContents
    Resources ","-opt

Resources
    Resource
    Resources "," Resource

Resource
    TypeExpression ResourceFinish

ResourceFinish
    Name
    "_"

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
    PropertyDeclarationStatement
    MethodDeclarationStatement

#
# properties
#

PropertyDeclarationStatement
    PropertyModifiers-opt TypeExpression Name PropertyDeclarationFinish

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
    ";"
    PropertyDeclarationInitialValue
    "." Name Parameters MethodBody PropertyDeclarationInitialValue-opt
    TypeCompositionBody PropertyDeclarationInitialValue-opt

PropertyDeclarationInitialValue
    "=" Expression ";"

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
    "(" MultiReturnList ")"

SingleReturnList
    TypeExpression

MultiReturnList
    MultiReturn
    MultiReturnList "," MultiReturn

MultiReturn
    TypeExpression Name-opt

RedundantReturnSpecifier
    "<" TypeExpressionList ">"

MethodDeclarationFinish
    ";"
    "=" Expression ";"
    StatementBlock

#
# statements
#

# note: not explicitly spelling out the grammar necessary to avoid the dangling "else" problem, but
#       the approach is identical to C/Java in that the parser greedily looks for an else, causing
#       the else to be associated with the inner-most "if" that is in the parse stack
# note: no "empty statement"
Statement
    TypeCompositionStatement
    PropertyDeclarationStatement        # note: always "static" or "private"
    MethodDeclarationStatement
	VariableDeclaration ";"
	Assignment ";"
    LabeledStatement
    AssertStatement
    BreakStatement
    ContinueStatement
    DoStatement
    ForStatement
    IfStatement
	ImportStatement
	ReturnStatement
    SwitchStatement
    TryStatement
	TypeDefStatement
    UsingStatement
    WhileStatement
    StatementBlock
	Expression ";"    # for parsing purposes (compilation will only allow specific expression forms)

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
    "?="                // assigns only if the RVal is not null (also used in conditional statements e.g. "if" to produce conditional False for Null)

# whitespace is documented here to differentiate from the left hand side type name using ":"+access
LabeledStatement
    Name ":" Whitespace Statement

AssertStatement
    AssertInstruction ConditionList-opt AssertMessage-opt ";"

AssertInstruction                               # (when active, what gets thrown)
    "assert"                                    # runtime, IllegalState
    "assert:arg"                                # runtime, IllegalArgument
    "assert:bounds"                             # runtime, OutOfBounds
    "assert:TODO"                               # runtime, NotImplemented
    "assert:once"                               # runtime, Assertion (only tested "the first time")
    "assert:rnd(" Expression ")"                # runtime (sampling), IllegalState
    "assert:test"                               # test mode (e.g. CI/QC), Assertion
    "assert:debug"                              # debug mode, breakpoint-only (i.e. no throw)

AssertMessage
    "as" Expression

ForStatement
    "for" "(" ForCondition ")" StatementBlock

ForCondition
    VariableInitializationList-opt ";" ConditionList-opt ";" VariableModificationList-opt
    OptionalDeclaration ":" Expression
    "(" OptionalDeclarationList "," OptionalDeclaration ")" ":" Expression

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

ElseStatement
    "else" IfStatement
    "else" StatementBlock

DoStatement
    "do" StatementBlock "while" "(" ConditionList ")" ";"

WhileStatement
    "while" "(" ConditionList ")" StatementBlock

ConditionList
    Condition
    ConditionList "," Condition

Condition
    ConditionalAssignmentCondition
    "!" "(" ConditionalAssignmentCondition ")"
    Expression

ConditionalAssignmentCondition
    OptionalDeclaration ConditionalAssignmentOp Expression
    "(" OptionalDeclarationList "," OptionalDeclaration ")" ConditionalAssignmentOp Expression

ConditionalAssignmentOp
    ":="
    "?="

ImportStatement
    "import" QualifiedName ImportFinish

ImportFinish
    ";"
    "as" Name ";"
    NoWhitespace ".*" ";"

ReturnStatement
    "return" ReturnValue-opt ";"

ReturnValue
    TupleLiteral
    ExpressionList

SwitchStatement
    "switch" "(" SwitchCondition ")" "{" SwitchBlocks "}"

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
#    (b) a Range of that type; or
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
    "try" TryResources-opt StatementBlock TryFinish

TryResources
    "(" VariableInitializationList ")"

TryFinish
    Catches
    Catches-opt "finally" StatementBlock

Catches
    Catch
    Catches Catch

Catch
    "catch" "(" TypeExpression Name ")" StatementBlock

UsingResources
    UsingResource
    UsingResources "," UsingResource

UsingResource
    OptionalDeclaration "=" Expression
    "(" OptionalDeclarationList "," OptionalDeclaration ")" "=" Expression
    Expression                                          # implicitly "val _ = Expression"

UsingStatement
    "using" "(" UsingResources ")" StatementBlock

TypeDefStatement
    "typedef" AnyTypeExpression "as" Name ";"

#
# expressions
#

#   Operator        Description             Level   Associativity
#   --------------  ----------------------  -----   -------------
#   &               reference-of              1
#   ->              lambda
#
#   ++              post-increment            2     left to right
#   --              post-decrement
#   ()              invoke a method
#   []              access array element
#   ?               conditional
#   .               access object member
#   .new            postfix object creation
#   .as             postfix type assertion
#   .is             postfix type comparison
#
#   ++              pre-increment             3     right to left
#   --              pre-decrement
#   +               unary plus
#   -               unary minus
#   !               logical NOT
#   ~               bitwise NOT
#
#   ?:              conditional elvis         4     right to left
#
#   *               multiplicative            5     left to right
#   /
#   %               (modulo)
#   /%              (divide with remainder)
#
#   +               additive                  6     left to right
#   -
#
#   << >>           bitwise                   7     left to right
#   >>>
#   &  &!
#   ^
#   |
#
#   ..              range/interval            8     left to right
#
#   <-              assignment                9     right to left
#
#   <  <=           relational               10     left to right
#   >  >=
#   <=>             order ("star-trek")
#
#   ==              equality                 11     left to right
#   !=
#
#   &&              conditional AND          12     left to right
#
#   ^^              conditional XOR          13     left to right
#   ||              conditional OR
#
#   ? :             conditional ternary      14     right to left
#
#   :               conditional ELSE         15     right to left

Expression
    ElseExpression

ElseExpression
    TernaryExpression
    TernaryExpression ":" ElseExpression

# whitespace is documented here to differentiate from the conditional expression of the form "e?"
TernaryExpression
    OrExpression
    OrExpression Whitespace "?" TernaryExpression ":" TernaryExpression

OrExpression
    AndExpression
    OrExpression "||" AndExpression
    OrExpression "^^" AndExpression

AndExpression
    EqualityExpression
    AndExpression "&&" EqualityExpression

EqualityExpression
    RelationalExpression
    EqualityExpression "==" RelationalExpression
    EqualityExpression "!=" RelationalExpression

RelationalExpression
    AssignmentExpression
    AssignmentExpression "<=>" AssignmentExpression
    RelationalExpression "<"   AssignmentExpression
    RelationalExpression "<="  AssignmentExpression
    RelationalExpression ">"   AssignmentExpression
    RelationalExpression ">="  AssignmentExpression

AssignmentExpression
    RangeExpression
    RangeExpression "<-" AssignmentExpression

RangeExpression
    BitwiseExpression
    RangeExpression ".." BitwiseExpression

BitwiseExpression
    AdditiveExpression
    BitwiseExpression "<<"  AdditiveExpression
    BitwiseExpression ">>"  AdditiveExpression
    BitwiseExpression ">>>" AdditiveExpression
    BitwiseExpression "&"   AdditiveExpression
    BitwiseExpression "^"   AdditiveExpression
    BitwiseExpression "|"   AdditiveExpression

AdditiveExpression
    MultiplicativeExpression
    AdditiveExpression "+" MultiplicativeExpression
    AdditiveExpression "-" MultiplicativeExpression

MultiplicativeExpression
    ElvisExpression
    MultiplicativeExpression "*"  ElvisExpression
    MultiplicativeExpression "/"  ElvisExpression
    MultiplicativeExpression "%"  ElvisExpression
    MultiplicativeExpression "/%" ElvisExpression

ElvisExpression
    PrefixExpression
    PrefixExpression "?:" ElvisExpression

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
    PostfixExpression ArrayDims             # TODO REVIEW - is this correct? (does it imply that the expression is a type expression?)
    PostfixExpression ArrayIndexes
    PostfixExpression NoWhitespace "?"
    PostfixExpression "." "&"-opt DotNameFinish
    PostfixExpression ".new" NewFinish
    PostfixExpression ".as" "(" AnyTypeExpression ")"
    PostfixExpression ".is" "(" AnyTypeExpression ")"

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

DotNameFinish
    Name TypeParameterTypeList-opt
    "default"

NewFinish
    TypeExpression NewArguments AnonClassBody-opt
    ArgumentList

NewArguments
    ArrayIndexes ArgumentList-opt
    ArgumentList


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
    "new" NewFinish
    "&"-opt "construct"-opt QualifiedName TypeParameterTypeList-opt
    StatementExpression
    SwitchExpression
    LambdaExpression
    "_"
    Literal

AnonClassBody
    "{" TypeCompositionComponents "}"

# a "block expression" is a lambda with an implicit "()->" preamble and with an implicit "()"
# trailing invocation, i.e. it is a block of statements that executes, and at the end, it must
# return a value (unlike a "naked" lambda, it can not just be an expression)
StatementExpression
    StatementBlock                  # a "block expression"
    "throw" TernaryExpression       # non-completing
    "TODO" TodoFinish-opt           # non-completing
    "assert"                        # non-completing

SwitchExpression
    "switch" "(" SwitchCondition-opt ")" "{" SwitchExpressionBlocks "}"

SwitchExpressionBlocks
    SwitchExpressionBlock
    SwitchExpressionBlocks SwitchExpressionBlock

SwitchExpressionBlock
    SwitchLabels ExpressionList ";"

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
    "_"
    Name

LambdaBody
    Expression
    StatementBlock

TodoFinish
    InputCharacter-not-"(" InputCharacters LineTerminator
    NoWhitespace "(" Expression-opt ")"

Literal
    TypedNumericLiteral
    IntLiteral                                                  # defined in language spec
    FPDecimalLiteral                                            # defined in language spec
    FPBinaryLiteral                                             # defined in language spec
    CharLiteral                                                 # defined in language spec
    StringLiteral
    BinaryLiteral
    TupleLiteral
    ListLiteral
    MapLiteral
    VersionLiteral
    DateLiteral
    TimeOfDayLiteral
    TimeLiteral
    TimeZoneLiteral
    DurationLiteral
    PathLiteral
    FileLiteral
    DirectoryLiteral
    FileStoreLiteral

TypedNumericLiteral:
    IntTypeName ":" IntLiteral
    FPTypeName ":" FPLiteral

FPLiteral:
    IntLiteral
    FPDecimalLiteral
    FPBinaryLiteral

IntTypeName:
    "Int"
    "Int8"
    "Int16"
    "Int32"
    "Int64"
    "Int128"
    "IntN"
    "Byte"
    "UInt"
    "UInt8"
    "UInt16"
    "UInt32"
    "UInt64"
    "UInt128"
    "UIntN"

FPTypeName:
    "Dec"
    "Dec32"
    "Dec64"
    "Dec128"
    "DecN"
    "Float8e4"
    "Float8e5"
    "BFloat16"
    "Float16"
    "Float32"
    "Float64"
    "Float128"
    "FloatN"

StringLiteral
    '"' CharacterString-opt '"'
    '$"' CharacterString-opt '"'
    "\|" FreeformLiteral
    "$|" FreeformLiteral
    "$" NoWhitespace File                                       # value is String contents of file

FreeformLiteral
    FreeformChars LineTerminator FreeformLines-opt

FreeformLines
    FreeformLine
    FreeformLines FreeformLine

FreeformLine
    Whitespace-opt "|" FreeformChars LineTerminator

FreeformChars
    FreeformChar
    FreeformChars FreeformChar

FreeformChar
    InputCharacter except LineTerminator

BinaryLiteral
    "#" NoWhitespace Hexits                                     # "Hexits" defined in language spec
    "#|" FreeformLiteral                                        # containing only Hexits and whitespace
    "#" NoWhitespace File                                       # file to include as binary data

TupleLiteral
    "(" ExpressionList "," Expression ")"                       # compile/runtime type is Tuple
    TypeExpression NoWhitespace ":" "(" ExpressionList-opt ")"  # type must be a Tuple

CollectionLiteral
    "[" ExpressionList-opt "]"                                  # compile/runtime type is Array
    TypeExpression NoWhitespace ":" "[" ExpressionList-opt "]"  # type must be Collection, Set, List, or Array

MapLiteral
    "[" Entries-opt "]"                                         # compile/runtime type is Map
    TypeExpression NoWhitespace ":" "[" Entries-opt "]"         # type must be Map

Entries
    Entry
    Entries "," Entry

Entry
    Expression "=" Expression

VersionLiteral
    "Version:" NoWhitespace VersionString
    "v:" NoWhitespace VersionString

VersionString
    NonGASuffix
    VersionNumbers NoWhitespace VersionFinish-opt NoWhitespace Build-opt

VersionNumbers
    DigitsNoUnderscores
    VersionNumbers NoWhitespace "." NoWhitespace DigitsNoUnderscores

VersionFinish:
     "-" NoWhitespace NonGASuffix
     "." NoWhitespace NonGASuffix
     NonGASuffix

NonGASuffix
      NonGAPrefix NoWhitespace NonGAVersion-opt

NonGAVersion
    "-" NoWhitespace DigitsNoUnderscores
    "." NoWhitespace DigitsNoUnderscores
    DigitsNoUnderscores

Build
    "+" NoWhitespace BuildChars

BuildChars
    BuildChar
    BuildChars BuildChar

BuildChar
    "0".."9"
    "A".."Z"
    "a".."z"
    "-"
    "."

NonGAPrefix:        # note: not (!!!) case sensitive
    "dev"           # developer build (default compiler stamp)
    "ci"            # continuous integration build (automated build, automated test)
    "qc"            # build selected for internal Quality Control
    "alpha"         # build selected for external alpha test (pre-release)
    "beta"          # build selected for external beta test (pre-release)
    "rc"            # build selected as a release candidate (pre-release; GA pending)

DateLiteral
    "Date:" Digit Digit Digit Digit "-" Digit Digit "-" Digit Digit         # NoWhitespace

TimeOfDayLiteral
    "TimeOfDay:" Digit Digit ":" Digit Digit Seconds-opt                    # NoWhitespace

Seconds
     ":" Digit Digit SecondsFraction-opt                                    # NoWhitespace

SecondsFraction
     "." NoWhitespace Digits

# with NoWhitespace
TimeLiteral
    "Time:" Digit Digit Digit Digit "-" Digit Digit "-" Digit Digit "T" Digit Digit ":" Digit Digit Seconds-opt TimeZone-opt

TimeZoneLiteral
    "TimeZone:" NoWhitespace TimeZone

TimeZone
    "Z"
    "+" NoWhitespace Digit NoWhitespace Digit NoWhitespace MinutesOffset-opt
    "-" NoWhitespace Digit NoWhitespace Digit NoWhitespace MinutesOffset-opt

MinutesOffset
    ":" NoWhitespace Digit NoWhitespace Digit

# using ISO 8601 "PnYnMnDTnHnMnS" format, with NoWhitespace
DurationLiteral
    "Duration:P" YearsDuration-opt MonthsDuration-opt DaysDuration-opt TimeDuration-opt

TimeDuration
     "T" NoWhitespace HoursDuration-opt NoWhitespace MinutesDuration-opt NoWhitespace SecondsDuration-opt

YearsDuration
    DigitsNoUnderscores NoWhitespace "Y"

MonthsDuration
    DigitsNoUnderscores NoWhitespace "M"

DaysDuration
    DigitsNoUnderscores NoWhitespace "D"

HoursDuration
    DigitsNoUnderscores NoWhitespace "H"

MinutesDuration
    DigitsNoUnderscores NoWhitespace "M"

SecondsDuration
    DigitsNoUnderscores NoWhitespace "S"

PathLiteral
    "Path:" NoWhitespace Dir NoWhitespace PathName-opt

FileLiteral
    "File:"-opt NoWhitespace File

DirectoryLiteral
    "Directory:"-opt NoWhitespace Dir

FileStoreLiteral
    "FileStore:" NoWhitespace Dir NoWhitespace PathName-opt

# Dir and File paths are not intended to support all possible directory and file names -- just the
# ones likely to actually occur in the real world; names in a File are NOT permitted to end
# with a dot, contain 2 dots in a row, contain spaces, etc.
File
    Dir NoWhitespace PathName

Dir
    "/" NoWhitespace DirElements-opt
    "./" NoWhitespace DirElements-opt
    "../" NoWhitespace DirElements-opt

DirElements
    DirElement
    DirElements NoWhitespace DirElement

DirElement
    "../"
    PathName NoWhitespace "/"

PathName
    "."-opt NoWhitespace PathNameParts          # allows UNIX-style hidden files, e.g. ".gitignore"

PathNameParts
    PathNamePart
    PathNameParts NoWhitespace PathNameSpecial NoWhitespace PathNamePart

PathNamePart
    IdentifierTrails

PathNameSpecial
    "."
    "-"

IdentifierTrails
    IdentifierTrail
    IdentifierTrails IdentifierTrail

IdentifierTrail
    # defined by the Ecstasy spec as Unicode categories Lu Ll Lt Lm Lo Mn Mc Me Nd Nl No Sc plus U+005F

#
# types
#

TypeExpression
    IntersectingTypeExpression

ExtendedTypeExpression
    ExtendedIntersectingTypeExpression

AnyTypeExpression
    ExtendedTypeExpression

# '+' creates an intersection of two types; '-' creates a difference of two types
IntersectingTypeExpression
    UnionedTypeExpression
    IntersectingTypeExpression "+" UnionedTypeExpression
    IntersectingTypeExpression "-" UnionedTypeExpression

ExtendedIntersectingTypeExpression
    ExtendedUnionedTypeExpression
    ExtendedIntersectingTypeExpression "+" ExtendedUnionedTypeExpression
    ExtendedIntersectingTypeExpression "-" ExtendedUnionedTypeExpression

UnionedTypeExpression
    PrefixTypeExpression
    UnionedTypeExpression "|" PrefixTypeExpression

ExtendedUnionedTypeExpression
    ExtendedPrefixTypeExpression
    ExtendedUnionedTypeExpression "|" ExtendedPrefixTypeExpression

PrefixTypeExpression
    "immutable"-opt Annotations-opt PostfixTypeExpression

ExtendedPrefixTypeExpression
    "immutable"-opt TypeAccessModifier-opt Annotations-opt ExtendedPostfixTypeExpression

TypeAccessModifier
    "struct"
    AccessModifier

PostfixTypeExpression
    PrimaryTypeExpression
    PostfixTypeExpression NoWhitespace "?"
    PostfixTypeExpression ArrayDims
    PostfixTypeExpression ArrayIndexes           # ArrayIndexes is not consumed by this construction

ExtendedPostfixTypeExpression
    ExtendedPrimaryTypeExpression
    ExtendedPostfixTypeExpression NoWhitespace "?"
    ExtendedPostfixTypeExpression ArrayDims
    ExtendedPostfixTypeExpression ArrayIndexes   # ArrayIndexes is not consumed by this construction

PrimaryTypeExpression
    "(" ExtendedTypeExpression ")"
    NamedTypeExpression
    FunctionTypeExpression

ExtendedPrimaryTypeExpression
    "(" ExtendedTypeExpression ")"
    NamedTypeExpression
    FunctionTypeExpression
    AnonTypeExpression
    "const"
    "enum"
    "module"
    "package"
    "service"
    "class"

NamedTypeExpression
    NamedTypeExpressionPart
    NamedTypeExpression "." Annotations-opt NamedTypeExpressionPart

NamedTypeExpressionPart
    QualifiedName NoAutoNarrowModifier-opt TypeParameterTypeList-opt TypeValueSet-opt

NoAutoNarrowModifier
    NoWhitespace "!"

TypeValueSet
    "{" TypeValueList "}"

TypeValueList
    TypeValue
    TypeValueList "," TypeValue

# 1. the expression must be a "constant expression", i.e. compiler has to be able to determine the
#    value (or a constant that points to a value that is constant at run-time)
# 2. the '!' aka "not" operator is permitted; it indicates exclusion from the set
# 3. the '..' aka "range" operator is permitted; it indicates a range of values; when combined with
#    the '!' operator, precedence rules require the range to be inside parens, e.g. "!(v1..v2)"
TypeValue
    Expression

AnonTypeExpression
    "{" NameOrSignatureList ";" "}"             # note: at least one name or signature is required

NameOrSignatureList
    NameOrSignature
    NameOrSignatureList ";" NameOrSignature

NameOrSignature
    Name                                                                    # ref to 1+ property/method
    PropertyModifiers-opt TypeExpression Name                               # property
    MethodModifiers-opt TypeParameterList-opt MethodIdentity ParameterList  # method

# Note: in the case that the name precedes the ParameterTypeList, the token
#       stream is re-ordered such that the name is deposited into the stream
#       after the ParameterTypeList, and is not consumed by this construction
FunctionTypeExpression
    "function" "conditional"-opt ReturnList Name-opt "(" TypeExpressionList-opt ")"