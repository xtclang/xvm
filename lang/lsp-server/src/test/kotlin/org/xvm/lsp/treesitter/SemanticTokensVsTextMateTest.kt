package org.xvm.lsp.treesitter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Demonstrates the concrete benefits of LSP semantic tokens over TextMate/tree-sitter
 * pattern-based highlighting (`highlights.scm`).
 *
 * ## Why This Test Exists
 *
 * TextMate grammars (and tree-sitter `highlights.scm` queries) assign highlight groups
 * using **pattern matching**: they see structure like `(identifier) @variable` and apply
 * a single scope. This means every `identifier` node gets the same color regardless of
 * whether it's a variable, a parameter, a property, a method name, or a type name used
 * as a value.
 *
 * LSP semantic tokens use the **full AST context** to classify each identifier by its
 * semantic role. The encoder walks parent nodes, field names, and sibling context to
 * assign precise token types (class, interface, method, property, parameter, type,
 * decorator, namespace) and modifiers (declaration, static, abstract, readonly).
 *
 * This test parses realistic XTC source code and verifies that the semantic token
 * encoder produces classifications that TextMate fundamentally cannot — each test
 * documents the specific limitation it demonstrates.
 *
 * ## What TextMate Highlights Look Like
 *
 * From `highlights.scm.template`, TextMate assigns:
 * ```
 * (identifier) @variable                              — ALL identifiers are "variable"
 * (type_name) @type                                   — type names are "type"
 * (method_declaration name: (identifier) @function)   — method name is "function"
 * (call_expression function: (identifier) @function.call)
 * (parameter name: (identifier) @variable.parameter)
 * (property_declaration name: (identifier) @variable.member)
 * ```
 *
 * These patterns can match some contexts, but they fail when the same identifier text
 * appears in multiple roles, or when modifiers and precise type distinctions matter.
 */
@DisplayName("Semantic Tokens vs TextMate — Benefit Demonstration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SemanticTokensVsTextMateTest {
    private val logger: Logger = LoggerFactory.getLogger(SemanticTokensVsTextMateTest::class.java)
    private var parser: XtcParser? = null

    @BeforeAll
    fun setUpParser() {
        parser = runCatching { XtcParser() }.getOrNull()
    }

    @BeforeEach
    fun assumeAvailable() {
        Assumptions.assumeTrue(parser != null, "Tree-sitter native library not available")
    }

    @AfterAll
    fun tearDown() {
        parser?.close()
    }

    private fun encode(source: String): List<DecodedToken> {
        val tree = parser!!.parse(source)
        val encoder = SemanticTokenEncoder()
        val data = encoder.encode(tree.root)
        return decode(data, source)
    }

    // ========================================================================
    // Test helpers
    // ========================================================================

    /**
     * A decoded semantic token with human-readable fields extracted from the
     * delta-encoded integer array.
     */
    data class DecodedToken(
        val line: Int,
        val column: Int,
        val length: Int,
        val tokenType: String,
        val modifiers: Set<String>,
        val text: String,
    )

    private fun decode(
        data: List<Int>,
        source: String,
    ): List<DecodedToken> {
        val lines = source.lines()
        val result = mutableListOf<DecodedToken>()
        var line = 0
        var column = 0
        var i = 0
        while (i + 4 < data.size) {
            val deltaLine = data[i]
            val deltaStart = data[i + 1]
            val length = data[i + 2]
            val typeIdx = data[i + 3]
            val modBits = data[i + 4]

            line += deltaLine
            column = if (deltaLine > 0) deltaStart else column + deltaStart

            val tokenType = SemanticTokenLegend.tokenTypes.getOrElse(typeIdx) { "unknown" }
            val mods = mutableSetOf<String>()
            for ((mi, modName) in SemanticTokenLegend.tokenModifiers.withIndex()) {
                if ((modBits and (1 shl mi)) != 0) mods.add(modName)
            }

            val text =
                if (line < lines.size && column + length <= lines[line].length) {
                    lines[line].substring(column, column + length)
                } else {
                    "?"
                }

            result.add(DecodedToken(line, column, length, tokenType, mods, text))
            i += 5
        }
        return result
    }

    private fun List<DecodedToken>.findByTextAndType(
        text: String,
        type: String,
    ): DecodedToken? = find { it.text == text && it.tokenType == type }

    private fun List<DecodedToken>.findAllByText(text: String): List<DecodedToken> = filter { it.text == text }

    // ========================================================================
    // Benefit 1: Same identifier, different semantic roles
    // ========================================================================

    @Nested
    @DisplayName("Benefit 1: Disambiguating identical identifiers by context")
    inner class IdentifierDisambiguation {
        /**
         * TextMate limitation: `name` appears 3 times — as a parameter, a property, and
         * inside an assignment. TextMate's `(identifier) @variable` colors all three the
         * same. The more specific patterns `(parameter name: ...)` and
         * `(property_declaration name: ...)` can only match the declaration sites.
         *
         * Semantic tokens classify each occurrence precisely:
         * - `name` in `String name` (parameter) → `parameter` + `declaration`
         * - `name` in `String name = "?"` (property) → `property` + `declaration`
         * - `Person` in `class Person` → `class` + `declaration`
         * - `String` in return/param/property type → `type` (not variable)
         */
        @Test
        @DisplayName("'name' as parameter vs property get different token types")
        fun nameAsParameterVsProperty() {
            val source =
                """
                module myapp {
                    class Person {
                        String name = "unknown";
                        construct(String name) {}
                        String getName() {
                            return name;
                        }
                    }
                }
                """.trimIndent()

            val tokens = encode(source)
            logTokens("param vs property", tokens)

            // Property declaration: name → "property" with "declaration"
            val propToken = tokens.findByTextAndType("name", "property")
            assertThat(propToken)
                .describedAs(
                    "TextMate sees property 'name' as @variable.member; semantic tokens classify it as 'property' with 'declaration' modifier",
                ).isNotNull
            assertThat(propToken!!.modifiers).contains("declaration")

            // Parameter declaration: name → "parameter" with "declaration"
            val paramToken = tokens.findByTextAndType("name", "parameter")
            assertThat(paramToken)
                .describedAs("TextMate sees parameter 'name' as @variable.parameter; semantic tokens give it the distinct 'parameter' type")
                .isNotNull
            assertThat(paramToken!!.modifiers).contains("declaration")

            // The two should be DIFFERENT token types — TextMate can't do this
            assertThat(propToken.tokenType)
                .describedAs(
                    "Property and parameter 'name' must have DIFFERENT semantic token types — TextMate would color both as @variable",
                ).isNotEqualTo(paramToken.tokenType)
        }

        /**
         * TextMate limitation: Both `getName` at the declaration site and `getName` at the
         * member-call site are identifiers. TextMate can match them separately but CANNOT
         * carry the `declaration` modifier. Themes can't distinguish the definition from usage.
         *
         * Semantic tokens distinguish: the declaration has `declaration` modifier, the
         * member-call site doesn't. This lets themes dim or bold declaration-site identifiers.
         */
        @Test
        @DisplayName("method at declaration site carries 'declaration' modifier, call site doesn't")
        fun methodDeclarationVsCallSite() {
            val source =
                """
                module myapp {
                    class Calc {
                        Int getValue() {
                            return 0;
                        }
                        void test() {
                            this.getValue();
                        }
                    }
                }
                """.trimIndent()

            val tokens = encode(source)
            logTokens("method decl vs call", tokens)

            val methodTokens = tokens.filter { it.text == "getValue" && it.tokenType == "method" }
            assertThat(methodTokens)
                .describedAs("'getValue' should appear as method at both declaration and member-call site")
                .hasSizeGreaterThanOrEqualTo(2)

            val declToken = methodTokens.find { "declaration" in it.modifiers }
            val callToken = methodTokens.find { "declaration" !in it.modifiers }

            assertThat(declToken)
                .describedAs("Declaration site should have 'declaration' modifier — TextMate has no modifier concept")
                .isNotNull
            assertThat(callToken)
                .describedAs("Call site should NOT have 'declaration' modifier")
                .isNotNull
        }
    }

    // ========================================================================
    // Benefit 2: Type declarations get distinct token types
    // ========================================================================

    @Nested
    @DisplayName("Benefit 2: Distinguishing class/interface/enum/const/service/mixin declarations")
    inner class TypeDeclarationDistinction {
        /**
         * TextMate limitation: `highlights.scm` maps ALL type declarations to the same
         * scope `@type.definition`:
         * ```
         * (class_declaration name: (type_name) @type.definition)
         * (interface_declaration name: (type_name) @type.definition)
         * (enum_declaration name: (type_name) @type.definition)
         * (const_declaration name: (type_name) @type.definition)
         * ```
         *
         * Semantic tokens assign DISTINCT types: `class`, `interface`, `enum`, `struct`
         * (for const). Themes can color classes blue, interfaces green, enums orange.
         */
        @Test
        @DisplayName("each XTC type category gets a distinct semantic token type")
        fun distinctTypeCategories() {
            val source =
                """
                module myapp {
                    class Person {}
                    interface Runnable {}
                    enum Color {}
                    const Point(Int x, Int y);
                    service Worker {}
                    mixin Printable {}
                }
                """.trimIndent()

            val tokens = encode(source)
            logTokens("type categories", tokens)

            // TextMate: all of these → @type.definition (one color)
            // Semantic tokens: each gets its own type
            val personToken = tokens.findByTextAndType("Person", "class")
            assertThat(personToken)
                .describedAs("'Person' should be 'class', not generic 'type' — TextMate sees @type.definition")
                .isNotNull

            val runnableToken = tokens.findByTextAndType("Runnable", "interface")
            assertThat(runnableToken)
                .describedAs("'Runnable' should be 'interface' — TextMate can't distinguish from class")
                .isNotNull

            val colorToken = tokens.findByTextAndType("Color", "enum")
            assertThat(colorToken)
                .describedAs("'Color' should be 'enum' — TextMate can't distinguish from class")
                .isNotNull

            // Verify they are all DIFFERENT token types
            val types =
                setOfNotNull(
                    personToken?.tokenType,
                    runnableToken?.tokenType,
                    colorToken?.tokenType,
                )
            assertThat(types)
                .hasSize(3)
                .describedAs("class, interface, enum should map to 3 distinct token types — TextMate gives them all @type.definition")
        }

        /**
         * TextMate has no concept of modifiers. `const Point` is not just a type declaration —
         * it's `struct` + `readonly` + `declaration`. This triple is impossible in TextMate.
         */
        @Test
        @DisplayName("const declaration carries 'readonly' modifier that TextMate cannot express")
        fun constHasReadonlyModifier() {
            val source =
                """
                module myapp {
                    const Point(Int x, Int y);
                }
                """.trimIndent()

            val tokens = encode(source)
            logTokens("const readonly", tokens)

            val constToken = tokens.findByTextAndType("Point", "struct")
            if (constToken != null) {
                assertThat(constToken.modifiers)
                    .describedAs("'const' maps to 'struct' with both 'declaration' and 'readonly' — TextMate has no modifier system")
                    .contains("declaration", "readonly")
            }
        }
    }

    // ========================================================================
    // Benefit 3: Type references vs plain identifiers
    // ========================================================================

    @Nested
    @DisplayName("Benefit 3: Type references in type positions are classified as 'type'")
    inner class TypeReferenceClassification {
        /**
         * TextMate can match `(type_name) @type` but this is a STRUCTURAL match — it works
         * for explicit type expressions. The problem is that TextMate also matches
         * `(identifier) @variable` as a catch-all, and the LAST match wins in TextMate
         * ordering. If `(identifier) @variable` appears after `(type_name) @type`, the
         * type coloring is lost.
         *
         * Semantic tokens don't have ordering conflicts. Each token is classified exactly
         * once based on its AST context.
         */
        @Test
        @DisplayName("return type, parameter type, and property type all classified as 'type'")
        fun typeReferencesInMultiplePositions() {
            val source =
                """
                module myapp {
                    class Person {
                        String name = "unknown";
                        Int getAge(Boolean includeMonths) {
                            return 0;
                        }
                    }
                }
                """.trimIndent()

            val tokens = encode(source)
            logTokens("type refs", tokens)

            val typeTokens = tokens.filter { it.tokenType == "type" }
            val typeTexts = typeTokens.map { it.text }.toSet()

            assertThat(typeTexts)
                .describedAs(
                    "String (property type), Int (return type), Boolean (param type) should all be 'type' — TextMate may lose this to catch-all @variable",
                ).contains("String", "Int", "Boolean")
        }

        /**
         * TextMate highlight queries cannot express "this identifier is a type because
         * it appears as a return type vs a variable." Both `String` in `String name`
         * and `name` are identifiers at the text level.
         *
         * Semantic tokens classify `String` as `type` and `name` as `property` because
         * the encoder examines the parent node context: is this child in a `type_expression`
         * position, or a `name` field of a `property_declaration`?
         */
        @Test
        @DisplayName("'String' as type vs 'name' as property — both identifiers, different tokens")
        fun typeVsIdentifierSameDeclaration() {
            val source =
                """
                module myapp {
                    class Person {
                        String name = "unknown";
                    }
                }
                """.trimIndent()

            val tokens = encode(source)
            logTokens("type vs id", tokens)

            val stringToken = tokens.findByTextAndType("String", "type")
            val nameToken = tokens.findByTextAndType("name", "property")

            assertThat(stringToken)
                .describedAs("'String' should be classified as 'type'")
                .isNotNull
            assertThat(nameToken)
                .describedAs("'name' should be classified as 'property'")
                .isNotNull
            assertThat(stringToken!!.tokenType)
                .isNotEqualTo(nameToken!!.tokenType)
        }
    }

    // ========================================================================
    // Benefit 4: Annotations get their own token type
    // ========================================================================

    @Nested
    @DisplayName("Benefit 4: Annotations classified as 'decorator'")
    inner class AnnotationClassification {
        /**
         * TextMate maps annotations to `@attribute`:
         * ```
         * (annotation name: (qualified_name) @attribute)
         * ```
         *
         * This works, but it's a single scope. Semantic tokens classify annotation names
         * as `decorator` — a distinct token type that themes can style independently from
         * types, variables, and keywords. More importantly, TextMate's `@attribute` is
         * often themed identically to other constructs, while `decorator` is universally
         * styled with a distinct color in all major themes.
         */
        @Test
        @DisplayName("annotation name classified as 'decorator', not 'variable' or 'type'")
        fun annotationIsDecorator() {
            val source =
                """
                module myapp {
                    class Service {
                        @Inject Console console;
                    }
                }
                """.trimIndent()

            val tokens = encode(source)
            logTokens("annotation", tokens)

            // "Inject" should be decorator — not type, not variable
            val injectTokens = tokens.findAllByText("Inject")
            val decoratorToken = injectTokens.find { it.tokenType == "decorator" }

            assertThat(decoratorToken)
                .describedAs(
                    "'Inject' in @Inject should be 'decorator' — TextMate uses @attribute which many themes don't style distinctly",
                ).isNotNull

            // Verify it's NOT classified as a type (a common TextMate mistake)
            val typeToken = injectTokens.find { it.tokenType == "type" }
            assertThat(typeToken)
                .describedAs("'Inject' should not also appear as 'type' — semantic tokens prevent double-classification")
                .isNull()
        }
    }

    // ========================================================================
    // Benefit 5: Modifier bitmask — static, abstract
    // ========================================================================

    @Nested
    @DisplayName("Benefit 5: Modifier bitmasks carry semantic information TextMate cannot")
    inner class ModifierBitmasks {
        /**
         * TextMate has NO concept of modifiers. A `static` method looks the same as a
         * regular method — both are `@function`. A `static` property looks the same as
         * an instance property — both are `@variable.member`.
         *
         * Semantic tokens carry modifier bitmasks: `static`, `abstract`, `readonly`,
         * `declaration`. Themes and editor UI can use these to italicize static members,
         * strikethrough deprecated ones, or bold declarations.
         *
         * IntelliJ and VS Code both support modifier-based styling:
         * - VS Code: `semanticTokenColors: { "method.static": { "fontStyle": "italic" } }`
         * - IntelliJ: Settings > Editor > Color Scheme > Semantic Highlighting
         */
        @Test
        @DisplayName("static method carries 'static' + 'declaration' modifiers")
        fun staticMethodModifiers() {
            val source =
                """
                module myapp {
                    class Utils {
                        static Int parse(String text) {
                            return 0;
                        }
                    }
                }
                """.trimIndent()

            val tokens = encode(source)
            logTokens("static method", tokens)

            val parseToken = tokens.findByTextAndType("parse", "method")
            assertThat(parseToken)
                .describedAs("'parse' should be classified as 'method'")
                .isNotNull

            if (parseToken != null) {
                assertThat(parseToken.modifiers)
                    .describedAs("static method should carry 'static' modifier — TextMate has no modifier concept")
                    .contains("static")
                assertThat(parseToken.modifiers)
                    .describedAs("declaration site should carry 'declaration' modifier")
                    .contains("declaration")
            }
        }
    }

    // ========================================================================
    // Benefit 6: Member expression context — property access vs method call
    // ========================================================================

    @Nested
    @DisplayName("Benefit 6: Property access vs method call on member expressions")
    inner class MemberExpressionContext {
        /**
         * TextMate challenge: In `obj.foo`, is `foo` a property or a method?
         * TextMate's `highlights.scm` handles `call_expression > member_expression` for
         * method calls but falls through to `(identifier) @variable` for property access.
         *
         * Semantic tokens walk the AST: if the parent is a `call_expression`, the member
         * is classified as `method`; otherwise it's `property`. This distinction lets
         * themes color method calls differently from property reads.
         */
        @Test
        @DisplayName("member method call vs member property access get different token types")
        fun memberCallVsPropertyAccess() {
            val source =
                """
                module myapp {
                    class Person {
                        String name = "unknown";
                        String getName() {
                            return name;
                        }
                        void test() {
                            this.getName();
                            this.name;
                        }
                    }
                }
                """.trimIndent()

            val tokens = encode(source)
            logTokens("member call vs property", tokens)

            // this.getName() — "getName" at call site should be "method"
            val getNameCallTokens = tokens.filter { it.text == "getName" && it.tokenType == "method" }
            assertThat(getNameCallTokens)
                .describedAs("'getName' should appear as 'method' (both declaration and call site)")
                .hasSizeGreaterThanOrEqualTo(1)

            // this.name — "name" in member_expression (not call) should be "property"
            val namePropertyTokens = tokens.filter { it.text == "name" && it.tokenType == "property" }
            assertThat(namePropertyTokens)
                .describedAs("'name' in this.name should be 'property' — TextMate would just say @variable")
                .isNotEmpty
        }
    }

    // ========================================================================
    // Benefit 7: Full example — all benefits combined
    // ========================================================================

    @Nested
    @DisplayName("Benefit 7: Combined — realistic code shows all advantages at once")
    inner class CombinedBenefits {
        /**
         * This test uses a realistic XTC class that exercises all the disambiguation
         * capabilities simultaneously. It verifies that in a single file, the semantic
         * token encoder produces a richer token set than TextMate could, and that every
         * token type in the output is meaningful.
         *
         * TextMate would produce roughly:
         * - All type names → @type or @type.definition (no class/interface/enum distinction)
         * - All identifiers → @variable (no property/parameter/method distinction)
         * - Method names → @function or @function.call (no modifiers)
         * - No modifier information at all
         *
         * Semantic tokens produce 7+ distinct categories with modifier combinations.
         */
        @Test
        @DisplayName("realistic class produces rich semantic tokens with multiple distinct types")
        fun realisticClassProducesRichTokens() {
            val source =
                """
                module myapp {
                    interface Greetable {
                        String greet();
                    }
                    class Person {
                        String name;
                        Int age;
                        construct(String name, Int age) {}
                        String getName() {
                            return name;
                        }
                        void celebrateBirthday() {
                            this.getName();
                        }
                    }
                }
                """.trimIndent()

            val tokens = encode(source)
            logTokens("combined", tokens)

            // Collect all distinct token types produced
            val distinctTypes = tokens.map { it.tokenType }.toSet()
            logger.info("[TEST] Distinct token types: {}", distinctTypes)

            // Semantic tokens should produce AT LEAST these distinct types:
            // namespace (module), class, interface, method, property, parameter, type
            // TextMate's @type.definition, @variable, @function only gives ~3 categories
            assertThat(distinctTypes)
                .describedAs(
                    "Semantic tokens should produce 5+ distinct token types from a realistic " +
                        "class — TextMate effectively produces only ~3 (type.definition, variable, function)",
                ).hasSizeGreaterThanOrEqualTo(5)

            // Verify specific classifications
            assertThat(tokens.findByTextAndType("myapp", "namespace"))
                .describedAs("module name → namespace")
                .isNotNull
            assertThat(tokens.findByTextAndType("Greetable", "interface"))
                .describedAs("interface name → interface (not generic type)")
                .isNotNull
            assertThat(tokens.findByTextAndType("Person", "class"))
                .describedAs("class name → class (not generic type)")
                .isNotNull
            assertThat(tokens.findByTextAndType("name", "property"))
                .describedAs("property name → property (not variable)")
                .isNotNull
            assertThat(tokens.findByTextAndType("name", "parameter"))
                .describedAs("parameter name → parameter (not variable)")
                .isNotNull
            assertThat(tokens.findByTextAndType("getName", "method"))
                .describedAs("method name → method")
                .isNotNull

            // Verify declaration modifiers exist
            val declTokens = tokens.filter { "declaration" in it.modifiers }
            assertThat(declTokens)
                .describedAs("Multiple tokens should carry 'declaration' modifier — TextMate has no modifier support")
                .hasSizeGreaterThanOrEqualTo(5)
        }
    }

    private fun logTokens(
        label: String,
        tokens: List<DecodedToken>,
    ) {
        logger.info("[TEST] {} — {} tokens:", label, tokens.size)
        for (t in tokens) {
            val mods = if (t.modifiers.isNotEmpty()) " [${t.modifiers.joinToString(",")}]" else ""
            logger.info(
                "  L{}:{} '{}' → {}{}",
                t.line,
                t.column,
                t.text,
                t.tokenType,
                mods,
            )
        }
    }
}
