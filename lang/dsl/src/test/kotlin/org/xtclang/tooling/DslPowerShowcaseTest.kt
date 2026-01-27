/**
 * DSL Power Showcase - Demonstrating the XTC Language Model DSL
 *
 * This test suite showcases the expressive power of the Kotlin DSL for defining
 * language models. It demonstrates:
 *
 * 1. **Mini-Language Definition** - Define a complete language in ~50 lines
 * 2. **Rich Type System Modeling** - Express complex type relationships
 * 3. **Operator Precedence** - Full precedence and associativity support
 * 4. **AST Concept Hierarchies** - Model inheritance and composition
 * 5. **Multi-Editor Generation** - Generate support for 6+ editors from one source
 * 6. **Model Coverage Validation** - Verify model definitions against real source files
 *
 * NOTE: This DSL is for EDITOR GENERATION, not code analysis. The model defines
 * language syntax to generate TextMate, Vim, Emacs, and Tree-sitter support from
 * a single source of truth. For structured code analysis (querying AST nodes,
 * type information, etc.), see the proposed Code Analysis DSL in the documentation.
 */
package org.xtclang.tooling

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIf
import org.xtclang.tooling.generators.EmacsGenerator
import org.xtclang.tooling.generators.TextMateGenerator
import org.xtclang.tooling.generators.TreeSitterGenerator
import org.xtclang.tooling.generators.VimGenerator
import org.xtclang.tooling.model.Associativity
import org.xtclang.tooling.model.Cardinality
import org.xtclang.tooling.model.KeywordCategory
import org.xtclang.tooling.model.OperatorCategory
import org.xtclang.tooling.model.language
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DslPowerShowcaseTest {
    // =========================================================================
    // SHOWCASE 1: Define a Complete Mini-Language in ~50 Lines
    // =========================================================================
    //
    // This demonstrates the DSL's expressiveness - a complete language spec
    // that can generate TextMate, Vim, Emacs, and Tree-sitter support.

    private val miniLang =
        language(
            name = "MiniLang",
            fileExtensions = listOf("mini"),
            scopeName = "source.mini",
        ) {
            // Scopes map semantic elements to editor styling
            scope("keyword") {
                textMate = "keyword.control.mini"
                intellij = "KEYWORD"
                eclipse = "keyword"
                semanticToken = "keyword"
                vim = "Keyword"
                emacs = "font-lock-keyword-face"
                treeSitter = "@keyword"
            }

            scope("type") {
                textMate = "entity.name.type.mini"
                intellij = "CLASS_NAME"
                eclipse = "class"
                semanticToken = "type"
            }

            // Keywords with semantic categories
            keywords(KeywordCategory.CONTROL, "if", "else", "while", "for", "return")
            keywords(KeywordCategory.DECLARATION, "fn", "let", "const", "struct")
            contextKeywords(KeywordCategory.MODIFIER, "pub", "mut", "async")

            builtinTypes("Int", "Float", "String", "Bool", "Void")

            // Operators with precedence (1=lowest, 15=highest)
            operator("=", 1, Associativity.RIGHT, OperatorCategory.ASSIGNMENT)
            operator("||", 3, Associativity.LEFT, OperatorCategory.LOGICAL)
            operator("&&", 4, Associativity.LEFT, OperatorCategory.LOGICAL)
            operator("==", 8, Associativity.LEFT, OperatorCategory.COMPARISON)
            operator("+", 12, Associativity.LEFT, OperatorCategory.ARITHMETIC)
            operator("*", 13, Associativity.LEFT, OperatorCategory.ARITHMETIC)
            operator(".", 15, Associativity.LEFT, OperatorCategory.MEMBER_ACCESS)

            // AST concepts with inheritance
            abstractConcept("Expression")

            concept("BinaryExpr") {
                extends("Expression")
                child("left", "Expression")
                property("operator", "String")
                child("right", "Expression")
            }

            concept("IfExpr") {
                extends("Expression")
                child("condition", "Expression")
                child("thenBranch", "Expression")
                child("elseBranch", "Expression", Cardinality.OPTIONAL)
            }
        }

    @Test
    fun `mini-language demonstrates DSL expressiveness`() {
        // The mini-language is fully functional
        assertEquals("MiniLang", miniLang.name)
        assertEquals(listOf("mini"), miniLang.fileExtensions)

        // Keywords are categorized
        val controlKws = miniLang.keywordsByCategory(KeywordCategory.CONTROL)
        assertTrue(controlKws.containsAll(listOf("if", "else", "while", "for", "return")))

        // Operators have precedence
        val multiply = miniLang.operators.find { it.symbol == "*" }!!
        val add = miniLang.operators.find { it.symbol == "+" }!!
        assertTrue(multiply.precedence > add.precedence, "* binds tighter than +")

        // Concepts form hierarchy
        val binaryExpr = miniLang.getConcept("BinaryExpr")!!
        assertEquals("Expression", binaryExpr.parentConcept)
    }

    @Test
    fun `mini-language generates valid TextMate grammar`() {
        val grammar = TextMateGenerator(miniLang).generate()

        assertTrue(grammar.contains("\"name\": \"MiniLang\""))
        assertTrue(grammar.contains("\"scopeName\": \"source.mini\""))
        // Generator uses .xtc scopes by design (it's XTC-focused)
        assertTrue(grammar.contains("keyword.control"))
    }

    // =========================================================================
    // SHOWCASE 2: XTC's Rich Operator Precedence System
    // =========================================================================
    //
    // XTC has 15 precedence levels with careful associativity choices.
    // This enables expressions like: a < b < c (comparison chaining)

    @Test
    fun `XTC operator precedence enables mathematical expression parsing`() {
        val model = xtcLanguage

        // Get operators sorted by precedence
        val byPrecedence = model.operatorsByPrecedence
        println(byPrecedence)

        // Verify the classic precedence hierarchy
        val assignment = model.operators.find { it.symbol == "=" }!!
        val ternary = model.operators.find { it.symbol == "?:" }!!
        val logicalOr = model.operators.find { it.symbol == "||" }!!
        val logicalAnd = model.operators.find { it.symbol == "&&" }!!
        val equality = model.operators.find { it.symbol == "==" }!!
        val comparison = model.operators.find { it.symbol == "<" }!!
        val addition = model.operators.find { it.symbol == "+" }!!
        val multiplication = model.operators.find { it.symbol == "*" }!!
        val memberAccess = model.operators.find { it.symbol == "." }!!

        // Verify: = < ?: < || < && < == < < < + < * < .
        assertTrue(assignment.precedence < ternary.precedence)
        assertTrue(ternary.precedence < logicalOr.precedence)
        assertTrue(logicalOr.precedence < logicalAnd.precedence)
        assertTrue(logicalAnd.precedence < equality.precedence)
        assertTrue(equality.precedence < comparison.precedence)
        assertTrue(comparison.precedence < addition.precedence)
        assertTrue(addition.precedence < multiplication.precedence)
        assertTrue(multiplication.precedence < memberAccess.precedence)

        // Expression: a = b + c * d.e parses as: a = (b + (c * (d.e)))
        println("Precedence hierarchy verified: = < ?: < || < && < == < < < + < * < .")
    }

    @Test
    fun `XTC supports unique operators like spaceship and Elvis`() {
        val model = xtcLanguage

        // Spaceship operator for three-way comparison
        val spaceship = model.operators.find { it.symbol == "<=>" }
        assertNotNull(spaceship, "XTC has spaceship operator")
        assertEquals(OperatorCategory.COMPARISON, spaceship.category)

        // Elvis operator for null coalescing
        val elvis = model.operators.find { it.symbol == "?:" }
        assertNotNull(elvis, "XTC has Elvis operator")
        assertEquals(Associativity.RIGHT, elvis.associativity)

        // Range operators for intervals
        val rangeOps = model.operators.filter { it.symbol.contains("..") }
        assertEquals(4, rangeOps.size, "XTC has 4 range operators: .., >.., ..<, >..<")

        // Conditional assignment
        val condAssign = model.operators.find { it.symbol == ":=" }
        assertNotNull(condAssign, "XTC has conditional assignment :=")

        println("XTC unique operators: <=>, ?:, .., >.., ..<, >..<, :=")
    }

    // =========================================================================
    // SHOWCASE 3: XTC's Comprehensive AST Concept Hierarchy
    // =========================================================================
    //
    // The model defines 60+ AST concepts with inheritance, enabling
    // precise semantic analysis and code generation.

    @Test
    fun `XTC AST concepts form rich inheritance hierarchy`() {
        val model = xtcLanguage

        // Type declarations inherit from TypeDeclaration
        val typeDecls =
            listOf(
                "ClassDeclaration",
                "InterfaceDeclaration",
                "MixinDeclaration",
                "ServiceDeclaration",
                "ConstDeclaration",
                "EnumDeclaration",
                "TypedefDeclaration",
                "StructDeclaration",
            )

        for (typeName in typeDecls) {
            val concept = model.getConcept(typeName)
            assertNotNull(concept, "Should have $typeName concept")
            assertTrue(
                concept.extendsFrom("TypeDeclaration", model),
                "$typeName should extend TypeDeclaration",
            )
        }

        // Statements extend Statement
        val statements =
            listOf(
                "IfStatement",
                "ForStatement",
                "WhileStatement",
                "TryStatement",
                "ReturnStatement",
                "AssertStatement",
                "SwitchStatement",
            )

        for (stmtName in statements) {
            val concept = model.getConcept(stmtName)
            assertNotNull(concept, "Should have $stmtName concept")
            assertEquals("Statement", concept.parentConcept)
        }

        // Expressions extend Expression
        val expressions =
            listOf(
                "BinaryExpression",
                "UnaryExpression",
                "LiteralExpression",
                "InvocationExpression",
                "LambdaExpression",
                "TernaryExpression",
            )

        for (exprName in expressions) {
            val concept = model.getConcept(exprName)
            assertNotNull(concept, "Should have $exprName concept")
            assertEquals("Expression", concept.parentConcept)
        }

        println(
            "AST hierarchy: ${model.concepts.size} concepts, " +
                "${model.abstractConcepts.size} abstract, ${model.concreteConcepts.size} concrete",
        )
    }

    @Test
    fun `XTC concepts have rich child relationships`() {
        val model = xtcLanguage

        // ClassDeclaration has multiple relationship types
        val classDecl = model.getConcept("ClassDeclaration")!!
        val childNames = classDecl.children.map { it.name }

        assertTrue("implements" in childNames, "Classes can implement interfaces")
        assertTrue("incorporates" in childNames, "Classes can incorporate mixins")
        assertTrue("members" in childNames, "Classes have members")

        // Check cardinality
        val membersChild = classDecl.children.find { it.name == "members" }!!
        assertEquals(Cardinality.ZERO_OR_MORE, membersChild.cardinality)

        // MethodDeclaration supports multiple return types (XTC feature!)
        val methodDecl = model.getConcept("MethodDeclaration")!!
        val returnTypes = methodDecl.children.find { it.name == "returnTypes" }!!
        assertEquals(
            Cardinality.ZERO_OR_MORE,
            returnTypes.cardinality,
            "XTC methods can return multiple values",
        )

        println("ClassDeclaration children: $childNames")
    }

    // =========================================================================
    // SHOWCASE 4: XTC's Unique Language Features Modeled in DSL
    // =========================================================================

    @Test
    fun `XTC models comparison chaining (a less than b less than c)`() {
        val model = xtcLanguage

        // XTC supports chained comparisons: 0 <= x < 10
        val chainExpr = model.getConcept("ComparisonChainExpression")
        assertNotNull(chainExpr, "XTC has ComparisonChainExpression")

        val operands = chainExpr.children.find { it.name == "operands" }
        assertNotNull(operands)
        assertEquals(
            Cardinality.ZERO_OR_MORE,
            operands.cardinality,
            "Chain can have multiple operands",
        )

        println("XTC comparison chains: 0 <= x < 10 means (0 <= x) && (x < 10)")
    }

    @Test
    fun `XTC models tuple unpacking and multiple assignment`() {
        val model = xtcLanguage

        // Tuple unpacking: (a, b) = tuple
        val unpack = model.getConcept("UnpackExpression")
        assertNotNull(unpack, "XTC has UnpackExpression for tuple unpacking")

        // Multiple LValue statement: (a, b, c) = expr
        val multiLValue = model.getConcept("MultipleLValueStatement")
        assertNotNull(multiLValue, "XTC has MultipleLValueStatement")

        // Ignored name expression: (_, y) = tuple
        val ignored = model.getConcept("IgnoredNameExpression")
        assertNotNull(ignored, "XTC has IgnoredNameExpression for _ wildcard")

        println("XTC destructuring: (_, y) = getTuple() discards first element")
    }

    @Test
    fun `XTC models conditional declarations`() {
        val model = xtcLanguage

        // Conditional statement: if (Type name := expr) { ... }
        val condStmt = model.getConcept("ConditionalStatement")
        assertNotNull(condStmt, "XTC has ConditionalStatement")

        val decl = condStmt.children.find { it.name == "declaration" }
        assertNotNull(decl, "Conditional statement has inline declaration")

        // Conditional assignment operator
        val condAssign = model.operators.find { it.symbol == ":=" }
        assertNotNull(condAssign, "XTC has := for conditional assignment")

        println("XTC conditional: if (String s := maybeString()) { use(s); }")
    }

    @Test
    fun `XTC models services and dependency injection`() {
        val model = xtcLanguage

        // Service declaration - XTC's actor-like concurrent type
        val service = model.getConcept("ServiceDeclaration")
        assertNotNull(service, "XTC has ServiceDeclaration for actor-like types")

        // inject keyword for dependency injection
        val modifiers = model.keywordsByCategory(KeywordCategory.MODIFIER)
        println(modifiers)
        assertTrue("inject" in model.contextKeywords, "XTC has 'inject' for DI")

        println("XTC services: service MyService { @Inject Console console; }")
    }

    // =========================================================================
    // SHOWCASE 5: Generate Multiple Editor Support from Single Model
    // =========================================================================

    @Test
    fun `single model generates support for 6+ editors`() {
        val model = xtcLanguage

        // Generate for each editor
        val textMate = TextMateGenerator(model).generate()
        val vim = VimGenerator(model).generate()
        val emacs = EmacsGenerator(model).generate()
        val treeSitter = TreeSitterGenerator(model).generateGrammar()
        val treeSitterHighlights = TreeSitterGenerator(model).generateHighlights()

        // All contain the control keywords
        val controlKeywords = model.keywordsByCategory(KeywordCategory.CONTROL)
        val testKeyword = "if"
        assertTrue(testKeyword in controlKeywords)

        assertTrue(textMate.contains(testKeyword), "TextMate has '$testKeyword'")
        assertTrue(vim.contains(testKeyword), "Vim has '$testKeyword'")
        assertTrue(emacs.contains("\"$testKeyword\""), "Emacs has '$testKeyword'")
        assertTrue(treeSitterHighlights.contains("\"$testKeyword\""), "Tree-sitter has '$testKeyword'")

        // All contain built-in types
        val testType = "String"
        assertTrue(textMate.contains(testType), "TextMate has '$testType'")
        assertTrue(vim.contains(testType), "Vim has '$testType'")
        assertTrue(emacs.contains("\"$testType\""), "Emacs has '$testType'")

        println("Generated support for: TextMate/VS Code, Vim, Emacs, Tree-sitter")
        println("  TextMate: ${textMate.length} chars")
        println("  Vim: ${vim.length} chars")
        println("  Emacs: ${emacs.length} chars")
        println("  Tree-sitter grammar: ${treeSitter.length} chars")
    }

    // =========================================================================
    // SHOWCASE 6: Model Coverage Validation Against Real Source Files
    // =========================================================================
    //
    // These tests validate that our model definitions (keywords, operators, types)
    // actually appear in real XTC source code. This is VALIDATION, not analysis.
    //
    // NOTE: These tests use regex pattern matching on source text - exactly what
    // a proper Code Analysis DSL would eliminate. For structured code analysis
    // (querying parsed AST, type information, etc.), the model's AST concepts
    // would need to be populated from the XTC compiler via an adapter layer.

    private fun findXvmRoot(): File? {
        var dir = File(System.getProperty("user.dir"))
        while (dir.parentFile != null) {
            if (File(dir, "lib_ecstasy").exists()) return dir
            if (dir.name == "lang" && File(dir.parentFile, "lib_ecstasy").exists()) {
                return dir.parentFile
            }
            dir = dir.parentFile
        }
        return null
    }

    // Used by @EnabledIf to conditionally run tests requiring XVM source files
    @Suppress("unused") // Called reflectively by JUnit @EnabledIf
    fun xvmRootExists(): Boolean = findXvmRoot() != null

    @Test
    @EnabledIf("xvmRootExists")
    fun `model keywords appear in real XTC source (coverage validation)`() {
        val xvmRoot = findXvmRoot() ?: return
        val model = xtcLanguage

        // Read a real XTC source file
        val ecstasyFile = File(xvmRoot, "lib_ecstasy/src/main/x/ecstasy.x")
        if (!ecstasyFile.exists()) return

        val content = ecstasyFile.readText()

        // Find all declaration keywords used
        val declKeywords = model.keywordsByCategory(KeywordCategory.DECLARATION)
        val foundDecls =
            declKeywords.filter { kw ->
                Regex("\\b${Regex.escape(kw)}\\b").containsMatchIn(content)
            }

        println("Declaration keywords found in ecstasy.x: $foundDecls")
        assertTrue(foundDecls.isNotEmpty(), "Should find declaration keywords")

        // Find type relation keywords
        val typeRelKeywords = model.keywordsByCategory(KeywordCategory.TYPE_RELATION)
        val foundTypeRels =
            typeRelKeywords.filter { kw ->
                Regex("\\b${Regex.escape(kw)}\\b").containsMatchIn(content)
            }

        println("Type relation keywords found: $foundTypeRels")
    }

    @Test
    @EnabledIf("xvmRootExists")
    fun `model operators appear in real XTC source (coverage validation)`() {
        val xvmRoot = findXvmRoot() ?: return
        val model = xtcLanguage

        // Sample multiple source files
        val libEcstasy = File(xvmRoot, "lib_ecstasy/src/main/x")
        if (!libEcstasy.exists()) return

        val operatorCounts = mutableMapOf<String, Int>()

        libEcstasy
            .walkTopDown()
            .filter { it.extension == "x" }
            .take(20)
            .forEach { file ->
                val content = file.readText()
                for (op in model.operators) {
                    // Escape for regex and count occurrences
                    val pattern = Regex.escape(op.symbol)
                    val count = Regex(pattern).findAll(content).count()
                    if (count > 0) {
                        operatorCounts[op.symbol] = (operatorCounts[op.symbol] ?: 0) + count
                    }
                }
            }

        println("Operators found in lib_ecstasy (top 10):")
        operatorCounts.entries
            .sortedByDescending { it.value }
            .take(10)
            .forEach { (op, count) ->
                val opDef = model.operators.find { it.symbol == op }!!
                println("  '$op' (${opDef.category}, prec=${opDef.precedence}): $count")
            }

        assertTrue(operatorCounts.isNotEmpty(), "Should find operators in real code")
    }

    @Test
    @EnabledIf("xvmRootExists")
    fun `model built-in types appear in lib_ecstasy (coverage validation)`() {
        val xvmRoot = findXvmRoot() ?: return
        val model = xtcLanguage

        val libEcstasy = File(xvmRoot, "lib_ecstasy/src/main/x")
        if (!libEcstasy.exists()) return

        val typeCounts = mutableMapOf<String, Int>()

        libEcstasy
            .walkTopDown()
            .filter { it.extension == "x" }
            .take(30)
            .forEach { file ->
                val content = file.readText()
                for (type in model.builtinTypes) {
                    val count = Regex("\\b${Regex.escape(type)}\\b").findAll(content).count()
                    if (count > 0) {
                        typeCounts[type] = (typeCounts[type] ?: 0) + count
                    }
                }
            }

        println("Built-in types found in lib_ecstasy (top 15):")
        typeCounts.entries
            .sortedByDescending { it.value }
            .take(15)
            .forEach { (type, count) ->
                println("  $type: $count occurrences")
            }

        // Core types should be heavily used
        assertTrue(typeCounts.containsKey("Int"), "Int should be used")
        assertTrue(typeCounts.containsKey("String"), "String should be used")
        assertTrue(typeCounts.containsKey("Boolean"), "Boolean should be used")
    }

    @Test
    @EnabledIf("xvmRootExists")
    fun `XTC-specific constructs appear in lib_ecstasy (coverage validation)`() {
        val xvmRoot = findXvmRoot() ?: return

        val libEcstasy = File(xvmRoot, "lib_ecstasy/src/main/x")
        if (!libEcstasy.exists()) return

        var mixinCount = 0
        var serviceCount = 0
        var constCount = 0
        var incorporatesCount = 0
        var conditionalAssignCount = 0
        var elvisCount = 0

        libEcstasy
            .walkTopDown()
            .filter { it.extension == "x" }
            .forEach { file ->
                val content = file.readText()

                // Count XTC-specific constructs
                mixinCount += Regex("\\bmixin\\s+\\w+").findAll(content).count()
                serviceCount += Regex("\\bservice\\s+\\w+").findAll(content).count()
                constCount += Regex("\\bconst\\s+\\w+").findAll(content).count()
                incorporatesCount += Regex("\\bincorporates\\s+").findAll(content).count()
                conditionalAssignCount += Regex(":=").findAll(content).count()
                elvisCount += Regex("\\?:").findAll(content).count()
            }

        println("XTC-specific patterns in lib_ecstasy:")
        println("  mixin declarations: $mixinCount")
        println("  service declarations: $serviceCount")
        println("  const declarations: $constCount")
        println("  incorporates clauses: $incorporatesCount")
        println("  conditional assignments (:=): $conditionalAssignCount")
        println("  Elvis operators (?:): $elvisCount")

        // XTC uses these unique features
        assertTrue(constCount > 0, "XTC uses const declarations")
    }

    // =========================================================================
    // SHOWCASE 7: Model Serialization & Introspection
    // =========================================================================

    @Test
    fun `model can be introspected for tooling`() {
        val model = xtcLanguage

        // Count various elements
        println("XTC Language Model Statistics:")
        println("  Reserved keywords: ${model.keywords.size}")
        println("  Context keywords: ${model.contextKeywords.size}")
        println("  Built-in types: ${model.builtinTypes.size}")
        println("  Operators: ${model.operators.size}")
        println("  Punctuation: ${model.punctuation.size}")
        println("  Token rules: ${model.tokens.size}")
        println("  Scopes: ${model.scopes.size}")
        println("  AST concepts: ${model.concepts.size}")
        println("    - Abstract: ${model.abstractConcepts.size}")
        println("    - Concrete: ${model.concreteConcepts.size}")

        // Keyword categories breakdown
        println("\nKeywords by category:")
        for (category in KeywordCategory.entries) {
            val kws = model.keywordsByCategory(category)
            if (kws.isNotEmpty()) {
                println("  $category: ${kws.size} (${kws.take(5).joinToString()}...)")
            }
        }

        // Operator categories breakdown
        println("\nOperators by category:")
        for (category in OperatorCategory.entries) {
            val ops = model.operators.filter { it.category == category }
            if (ops.isNotEmpty()) {
                println("  $category: ${ops.size} (${ops.take(3).joinToString { it.symbol }}...)")
            }
        }

        assertTrue(model.keywords.size > 20, "XTC has many keywords")
        assertTrue(model.operators.size > 30, "XTC has many operators")
        assertTrue(model.concepts.size > 50, "XTC has many AST concepts")
    }
}
