package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.SymbolInfo

/**
 * Core adapter contract tests for [TreeSitterAdapter].
 *
 * Exercises lifecycle, compile, symbol lookup, and hover -- the fundamental
 * operations that every [Adapter] must support.
 *
 * All tests are skipped (not failed) when the tree-sitter native library is unavailable.
 */
@DisplayName("TreeSitterAdapter")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TreeSitterAdapterTest : TreeSitterTestBase() {
    // ========================================================================
    // Lifecycle & health
    // ========================================================================

    @Nested
    @DisplayName("lifecycle")
    inner class LifecycleTests {
        /** The native library loaded and self-verified during construction. */
        @Test
        @DisplayName("healthCheck should return true")
        fun healthCheckShouldReturnTrue() {
            assertThat(ts.healthCheck()).isTrue()
        }

        /** Adapter identifies itself as "TreeSitter" in logs and UI. */
        @Test
        @DisplayName("displayName should be TreeSitter")
        fun displayNameShouldBeTreeSitter() {
            assertThat(ts.displayName).isEqualTo("TreeSitter")
        }
    }

    // ========================================================================
    // compile()
    // ========================================================================

    @Nested
    @DisplayName("compile()")
    inner class CompileTests {
        /**
         * A minimal `module myapp { }` should parse without errors and produce a
         * MODULE symbol named "myapp" via the tree-sitter declaration query.
         */
        @Test
        @DisplayName("should parse module declaration")
        fun shouldParseModuleDeclaration() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                }
                """.trimIndent()

            val result = ts.compile(uri, source)

            assertThat(result.success).isTrue()
            assertThat(result.symbols)
                .anyMatch { it.name == "myapp" && it.kind == SymbolInfo.SymbolKind.MODULE }
        }

        /**
         * A class nested inside a module should produce a CLASS symbol.
         * Verifies the query pattern `(class_declaration (identifier) @name)`.
         */
        @Test
        @DisplayName("should parse class declaration")
        fun shouldParseClassDeclaration() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            val result = ts.compile(uri, source)

            assertThat(result.success).isTrue()
            assertThat(result.symbols)
                .anyMatch { it.name == "Person" && it.kind == SymbolInfo.SymbolKind.CLASS }
        }

        /**
         * Gene's email preamble: navigation jumped to the doc-comment line, not the
         * declaration itself. The symbol's location must point at the identifier
         * (`Person`) so cmd-click lands on the `class Person {` line, not on the
         * doc-comment prefix that precedes it.
         */
        @Test
        @DisplayName("symbol location should be the name identifier, not the doc-comment prefix")
        fun symbolLocationShouldBeNameIdentifierNotDocComment() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    /**
                     * A person.
                     */
                    class Person {
                    }
                }
                """.trimIndent()

            val result = ts.compile(uri, source)

            val person = result.symbols.first { it.name == "Person" }
            assertThat(person.location.startLine).isEqualTo(4)
        }

        /**
         * Verifies tree-sitter recognizes the `interface` keyword and maps it
         * to [SymbolInfo.SymbolKind.INTERFACE] via `interface_declaration`.
         */
        @Test
        @DisplayName("should parse interface declaration")
        fun shouldParseInterfaceDeclaration() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    interface Runnable {
                    }
                }
                """.trimIndent()

            val result = ts.compile(uri, source)

            assertThat(result.symbols)
                .anyMatch { it.name == "Runnable" && it.kind == SymbolInfo.SymbolKind.INTERFACE }
        }

        /**
         * Methods require a `(type_expression)` followed by an `(identifier)` inside a
         * `method_declaration` node. This ensures the grammar and query agree on that shape.
         */
        @Test
        @DisplayName("should parse method declaration")
        fun shouldParseMethodDeclaration() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Person {
                        String getName() {
                            return name;
                        }
                    }
                }
                """.trimIndent()

            val result = ts.compile(uri, source)

            assertThat(result.symbols)
                .anyMatch { it.name == "getName" && it.kind == SymbolInfo.SymbolKind.METHOD }
        }

        /**
         * Short-form property getter at module level: `Int val2.get() = 43;`.
         * The grammar's `module_body` rule must accept `property_getter_declaration`,
         * not just `property_declaration` and `method_declaration`. Same shape was
         * already accepted inside class bodies; this guards against regression of
         * the parity fix.
         */
        @Test
        @DisplayName("should parse module-level property getter")
        fun shouldParseModuleLevelPropertyGetter() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    Int val2.get() = 43;
                }
                """.trimIndent()

            val result = ts.compile(uri, source)

            assertThat(result.success).isTrue()
            assertThat(result.diagnostics)
                .noneMatch { it.severity == Diagnostic.Severity.ERROR }
        }

        /**
         * Same shape inside a package body. `package_body` mirrors `module_body`
         * for top-level declarations; both should now accept the short-form getter.
         */
        @Test
        @DisplayName("should parse package-level property getter")
        fun shouldParsePackageLevelPropertyGetter() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    package util {
                        Int val2.get() = 43;
                    }
                }
                """.trimIndent()

            val result = ts.compile(uri, source)

            assertThat(result.success).isTrue()
            assertThat(result.diagnostics)
                .noneMatch { it.severity == Diagnostic.Severity.ERROR }
        }

        /**
         * Fractional duration literal: `Duration:0.5S` (half a second). The
         * `duration_literal` regex previously accepted only integer values per
         * unit, so `manualTests/.../services.x`'s `Timeout(Duration:0.5S, True)`
         * call failed to parse. ISO-8601 allows fractional values; the grammar
         * regex now matches `[0-9]+(\.[0-9]+)?` per unit.
         */
        @Test
        @DisplayName("should parse fractional duration literal")
        fun shouldParseFractionalDurationLiteral() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    void run() {
                        Duration d1 = Duration:0.5S;
                        Duration d2 = Duration:0.001S;
                        Duration d3 = Duration:30S;
                    }
                }
                """.trimIndent()

            val result = ts.compile(uri, source)

            assertThat(result.success).isTrue()
            assertThat(result.diagnostics)
                .noneMatch { it.severity == Diagnostic.Severity.ERROR }
        }

        /**
         * Multi-return destructuring assignment with annotations on each element:
         * `(@Future Int v1, @Future Int v2) = svc.multiReturn(...)`. This is how
         * services declare async future variables via tuple destructuring. The
         * `tuple_assignment_element` rule previously accepted typed forms but
         * not leading annotations, so `manualTests/.../services.x`'s multi-return
         * call site failed to parse. The rule now allows `repeat($.annotation)`
         * before both the val/var-prefixed form and the type-prefixed form, with
         * a 2-way conflict declared between `parameter` and `tuple_assignment_element`.
         */
        @Test
        @DisplayName("should parse annotated multi-return destructuring assignment")
        fun shouldParseAnnotatedMultiReturnAssignment() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    void run() {
                        (@Future Int v1, @Future Int v2) = svc.multiReturn(1, 2);
                    }
                }
                """.trimIndent()

            val result = ts.compile(uri, source)

            assertThat(result.success).isTrue()
            assertThat(result.diagnostics)
                .noneMatch { it.severity == Diagnostic.Severity.ERROR }
        }

        /**
         * Stacked switch labels: `default: case 0..39: ...` (or any combination
         * of `case ...:` and `default:` labels for the same arm). The grammar's
         * `case_clause` and `expression_case_clause` rules previously allowed a
         * leading `repeat(case <pattern>:)` followed by a final `case <pattern>`
         * or `default` -- so `default:` could only appear as the final label,
         * not stacked before another `case:`. The repeat now accepts both
         * `case <pattern>:` and `default:`. From `manualTests/.../StringBufferTest.x`.
         */
        @Test
        @DisplayName("should parse stacked default+case switch labels")
        fun shouldParseStackedDefaultAndCaseLabels() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    Int classify(Int n) {
                        return switch (n) {
                            default:
                            case 0..39: 1;
                            case 40..59: 2;
                            case 60..100: 3;
                        };
                    }
                }
                """.trimIndent()

            val result = ts.compile(uri, source)

            assertThat(result.success).isTrue()
            assertThat(result.diagnostics)
                .noneMatch { it.severity == Diagnostic.Severity.ERROR }
        }

        /**
         * Constructor short-form body: `construct(...) = expr;`. The grammar
         * already supported short-form `= expr;` for methods and getters but
         * not constructors, so `@Override construct(String s) = TODO();`
         * (a placeholder constructor signature satisfying a contract) failed
         * to parse. From `manualTests/.../StringBufferTest.x`.
         */
        @Test
        @DisplayName("should parse short-form constructor body")
        fun shouldParseShortFormConstructorBody() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Step {
                        @Override construct(String s) = TODO();
                    }
                }
                """.trimIndent()

            val result = ts.compile(uri, source)

            assertThat(result.success).isTrue()
            assertThat(result.diagnostics)
                .noneMatch { it.severity == Diagnostic.Severity.ERROR }
        }

        /**
         * Single-element tuple expression with explicit trailing comma: `(x,)`.
         * Disambiguates a 1-tuple from a parenthesized expression `(x)`. The
         * grammar previously accepted only empty `()` and 2+ element tuples;
         * `manualTests/.../tuple.x`'s `(1.toInt(), )` therefore failed.
         */
        @Test
        @DisplayName("should parse single-element tuple with trailing comma")
        fun shouldParseSingleElementTuple() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    void run() {
                        Tuple t = (42,);
                        Tuple u = (1.toInt(),);
                    }
                }
                """.trimIndent()

            val result = ts.compile(uri, source)

            assertThat(result.success).isTrue()
            assertThat(result.diagnostics)
                .noneMatch { it.severity == Diagnostic.Severity.ERROR }
        }

        /**
         * Annotation on a for-loop's variable initializer:
         * `for (@Watch(x) Int i = 3; i > 0; --i) {}`. The `for_var_declarations`
         * rule previously accepted only `Type identifier = expr` without
         * leading annotations. Local-variable annotations like `@Watch(...)`
         * (used in `manualTests/.../annos.x` for property-watch tracing) needed
         * the same `repeat($.annotation)` prefix that other variable-declaration
         * forms already had. Without this, the for-loop init failed to parse
         * and cascade errors propagated through the rest of the method body.
         */
        @Test
        @DisplayName("should parse annotation on for-loop variable initializer")
        fun shouldParseAnnotationOnForLoopInit() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    void run() {
                        for (@Watch(logger) Int i = 3; i > 0; --i) {}
                    }
                }
                """.trimIndent()

            val result = ts.compile(uri, source)

            assertThat(result.success).isTrue()
            assertThat(result.diagnostics)
                .noneMatch { it.severity == Diagnostic.Severity.ERROR }
        }

        /**
         * Package-import resource-provider clause:
         * `package foo import bar.Baz inject(Int n, String _) using ProviderType;`.
         * The `inject(parameters)` clause declares the formal parameters the
         * resource provider must supply; `using <ProviderType>` names the
         * provider class. Both clauses are optional. From
         * `manualTests/.../container.x`'s
         * `package contained import TestContained inject(Int value, String _) using SimpleResourceProvider;`.
         */
        @Test
        @DisplayName("should parse package-import resource-provider clause")
        fun shouldParsePackageImportWithResourceProvider() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    package contained import TestContained inject(Int value, String _) using SimpleResourceProvider;
                    package other import OtherModule using OtherProvider;
                    package plain import PlainModule;
                }
                """.trimIndent()

            val result = ts.compile(uri, source)

            assertThat(result.success).isTrue()
            assertThat(result.diagnostics)
                .noneMatch { it.severity == Diagnostic.Severity.ERROR }
        }

        /**
         * Wildcard `_` is allowed as the name in a `val`/`var` declaration to
         * discard a value: `val _ = r.eof;`. The grammar's
         * `variable_declaration` `name:` field must accept the wildcard token
         * in addition to identifiers. Without this, the standard library's
         * "evaluate-and-discard" idiom fails to parse.
         */
        @Test
        @DisplayName("should parse wildcard variable name in val/var declaration")
        fun shouldParseWildcardVariableNameInValDeclaration() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class C {
                        Int eof = 0;
                        Int size = 0;
                    }
                    void run(C r) {
                        val _ = r.eof;
                        var _ = r.size;
                    }
                }
                """.trimIndent()

            val result = ts.compile(uri, source)

            assertThat(result.success).isTrue()
            assertThat(result.diagnostics)
                .noneMatch { it.severity == Diagnostic.Severity.ERROR }
        }

        /**
         * Bare relative-path literal as a primary expression:
         * `File f = ./IO.x;` or `Directory d = ../shared;`. Distinct from
         * `#path` (binary file embed) and `$./path` (string file embed) --
         * this form is a runtime File/Directory reference. Without this
         * rule, `manualTests/.../IO.x` failed at the assignment.
         */
        @Test
        @DisplayName("should parse relative path literal as expression")
        fun shouldParseRelativePathLiteralAsExpression() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    void run() {
                        File      f1 = ./IO.x;
                        File      f2 = ../shared/data.txt;
                        Directory d  = ./subdir;
                    }
                }
                """.trimIndent()

            val result = ts.compile(uri, source)

            assertThat(result.success).isTrue()
            assertThat(result.diagnostics)
                .noneMatch { it.severity == Diagnostic.Severity.ERROR }
        }

        /**
         * `assert:rnd(N) cond` is the sample-rate form of assert -- the
         * assertion fires roughly once per N invocations. The argument list
         * is specific to `assert:rnd`; the other variants (`assert:arg`,
         * `assert:bounds`, ...) do not take args, and in particular
         * `assert:arg (Type x, ...) := expr` uses the parens for a tuple
         * destructuring conditional declaration, not a variant arg list.
         * The grammar must distinguish these.
         */
        @Test
        @DisplayName("should parse assert:rnd with sample-rate argument")
        fun shouldParseAssertRndWithSampleRate() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    void run(Int size) {
                        for (Int i : 1..1000) {
                            assert:rnd(100) i < size;
                        }
                    }
                }
                """.trimIndent()

            val result = ts.compile(uri, source)

            assertThat(result.success).isTrue()
            assertThat(result.diagnostics)
                .noneMatch { it.severity == Diagnostic.Severity.ERROR }
        }

        /**
         * Companion to `shouldParseAssertRndWithSampleRate`: confirms that
         * `assert:arg` followed by a tuple destructuring conditional
         * declaration still parses (regression guard -- an earlier attempt
         * at the assert:rnd fix accidentally swallowed `(Type x, ...)` as
         * a variant arg list, breaking `lib_net/.../UriTemplate.x`).
         */
        @Test
        @DisplayName("should parse assert:arg with tuple destructuring conditional")
        fun shouldParseAssertArgWithTupleDestructuring() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    conditional (Int, String) parse(String s) {
                        return False;
                    }
                    void run() {
                        assert:arg (Int n, String t) := parse("x");
                    }
                }
                """.trimIndent()

            val result = ts.compile(uri, source)

            assertThat(result.success).isTrue()
            assertThat(result.diagnostics)
                .noneMatch { it.severity == Diagnostic.Severity.ERROR }
        }

        /**
         * `assert !(Type x := expr)` -- negated typed conditional binding.
         * Used to assert that a `:=` conditional form *fails* to bind
         * (e.g. asserting that `next()` does not yield a value of the
         * named type). The negation cannot be expressed by simply wrapping
         * a `_expression` because the parenthesized form contains a
         * `conditional_declaration`, which is not an expression.
         */
        @Test
        @DisplayName("should parse negated typed conditional in assert")
        fun shouldParseNegatedTypedConditionalInAssert() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    enum Color {Red, Green, Blue}
                    void run() {
                        Color c1 = Red;
                        assert !(Color c2 := c1.next());
                    }
                }
                """.trimIndent()

            val result = ts.compile(uri, source)

            assertThat(result.success).isTrue()
            assertThat(result.diagnostics)
                .noneMatch { it.severity == Diagnostic.Severity.ERROR }
        }

        /**
         * Version literal: `v:` followed by a version string. Covers the
         * full range of forms Ecstasy supports -- simple integer
         * (`v:1`), dotted (`v:1.2`, `v:5.6.7.8`), pre-release identifier
         * alone (`v:beta2`), pre-release suffix on a dotted version
         * (`v:5.6.7.8-alpha`, `v:1.2-beta5`), concatenated pre-release
         * (`v:1.2beta5`), and build-metadata suffix (`v:1.2beta5+123-456.abc`).
         * Without this, `typed_literal` would try to parse `v` as a type
         * expression, `:` as the typed-literal separator, and the rest as
         * a regular literal -- which works only for trivial cases and
         * fails on multi-segment dotted versions and pre-release suffixes.
         */
        @Test
        @DisplayName("should parse version literals across all supported forms")
        fun shouldParseVersionLiterals() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    void run() {
                        Version v1 = v:1;
                        Version v2 = v:1.0;
                        Version v3 = v:beta2;
                        Version v4 = v:5.6.7.8-alpha;
                        Version v5 = v:1.2-beta5;
                        Version v6 = v:1.2beta5;
                        Version v7 = v:1.2beta5+123-456.abc;
                    }
                }
                """.trimIndent()

            val result = ts.compile(uri, source)

            assertThat(result.success).isTrue()
            assertThat(result.diagnostics)
                .noneMatch { it.severity == Diagnostic.Severity.ERROR }
        }

        /**
         * A missing closing brace should still parse (tree-sitter is error-tolerant),
         * but the resulting tree must contain ERROR or MISSING nodes that get reported
         * as diagnostics with severity ERROR.
         */
        @Test
        @DisplayName("should detect syntax errors")
        fun shouldDetectSyntaxErrors() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Person {
                """.trimIndent()

            val result = ts.compile(uri, source)

            assertThat(result.diagnostics).isNotEmpty()
            assertThat(result.diagnostics)
                .anyMatch { it.severity == Diagnostic.Severity.ERROR }
        }

        /**
         * Tree-sitter's error-recovery means a valid class followed by a malformed one
         * should yield both diagnostics (for the broken class) and the valid "Person"
         * symbol -- the key advantage over a traditional parser that would bail out.
         */
        @Test
        @DisplayName("should perform error-tolerant parsing")
        fun shouldPerformErrorTolerantParsing() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Person {
                    }
                    class {
                }
                """.trimIndent()

            val result = ts.compile(uri, source)

            assertThat(result.diagnostics).isNotEmpty()
            assertThat(result.symbols)
                .anyMatch { it.name == "Person" && it.kind == SymbolInfo.SymbolKind.CLASS }
        }

        /**
         * Re-compiling the same URI with different content exercises the incremental
         * parsing path (the adapter passes the old tree to `parser.parse`). Both
         * compilations must succeed independently.
         */
        @Test
        @DisplayName("should support incremental re-parsing")
        fun shouldSupportIncrementalReparsing() {
            val uri = freshUri()
            val first = ts.compile(uri, "module myapp { class A {} }")
            val second = ts.compile(uri, "module myapp { class B {} }")

            assertThat(first.symbols).anyMatch { it.name == "A" }
            assertThat(second.symbols).anyMatch { it.name == "B" }
            assertThat(second.symbols).noneMatch { it.name == "A" }
        }
    }

    // ========================================================================
    // findSymbolAt()
    // ========================================================================

    @Nested
    @DisplayName("findSymbolAt()")
    inner class FindSymbolAtTests {
        /**
         * Positioning the cursor on the class name "Person" (line 1, col 10) should
         * resolve to the class declaration via the AST node lookup + declaration query.
         */
        @Test
        @DisplayName("should find class symbol at cursor position")
        fun shouldFindClassSymbol() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            val symbol = ts.findSymbolAt(uri, 1, 10)

            assertThat(symbol).isNotNull
            assertThat(symbol!!.name).isEqualTo("Person")
            assertThat(symbol.kind).isEqualTo(SymbolInfo.SymbolKind.CLASS)
        }

        /** A position past the end of the document has no AST node, so must return null. */
        @Test
        @DisplayName("should return null past EOF")
        fun shouldReturnNullPastEof() {
            val uri = freshUri()
            ts.compile(uri, "module myapp {}")
            assertThat(ts.findSymbolAt(uri, 100, 0)).isNull()
        }

        /**
         * Same doc-comment landing fix as [XtcQueryEngine.findAllDeclarations] but
         * for the [XtcQueryEngine.findDeclarationAt] path (cursor-inside-a-declaration).
         * The returned symbol's location must point at the identifier line, not at
         * the leading doc-comment opener.
         */
        @Test
        @DisplayName("doc-commented method symbol location should be the name identifier")
        fun docCommentedMethodSymbolShouldLandOnNameLine() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Person {
                        /**
                         * Returns the name.
                         */
                        String getName() {
                            return "n";
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // cursor inside the method body so findDeclarationAt walks up to method_declaration.
            val symbol = ts.findSymbolAt(uri, 6, 16)

            assertThat(symbol).isNotNull
            assertThat(symbol!!.name).isEqualTo("getName")
            // 0-indexed: doc-open=2, doc-body=3, doc-close=4, `String getName()` line = 5.
            // The name identifier `getName` is on line 5.
            assertThat(symbol.location.startLine).isEqualTo(5)
        }

        /** Same as above but for a property declaration. */
        @Test
        @DisplayName("doc-commented property symbol location should be the name identifier")
        fun docCommentedPropertySymbolShouldLandOnNameLine() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Person {
                        /**
                         * The name.
                         */
                        String name;
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // cursor on `name` in the property declaration line.
            val symbol = ts.findSymbolAt(uri, 5, 15)

            assertThat(symbol).isNotNull
            assertThat(symbol!!.name).isEqualTo("name")
            assertThat(symbol.location.startLine).isEqualTo(5)
        }
    }

    // ========================================================================
    // getHoverInfo()
    // ========================================================================

    @Nested
    @DisplayName("getHoverInfo()")
    inner class HoverTests {
        /**
         * Hover delegates to [TreeSitterAdapter.findSymbolAt] then formats the symbol
         * as Markdown. Positioning on "Person" should produce hover text containing
         * the class name.
         */
        @Test
        @DisplayName("should return hover info for class")
        fun shouldReturnHoverInfoForClass() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            val hover = ts.getHoverInfo(uri, 1, 10)

            assertThat(hover).isNotNull
            assertThat(hover).contains("Person")
        }

        /** No AST node exists past EOF, so hover must gracefully return null. */
        @Test
        @DisplayName("should return null past EOF")
        fun shouldReturnNullPastEof() {
            val uri = freshUri()
            ts.compile(uri, "module myapp {}")

            assertThat(ts.getHoverInfo(uri, 100, 0)).isNull()
        }
    }
}
