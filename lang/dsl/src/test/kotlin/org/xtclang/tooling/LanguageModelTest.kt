/**
 * Test suite for the XTC Language Model.
 *
 * These tests validate:
 * 1. The language model DSL correctly builds the model
 * 2. The model contains all expected language elements
 * 3. The model can be used to recognize patterns in real .x source files
 * 4. All generators produce valid output from the model
 */
package org.xtclang.tooling

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIf
import org.xtclang.tooling.generators.EmacsGenerator
import org.xtclang.tooling.generators.TextMateGenerator
import org.xtclang.tooling.generators.TreeSitterGenerator
import org.xtclang.tooling.generators.VSCodeConfigGenerator
import org.xtclang.tooling.generators.VimGenerator
import org.xtclang.tooling.model.Cardinality
import org.xtclang.tooling.model.KeywordCategory
import org.xtclang.tooling.model.OperatorCategory
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LanguageModelTest {
    private val model = xtcLanguage

    // =========================================================================
    // Basic Model Tests
    // =========================================================================

    @Test
    fun `language model has correct metadata`() {
        assertEquals("Ecstasy", model.name)
        assertTrue(model.fileExtensions.contains("x"))
        assertTrue(model.fileExtensions.contains("xtc"))
        assertEquals("source.xtc", model.scopeName)
    }

    @Test
    fun `language model has keywords`() {
        assertTrue(model.keywords.isNotEmpty(), "Should have reserved keywords")
        assertTrue(model.contextKeywords.isNotEmpty(), "Should have context keywords")

        // Check some essential keywords
        assertTrue("if" in model.keywords, "Should have 'if' keyword")
        assertTrue("else" in model.keywords, "Should have 'else' keyword")
        assertTrue("class" in model.contextKeywords, "Should have 'class' context keyword")
        assertTrue("interface" in model.contextKeywords, "Should have 'interface' context keyword")
    }

    @Test
    fun `language model has operators with correct precedence`() {
        assertTrue(model.operators.isNotEmpty(), "Should have operators")

        // Check operator precedence ordering
        val assignment = model.operators.find { it.symbol == "=" }
        val addition = model.operators.find { it.symbol == "+" }
        val multiplication = model.operators.find { it.symbol == "*" }
        val memberAccess = model.operators.find { it.symbol == "." }

        assertNotNull(assignment)
        assertNotNull(addition)
        assertNotNull(multiplication)
        assertNotNull(memberAccess)

        // Precedence should be: = < + < * < .
        assertTrue(assignment.precedence < addition.precedence, "Assignment should have lower precedence than addition")
        assertTrue(addition.precedence < multiplication.precedence, "Addition should have lower precedence than multiplication")
        assertTrue(multiplication.precedence < memberAccess.precedence, "Multiplication should have lower precedence than member access")
    }

    @Test
    fun `language model has assignment operators`() {
        val assignmentOps = model.operators.filter { it.category == OperatorCategory.ASSIGNMENT }
        assertTrue(assignmentOps.isNotEmpty(), "Should have assignment operators")

        val symbols = assignmentOps.map { it.symbol }
        assertTrue("=" in symbols)
        assertTrue("+=" in symbols)
        assertTrue("-=" in symbols)
        assertTrue(":=" in symbols, "Should have conditional assignment")
    }

    @Test
    fun `language model has comparison operators`() {
        val comparisonOps = model.operators.filter { it.category == OperatorCategory.COMPARISON }

        val symbols = comparisonOps.map { it.symbol }
        assertTrue("==" in symbols)
        assertTrue("!=" in symbols)
        assertTrue("<" in symbols)
        assertTrue("<=" in symbols)
        assertTrue("<=>" in symbols, "Should have spaceship operator")
    }

    @Test
    fun `language model has scopes for styling`() {
        assertTrue(model.scopes.isNotEmpty(), "Should have scope definitions")

        val scopeNames = model.scopes.map { it.name }
        assertTrue("keyword" in scopeNames)
        assertTrue("string" in scopeNames)
        assertTrue("comment" in scopeNames)
        assertTrue("type" in scopeNames)
    }

    @Test
    fun `language model has AST concepts`() {
        assertTrue(model.concepts.isNotEmpty(), "Should have AST concepts")
        assertTrue(model.abstractConcepts.isNotEmpty(), "Should have abstract concepts")
        assertTrue(model.concreteConcepts.isNotEmpty(), "Should have concrete concepts")

        // Check essential concepts exist
        val conceptNames = model.concepts.map { it.name }
        assertTrue("Statement" in conceptNames)
        assertTrue("Expression" in conceptNames)
        assertTrue("IfStatement" in conceptNames)
        assertTrue("ClassDeclaration" in conceptNames)
    }

    @Test
    fun `concept hierarchy is correct`() {
        val ifStatement = model.getConcept("IfStatement")
        assertNotNull(ifStatement)
        assertEquals("Statement", ifStatement.parentConcept)

        val classDecl = model.getConcept("ClassDeclaration")
        assertNotNull(classDecl)
        assertEquals("TypeDeclaration", classDecl.parentConcept)
    }

    @Test
    fun `concepts have expected children`() {
        val ifStatement = model.getConcept("IfStatement")
        assertNotNull(ifStatement)

        val childNames = ifStatement.children.map { it.name }
        assertTrue("condition" in childNames, "IfStatement should have condition child")
        assertTrue("thenBranch" in childNames, "IfStatement should have thenBranch child")
        assertTrue("elseBranch" in childNames, "IfStatement should have elseBranch child")

        // Check cardinality
        val elseBranch = ifStatement.children.find { it.name == "elseBranch" }
        assertNotNull(elseBranch)
        assertEquals(Cardinality.OPTIONAL, elseBranch.cardinality, "elseBranch should be optional")
    }

    // =========================================================================
    // Generator Tests
    // =========================================================================

    @Test
    fun `TextMate grammar can be generated`() {
        val grammar = TextMateGenerator(model).generate()

        assertTrue(grammar.isNotEmpty())
        assertTrue(grammar.contains("\"name\": \"Ecstasy\""))
        assertTrue(grammar.contains("\"scopeName\": \"source.xtc\""))
        assertTrue(grammar.contains("patterns"))
        assertTrue(grammar.contains("repository"))
    }

    @Test
    fun `TextMate grammar has keyword patterns`() {
        val grammar = TextMateGenerator(model).generate()

        assertTrue(grammar.contains("keyword.control.xtc"))
        assertTrue(grammar.contains("keyword.declaration.xtc"))
    }

    @Test
    fun `TextMate grammar has string patterns`() {
        val grammar = TextMateGenerator(model).generate()

        assertTrue(grammar.contains("string.quoted.double.xtc"))
        assertTrue(grammar.contains("string.interpolated.xtc"))
    }

    // =========================================================================
    // Real Source File Tests
    // =========================================================================

    private fun findXvmRoot(): File? {
        // Try to find the XVM root directory
        var dir = File(System.getProperty("user.dir"))
        while (dir.parentFile != null) {
            if (File(dir, "lib_ecstasy").exists()) {
                return dir
            }
            // Check if we're in the lang directory
            if (dir.name == "lang" && File(dir.parentFile, "lib_ecstasy").exists()) {
                return dir.parentFile
            }
            dir = dir.parentFile
        }
        return null
    }

    private fun xvmRootExists(): Boolean = findXvmRoot() != null

    @Test
    @EnabledIf("xvmRootExists")
    fun `keywords are found in lib_ecstasy sources`() {
        val xvmRoot = findXvmRoot() ?: return
        val libEcstasy = File(xvmRoot, "lib_ecstasy/src/main/x")

        if (!libEcstasy.exists()) {
            println("lib_ecstasy not found, skipping test")
            return
        }

        val keywordCounts = mutableMapOf<String, Int>()
        var fileCount = 0

        libEcstasy
            .walkTopDown()
            .filter { it.extension == "x" }
            .forEach { file ->
                fileCount++
                val content = file.readText()
                for (keyword in model.allKeywords.filter { !it.contains(":") }) {
                    val regex = Regex("\\b${Regex.escape(keyword)}\\b")
                    val count = regex.findAll(content).count()
                    if (count > 0) {
                        keywordCounts[keyword] = (keywordCounts[keyword] ?: 0) + count
                    }
                }
            }

        println("Scanned $fileCount .x files in lib_ecstasy")
        println("Found ${keywordCounts.size} different keywords")

        // Essential keywords should be found
        assertTrue(keywordCounts.containsKey("class"), "Should find 'class' keyword")
        assertTrue(keywordCounts.containsKey("if"), "Should find 'if' keyword")
        assertTrue(keywordCounts.containsKey("return"), "Should find 'return' keyword")
        assertTrue(keywordCounts.containsKey("import"), "Should find 'import' keyword")
    }

    @Test
    @EnabledIf("xvmRootExists")
    fun `built-in types are found in lib_ecstasy sources`() {
        val xvmRoot = findXvmRoot() ?: return
        val libEcstasy = File(xvmRoot, "lib_ecstasy/src/main/x")

        if (!libEcstasy.exists()) {
            println("lib_ecstasy not found, skipping test")
            return
        }

        // Extract built-in types from the token pattern
        val builtinTypes =
            listOf(
                "Int",
                "String",
                "Boolean",
                "Char",
                "Byte",
                "Array",
                "List",
                "Map",
                "Set",
                "Object",
                "Null",
                "True",
                "False",
            )

        val typeCounts = mutableMapOf<String, Int>()

        libEcstasy
            .walkTopDown()
            .filter { it.extension == "x" }
            .take(50) // Sample first 50 files for speed
            .forEach { file ->
                val content = file.readText()
                for (type in builtinTypes) {
                    val regex = Regex("\\b${Regex.escape(type)}\\b")
                    val count = regex.findAll(content).count()
                    if (count > 0) {
                        typeCounts[type] = (typeCounts[type] ?: 0) + count
                    }
                }
            }

        println("Built-in type occurrences (sample of 50 files):")
        typeCounts.entries.sortedByDescending { it.value }.forEach { (type, count) ->
            println("  $type: $count")
        }

        assertTrue(typeCounts.containsKey("String"), "Should find String type")
        assertTrue(typeCounts.containsKey("Int"), "Should find Int type")
    }

    @Test
    @EnabledIf("xvmRootExists")
    fun `operators are found in lib_ecstasy sources`() {
        val xvmRoot = findXvmRoot() ?: return
        val libEcstasy = File(xvmRoot, "lib_ecstasy/src/main/x")

        if (!libEcstasy.exists()) {
            println("lib_ecstasy not found, skipping test")
            return
        }

        // Test a few key operators
        val testOperators = listOf("==", "!=", "<=", ">=", "&&", "||", "->", "?:")

        val opCounts = mutableMapOf<String, Int>()

        libEcstasy
            .walkTopDown()
            .filter { it.extension == "x" }
            .take(50)
            .forEach { file ->
                val content = file.readText()
                for (op in testOperators) {
                    val count = content.windowed(op.length).count { it == op }
                    if (count > 0) {
                        opCounts[op] = (opCounts[op] ?: 0) + count
                    }
                }
            }

        println("Operator occurrences (sample of 50 files):")
        opCounts.entries.sortedByDescending { it.value }.forEach { (op, count) ->
            println("  '$op': $count")
        }

        assertTrue(opCounts.containsKey("=="), "Should find == operator")
    }

    // =========================================================================
    // Model Integrity Tests
    // =========================================================================

    @Test
    fun `all concept parent references are valid`() {
        val conceptNames = model.concepts.map { it.name }.toSet()

        for (concept in model.concepts) {
            if (concept.parentConcept != null) {
                assertTrue(
                    concept.parentConcept in conceptNames,
                    "Concept ${concept.name} has invalid parent: ${concept.parentConcept}",
                )
            }
        }
    }

    @Test
    fun `no duplicate concept names`() {
        val names = model.concepts.map { it.name }
        val duplicates = names.groupBy { it }.filter { it.value.size > 1 }.keys

        assertTrue(duplicates.isEmpty(), "Found duplicate concept names: $duplicates")
    }

    @Test
    fun `no duplicate operator symbols`() {
        val symbols = model.operators.map { it.symbol }
        val duplicates = symbols.groupBy { it }.filter { it.value.size > 1 }.keys

        assertTrue(duplicates.isEmpty(), "Found duplicate operator symbols: $duplicates")
    }

    @Test
    fun `all operators have valid precedence`() {
        for (op in model.operators) {
            assertTrue(
                op.precedence in 0..15,
                "Operator ${op.symbol} has invalid precedence: ${op.precedence}",
            )
        }
    }

    // =========================================================================
    // Keyword Category Tests
    // =========================================================================

    @Test
    fun `model has keywords by category`() {
        val controlKeywords = model.keywordsByCategory(KeywordCategory.CONTROL)
        assertTrue(controlKeywords.isNotEmpty(), "Should have control keywords")
        assertTrue("if" in controlKeywords, "Control keywords should include 'if'")
        assertTrue("for" in controlKeywords, "Control keywords should include 'for'")
        assertTrue("while" in controlKeywords, "Control keywords should include 'while'")
        assertTrue("return" in controlKeywords, "Control keywords should include 'return'")
    }

    @Test
    fun `model has exception keywords`() {
        val exceptionKeywords = model.keywordsByCategory(KeywordCategory.EXCEPTION)
        assertTrue(exceptionKeywords.isNotEmpty(), "Should have exception keywords")
        assertTrue("try" in exceptionKeywords, "Exception keywords should include 'try'")
        assertTrue("catch" in exceptionKeywords, "Exception keywords should include 'catch'")
        assertTrue("throw" in exceptionKeywords, "Exception keywords should include 'throw'")
    }

    @Test
    fun `model has declaration keywords`() {
        val declarationKeywords = model.keywordsByCategory(KeywordCategory.DECLARATION)
        assertTrue(declarationKeywords.isNotEmpty(), "Should have declaration keywords")
        assertTrue("class" in declarationKeywords, "Declaration keywords should include 'class'")
        assertTrue("interface" in declarationKeywords, "Declaration keywords should include 'interface'")
        assertTrue("module" in declarationKeywords, "Declaration keywords should include 'module'")
    }

    @Test
    fun `model has modifier keywords`() {
        val modifierKeywords = model.keywordsByCategory(KeywordCategory.MODIFIER)
        assertTrue(modifierKeywords.isNotEmpty(), "Should have modifier keywords")
        assertTrue("public" in modifierKeywords, "Modifier keywords should include 'public'")
        assertTrue("private" in modifierKeywords, "Modifier keywords should include 'private'")
        assertTrue("static" in modifierKeywords, "Modifier keywords should include 'static'")
    }

    @Test
    fun `model has built-in types`() {
        assertTrue(model.builtinTypes.isNotEmpty(), "Should have built-in types")
        assertTrue("Int" in model.builtinTypes, "Should include Int")
        assertTrue("String" in model.builtinTypes, "Should include String")
        assertTrue("Boolean" in model.builtinTypes, "Should include Boolean")
        assertTrue("Array" in model.builtinTypes, "Should include Array")
        assertTrue("Map" in model.builtinTypes, "Should include Map")
    }

    // =========================================================================
    // Additional Generator Tests
    // =========================================================================

    @Test
    fun `Vim syntax file can be generated`() {
        val vim = VimGenerator(model).generate()

        assertTrue(vim.isNotEmpty())
        assertTrue(vim.contains("Vim syntax file"), "Should have Vim header")
        assertTrue(vim.contains("syn keyword"), "Should have keyword definitions")
        assertTrue(vim.contains("xtcControl"), "Should have control keywords group")
        assertTrue(vim.contains("hi def link"), "Should have highlight links")
    }

    @Test
    fun `Vim generator uses model keywords`() {
        val vim = VimGenerator(model).generate()

        // Control keywords from model should appear
        val controlKeywords = model.keywordsByCategory(KeywordCategory.CONTROL)
        for (kw in controlKeywords.take(3)) {
            assertTrue(vim.contains(kw), "Vim syntax should contain '$kw'")
        }
    }

    @Test
    fun `Emacs major mode can be generated`() {
        val emacs = EmacsGenerator(model).generate()

        assertTrue(emacs.isNotEmpty())
        assertTrue(emacs.contains("xtc-mode.el"), "Should have Emacs header")
        assertTrue(emacs.contains("define-derived-mode"), "Should define major mode")
        assertTrue(emacs.contains("font-lock-keywords"), "Should have font-lock")
        assertTrue(emacs.contains("provide 'xtc-mode"), "Should provide the mode")
    }

    @Test
    fun `Emacs generator uses model keywords`() {
        val emacs = EmacsGenerator(model).generate()

        // Control keywords should be in xtc-control-keywords
        val controlKeywords = model.keywordsByCategory(KeywordCategory.CONTROL)
        for (kw in controlKeywords.take(3)) {
            assertTrue(emacs.contains("\"$kw\""), "Emacs mode should contain '$kw'")
        }
    }

    @Test
    fun `Tree-sitter grammar can be generated`() {
        val grammar = TreeSitterGenerator(model).generateGrammar()

        assertTrue(grammar.isNotEmpty())
        assertTrue(grammar.contains("module.exports = grammar"), "Should have grammar export")
        assertTrue(grammar.contains("name: 'xtc'"), "Should have language name")
        assertTrue(grammar.contains("rules:"), "Should have rules section")
    }

    @Test
    fun `Tree-sitter highlights can be generated`() {
        val highlights = TreeSitterGenerator(model).generateHighlights()

        assertTrue(highlights.isNotEmpty())
        assertTrue(highlights.contains("@keyword"), "Should have keyword captures")
        assertTrue(highlights.contains("@comment"), "Should have comment captures")
        assertTrue(highlights.contains("@string"), "Should have string captures")
    }

    @Test
    fun `Tree-sitter uses model operators for precedence`() {
        val grammar = TreeSitterGenerator(model).generateGrammar()

        // Binary expression should contain model operators
        assertTrue(grammar.contains("binary_expression"), "Should have binary_expression rule")

        // Check that some operators from the model appear
        val logicalOps = model.operators.filter { it.category == OperatorCategory.LOGICAL }
        val foundOps = logicalOps.count { grammar.contains("'${it.symbol}'") }
        assertTrue(foundOps > 0, "Grammar should contain logical operators from model")
    }

    @Test
    fun `VS Code config can be generated`() {
        val config = VSCodeConfigGenerator(model).generate()

        assertTrue(config.isNotEmpty())
        assertTrue(config.contains("comments"), "Should have comments section")
        assertTrue(config.contains("brackets"), "Should have brackets section")
        assertTrue(config.contains("autoClosingPairs"), "Should have autoClosingPairs")
    }

    // =========================================================================
    // Cross-Generator Consistency Tests
    // =========================================================================

    @Test
    fun `all generators include control keywords from model`() {
        val controlKeywords = model.keywordsByCategory(KeywordCategory.CONTROL)
        val vim = VimGenerator(model).generate()
        val emacs = EmacsGenerator(model).generate()
        val highlights = TreeSitterGenerator(model).generateHighlights()

        // Each generator should include the control keywords
        for (kw in listOf("if", "else", "for", "while")) {
            assertTrue(kw in controlKeywords, "'$kw' should be a control keyword")
            assertTrue(vim.contains(kw), "Vim should contain '$kw'")
            assertTrue(emacs.contains("\"$kw\""), "Emacs should contain '$kw'")
            assertTrue(highlights.contains("\"$kw\""), "Tree-sitter highlights should contain '$kw'")
        }
    }

    @Test
    fun `all generators include built-in types from model`() {
        val vim = VimGenerator(model).generate()
        val emacs = EmacsGenerator(model).generate()

        // Check a few built-in types
        for (type in listOf("Int", "String", "Boolean")) {
            assertTrue(type in model.builtinTypes, "'$type' should be a built-in type")
            assertTrue(vim.contains(type), "Vim should contain '$type'")
            assertTrue(emacs.contains("\"$type\""), "Emacs should contain '$type'")
        }
    }
}
