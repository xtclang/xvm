
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
    "@" NamedTypeExpression ArgumentList-opt

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

Argument                                        // TODO this does not support named arguments
    Expression

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
    MethodModifiers-opt TypeVariableList-opt NamePrecursor Name ParameterList MethodDeclarationFinish

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

Returns
    Return
    Returns "," Return

Return
    TypeExpression Name-opt

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
    // TODO

VariableDeclarationStatement
    TypeExpression Name VariableDeclarationFinish-opt

VariableDeclarationFinish
    "=" Expression ";"

PropertyDeclarationStatement
    "static" TypeExpression Name PropertyDeclarationFinish-opt

ExpressionStatement
    Expression ";"

ReturnStatement
    "return" TupleExpression-opt ";"
    "return" ExpressionList-opt ";"

#
# expressions
#

TupleExpression
    "Tuple:(" ExpressionList-opt ")"
    "(" ExpressionList "," Expression ")"

ExpressionList
    Expression
    ExpressionList "," Expression

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
    NonBiTypeExpression "[" "]"
    NonBiTypeExpression "..."

AnnotatedTypeExpression
    Annotation TypeExpression

NamedTypeExpression
    "immutable"-opt QualifiedName TypeParameterTypeList-opt

FunctionTypeExpression
    "function" ReturnList FunctionTypeFinish

FunctionTypeFinish
    Name ParameterList
    ParameterList Name
