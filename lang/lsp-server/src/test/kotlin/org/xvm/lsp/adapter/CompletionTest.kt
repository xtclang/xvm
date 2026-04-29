package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Completion tests for [TreeSitterAdapter].
 *
 * Exercises keyword completions, built-in type completions, and context-aware
 * completions derived from document symbols after compilation.
 */
@DisplayName("TreeSitterAdapter - Completions")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompletionTest : TreeSitterTestBase() {
    // ========================================================================
    // getCompletions()
    // ========================================================================

    @Nested
    @DisplayName("getCompletions()")
    inner class CompletionTests {
        /**
         * Even without any compiled document, the adapter should offer XTC keywords
         * (`class`, `interface`, `module`) from [XtcLanguageConstants].
         */
        @Test
        @DisplayName("should return keywords")
        fun shouldReturnKeywords() {
            val completions = ts.getCompletions(freshUri(), 0, 0)

            assertThat(completions)
                .anyMatch { it.label == "class" }
                .anyMatch { it.label == "interface" }
                .anyMatch { it.label == "module" }
        }

        /**
         * Built-in types like `String`, `Int`, `Boolean` come from
         * [XtcLanguageConstants.builtInTypeCompletions] and must always be present.
         */
        @Test
        @DisplayName("should return built-in types")
        fun shouldReturnBuiltInTypes() {
            val completions = ts.getCompletions(freshUri(), 0, 0)

            assertThat(completions)
                .anyMatch { it.label == "String" }
                .anyMatch { it.label == "Int" }
                .anyMatch { it.label == "Boolean" }
        }

        /**
         * After compiling, the adapter re-queries declarations from the parse tree
         * and includes them as completion items. "Person" should appear.
         */
        @Test
        @DisplayName("should include document symbols after compile")
        fun shouldIncludeDocumentSymbols() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            val completions = ts.getCompletions(uri, 3, 0)

            assertThat(completions).anyMatch { it.label == "Person" }
        }

        /**
         * After compiling a class with multiple methods, completions should include
         * those method names alongside keywords and built-in types.
         */
        @Test
        @DisplayName("should include method names from compiled source")
        fun shouldIncludeMethodNames() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Calculator {
                        Int add(Int a, Int b) {
                            return a + b;
                        }
                        Int multiply(Int a, Int b) {
                            return a * b;
                        }
                        void test() {
                            add(1, 2);
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            val completions = logged("shouldIncludeMethodNames", ts.getCompletions(uri, 9, 0))
            logger.info("  completion labels: {}", completions.map { it.label })

            assertThat(completions).anyMatch { it.label == "add" }
            assertThat(completions).anyMatch { it.label == "multiply" }
            assertThat(completions).anyMatch { it.label == "test" }
            assertThat(completions).anyMatch { it.label == "Calculator" }
        }
    }

    // ========================================================================
    // Completions at call sites -- tree-sitter-specific
    // ========================================================================

    @Nested
    @DisplayName("completions at call sites -- tree-sitter-specific")
    inner class CompletionsAtCallSiteTests {
        /**
         * Completions inside a class body should include all sibling method names
         * and the class name itself, since the adapter adds all document symbols.
         */
        @Test
        @DisplayName("should include sibling methods in completions")
        fun shouldIncludeSiblingMethodsInCompletions() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Calculator {
                        Int add(Int a, Int b) {
                            return a + b;
                        }
                        Int multiply(Int a, Int b) {
                            return a * b;
                        }
                        void test() {
                            add(1, 2);
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            val completions = logged("shouldIncludeSiblingMethodsInCompletions", ts.getCompletions(uri, 9, 12))
            logger.info("  completion labels: {}", completions.map { it.label })

            assertThat(completions).anyMatch { it.label == "add" }
            assertThat(completions).anyMatch { it.label == "multiply" }
            assertThat(completions).anyMatch { it.label == "test" }
            assertThat(completions).anyMatch { it.label == "Calculator" }
        }

        /**
         * After compiling source with multiple classes, completions should include
         * all class names and method names from the entire file.
         */
        @Test
        @DisplayName("should include symbols from multiple classes")
        fun shouldIncludeSymbolsFromMultipleClasses() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Parser {
                        void parse() {
                            return;
                        }
                    }
                    class Lexer {
                        void tokenize() {
                            return;
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            val completions = logged("shouldIncludeSymbolsFromMultipleClasses", ts.getCompletions(uri, 0, 0))
            logger.info("  completion labels: {}", completions.map { it.label })

            assertThat(completions).anyMatch { it.label == "Parser" }
            assertThat(completions).anyMatch { it.label == "Lexer" }
            assertThat(completions).anyMatch { it.label == "parse" }
            assertThat(completions).anyMatch { it.label == "tokenize" }
        }

        @Test
        @DisplayName("should include module-level @Inject property when completing inside a function body")
        fun shouldIncludeModuleLevelInjectPropertyInBodyCompletions() {
            val uri = freshUri()
            val source =
                """
                module test.examples.org {
                    @Inject Console console;

                    void run() {
                        String s = "hello";
                        co
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // Cursor on the `co` line, inside the run() body.
            val completions =
                logged(
                    "shouldIncludeModuleLevelInjectPropertyInBodyCompletions",
                    ts.getCompletions(uri, 5, 10),
                )

            assertThat(completions).anyMatch { it.label == "console" }
            assertThat(completions).anyMatch { it.label == "run" }
        }

        /**
         * Function-local variables declared above the cursor must appear in BODY-context
         * completions. This complements the @Inject test above and exercises the AST
         * scope-walk path (variable_declaration found in the enclosing block).
         */
        @Test
        @DisplayName("should include function-local variable in body completions")
        fun shouldIncludeFunctionLocalVariableInBodyCompletions() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    void run() {
                        String greeting = "hello";
                        gr
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // Cursor right after the `gr` partial -- line 3, column 10
            val completions =
                logged(
                    "shouldIncludeFunctionLocalVariableInBodyCompletions",
                    ts.getCompletions(uri, 3, 10),
                )

            assertThat(completions).anyMatch { it.label == "greeting" }
        }

        /**
         * Method parameters must appear in BODY-context completions inside the method body.
         */
        @Test
        @DisplayName("should include method parameters in body completions")
        fun shouldIncludeMethodParametersInBodyCompletions() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    Int process(Int amount, String label) {
                        return amount;
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // Cursor inside the body, line 2, column 8 (start of `return`)
            val completions =
                logged(
                    "shouldIncludeMethodParametersInBodyCompletions",
                    ts.getCompletions(uri, 2, 8),
                )

            assertThat(completions).anyMatch { it.label == "amount" }
            assertThat(completions).anyMatch { it.label == "label" }
        }

        @Test
        @DisplayName("should rank in-scope @Inject property ahead of built-in types")
        fun shouldRankInjectPropertyAheadOfBuiltIns() {
            val uri = freshUri()
            val source =
                """
                module test.examples.org {
                    @Inject Console console;

                    void run() {
                        String s = "hello";
                        co
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            val completions =
                logged(
                    "shouldRankInjectPropertyAheadOfBuiltIns",
                    ts.getCompletions(uri, 5, 10),
                )

            val labels = completions.map { it.label }
            val consoleIndex = labels.indexOf("console")
            val collectionIndex = labels.indexOf("Collection")

            assertThat(consoleIndex).isGreaterThanOrEqualTo(0)
            assertThat(collectionIndex).isGreaterThanOrEqualTo(0)
            assertThat(consoleIndex).isLessThan(collectionIndex)
        }

        /**
         * Function-local variables and parameters should rank ahead of generic keywords like
         * `return` in the response. The user typed an identifier prefix; the closest-scope
         * names are the most likely target.
         */
        @Test
        @DisplayName("should rank function-local variable ahead of body keywords")
        fun shouldRankFunctionLocalAheadOfKeywords() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    void greet() {
                        String greeting = "hello";
                        gr
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            val completions = ts.getCompletions(uri, 3, 10)
            val labels = completions.map { it.label }
            val greetingIndex = labels.indexOf("greeting")
            val returnIndex = labels.indexOf("return")

            assertThat(greetingIndex).isGreaterThanOrEqualTo(0)
            assertThat(returnIndex).isGreaterThanOrEqualTo(0)
            assertThat(greetingIndex).isLessThan(returnIndex)
        }

        /**
         * Slightly complex example: a method that mixes
         *   - a function-local variable (`localVar`)
         *   - method parameters (`a`, `b`)
         *   - sibling class members (`classProperty`, `helper`)
         *   - module-level declarations (`moduleProperty`, `Calculator`)
         *   - built-in types (`Int`, `String`)
         *   - body keywords (`return`)
         *
         * The response order should reflect relevance: anything reachable through the AST
         * scope walk (local, parameters, class members, module-level) is more relevant than
         * built-in types or keywords. Within the scope-walked items, inner scopes outrank
         * outer scopes.
         */
        @Test
        @DisplayName("should order completions by scope distance with mixed kinds")
        fun shouldOrderCompletionsByScopeDistance() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    String moduleProperty;

                    class Calculator {
                        Int classProperty;

                        void helper() {
                        }

                        Int compute(Int a, Int b) {
                            String localVar = "tmp";
                            return a;
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // Cursor on the `return a;` line, after the local declaration. Line 11, column 19.
            val completions =
                logged(
                    "shouldOrderCompletionsByScopeDistance",
                    ts.getCompletions(uri, 11, 19),
                )
            val labels = completions.map { it.label }

            // Every name is reachable.
            assertThat(labels).contains(
                "localVar",
                "a",
                "b",
                "classProperty",
                "helper",
                "moduleProperty",
                "Calculator",
                "Int",
                "String",
                "return",
            )

            // Scope-walked items rank ahead of built-in types and keywords.
            assertThat(labels.indexOf("localVar")).isLessThan(labels.indexOf("Int"))
            assertThat(labels.indexOf("a")).isLessThan(labels.indexOf("Int"))
            assertThat(labels.indexOf("classProperty")).isLessThan(labels.indexOf("Int"))
            assertThat(labels.indexOf("moduleProperty")).isLessThan(labels.indexOf("Int"))
            assertThat(labels.indexOf("Calculator")).isLessThan(labels.indexOf("Int"))

            // Built-in types rank ahead of body keywords.
            assertThat(labels.indexOf("Int")).isLessThan(labels.indexOf("return"))

            // Within the scope-walked items: inner scopes outrank outer scopes.
            // localVar (block) before a/b (parameters) before classProperty (class_body)
            // before moduleProperty (module_body).
            assertThat(labels.indexOf("localVar")).isLessThan(labels.indexOf("a"))
            assertThat(labels.indexOf("a")).isLessThan(labels.indexOf("classProperty"))
            assertThat(labels.indexOf("classProperty")).isLessThan(labels.indexOf("moduleProperty"))
        }

        @Test
        @DisplayName("should include visible names and control-flow keywords in method body")
        fun shouldIncludeBodyContextSuggestions() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Calculator {
                        Int add(Int a, Int b) {
                            return a + b;
                        }

                        void bepa5() {

                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            val completions = logged("shouldIncludeBodyContextSuggestions", ts.getCompletions(uri, 7, 8))
            logger.info("  completion labels: {}", completions.take(20).map { it.label })

            assertThat(completions).anyMatch { it.label == "add" }
            assertThat(completions).anyMatch { it.label == "bepa5" }
            assertThat(completions).anyMatch { it.label == "return" }
            assertThat(completions).anyMatch { it.label == "this" }
        }
    }
}
