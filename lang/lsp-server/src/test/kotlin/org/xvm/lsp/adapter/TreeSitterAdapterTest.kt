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
