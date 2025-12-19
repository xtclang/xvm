# Reflective Kotlin DSL for Language Support

## Part 1: kotlinx.ast - How It Works

**kotlinx.ast** is a generic AST (Abstract Syntax Tree) parsing library for Kotlin that uses ANTLR under the hood. Unlike using the Kotlin compiler directly, it provides a clean, multiplatform-compatible way to parse Kotlin source code into a navigable tree structure.

### Core Concepts

The library uses **Klass** - a collection of language-independent data classes to represent AST nodes. This abstraction makes it easy to traverse and query syntax trees.

### Installation

```kotlin
// build.gradle.kts
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.kotlinx.ast:grammar-kotlin-parser-antlr-kotlin:0.1.0")
}
```

### Basic Usage Example

```kotlin
import kotlinx.ast.common.AstSource
import kotlinx.ast.common.ast.Ast
import kotlinx.ast.common.klass.KlassDeclaration
import kotlinx.ast.grammar.kotlin.common.summary
import kotlinx.ast.grammar.kotlin.target.antlr.kotlin.KotlinGrammarAntlrKotlinParser

fun main() {
    val source = AstSource.String(
        """
        package com.example
        
        class Person(val name: String, val age: Int) {
            fun greet() = "Hello, I'm ${'$'}name"
        }
        """.trimIndent()
    )
    
    // Parse the source code
    val kotlinFile = KotlinGrammarAntlrKotlinParser.parseKotlinFile(source)
    
    // Get a summary of the AST
    kotlinFile.summary(attachRawAst = false)
        .onSuccess { ast ->
            println("Parsed successfully!")
            ast.forEach { node ->
                printAstNode(node, indent = 0)
            }
        }
        .onFailure { errors ->
            errors.forEach { println("Error: $it") }
        }
}

fun printAstNode(node: Ast, indent: Int) {
    val prefix = "  ".repeat(indent)
    when (node) {
        is KlassDeclaration -> {
            println("$prefix${node.keyword} ${node.identifier?.rawName}")
            node.children.forEach { printAstNode(it, indent + 1) }
        }
        else -> {
            println("$prefix${node::class.simpleName}")
        }
    }
}
```

### Key Classes in kotlinx.ast

```kotlin
// Core AST node types from the Klass abstraction
sealed class Ast
class KlassDeclaration(
    val keyword: String,           // "class", "interface", "object", etc.
    val identifier: KlassIdentifier?,
    val modifiers: List<KlassModifier>,
    val typeParameters: List<KlassTypeParameter>,
    val inheritance: List<KlassInheritance>,
    val children: List<Ast>
)

class KlassFunction(
    val identifier: KlassIdentifier?,
    val modifiers: List<KlassModifier>,
    val parameters: List<KlassParameter>,
    val returnType: KlassType?,
    val body: Ast?
)

class KlassProperty(
    val identifier: KlassIdentifier?,
    val type: KlassType?,
    val initializer: Ast?
)
```

---

## Part 2: Klaxon - JSON Parser for Kotlin

**Klaxon** is a lightweight JSON parsing library for Kotlin with four distinct APIs:
1. **Object binding** - Map JSON to Kotlin classes
2. **Streaming API** - Process large JSON documents
3. **Low-level API** - Direct JsonObject/JsonArray manipulation
4. **JSON Path** - Query specific elements

### Basic Object Binding

```kotlin
import com.beust.klaxon.Klaxon

data class Person(
    val name: String,
    val age: Int,
    val isDeveloper: Boolean
)

fun main() {
    val jsonString = """
        {
            "name": "Marcus",
            "age": 42,
            "isDeveloper": true
        }
    """
    
    // Parse JSON to object
    val person = Klaxon().parse<Person>(jsonString)
    println("Name: ${person?.name}, Age: ${person?.age}")
    
    // Serialize object to JSON
    val json = Klaxon().toJsonString(Person("Ada", 36, true))
    println(json)
}
```

### DSL for Creating JSON

Klaxon provides a powerful DSL for building JSON programmatically:

```kotlin
import com.beust.klaxon.json

// Declarative style
val person = json {
    obj(
        "name" to "Marcus",
        "age" to 42,
        "languages" to array("Kotlin", "Java", "Ecstasy"),
        "address" to obj(
            "city" to "Stockholm",
            "country" to "Sweden"
        )
    )
}

// Imperative style (allows arbitrary Kotlin code)
val dynamic = json {
    obj {
        // You can use loops and conditionals!
        (1..5).forEach { i ->
            put("field$i", i * 10)
        }
    }
}

println(person.toJsonString())
```

### Streaming API for Large Documents

```kotlin
import com.beust.klaxon.JsonReader
import java.io.StringReader

val jsonArray = """
    [
        {"name": "Alice", "score": 95},
        {"name": "Bob", "score": 87},
        {"name": "Carol", "score": 92}
    ]
"""

JsonReader(StringReader(jsonArray)).use { reader ->
    reader.beginArray {
        while (reader.hasNext()) {
            reader.beginObject {
                var name: String? = null
                var score: Int? = null
                
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "name" -> name = reader.nextString()
                        "score" -> score = reader.nextInt()
                    }
                }
                println("$name scored $score")
            }
        }
    }
}
```

### Custom Type Converters

```kotlin
import com.beust.klaxon.*
import java.time.LocalDate

// Custom converter for LocalDate
val dateConverter = object : Converter {
    override fun canConvert(cls: Class<*>) = cls == LocalDate::class.java
    
    override fun fromJson(jv: JsonValue): LocalDate {
        return LocalDate.parse(jv.string)
    }
    
    override fun toJson(value: Any): String {
        return "\"${(value as LocalDate)}\""
    }
}

data class Event(
    val name: String,
    @Json(name = "event_date")
    val date: LocalDate
)

val klaxon = Klaxon().converter(dateConverter)
val event = klaxon.parse<Event>("""{"name": "Conference", "event_date": "2025-06-15"}""")
```

---

## Part 3: Reflective Kotlin DSL for XTC (Ecstasy) Language

Based on analysis of the XVM repository's Lexer, Parser, and Compiler along with XTC source files, here's a comprehensive Kotlin DSL that models the Ecstasy language for tooling support.

### Design Philosophy

The DSL serves multiple purposes:
1. **Parser generation** - The model can generate parsers
2. **AST classes** - Sealed class hierarchies generated from concept definitions
3. **IDE support** - Completion, navigation, validation derived from the model
4. **Serialization** - AST can be serialized/deserialized (via Klaxon or kotlinx.serialization)

### Core Framework

```kotlin
package org.xtclang.dsl

import kotlin.reflect.KClass

// ============================================================================
// Core DSL Infrastructure
// ============================================================================

@DslMarker
annotation class LanguageDsl

/**
 * Root of the language model - describes an entire programming language
 */
@LanguageDsl
class LanguageModel(val name: String) {
    val concepts = mutableMapOf<String, ConceptDefinition>()
    val keywords = mutableSetOf<String>()
    val operators = mutableListOf<OperatorDefinition>()
    val tokenRules = mutableListOf<TokenRule>()
    
    fun concept(name: String, block: ConceptBuilder.() -> Unit): ConceptDefinition {
        val builder = ConceptBuilder(name)
        builder.block()
        return builder.build().also { concepts[name] = it }
    }
    
    fun abstractConcept(name: String, block: ConceptBuilder.() -> Unit): ConceptDefinition {
        val builder = ConceptBuilder(name, isAbstract = true)
        builder.block()
        return builder.build().also { concepts[name] = it }
    }
    
    fun keywords(vararg words: String) {
        keywords.addAll(words)
    }
    
    fun operator(symbol: String, precedence: Int, assoc: Associativity = Associativity.LEFT) {
        operators.add(OperatorDefinition(symbol, precedence, assoc))
    }
    
    fun token(name: String, pattern: String) {
        tokenRules.add(TokenRule(name, pattern))
    }
}

enum class Associativity { LEFT, RIGHT, NONE }

data class OperatorDefinition(
    val symbol: String,
    val precedence: Int,
    val associativity: Associativity
)

data class TokenRule(val name: String, val pattern: String)

// ============================================================================
// Concept Definition (AST Node Types)
// ============================================================================

data class ConceptDefinition(
    val name: String,
    val isAbstract: Boolean,
    val extends: String?,
    val implements: List<String>,
    val properties: List<PropertyDef>,
    val children: List<ChildDef>,
    val references: List<ReferenceDef>
)

data class PropertyDef(
    val name: String,
    val type: KClass<*>,
    val optional: Boolean = false,
    val defaultValue: Any? = null
)

data class ChildDef(
    val name: String,
    val conceptType: String,
    val cardinality: Cardinality
)

data class ReferenceDef(
    val name: String,
    val targetConcept: String,
    val optional: Boolean
)

enum class Cardinality { ONE, OPTIONAL, MANY }

@LanguageDsl
class ConceptBuilder(
    private val name: String,
    private val isAbstract: Boolean = false
) {
    private var extends: String? = null
    private val implements = mutableListOf<String>()
    private val properties = mutableListOf<PropertyDef>()
    private val children = mutableListOf<ChildDef>()
    private val references = mutableListOf<ReferenceDef>()
    
    fun extends(parent: String) {
        this.extends = parent
    }
    
    fun implements(vararg interfaces: String) {
        this.implements.addAll(interfaces)
    }
    
    inline fun <reified T : Any> property(
        name: String,
        optional: Boolean = false,
        defaultValue: T? = null
    ) {
        properties.add(PropertyDef(name, T::class, optional, defaultValue))
    }
    
    fun child(name: String, conceptType: String, cardinality: Cardinality = Cardinality.ONE) {
        children.add(ChildDef(name, conceptType, cardinality))
    }
    
    fun children(name: String, conceptType: String) {
        children.add(ChildDef(name, conceptType, Cardinality.MANY))
    }
    
    fun optionalChild(name: String, conceptType: String) {
        children.add(ChildDef(name, conceptType, Cardinality.OPTIONAL))
    }
    
    fun reference(name: String, targetConcept: String, optional: Boolean = false) {
        references.add(ReferenceDef(name, targetConcept, optional))
    }
    
    fun build() = ConceptDefinition(
        name, isAbstract, extends, implements, properties, children, references
    )
}

// ============================================================================
// DSL Entry Point
// ============================================================================

fun language(name: String, block: LanguageModel.() -> Unit): LanguageModel {
    return LanguageModel(name).apply(block)
}
```

### XTC Language Model Definition

```kotlin
package org.xtclang.dsl

/**
 * Complete XTC (Ecstasy) Language Model
 * 
 * Based on analysis of:
 * - javatools/src/main/java/org/xvm/compiler/Token.java
 * - javatools/src/main/java/org/xvm/compiler/Lexer.java  
 * - javatools/src/main/java/org/xvm/compiler/Parser.java
 * - lib_ecstasy/src/main/x/*.x source files
 */
val XtcLanguage = language("Ecstasy") {
    
    // ========================================================================
    // Keywords
    // ========================================================================
    keywords(
        // Type declarations
        "module", "package", "class", "interface", "mixin", "service",
        "const", "enum", "typedef",
        
        // Access modifiers
        "public", "protected", "private", "static",
        
        // Type modifiers
        "abstract", "final", "immutable",
        
        // Control flow
        "if", "else", "switch", "case", "default",
        "for", "while", "do", "foreach",
        "break", "continue", "return",
        "try", "catch", "finally", "throw", "using",
        
        // Operators as keywords
        "new", "is", "as", "instanceof",
        "this", "super", "outer",
        
        // Literals
        "True", "False", "Null",
        
        // Special
        "import", "extends", "implements", "incorporates",
        "delegates", "into", "inject", "assert",
        "construct", "finally", "conditional", "void"
    )
    
    // ========================================================================
    // Operators (with precedence, higher = binds tighter)
    // ========================================================================
    
    // Assignment (lowest precedence)
    operator("=", 1, Associativity.RIGHT)
    operator("+=", 1, Associativity.RIGHT)
    operator("-=", 1, Associativity.RIGHT)
    operator("*=", 1, Associativity.RIGHT)
    operator("/=", 1, Associativity.RIGHT)
    operator("%=", 1, Associativity.RIGHT)
    operator("&=", 1, Associativity.RIGHT)
    operator("|=", 1, Associativity.RIGHT)
    operator("^=", 1, Associativity.RIGHT)
    operator("<<=", 1, Associativity.RIGHT)
    operator(">>=", 1, Associativity.RIGHT)
    operator(">>>=", 1, Associativity.RIGHT)
    operator(":=", 1, Associativity.RIGHT)  // Conditional assignment
    operator("?=", 1, Associativity.RIGHT)  // Elvis assignment
    
    // Ternary
    operator("?", 2, Associativity.RIGHT)   // Ternary conditional
    operator(":", 2)                         // Ternary separator
    operator("?:", 2)                        // Elvis operator
    
    // Logical OR
    operator("||", 3)
    operator("^^", 3)  // XOR
    
    // Logical AND
    operator("&&", 4)
    
    // Bitwise OR
    operator("|", 5)
    
    // Bitwise XOR
    operator("^", 6)
    
    // Bitwise AND
    operator("&", 7)
    
    // Equality
    operator("==", 8)
    operator("!=", 8)
    
    // Relational
    operator("<", 9)
    operator("<=", 9)
    operator(">", 9)
    operator(">=", 9)
    operator("<=>", 9)  // Spaceship operator
    
    // Range
    operator("..", 10)
    operator("..<", 10)  // Exclusive end
    
    // Shift
    operator("<<", 11)
    operator(">>", 11)
    operator(">>>", 11)  // Unsigned shift
    
    // Additive
    operator("+", 12)
    operator("-", 12)
    
    // Multiplicative
    operator("*", 13)
    operator("/", 13)
    operator("%", 13)
    operator("/%", 13)  // Div-mod
    
    // Unary (highest)
    operator("!", 14)
    operator("~", 14)
    operator("++", 14)
    operator("--", 14)
    operator("?.", 15)   // Null-safe access
    operator(".", 15)    // Member access
    
    // ========================================================================
    // Token Rules
    // ========================================================================
    
    token("IDENTIFIER", "[a-zA-Z_][a-zA-Z0-9_]*")
    token("INT_LITERAL", "[0-9]+")
    token("HEX_LITERAL", "0[xX][0-9a-fA-F]+")
    token("BIN_LITERAL", "0[bB][01]+")
    token("FLOAT_LITERAL", "[0-9]+\\.[0-9]+([eE][+-]?[0-9]+)?")
    token("STRING_LITERAL", "\"([^\"\\\\]|\\\\.)*\"")
    token("CHAR_LITERAL", "'([^'\\\\]|\\\\.)'")
    token("TEMPLATE_STRING", "\\$\"([^\"\\\\]|\\\\.)*\"")
    token("DOC_COMMENT", "/\\*\\*.*?\\*/")
    token("ANNOTATION", "@[a-zA-Z_][a-zA-Z0-9_]*")
    
    // ========================================================================
    // AST Concepts - Source Structure
    // ========================================================================
    
    concept("SourceFile") {
        property<String>("path")
        optionalChild("module", "ModuleDeclaration")
        children("imports", "ImportStatement")
        children("types", "TypeDeclaration")
    }
    
    concept("ModuleDeclaration") {
        property<String>("qualifiedName")
        property<String>("simpleName")
        children("annotations", "Annotation")
        optionalChild("version", "VersionDeclaration")
        children("packages", "PackageDeclaration")
        children("types", "TypeDeclaration")
    }
    
    concept("PackageDeclaration") {
        property<String>("name")
        property<Boolean>("isImport", defaultValue = false)
        property<String?>("importedModule", optional = true)
        children("types", "TypeDeclaration")
    }
    
    concept("ImportStatement") {
        property<String>("qualifiedName")
        property<String?>("alias", optional = true)
        property<Boolean>("isWildcard", defaultValue = false)
    }
    
    // ========================================================================
    // AST Concepts - Type Declarations
    // ========================================================================
    
    abstractConcept("TypeDeclaration") {
        property<String>("name")
        property<Visibility>("visibility", defaultValue = Visibility.PUBLIC)
        children("annotations", "Annotation")
        children("typeParameters", "TypeParameter")
        optionalChild("docComment", "DocComment")
        optionalChild("condition", "ConditionalClause")
    }
    
    concept("ClassDeclaration") {
        extends("TypeDeclaration")
        property<Boolean>("isAbstract", defaultValue = false)
        property<Boolean>("isStatic", defaultValue = false)
        reference("superclass", "TypeExpression", optional = true)
        children("implements", "TypeExpression")
        children("incorporates", "IncorporatesClause")
        children("members", "ClassMember")
    }
    
    concept("InterfaceDeclaration") {
        extends("TypeDeclaration")
        children("extends", "TypeExpression")
        children("members", "InterfaceMember")
    }
    
    concept("MixinDeclaration") {
        extends("TypeDeclaration")
        reference("into", "TypeExpression", optional = true)
        children("extends", "TypeExpression")
        children("members", "ClassMember")
    }
    
    concept("ServiceDeclaration") {
        extends("TypeDeclaration")
        reference("superclass", "TypeExpression", optional = true)
        children("implements", "TypeExpression")
        children("incorporates", "IncorporatesClause")
        children("members", "ClassMember")
    }
    
    concept("ConstDeclaration") {
        extends("TypeDeclaration")
        reference("superclass", "TypeExpression", optional = true)
        children("implements", "TypeExpression")
        children("members", "ClassMember")
    }
    
    concept("EnumDeclaration") {
        extends("TypeDeclaration")
        children("implements", "TypeExpression")
        children("values", "EnumValue")
        children("members", "ClassMember")
    }
    
    concept("EnumValue") {
        property<String>("name")
        children("arguments", "Expression")
        optionalChild("body", "ClassBody")
    }
    
    concept("TypedefDeclaration") {
        extends("TypeDeclaration")
        child("targetType", "TypeExpression")
    }
    
    // ========================================================================
    // AST Concepts - Members
    // ========================================================================
    
    abstractConcept("ClassMember") {
        property<String>("name")
        property<Visibility>("visibility", defaultValue = Visibility.PUBLIC)
        children("annotations", "Annotation")
        optionalChild("docComment", "DocComment")
    }
    
    concept("PropertyDeclaration") {
        extends("ClassMember")
        child("type", "TypeExpression")
        property<Boolean>("isReadOnly", defaultValue = false)
        property<Boolean>("isStatic", defaultValue = false)
        optionalChild("initializer", "Expression")
        optionalChild("getter", "PropertyAccessor")
        optionalChild("setter", "PropertyAccessor")
    }
    
    concept("PropertyAccessor") {
        property<AccessorKind>("kind")  // GET, SET
        optionalChild("body", "StatementBlock")
    }
    
    concept("MethodDeclaration") {
        extends("ClassMember")
        property<Boolean>("isAbstract", defaultValue = false)
        property<Boolean>("isStatic", defaultValue = false)
        property<Boolean>("isConditional", defaultValue = false)
        children("typeParameters", "TypeParameter")
        children("returnTypes", "TypeExpression")  // XTC supports multi-return
        children("parameters", "Parameter")
        optionalChild("body", "StatementBlock")
    }
    
    concept("ConstructorDeclaration") {
        extends("ClassMember")
        property<ConstructorKind>("kind")  // CONSTRUCT, FINALLY
        children("parameters", "Parameter")
        optionalChild("body", "StatementBlock")
    }
    
    concept("Parameter") {
        property<String>("name")
        child("type", "TypeExpression")
        property<Boolean>("isVariadic", defaultValue = false)
        optionalChild("defaultValue", "Expression")
    }
    
    concept("TypeParameter") {
        property<String>("name")
        reference("constraint", "TypeExpression", optional = true)
    }
    
    // ========================================================================
    // AST Concepts - Type Expressions
    // ========================================================================
    
    abstractConcept("TypeExpression") {}
    
    concept("NamedType") {
        extends("TypeExpression")
        property<String>("name")
        children("typeArguments", "TypeExpression")
        property<Boolean>("isNullable", defaultValue = false)
    }
    
    concept("QualifiedType") {
        extends("TypeExpression")
        children("parts", "NamedType")
    }
    
    concept("FunctionType") {
        extends("TypeExpression")
        children("parameterTypes", "TypeExpression")
        children("returnTypes", "TypeExpression")
    }
    
    concept("TupleType") {
        extends("TypeExpression")
        children("elementTypes", "TypeExpression")
    }
    
    concept("UnionType") {
        extends("TypeExpression")
        children("types", "TypeExpression")
    }
    
    concept("IntersectionType") {
        extends("TypeExpression")
        children("types", "TypeExpression")
    }
    
    concept("ImmutableType") {
        extends("TypeExpression")
        child("baseType", "TypeExpression")
    }
    
    // ========================================================================
    // AST Concepts - Statements
    // ========================================================================
    
    abstractConcept("Statement") {}
    
    concept("StatementBlock") {
        extends("Statement")
        children("statements", "Statement")
    }
    
    concept("VariableDeclarationStatement") {
        extends("Statement")
        child("type", "TypeExpression")
        property<String>("name")
        optionalChild("initializer", "Expression")
        property<Boolean>("isVal", defaultValue = true)  // val vs var
    }
    
    concept("MultiVariableDeclaration") {
        extends("Statement")
        children("declarations", "VariableDeclarationStatement")
        child("initializer", "Expression")  // Destructuring
    }
    
    concept("ExpressionStatement") {
        extends("Statement")
        child("expression", "Expression")
    }
    
    concept("IfStatement") {
        extends("Statement")
        child("condition", "Expression")
        child("thenBranch", "Statement")
        optionalChild("elseBranch", "Statement")
    }
    
    concept("SwitchStatement") {
        extends("Statement")
        child("expression", "Expression")
        children("cases", "CaseClause")
    }
    
    concept("CaseClause") {
        children("labels", "Expression")  // Empty for default
        property<Boolean>("isDefault", defaultValue = false)
        children("statements", "Statement")
    }
    
    concept("ForStatement") {
        extends("Statement")
        optionalChild("initializer", "Statement")
        optionalChild("condition", "Expression")
        optionalChild("update", "Expression")
        child("body", "Statement")
    }
    
    concept("ForEachStatement") {
        extends("Statement")
        child("variableType", "TypeExpression")
        property<String>("variableName")
        child("iterable", "Expression")
        child("body", "Statement")
    }
    
    concept("WhileStatement") {
        extends("Statement")
        child("condition", "Expression")
        child("body", "Statement")
    }
    
    concept("DoWhileStatement") {
        extends("Statement")
        child("body", "Statement")
        child("condition", "Expression")
    }
    
    concept("TryStatement") {
        extends("Statement")
        optionalChild("resources", "ResourceList")
        child("tryBlock", "StatementBlock")
        children("catchClauses", "CatchClause")
        optionalChild("finallyBlock", "StatementBlock")
    }
    
    concept("CatchClause") {
        child("exceptionType", "TypeExpression")
        property<String>("variableName")
        child("body", "StatementBlock")
    }
    
    concept("ReturnStatement") {
        extends("Statement")
        children("values", "Expression")  // Multi-return support
    }
    
    concept("ThrowStatement") {
        extends("Statement")
        child("exception", "Expression")
    }
    
    concept("AssertStatement") {
        extends("Statement")
        child("condition", "Expression")
        optionalChild("message", "Expression")
        property<AssertKind>("kind", defaultValue = AssertKind.ASSERT)
    }
    
    concept("BreakStatement") {
        extends("Statement")
        property<String?>("label", optional = true)
    }
    
    concept("ContinueStatement") {
        extends("Statement")
        property<String?>("label", optional = true)
    }
    
    concept("UsingStatement") {
        extends("Statement")
        child("resource", "Expression")
        child("body", "Statement")
    }
    
    // ========================================================================
    // AST Concepts - Expressions
    // ========================================================================
    
    abstractConcept("Expression") {}
    
    concept("LiteralExpression") {
        extends("Expression")
        property<Any>("value")
        property<LiteralKind>("kind")
    }
    
    concept("IdentifierExpression") {
        extends("Expression")
        property<String>("name")
    }
    
    concept("QualifiedExpression") {
        extends("Expression")
        child("qualifier", "Expression")
        property<String>("member")
        property<Boolean>("nullSafe", defaultValue = false)
    }
    
    concept("ThisExpression") {
        extends("Expression")
        property<String?>("targetType", optional = true)
    }
    
    concept("SuperExpression") {
        extends("Expression")
        property<String?>("targetType", optional = true)
    }
    
    concept("BinaryExpression") {
        extends("Expression")
        child("left", "Expression")
        property<String>("operator")
        child("right", "Expression")
    }
    
    concept("UnaryExpression") {
        extends("Expression")
        property<String>("operator")
        child("operand", "Expression")
        property<Boolean>("isPrefix", defaultValue = true)
    }
    
    concept("TernaryExpression") {
        extends("Expression")
        child("condition", "Expression")
        child("thenExpr", "Expression")
        child("elseExpr", "Expression")
    }
    
    concept("ElvisExpression") {
        extends("Expression")
        child("left", "Expression")
        child("right", "Expression")
    }
    
    concept("AssignmentExpression") {
        extends("Expression")
        child("target", "Expression")
        property<String>("operator")  // =, +=, -=, etc.
        child("value", "Expression")
    }
    
    concept("InvocationExpression") {
        extends("Expression")
        child("target", "Expression")
        children("typeArguments", "TypeExpression")
        children("arguments", "Argument")
    }
    
    concept("Argument") {
        property<String?>("name", optional = true)  // Named argument
        child("value", "Expression")
    }
    
    concept("IndexExpression") {
        extends("Expression")
        child("target", "Expression")
        children("indices", "Expression")
    }
    
    concept("NewExpression") {
        extends("Expression")
        child("type", "TypeExpression")
        children("arguments", "Argument")
        optionalChild("body", "ClassBody")  // Anonymous class
    }
    
    concept("LambdaExpression") {
        extends("Expression")
        children("parameters", "LambdaParameter")
        child("body", "Expression")  // Or StatementBlock
    }
    
    concept("LambdaParameter") {
        property<String>("name")
        optionalChild("type", "TypeExpression")
    }
    
    concept("TypeCheckExpression") {
        extends("Expression")
        child("expression", "Expression")
        property<TypeCheckKind>("kind")  // IS, AS
        child("type", "TypeExpression")
    }
    
    concept("RangeExpression") {
        extends("Expression")
        child("start", "Expression")
        child("end", "Expression")
        property<Boolean>("inclusive", defaultValue = true)
    }
    
    concept("ListExpression") {
        extends("Expression")
        optionalChild("elementType", "TypeExpression")
        children("elements", "Expression")
    }
    
    concept("MapExpression") {
        extends("Expression")
        optionalChild("keyType", "TypeExpression")
        optionalChild("valueType", "TypeExpression")
        children("entries", "MapEntry")
    }
    
    concept("MapEntry") {
        child("key", "Expression")
        child("value", "Expression")
    }
    
    concept("TupleExpression") {
        extends("Expression")
        children("elements", "Expression")
    }
    
    concept("TemplateExpression") {
        extends("Expression")
        children("parts", "TemplatePart")
    }
    
    abstractConcept("TemplatePart") {}
    
    concept("TemplateStringPart") {
        extends("TemplatePart")
        property<String>("text")
    }
    
    concept("TemplateExpressionPart") {
        extends("TemplatePart")
        child("expression", "Expression")
    }
    
    concept("SwitchExpression") {
        extends("Expression")
        optionalChild("expression", "Expression")
        children("cases", "SwitchExpressionCase")
    }
    
    concept("SwitchExpressionCase") {
        children("patterns", "Expression")
        property<Boolean>("isDefault", defaultValue = false)
        child("result", "Expression")
    }
    
    // ========================================================================
    // AST Concepts - Annotations & Conditions
    // ========================================================================
    
    concept("Annotation") {
        property<String>("name")
        children("arguments", "Argument")
    }
    
    concept("ConditionalClause") {
        child("condition", "Expression")
    }
    
    concept("IncorporatesClause") {
        child("mixin", "TypeExpression")
        children("arguments", "Argument")
        property<Boolean>("isConditional", defaultValue = false)
    }
    
    concept("DocComment") {
        property<String>("text")
        children("tags", "DocTag")
    }
    
    concept("DocTag") {
        property<String>("name")  // @param, @return, etc.
        property<String>("content")
    }
}

// ========================================================================
// Supporting Enums
// ========================================================================

enum class Visibility { PUBLIC, PROTECTED, PRIVATE }
enum class AccessorKind { GET, SET }
enum class ConstructorKind { CONSTRUCT, FINALLY }
enum class AssertKind { ASSERT, ASSERT_RND, ASSERT_ARG, ASSERT_BOUNDS, ASSERT_TODO, ASSERT_ONCE, ASSERT_TEST, ASSERT_DBG }
enum class LiteralKind { INT, FLOAT, STRING, CHAR, BOOLEAN, NULL, BINARY, DATE, TIME, DURATION, VERSION, PATH }
enum class TypeCheckKind { IS, AS }
```

### Using the Language Model

```kotlin
package org.xtclang.dsl

/**
 * Example: Generate sealed class hierarchy from concept definitions
 */
fun LanguageModel.generateAstClasses(): String {
    val sb = StringBuilder()
    sb.appendLine("// Auto-generated AST classes for $name")
    sb.appendLine("package org.xtclang.ast")
    sb.appendLine()
    
    // Generate base sealed interface
    sb.appendLine("sealed interface AstNode {")
    sb.appendLine("    val sourceRange: SourceRange?")
    sb.appendLine("}")
    sb.appendLine()
    
    // Generate classes for each concept
    concepts.values.forEach { concept ->
        val sealed = if (concept.isAbstract) "sealed " else ""
        val extends = concept.extends?.let { ": $it()" } ?: ": AstNode"
        
        sb.appendLine("${sealed}class ${concept.name}(")
        
        // Properties
        concept.properties.forEach { prop ->
            val nullable = if (prop.optional) "?" else ""
            val default = prop.defaultValue?.let { " = $it" } ?: if (prop.optional) " = null" else ""
            sb.appendLine("    val ${prop.name}: ${prop.type.simpleName}$nullable$default,")
        }
        
        // Children
        concept.children.forEach { child ->
            val type = when (child.cardinality) {
                Cardinality.ONE -> child.conceptType
                Cardinality.OPTIONAL -> "${child.conceptType}?"
                Cardinality.MANY -> "List<${child.conceptType}>"
            }
            val default = when (child.cardinality) {
                Cardinality.OPTIONAL -> " = null"
                Cardinality.MANY -> " = emptyList()"
                else -> ""
            }
            sb.appendLine("    val ${child.name}: $type$default,")
        }
        
        sb.appendLine("    override val sourceRange: SourceRange? = null")
        sb.appendLine(") $extends")
        sb.appendLine()
    }
    
    return sb.toString()
}

/**
 * Example: Generate completion provider from keywords and concepts
 */
fun LanguageModel.generateCompletionItems(context: CompletionContext): List<CompletionItem> {
    val items = mutableListOf<CompletionItem>()
    
    // Add keywords
    keywords.filter { it.startsWith(context.prefix) }.forEach {
        items.add(CompletionItem(it, CompletionKind.KEYWORD))
    }
    
    // Add type names from concepts
    concepts.values
        .filter { !it.isAbstract && it.name.startsWith(context.prefix) }
        .forEach { items.add(CompletionItem(it.name, CompletionKind.TYPE)) }
    
    return items
}

data class CompletionContext(val prefix: String, val position: Position)
data class CompletionItem(val text: String, val kind: CompletionKind)
enum class CompletionKind { KEYWORD, TYPE, PROPERTY, METHOD, VARIABLE }
data class Position(val line: Int, val column: Int)
data class SourceRange(val start: Position, val end: Position)
```

### Serializing AST with Klaxon

```kotlin
package org.xtclang.dsl

import com.beust.klaxon.*

/**
 * Serialize/deserialize XTC AST to JSON using Klaxon
 */
object AstSerializer {
    private val klaxon = Klaxon()
        .converter(VisibilityConverter)
        .converter(LiteralKindConverter)
    
    fun toJson(node: Any): String = klaxon.toJsonString(node)
    
    inline fun <reified T> fromJson(json: String): T? = klaxon.parse<T>(json)
}

object VisibilityConverter : Converter {
    override fun canConvert(cls: Class<*>) = cls == Visibility::class.java
    override fun fromJson(jv: JsonValue) = Visibility.valueOf(jv.string!!)
    override fun toJson(value: Any) = "\"${(value as Visibility).name}\""
}

object LiteralKindConverter : Converter {
    override fun canConvert(cls: Class<*>) = cls == LiteralKind::class.java
    override fun fromJson(jv: JsonValue) = LiteralKind.valueOf(jv.string!!)
    override fun toJson(value: Any) = "\"${(value as LiteralKind).name}\""
}

// Example usage:
fun main() {
    // Build AST programmatically
    val classDecl = mapOf(
        "name" to "Person",
        "visibility" to "PUBLIC",
        "isAbstract" to false,
        "members" to listOf(
            mapOf(
                "type" to "PropertyDeclaration",
                "name" to "name",
                "propertyType" to mapOf("name" to "String")
            )
        )
    )
    
    println(Klaxon().toJsonString(classDecl))
}
```

---

## Part 4: Generating IDE Support from the Model

The power of a reflective DSL is that you can derive tooling automatically:

```kotlin
package org.xtclang.tooling

import org.xtclang.dsl.*

/**
 * Generate LSP-compatible document symbols from the language model
 */
class SymbolProvider(private val model: LanguageModel) {
    
    fun getDocumentSymbols(ast: Any): List<DocumentSymbol> {
        return when (ast) {
            is Map<*, *> -> processNode(ast as Map<String, Any?>)
            is List<*> -> ast.flatMap { getDocumentSymbols(it!!) }
            else -> emptyList()
        }
    }
    
    private fun processNode(node: Map<String, Any?>): List<DocumentSymbol> {
        val type = node["type"] as? String ?: return emptyList()
        val concept = model.concepts[type] ?: return emptyList()
        
        val symbols = mutableListOf<DocumentSymbol>()
        
        // If this concept has a name property, it's a symbol
        if (concept.properties.any { it.name == "name" }) {
            val name = node["name"] as? String ?: "<anonymous>"
            symbols.add(DocumentSymbol(
                name = name,
                kind = conceptToSymbolKind(type),
                children = concept.children
                    .filter { it.cardinality == Cardinality.MANY }
                    .flatMap { child ->
                        (node[child.name] as? List<*>)
                            ?.flatMap { getDocumentSymbols(it!!) }
                            ?: emptyList()
                    }
            ))
        }
        
        return symbols
    }
    
    private fun conceptToSymbolKind(concept: String) = when {
        concept.endsWith("Declaration") && concept.contains("Class") -> SymbolKind.CLASS
        concept.endsWith("Declaration") && concept.contains("Interface") -> SymbolKind.INTERFACE
        concept.endsWith("Declaration") && concept.contains("Method") -> SymbolKind.METHOD
        concept.endsWith("Declaration") && concept.contains("Property") -> SymbolKind.PROPERTY
        concept.endsWith("Declaration") && concept.contains("Module") -> SymbolKind.MODULE
        else -> SymbolKind.VARIABLE
    }
}

data class DocumentSymbol(
    val name: String,
    val kind: SymbolKind,
    val children: List<DocumentSymbol> = emptyList()
)

enum class SymbolKind {
    MODULE, CLASS, INTERFACE, METHOD, PROPERTY, VARIABLE, ENUM, CONSTANT
}
```

---

## Summary

| Library | Purpose | Use Case |
|---------|---------|----------|
| **kotlinx.ast** | Parse Kotlin source → AST | Analyzing existing Kotlin code |
| **Klaxon** | JSON ↔ Kotlin objects | Serializing AST, config files |
| **Reflective DSL** | Define language model | IDE support, parser generation, validation |

The XTC language model DSL above captures:
- 50+ keywords
- 30+ operators with precedence
- 60+ AST concepts covering the full language
- Type expressions, statements, expressions
- Annotations and conditional compilation

This model can drive:
- Parser generation (ANTLR grammar output)
- AST class generation
- LSP server features (completion, symbols, navigation)
- Validation rules
- Code formatting
- Refactoring operations

# Reflective Kotlin DSL for Language Support

## Part 1: kotlinx.ast - How It Works

**kotlinx.ast** is a generic AST (Abstract Syntax Tree) parsing library for Kotlin that uses ANTLR under the hood. Unlike using the Kotlin compiler directly, it provides a clean, multiplatform-compatible way to parse Kotlin source code into a navigable tree structure.

### Core Concepts

The library uses **Klass** - a collection of language-independent data classes to represent AST nodes. This abstraction makes it easy to traverse and query syntax trees.

### Installation

```kotlin
// build.gradle.kts
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.kotlinx.ast:grammar-kotlin-parser-antlr-kotlin:0.1.0")
}
```

### Basic Usage Example

```kotlin
import kotlinx.ast.common.AstSource
import kotlinx.ast.common.ast.Ast
import kotlinx.ast.common.klass.KlassDeclaration
import kotlinx.ast.grammar.kotlin.common.summary
import kotlinx.ast.grammar.kotlin.target.antlr.kotlin.KotlinGrammarAntlrKotlinParser

fun main() {
    val source = AstSource.String(
        """
        package com.example
        
        class Person(val name: String, val age: Int) {
            fun greet() = "Hello, I'm ${'$'}name"
        }
        """.trimIndent()
    )
    
    // Parse the source code
    val kotlinFile = KotlinGrammarAntlrKotlinParser.parseKotlinFile(source)
    
    // Get a summary of the AST
    kotlinFile.summary(attachRawAst = false)
        .onSuccess { ast ->
            println("Parsed successfully!")
            ast.forEach { node ->
                printAstNode(node, indent = 0)
            }
        }
        .onFailure { errors ->
            errors.forEach { println("Error: $it") }
        }
}

fun printAstNode(node: Ast, indent: Int) {
    val prefix = "  ".repeat(indent)
    when (node) {
        is KlassDeclaration -> {
            println("$prefix${node.keyword} ${node.identifier?.rawName}")
            node.children.forEach { printAstNode(it, indent + 1) }
        }
        else -> {
            println("$prefix${node::class.simpleName}")
        }
    }
}
```

### Key Classes in kotlinx.ast

```kotlin
// Core AST node types from the Klass abstraction
sealed class Ast
class KlassDeclaration(
    val keyword: String,           // "class", "interface", "object", etc.
    val identifier: KlassIdentifier?,
    val modifiers: List<KlassModifier>,
    val typeParameters: List<KlassTypeParameter>,
    val inheritance: List<KlassInheritance>,
    val children: List<Ast>
)

class KlassFunction(
    val identifier: KlassIdentifier?,
    val modifiers: List<KlassModifier>,
    val parameters: List<KlassParameter>,
    val returnType: KlassType?,
    val body: Ast?
)

class KlassProperty(
    val identifier: KlassIdentifier?,
    val type: KlassType?,
    val initializer: Ast?
)
```

---

## Part 2: Klaxon - JSON Parser for Kotlin

**Klaxon** is a lightweight JSON parsing library for Kotlin with four distinct APIs:
1. **Object binding** - Map JSON to Kotlin classes
2. **Streaming API** - Process large JSON documents
3. **Low-level API** - Direct JsonObject/JsonArray manipulation
4. **JSON Path** - Query specific elements

### Basic Object Binding

```kotlin
import com.beust.klaxon.Klaxon

data class Person(
    val name: String,
    val age: Int,
    val isDeveloper: Boolean
)

fun main() {
    val jsonString = """
        {
            "name": "Marcus",
            "age": 42,
            "isDeveloper": true
        }
    """
    
    // Parse JSON to object
    val person = Klaxon().parse<Person>(jsonString)
    println("Name: ${person?.name}, Age: ${person?.age}")
    
    // Serialize object to JSON
    val json = Klaxon().toJsonString(Person("Ada", 36, true))
    println(json)
}
```

### DSL for Creating JSON

Klaxon provides a powerful DSL for building JSON programmatically:

```kotlin
import com.beust.klaxon.json

// Declarative style
val person = json {
    obj(
        "name" to "Marcus",
        "age" to 42,
        "languages" to array("Kotlin", "Java", "Ecstasy"),
        "address" to obj(
            "city" to "Stockholm",
            "country" to "Sweden"
        )
    )
}

// Imperative style (allows arbitrary Kotlin code)
val dynamic = json {
    obj {
        // You can use loops and conditionals!
        (1..5).forEach { i ->
            put("field$i", i * 10)
        }
    }
}

println(person.toJsonString())
```

### Streaming API for Large Documents

```kotlin
import com.beust.klaxon.JsonReader
import java.io.StringReader

val jsonArray = """
    [
        {"name": "Alice", "score": 95},
        {"name": "Bob", "score": 87},
        {"name": "Carol", "score": 92}
    ]
"""

JsonReader(StringReader(jsonArray)).use { reader ->
    reader.beginArray {
        while (reader.hasNext()) {
            reader.beginObject {
                var name: String? = null
                var score: Int? = null
                
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "name" -> name = reader.nextString()
                        "score" -> score = reader.nextInt()
                    }
                }
                println("$name scored $score")
            }
        }
    }
}
```

### Custom Type Converters

```kotlin
import com.beust.klaxon.*
import java.time.LocalDate

// Custom converter for LocalDate
val dateConverter = object : Converter {
    override fun canConvert(cls: Class<*>) = cls == LocalDate::class.java
    
    override fun fromJson(jv: JsonValue): LocalDate {
        return LocalDate.parse(jv.string)
    }
    
    override fun toJson(value: Any): String {
        return "\"${(value as LocalDate)}\""
    }
}

data class Event(
    val name: String,
    @Json(name = "event_date")
    val date: LocalDate
)

val klaxon = Klaxon().converter(dateConverter)
val event = klaxon.parse<Event>("""{"name": "Conference", "event_date": "2025-06-15"}""")
```

---

## Part 3: Reflective Kotlin DSL for XTC (Ecstasy) Language

Based on analysis of the XVM repository's Lexer, Parser, and Compiler along with XTC source files, here's a comprehensive Kotlin DSL that models the Ecstasy language for tooling support.

### Design Philosophy

The DSL serves multiple purposes:
1. **Parser generation** - The model can generate parsers
2. **AST classes** - Sealed class hierarchies generated from concept definitions
3. **IDE support** - Completion, navigation, validation derived from the model
4. **Serialization** - AST can be serialized/deserialized (via Klaxon or kotlinx.serialization)

### Core Framework

```kotlin
package org.xtclang.dsl

import kotlin.reflect.KClass

// ============================================================================
// Core DSL Infrastructure
// ============================================================================

@DslMarker
annotation class LanguageDsl

/**
 * Root of the language model - describes an entire programming language
 */
@LanguageDsl
class LanguageModel(val name: String) {
    val concepts = mutableMapOf<String, ConceptDefinition>()
    val keywords = mutableSetOf<String>()
    val operators = mutableListOf<OperatorDefinition>()
    val tokenRules = mutableListOf<TokenRule>()
    
    fun concept(name: String, block: ConceptBuilder.() -> Unit): ConceptDefinition {
        val builder = ConceptBuilder(name)
        builder.block()
        return builder.build().also { concepts[name] = it }
    }
    
    fun abstractConcept(name: String, block: ConceptBuilder.() -> Unit): ConceptDefinition {
        val builder = ConceptBuilder(name, isAbstract = true)
        builder.block()
        return builder.build().also { concepts[name] = it }
    }
    
    fun keywords(vararg words: String) {
        keywords.addAll(words)
    }
    
    fun operator(symbol: String, precedence: Int, assoc: Associativity = Associativity.LEFT) {
        operators.add(OperatorDefinition(symbol, precedence, assoc))
    }
    
    fun token(name: String, pattern: String) {
        tokenRules.add(TokenRule(name, pattern))
    }
}

enum class Associativity { LEFT, RIGHT, NONE }

data class OperatorDefinition(
    val symbol: String,
    val precedence: Int,
    val associativity: Associativity
)

data class TokenRule(val name: String, val pattern: String)

// ============================================================================
// Concept Definition (AST Node Types)
// ============================================================================

data class ConceptDefinition(
    val name: String,
    val isAbstract: Boolean,
    val extends: String?,
    val implements: List<String>,
    val properties: List<PropertyDef>,
    val children: List<ChildDef>,
    val references: List<ReferenceDef>
)

data class PropertyDef(
    val name: String,
    val type: KClass<*>,
    val optional: Boolean = false,
    val defaultValue: Any? = null
)

data class ChildDef(
    val name: String,
    val conceptType: String,
    val cardinality: Cardinality
)

data class ReferenceDef(
    val name: String,
    val targetConcept: String,
    val optional: Boolean
)

enum class Cardinality { ONE, OPTIONAL, MANY }

@LanguageDsl
class ConceptBuilder(
    private val name: String,
    private val isAbstract: Boolean = false
) {
    private var extends: String? = null
    private val implements = mutableListOf<String>()
    private val properties = mutableListOf<PropertyDef>()
    private val children = mutableListOf<ChildDef>()
    private val references = mutableListOf<ReferenceDef>()
    
    fun extends(parent: String) {
        this.extends = parent
    }
    
    fun implements(vararg interfaces: String) {
        this.implements.addAll(interfaces)
    }
    
    inline fun <reified T : Any> property(
        name: String,
        optional: Boolean = false,
        defaultValue: T? = null
    ) {
        properties.add(PropertyDef(name, T::class, optional, defaultValue))
    }
    
    fun child(name: String, conceptType: String, cardinality: Cardinality = Cardinality.ONE) {
        children.add(ChildDef(name, conceptType, cardinality))
    }
    
    fun children(name: String, conceptType: String) {
        children.add(ChildDef(name, conceptType, Cardinality.MANY))
    }
    
    fun optionalChild(name: String, conceptType: String) {
        children.add(ChildDef(name, conceptType, Cardinality.OPTIONAL))
    }
    
    fun reference(name: String, targetConcept: String, optional: Boolean = false) {
        references.add(ReferenceDef(name, targetConcept, optional))
    }
    
    fun build() = ConceptDefinition(
        name, isAbstract, extends, implements, properties, children, references
    )
}

// ============================================================================
// DSL Entry Point
// ============================================================================

fun language(name: String, block: LanguageModel.() -> Unit): LanguageModel {
    return LanguageModel(name).apply(block)
}
```

### XTC Language Model Definition

```kotlin
package org.xtclang.dsl

/**
 * Complete XTC (Ecstasy) Language Model
 * 
 * Based on analysis of:
 * - javatools/src/main/java/org/xvm/compiler/Token.java
 * - javatools/src/main/java/org/xvm/compiler/Lexer.java  
 * - javatools/src/main/java/org/xvm/compiler/Parser.java
 * - lib_ecstasy/src/main/x/*.x source files
 */
val XtcLanguage = language("Ecstasy") {
    
    // ========================================================================
    // Keywords
    // ========================================================================
    keywords(
        // Type declarations
        "module", "package", "class", "interface", "mixin", "service",
        "const", "enum", "typedef",
        
        // Access modifiers
        "public", "protected", "private", "static",
        
        // Type modifiers
        "abstract", "final", "immutable",
        
        // Control flow
        "if", "else", "switch", "case", "default",
        "for", "while", "do", "foreach",
        "break", "continue", "return",
        "try", "catch", "finally", "throw", "using",
        
        // Operators as keywords
        "new", "is", "as", "instanceof",
        "this", "super", "outer",
        
        // Literals
        "True", "False", "Null",
        
        // Special
        "import", "extends", "implements", "incorporates",
        "delegates", "into", "inject", "assert",
        "construct", "finally", "conditional", "void"
    )
    
    // ========================================================================
    // Operators (with precedence, higher = binds tighter)
    // ========================================================================
    
    // Assignment (lowest precedence)
    operator("=", 1, Associativity.RIGHT)
    operator("+=", 1, Associativity.RIGHT)
    operator("-=", 1, Associativity.RIGHT)
    operator("*=", 1, Associativity.RIGHT)
    operator("/=", 1, Associativity.RIGHT)
    operator("%=", 1, Associativity.RIGHT)
    operator("&=", 1, Associativity.RIGHT)
    operator("|=", 1, Associativity.RIGHT)
    operator("^=", 1, Associativity.RIGHT)
    operator("<<=", 1, Associativity.RIGHT)
    operator(">>=", 1, Associativity.RIGHT)
    operator(">>>=", 1, Associativity.RIGHT)
    operator(":=", 1, Associativity.RIGHT)  // Conditional assignment
    operator("?=", 1, Associativity.RIGHT)  // Elvis assignment
    
    // Ternary
    operator("?", 2, Associativity.RIGHT)   // Ternary conditional
    operator(":", 2)                         // Ternary separator
    operator("?:", 2)                        // Elvis operator
    
    // Logical OR
    operator("||", 3)
    operator("^^", 3)  // XOR
    
    // Logical AND
    operator("&&", 4)
    
    // Bitwise OR
    operator("|", 5)
    
    // Bitwise XOR
    operator("^", 6)
    
    // Bitwise AND
    operator("&", 7)
    
    // Equality
    operator("==", 8)
    operator("!=", 8)
    
    // Relational
    operator("<", 9)
    operator("<=", 9)
    operator(">", 9)
    operator(">=", 9)
    operator("<=>", 9)  // Spaceship operator
    
    // Range
    operator("..", 10)
    operator("..<", 10)  // Exclusive end
    
    // Shift
    operator("<<", 11)
    operator(">>", 11)
    operator(">>>", 11)  // Unsigned shift
    
    // Additive
    operator("+", 12)
    operator("-", 12)
    
    // Multiplicative
    operator("*", 13)
    operator("/", 13)
    operator("%", 13)
    operator("/%", 13)  // Div-mod
    
    // Unary (highest)
    operator("!", 14)
    operator("~", 14)
    operator("++", 14)
    operator("--", 14)
    operator("?.", 15)   // Null-safe access
    operator(".", 15)    // Member access
    
    // ========================================================================
    // Token Rules
    // ========================================================================
    
    token("IDENTIFIER", "[a-zA-Z_][a-zA-Z0-9_]*")
    token("INT_LITERAL", "[0-9]+")
    token("HEX_LITERAL", "0[xX][0-9a-fA-F]+")
    token("BIN_LITERAL", "0[bB][01]+")
    token("FLOAT_LITERAL", "[0-9]+\\.[0-9]+([eE][+-]?[0-9]+)?")
    token("STRING_LITERAL", "\"([^\"\\\\]|\\\\.)*\"")
    token("CHAR_LITERAL", "'([^'\\\\]|\\\\.)'")
    token("TEMPLATE_STRING", "\\$\"([^\"\\\\]|\\\\.)*\"")
    token("DOC_COMMENT", "/\\*\\*.*?\\*/")
    token("ANNOTATION", "@[a-zA-Z_][a-zA-Z0-9_]*")
    
    // ========================================================================
    // AST Concepts - Source Structure
    // ========================================================================
    
    concept("SourceFile") {
        property<String>("path")
        optionalChild("module", "ModuleDeclaration")
        children("imports", "ImportStatement")
        children("types", "TypeDeclaration")
    }
    
    concept("ModuleDeclaration") {
        property<String>("qualifiedName")
        property<String>("simpleName")
        children("annotations", "Annotation")
        optionalChild("version", "VersionDeclaration")
        children("packages", "PackageDeclaration")
        children("types", "TypeDeclaration")
    }
    
    concept("PackageDeclaration") {
        property<String>("name")
        property<Boolean>("isImport", defaultValue = false)
        property<String?>("importedModule", optional = true)
        children("types", "TypeDeclaration")
    }
    
    concept("ImportStatement") {
        property<String>("qualifiedName")
        property<String?>("alias", optional = true)
        property<Boolean>("isWildcard", defaultValue = false)
    }
    
    // ========================================================================
    // AST Concepts - Type Declarations
    // ========================================================================
    
    abstractConcept("TypeDeclaration") {
        property<String>("name")
        property<Visibility>("visibility", defaultValue = Visibility.PUBLIC)
        children("annotations", "Annotation")
        children("typeParameters", "TypeParameter")
        optionalChild("docComment", "DocComment")
        optionalChild("condition", "ConditionalClause")
    }
    
    concept("ClassDeclaration") {
        extends("TypeDeclaration")
        property<Boolean>("isAbstract", defaultValue = false)
        property<Boolean>("isStatic", defaultValue = false)
        reference("superclass", "TypeExpression", optional = true)
        children("implements", "TypeExpression")
        children("incorporates", "IncorporatesClause")
        children("members", "ClassMember")
    }
    
    concept("InterfaceDeclaration") {
        extends("TypeDeclaration")
        children("extends", "TypeExpression")
        children("members", "InterfaceMember")
    }
    
    concept("MixinDeclaration") {
        extends("TypeDeclaration")
        reference("into", "TypeExpression", optional = true)
        children("extends", "TypeExpression")
        children("members", "ClassMember")
    }
    
    concept("ServiceDeclaration") {
        extends("TypeDeclaration")
        reference("superclass", "TypeExpression", optional = true)
        children("implements", "TypeExpression")
        children("incorporates", "IncorporatesClause")
        children("members", "ClassMember")
    }
    
    concept("ConstDeclaration") {
        extends("TypeDeclaration")
        reference("superclass", "TypeExpression", optional = true)
        children("implements", "TypeExpression")
        children("members", "ClassMember")
    }
    
    concept("EnumDeclaration") {
        extends("TypeDeclaration")
        children("implements", "TypeExpression")
        children("values", "EnumValue")
        children("members", "ClassMember")
    }
    
    concept("EnumValue") {
        property<String>("name")
        children("arguments", "Expression")
        optionalChild("body", "ClassBody")
    }
    
    concept("TypedefDeclaration") {
        extends("TypeDeclaration")
        child("targetType", "TypeExpression")
    }
    
    // ========================================================================
    // AST Concepts - Members
    // ========================================================================
    
    abstractConcept("ClassMember") {
        property<String>("name")
        property<Visibility>("visibility", defaultValue = Visibility.PUBLIC)
        children("annotations", "Annotation")
        optionalChild("docComment", "DocComment")
    }
    
    concept("PropertyDeclaration") {
        extends("ClassMember")
        child("type", "TypeExpression")
        property<Boolean>("isReadOnly", defaultValue = false)
        property<Boolean>("isStatic", defaultValue = false)
        optionalChild("initializer", "Expression")
        optionalChild("getter", "PropertyAccessor")
        optionalChild("setter", "PropertyAccessor")
    }
    
    concept("PropertyAccessor") {
        property<AccessorKind>("kind")  // GET, SET
        optionalChild("body", "StatementBlock")
    }
    
    concept("MethodDeclaration") {
        extends("ClassMember")
        property<Boolean>("isAbstract", defaultValue = false)
        property<Boolean>("isStatic", defaultValue = false)
        property<Boolean>("isConditional", defaultValue = false)
        children("typeParameters", "TypeParameter")
        children("returnTypes", "TypeExpression")  // XTC supports multi-return
        children("parameters", "Parameter")
        optionalChild("body", "StatementBlock")
    }
    
    concept("ConstructorDeclaration") {
        extends("ClassMember")
        property<ConstructorKind>("kind")  // CONSTRUCT, FINALLY
        children("parameters", "Parameter")
        optionalChild("body", "StatementBlock")
    }
    
    concept("Parameter") {
        property<String>("name")
        child("type", "TypeExpression")
        property<Boolean>("isVariadic", defaultValue = false)
        optionalChild("defaultValue", "Expression")
    }
    
    concept("TypeParameter") {
        property<String>("name")
        reference("constraint", "TypeExpression", optional = true)
    }
    
    // ========================================================================
    // AST Concepts - Type Expressions
    // ========================================================================
    
    abstractConcept("TypeExpression") {}
    
    concept("NamedType") {
        extends("TypeExpression")
        property<String>("name")
        children("typeArguments", "TypeExpression")
        property<Boolean>("isNullable", defaultValue = false)
    }
    
    concept("QualifiedType") {
        extends("TypeExpression")
        children("parts", "NamedType")
    }
    
    concept("FunctionType") {
        extends("TypeExpression")
        children("parameterTypes", "TypeExpression")
        children("returnTypes", "TypeExpression")
    }
    
    concept("TupleType") {
        extends("TypeExpression")
        children("elementTypes", "TypeExpression")
    }
    
    concept("UnionType") {
        extends("TypeExpression")
        children("types", "TypeExpression")
    }
    
    concept("IntersectionType") {
        extends("TypeExpression")
        children("types", "TypeExpression")
    }
    
    concept("ImmutableType") {
        extends("TypeExpression")
        child("baseType", "TypeExpression")
    }
    
    // ========================================================================
    // AST Concepts - Statements
    // ========================================================================
    
    abstractConcept("Statement") {}
    
    concept("StatementBlock") {
        extends("Statement")
        children("statements", "Statement")
    }
    
    concept("VariableDeclarationStatement") {
        extends("Statement")
        child("type", "TypeExpression")
        property<String>("name")
        optionalChild("initializer", "Expression")
        property<Boolean>("isVal", defaultValue = true)  // val vs var
    }
    
    concept("MultiVariableDeclaration") {
        extends("Statement")
        children("declarations", "VariableDeclarationStatement")
        child("initializer", "Expression")  // Destructuring
    }
    
    concept("ExpressionStatement") {
        extends("Statement")
        child("expression", "Expression")
    }
    
    concept("IfStatement") {
        extends("Statement")
        child("condition", "Expression")
        child("thenBranch", "Statement")
        optionalChild("elseBranch", "Statement")
    }
    
    concept("SwitchStatement") {
        extends("Statement")
        child("expression", "Expression")
        children("cases", "CaseClause")
    }
    
    concept("CaseClause") {
        children("labels", "Expression")  // Empty for default
        property<Boolean>("isDefault", defaultValue = false)
        children("statements", "Statement")
    }
    
    concept("ForStatement") {
        extends("Statement")
        optionalChild("initializer", "Statement")
        optionalChild("condition", "Expression")
        optionalChild("update", "Expression")
        child("body", "Statement")
    }
    
    concept("ForEachStatement") {
        extends("Statement")
        child("variableType", "TypeExpression")
        property<String>("variableName")
        child("iterable", "Expression")
        child("body", "Statement")
    }
    
    concept("WhileStatement") {
        extends("Statement")
        child("condition", "Expression")
        child("body", "Statement")
    }
    
    concept("DoWhileStatement") {
        extends("Statement")
        child("body", "Statement")
        child("condition", "Expression")
    }
    
    concept("TryStatement") {
        extends("Statement")
        optionalChild("resources", "ResourceList")
        child("tryBlock", "StatementBlock")
        children("catchClauses", "CatchClause")
        optionalChild("finallyBlock", "StatementBlock")
    }
    
    concept("CatchClause") {
        child("exceptionType", "TypeExpression")
        property<String>("variableName")
        child("body", "StatementBlock")
    }
    
    concept("ReturnStatement") {
        extends("Statement")
        children("values", "Expression")  // Multi-return support
    }
    
    concept("ThrowStatement") {
        extends("Statement")
        child("exception", "Expression")
    }
    
    concept("AssertStatement") {
        extends("Statement")
        child("condition", "Expression")
        optionalChild("message", "Expression")
        property<AssertKind>("kind", defaultValue = AssertKind.ASSERT)
    }
    
    concept("BreakStatement") {
        extends("Statement")
        property<String?>("label", optional = true)
    }
    
    concept("ContinueStatement") {
        extends("Statement")
        property<String?>("label", optional = true)
    }
    
    concept("UsingStatement") {
        extends("Statement")
        child("resource", "Expression")
        child("body", "Statement")
    }
    
    // ========================================================================
    // AST Concepts - Expressions
    // ========================================================================
    
    abstractConcept("Expression") {}
    
    concept("LiteralExpression") {
        extends("Expression")
        property<Any>("value")
        property<LiteralKind>("kind")
    }
    
    concept("IdentifierExpression") {
        extends("Expression")
        property<String>("name")
    }
    
    concept("QualifiedExpression") {
        extends("Expression")
        child("qualifier", "Expression")
        property<String>("member")
        property<Boolean>("nullSafe", defaultValue = false)
    }
    
    concept("ThisExpression") {
        extends("Expression")
        property<String?>("targetType", optional = true)
    }
    
    concept("SuperExpression") {
        extends("Expression")
        property<String?>("targetType", optional = true)
    }
    
    concept("BinaryExpression") {
        extends("Expression")
        child("left", "Expression")
        property<String>("operator")
        child("right", "Expression")
    }
    
    concept("UnaryExpression") {
        extends("Expression")
        property<String>("operator")
        child("operand", "Expression")
        property<Boolean>("isPrefix", defaultValue = true)
    }
    
    concept("TernaryExpression") {
        extends("Expression")
        child("condition", "Expression")
        child("thenExpr", "Expression")
        child("elseExpr", "Expression")
    }
    
    concept("ElvisExpression") {
        extends("Expression")
        child("left", "Expression")
        child("right", "Expression")
    }
    
    concept("AssignmentExpression") {
        extends("Expression")
        child("target", "Expression")
        property<String>("operator")  // =, +=, -=, etc.
        child("value", "Expression")
    }
    
    concept("InvocationExpression") {
        extends("Expression")
        child("target", "Expression")
        children("typeArguments", "TypeExpression")
        children("arguments", "Argument")
    }
    
    concept("Argument") {
        property<String?>("name", optional = true)  // Named argument
        child("value", "Expression")
    }
    
    concept("IndexExpression") {
        extends("Expression")
        child("target", "Expression")
        children("indices", "Expression")
    }
    
    concept("NewExpression") {
        extends("Expression")
        child("type", "TypeExpression")
        children("arguments", "Argument")
        optionalChild("body", "ClassBody")  // Anonymous class
    }
    
    concept("LambdaExpression") {
        extends("Expression")
        children("parameters", "LambdaParameter")
        child("body", "Expression")  // Or StatementBlock
    }
    
    concept("LambdaParameter") {
        property<String>("name")
        optionalChild("type", "TypeExpression")
    }
    
    concept("TypeCheckExpression") {
        extends("Expression")
        child("expression", "Expression")
        property<TypeCheckKind>("kind")  // IS, AS
        child("type", "TypeExpression")
    }
    
    concept("RangeExpression") {
        extends("Expression")
        child("start", "Expression")
        child("end", "Expression")
        property<Boolean>("inclusive", defaultValue = true)
    }
    
    concept("ListExpression") {
        extends("Expression")
        optionalChild("elementType", "TypeExpression")
        children("elements", "Expression")
    }
    
    concept("MapExpression") {
        extends("Expression")
        optionalChild("keyType", "TypeExpression")
        optionalChild("valueType", "TypeExpression")
        children("entries", "MapEntry")
    }
    
    concept("MapEntry") {
        child("key", "Expression")
        child("value", "Expression")
    }
    
    concept("TupleExpression") {
        extends("Expression")
        children("elements", "Expression")
    }
    
    concept("TemplateExpression") {
        extends("Expression")
        children("parts", "TemplatePart")
    }
    
    abstractConcept("TemplatePart") {}
    
    concept("TemplateStringPart") {
        extends("TemplatePart")
        property<String>("text")
    }
    
    concept("TemplateExpressionPart") {
        extends("TemplatePart")
        child("expression", "Expression")
    }
    
    concept("SwitchExpression") {
        extends("Expression")
        optionalChild("expression", "Expression")
        children("cases", "SwitchExpressionCase")
    }
    
    concept("SwitchExpressionCase") {
        children("patterns", "Expression")
        property<Boolean>("isDefault", defaultValue = false)
        child("result", "Expression")
    }
    
    // ========================================================================
    // AST Concepts - Annotations & Conditions
    // ========================================================================
    
    concept("Annotation") {
        property<String>("name")
        children("arguments", "Argument")
    }
    
    concept("ConditionalClause") {
        child("condition", "Expression")
    }
    
    concept("IncorporatesClause") {
        child("mixin", "TypeExpression")
        children("arguments", "Argument")
        property<Boolean>("isConditional", defaultValue = false)
    }
    
    concept("DocComment") {
        property<String>("text")
        children("tags", "DocTag")
    }
    
    concept("DocTag") {
        property<String>("name")  // @param, @return, etc.
        property<String>("content")
    }
}

// ========================================================================
// Supporting Enums
// ========================================================================

enum class Visibility { PUBLIC, PROTECTED, PRIVATE }
enum class AccessorKind { GET, SET }
enum class ConstructorKind { CONSTRUCT, FINALLY }
enum class AssertKind { ASSERT, ASSERT_RND, ASSERT_ARG, ASSERT_BOUNDS, ASSERT_TODO, ASSERT_ONCE, ASSERT_TEST, ASSERT_DBG }
enum class LiteralKind { INT, FLOAT, STRING, CHAR, BOOLEAN, NULL, BINARY, DATE, TIME, DURATION, VERSION, PATH }
enum class TypeCheckKind { IS, AS }
```

### Using the Language Model

```kotlin
package org.xtclang.dsl

/**
 * Example: Generate sealed class hierarchy from concept definitions
 */
fun LanguageModel.generateAstClasses(): String {
    val sb = StringBuilder()
    sb.appendLine("// Auto-generated AST classes for $name")
    sb.appendLine("package org.xtclang.ast")
    sb.appendLine()
    
    // Generate base sealed interface
    sb.appendLine("sealed interface AstNode {")
    sb.appendLine("    val sourceRange: SourceRange?")
    sb.appendLine("}")
    sb.appendLine()
    
    // Generate classes for each concept
    concepts.values.forEach { concept ->
        val sealed = if (concept.isAbstract) "sealed " else ""
        val extends = concept.extends?.let { ": $it()" } ?: ": AstNode"
        
        sb.appendLine("${sealed}class ${concept.name}(")
        
        // Properties
        concept.properties.forEach { prop ->
            val nullable = if (prop.optional) "?" else ""
            val default = prop.defaultValue?.let { " = $it" } ?: if (prop.optional) " = null" else ""
            sb.appendLine("    val ${prop.name}: ${prop.type.simpleName}$nullable$default,")
        }
        
        // Children
        concept.children.forEach { child ->
            val type = when (child.cardinality) {
                Cardinality.ONE -> child.conceptType
                Cardinality.OPTIONAL -> "${child.conceptType}?"
                Cardinality.MANY -> "List<${child.conceptType}>"
            }
            val default = when (child.cardinality) {
                Cardinality.OPTIONAL -> " = null"
                Cardinality.MANY -> " = emptyList()"
                else -> ""
            }
            sb.appendLine("    val ${child.name}: $type$default,")
        }
        
        sb.appendLine("    override val sourceRange: SourceRange? = null")
        sb.appendLine(") $extends")
        sb.appendLine()
    }
    
    return sb.toString()
}

/**
 * Example: Generate completion provider from keywords and concepts
 */
fun LanguageModel.generateCompletionItems(context: CompletionContext): List<CompletionItem> {
    val items = mutableListOf<CompletionItem>()
    
    // Add keywords
    keywords.filter { it.startsWith(context.prefix) }.forEach {
        items.add(CompletionItem(it, CompletionKind.KEYWORD))
    }
    
    // Add type names from concepts
    concepts.values
        .filter { !it.isAbstract && it.name.startsWith(context.prefix) }
        .forEach { items.add(CompletionItem(it.name, CompletionKind.TYPE)) }
    
    return items
}

data class CompletionContext(val prefix: String, val position: Position)
data class CompletionItem(val text: String, val kind: CompletionKind)
enum class CompletionKind { KEYWORD, TYPE, PROPERTY, METHOD, VARIABLE }
data class Position(val line: Int, val column: Int)
data class SourceRange(val start: Position, val end: Position)
```

### Serializing AST with Klaxon

```kotlin
package org.xtclang.dsl

import com.beust.klaxon.*

/**
 * Serialize/deserialize XTC AST to JSON using Klaxon
 */
object AstSerializer {
    private val klaxon = Klaxon()
        .converter(VisibilityConverter)
        .converter(LiteralKindConverter)
    
    fun toJson(node: Any): String = klaxon.toJsonString(node)
    
    inline fun <reified T> fromJson(json: String): T? = klaxon.parse<T>(json)
}

object VisibilityConverter : Converter {
    override fun canConvert(cls: Class<*>) = cls == Visibility::class.java
    override fun fromJson(jv: JsonValue) = Visibility.valueOf(jv.string!!)
    override fun toJson(value: Any) = "\"${(value as Visibility).name}\""
}

object LiteralKindConverter : Converter {
    override fun canConvert(cls: Class<*>) = cls == LiteralKind::class.java
    override fun fromJson(jv: JsonValue) = LiteralKind.valueOf(jv.string!!)
    override fun toJson(value: Any) = "\"${(value as LiteralKind).name}\""
}

// Example usage:
fun main() {
    // Build AST programmatically
    val classDecl = mapOf(
        "name" to "Person",
        "visibility" to "PUBLIC",
        "isAbstract" to false,
        "members" to listOf(
            mapOf(
                "type" to "PropertyDeclaration",
                "name" to "name",
                "propertyType" to mapOf("name" to "String")
            )
        )
    )
    
    println(Klaxon().toJsonString(classDecl))
}
```

---

## Part 4: Generating IDE Support from the Model

The power of a reflective DSL is that you can derive tooling automatically:

```kotlin
package org.xtclang.tooling

import org.xtclang.dsl.*

/**
 * Generate LSP-compatible document symbols from the language model
 */
class SymbolProvider(private val model: LanguageModel) {
    
    fun getDocumentSymbols(ast: Any): List<DocumentSymbol> {
        return when (ast) {
            is Map<*, *> -> processNode(ast as Map<String, Any?>)
            is List<*> -> ast.flatMap { getDocumentSymbols(it!!) }
            else -> emptyList()
        }
    }
    
    private fun processNode(node: Map<String, Any?>): List<DocumentSymbol> {
        val type = node["type"] as? String ?: return emptyList()
        val concept = model.concepts[type] ?: return emptyList()
        
        val symbols = mutableListOf<DocumentSymbol>()
        
        // If this concept has a name property, it's a symbol
        if (concept.properties.any { it.name == "name" }) {
            val name = node["name"] as? String ?: "<anonymous>"
            symbols.add(DocumentSymbol(
                name = name,
                kind = conceptToSymbolKind(type),
                children = concept.children
                    .filter { it.cardinality == Cardinality.MANY }
                    .flatMap { child ->
                        (node[child.name] as? List<*>)
                            ?.flatMap { getDocumentSymbols(it!!) }
                            ?: emptyList()
                    }
            ))
        }
        
        return symbols
    }
    
    private fun conceptToSymbolKind(concept: String) = when {
        concept.endsWith("Declaration") && concept.contains("Class") -> SymbolKind.CLASS
        concept.endsWith("Declaration") && concept.contains("Interface") -> SymbolKind.INTERFACE
        concept.endsWith("Declaration") && concept.contains("Method") -> SymbolKind.METHOD
        concept.endsWith("Declaration") && concept.contains("Property") -> SymbolKind.PROPERTY
        concept.endsWith("Declaration") && concept.contains("Module") -> SymbolKind.MODULE
        else -> SymbolKind.VARIABLE
    }
}

data class DocumentSymbol(
    val name: String,
    val kind: SymbolKind,
    val children: List<DocumentSymbol> = emptyList()
)

enum class SymbolKind {
    MODULE, CLASS, INTERFACE, METHOD, PROPERTY, VARIABLE, ENUM, CONSTANT
}
```

---

## Summary

| Library | Purpose | Use Case |
|---------|---------|----------|
| **kotlinx.ast** | Parse Kotlin source → AST | Analyzing existing Kotlin code |
| **Klaxon** | JSON ↔ Kotlin objects | Serializing AST, config files |
| **Reflective DSL** | Define language model | IDE support, parser generation, validation |

The XTC language model DSL above captures:
- 50+ keywords
- 30+ operators with precedence
- 60+ AST concepts covering the full language
- Type expressions, statements, expressions
- Annotations and conditional compilation

This model can drive:
- Parser generation (ANTLR grammar output)
- AST class generation
- LSP server features (completion, symbols, navigation)
- Validation rules
- Code formatting
- Refactoring operations

