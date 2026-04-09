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
