package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Signature help tests for [TreeSitterAdapter].
 *
 * Exercises signature help resolution, active parameter tracking, overloads,
 * nested calls, and edge cases in call pattern parsing.
 *
 * All tests are skipped (not failed) when the tree-sitter native library is unavailable.
 */
@DisplayName("TreeSitterAdapter - Signature Help")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SignatureHelpTest : TreeSitterTestBase() {
    // ========================================================================
    // getSignatureHelp() -- tree-sitter-specific
    // ========================================================================

    @Nested
    @DisplayName("getSignatureHelp() -- tree-sitter-specific")
    inner class SignatureHelpTests {
        /**
         * When the cursor is inside the argument list of `add(1, 2)`, the adapter
         * walks up to the enclosing `call_expression`, extracts the function name,
         * and looks up the matching `method_declaration`. The result should contain
         * one signature with the full label and both parameters.
         */
        @Test
        @DisplayName("should return signature help for method call")
        fun shouldReturnSignatureHelpForMethodCall() {
            val uri = freshUri()
            ts.compile(uri, calculatorSource())
            val help = logged("shouldReturnSignatureHelpForMethodCall", ts.getSignatureHelp(uri, 6, 16))

            assertThat(help).isNotNull
            assertThat(help!!.signatures).hasSize(1)
            assertThat(help.signatures[0].label).isEqualTo("add(Int a, Int b)")
            assertThat(help.signatures[0].parameters).hasSize(2)
            assertThat(help.signatures[0].parameters[0].label).isEqualTo("Int a")
            assertThat(help.signatures[0].parameters[1].label).isEqualTo("Int b")
        }

        /**
         * With the cursor on the first argument (before the comma) in `add(1, 2)`,
         * the active parameter should be 0.
         */
        @Test
        @DisplayName("should report first parameter as active before comma")
        fun shouldReportFirstParameterActive() {
            val uri = freshUri()
            ts.compile(uri, calculatorSource())
            val help = logged("shouldReportFirstParameterActive", ts.getSignatureHelp(uri, 6, 16))

            assertThat(help).isNotNull
            assertThat(help!!.activeParameter).isEqualTo(0)
        }

        /**
         * With the cursor after the comma in `add(1, 2)`, the adapter counts commas
         * before the cursor to determine the active parameter index. The active
         * parameter should be 1 (the second parameter).
         */
        @Test
        @DisplayName("should track active parameter based on comma position")
        fun shouldTrackActiveParameter() {
            val uri = freshUri()
            ts.compile(uri, calculatorSource())
            val help = logged("shouldTrackActiveParameter", ts.getSignatureHelp(uri, 6, 19))

            assertThat(help).isNotNull
            assertThat(help!!.activeParameter).isEqualTo(1)
        }

        /**
         * Cursor on a class name (not inside a call expression) should return null
         * because there is no enclosing `call_expression` node to provide context.
         */
        @Test
        @DisplayName("should return null outside call expression")
        fun shouldReturnNullOutsideCallExpression() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            val help = logged("shouldReturnNullOutsideCallExpression", ts.getSignatureHelp(uri, 1, 10))

            assertThat(help).isNull()
        }

        /**
         * A call to a zero-parameter method `greet()` should produce a signature
         * with an empty parameter list and label "greet()".
         */
        @Test
        @DisplayName("should return signature help for no-arg call")
        fun shouldReturnSignatureHelpForNoArgCall() {
            val uri = freshUri()
            ts.compile(uri, greeterSource())
            // line 6: `            greet();` -- col 18 = ')' inside call
            val help = logged("shouldReturnSignatureHelpForNoArgCall", ts.getSignatureHelp(uri, 6, 18))

            assertThat(help).isNotNull
            assertThat(help!!.signatures).hasSize(1)
            assertThat(help.signatures[0].label).isEqualTo("greet()")
            assertThat(help.signatures[0].parameters).isEmpty()
        }

        /**
         * Cursor on the first argument of a three-parameter call `clamp(5, 0, 100)`
         * should report activeParameter = 0 and show the full three-param label.
         */
        @Test
        @DisplayName("should report first param active for three-arg call")
        fun shouldReportFirstParamActiveForThreeArgCall() {
            val uri = freshUri()
            ts.compile(uri, clampSource())
            // line 6: `            clamp(5, 0, 100);` -- col 18 = '5'
            val help = logged("shouldReportFirstParamActiveForThreeArgCall", ts.getSignatureHelp(uri, 6, 18))

            assertThat(help).isNotNull
            assertThat(help!!.activeParameter).isEqualTo(0)
            assertThat(help.signatures[0].label).isEqualTo("clamp(Int value, Int low, Int high)")
        }

        /**
         * Cursor on the second argument of `clamp(5, 0, 100)` should report
         * activeParameter = 1.
         */
        @Test
        @DisplayName("should report second param active for three-arg call")
        fun shouldReportSecondParamActiveForThreeArgCall() {
            val uri = freshUri()
            ts.compile(uri, clampSource())
            // line 6: `            clamp(5, 0, 100);` -- col 21 = '0'
            val help = logged("shouldReportSecondParamActiveForThreeArgCall", ts.getSignatureHelp(uri, 6, 21))

            assertThat(help).isNotNull
            assertThat(help!!.activeParameter).isEqualTo(1)
        }

        /**
         * Cursor on the third argument of `clamp(5, 0, 100)` should report
         * activeParameter = 2.
         */
        @Test
        @DisplayName("should report third param active for three-arg call")
        fun shouldReportThirdParamActiveForThreeArgCall() {
            val uri = freshUri()
            ts.compile(uri, clampSource())
            // line 6: `            clamp(5, 0, 100);` -- col 24 = '1' (first digit of 100)
            val help = logged("shouldReportThirdParamActiveForThreeArgCall", ts.getSignatureHelp(uri, 6, 24))

            assertThat(help).isNotNull
            assertThat(help!!.activeParameter).isEqualTo(2)
        }

        /**
         * Two methods with the same name but different parameter counts should
         * produce two signatures in the result.
         */
        @Test
        @DisplayName("should return multiple signatures for overloads")
        fun shouldReturnMultipleSignaturesForOverloads() {
            val uri = freshUri()
            ts.compile(uri, overloadedFormatSource())
            // line 9: `            format("hello", 10);` -- col 19 = '"' inside call
            val help = logged("shouldReturnMultipleSignaturesForOverloads", ts.getSignatureHelp(uri, 9, 19))

            assertThat(help).isNotNull
            assertThat(help!!.signatures).hasSize(2)
        }

        /**
         * In `add(negate(1), 2)`, cursor on `1` inside `negate(1)` should
         * return the signature for `negate`, not `add`.
         */
        @Test
        @DisplayName("should return signature help for inner nested call")
        fun shouldReturnSignatureHelpForInnerNestedCall() {
            val uri = freshUri()
            ts.compile(uri, nestedCallSource())
            // line 9: `            add(negate(1), 2);` -- col 23 = '1'
            val help = logged("shouldReturnSignatureHelpForInnerNestedCall", ts.getSignatureHelp(uri, 9, 23))

            assertThat(help).isNotNull
            assertThat(help!!.signatures).hasSize(1)
            assertThat(help.signatures[0].label).isEqualTo("negate(Int x)")
        }

        /**
         * In `add(negate(1), 2)`, cursor on `2` should return the signature
         * for `add` with activeParameter = 1.
         */
        @Test
        @DisplayName("should return signature help for outer nested call")
        fun shouldReturnSignatureHelpForOuterNestedCall() {
            val uri = freshUri()
            ts.compile(uri, nestedCallSource())
            // line 9: `            add(negate(1), 2);` -- col 27 = '2'
            val help = logged("shouldReturnSignatureHelpForOuterNestedCall", ts.getSignatureHelp(uri, 9, 27))

            assertThat(help).isNotNull
            assertThat(help!!.signatures).hasSize(1)
            assertThat(help.signatures[0].label).isEqualTo("add(Int a, Int b)")
            assertThat(help.activeParameter).isEqualTo(1)
        }

        /**
         * A call to a sibling method from a different method in the same class
         * should resolve correctly via same-file lookup.
         */
        @Test
        @DisplayName("should resolve cross-method call in same class")
        fun shouldResolveCrossMethodCallInSameClass() {
            val uri = freshUri()
            ts.compile(uri, crossMethodSource())
            // line 6: `            return repeat(s, width);` -- col 26 = 's'
            val help = logged("shouldResolveCrossMethodCallInSameClass", ts.getSignatureHelp(uri, 6, 26))

            assertThat(help).isNotNull
            assertThat(help!!.signatures).hasSize(1)
            assertThat(help.signatures[0].label).isEqualTo("repeat(String s, Int count)")
            assertThat(help.activeParameter).isEqualTo(0)
        }

        /**
         * Cursor on the fifth argument of a five-parameter method should
         * report activeParameter = 4 and the signature should have 5 params.
         */
        @Test
        @DisplayName("should track active param in five-param method")
        fun shouldTrackActiveParamInFiveParamMethod() {
            val uri = freshUri()
            ts.compile(uri, fiveParamSource())
            // line 6: `            execute("run", 30, True, 5, "out.log");` -- col 40 = '"' of "out.log"
            val help = logged("shouldTrackActiveParamInFiveParamMethod", ts.getSignatureHelp(uri, 6, 40))

            assertThat(help).isNotNull
            assertThat(help!!.activeParameter).isEqualTo(4)
            assertThat(help.signatures[0].parameters).hasSize(5)
        }

        /**
         * When the cursor is right at the opening paren of a call, the adapter
         * should still find the enclosing call expression and return a result.
         */
        @Test
        @DisplayName("should return signature help at open paren")
        fun shouldReturnSignatureHelpAtOpenParen() {
            val uri = freshUri()
            ts.compile(uri, calculatorSource())
            // line 6: `            add(1, 2);` -- col 15 = '('
            val help = logged("shouldReturnSignatureHelpAtOpenParen", ts.getSignatureHelp(uri, 6, 15))

            assertThat(help).isNotNull
            assertThat(help!!.signatures[0].label).isEqualTo("add(Int a, Int b)")
        }

        /**
         * Calling a method that has no declaration in the file should return null
         * without crashing.
         */
        @Test
        @DisplayName("should return null when called method not in file")
        fun shouldReturnNullWhenCalledMethodNotInFile() {
            val uri = freshUri()
            ts.compile(uri, unknownMethodSource())
            // line 3: `            unknown(42);` -- col 20 = '4'
            val help = logged("shouldReturnNullWhenCalledMethodNotInFile", ts.getSignatureHelp(uri, 3, 20))

            assertThat(help).isNull()
        }

        private fun calculatorSource() =
            """
            module myapp {
                class Calculator {
                    Int add(Int a, Int b) {
                        return a + b;
                    }
                    void test() {
                        add(1, 2);
                    }
                }
            }
            """.trimIndent()

        private fun greeterSource() =
            """
            module myapp {
                class Greeter {
                    void greet() {
                        return;
                    }
                    void test() {
                        greet();
                    }
                }
            }
            """.trimIndent()

        private fun clampSource() =
            """
            module myapp {
                class MathUtil {
                    Int clamp(Int value, Int low, Int high) {
                        return value;
                    }
                    void test() {
                        clamp(5, 0, 100);
                    }
                }
            }
            """.trimIndent()

        private fun overloadedFormatSource() =
            """
            module myapp {
                class Formatter {
                    String format(String pattern) {
                        return pattern;
                    }
                    String format(String pattern, Int width) {
                        return pattern;
                    }
                    void test() {
                        format("hello", 10);
                    }
                }
            }
            """.trimIndent()

        private fun nestedCallSource() =
            """
            module myapp {
                class Math {
                    Int add(Int a, Int b) {
                        return a + b;
                    }
                    Int negate(Int x) {
                        return -x;
                    }
                    void test() {
                        add(negate(1), 2);
                    }
                }
            }
            """.trimIndent()

        private fun crossMethodSource() =
            """
            module myapp {
                class StringUtil {
                    String repeat(String s, Int count) {
                        return s;
                    }
                    String padRight(String s, Int width) {
                        return repeat(s, width);
                    }
                }
            }
            """.trimIndent()

        private fun fiveParamSource() =
            """
            module myapp {
                class Runner {
                    void execute(String cmd, Int timeout, Boolean retry, Int max, String log) {
                        return;
                    }
                    void test() {
                        execute("run", 30, True, 5, "out.log");
                    }
                }
            }
            """.trimIndent()

        private fun unknownMethodSource() =
            """
            module myapp {
                class Caller {
                    void test() {
                        unknown(42);
                    }
                }
            }
            """.trimIndent()
    }

    // ========================================================================
    // Call pattern edge cases -- tree-sitter-specific
    // ========================================================================

    @Nested
    @DisplayName("call pattern edge cases -- tree-sitter-specific")
    inner class CallPatternParsingTests {
        /**
         * `new Box(42)` is a constructor invocation, not a method call. Signature
         * help should return null (no matching method declaration) without crashing.
         */
        @Test
        @DisplayName("should return null for new expression")
        fun shouldReturnNullForNewExpression() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Box {
                        Int value;
                    }
                    class Test {
                        void test() {
                            new Box(42);
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // cursor inside the `(42)` part of `new Box(42)` at line 6, col 20
            val help = logged("shouldReturnNullForNewExpression", ts.getSignatureHelp(uri, 6, 20))

            // No method named "Box" exists, so either null or no crash
            // (grammar may route through generic_type but method lookup fails)
            assertThat(help).isNull()
        }

        /**
         * Cursor on the method name in a declaration (not a call) should return null
         * because the declaration's parameters node is not named "arguments".
         */
        @Test
        @DisplayName("should return null at method declaration site")
        fun shouldReturnNullAtMethodDeclarationSite() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Calculator {
                        Int add(Int a, Int b) {
                            return a + b;
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // cursor on 'add' in declaration at line 2, col 12
            val help = logged("shouldReturnNullAtMethodDeclarationSite", ts.getSignatureHelp(uri, 2, 12))

            assertThat(help).isNull()
        }

        /**
         * Cursor on a bare identifier that is not a call expression (e.g., a
         * variable reference) should return null.
         */
        @Test
        @DisplayName("should return null for bare identifier reference")
        fun shouldReturnNullForBareIdentifierReference() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Test {
                        void test() {
                            Int x = 42;
                            Int y = x;
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // cursor on 'x' in `Int y = x;` at line 4, col 20
            val help = logged("shouldReturnNullForBareIdentifierReference", ts.getSignatureHelp(uri, 4, 20))

            assertThat(help).isNull()
        }
    }
}
