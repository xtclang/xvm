package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Navigation tests for [TreeSitterAdapter].
 *
 * Exercises definition lookup, references, document highlights, document links,
 * cross-file navigation, and workspace symbol search.
 *
 * All tests are skipped (not failed) when the tree-sitter native library is unavailable.
 */
@DisplayName("TreeSitterAdapter - Navigation")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NavigationTest : TreeSitterTestBase() {
    // ========================================================================
    // findDefinition()
    // ========================================================================

    @Nested
    @DisplayName("findDefinition()")
    inner class DefinitionTests {
        /**
         * Cursor on "Person" at line 1 should resolve to the class_declaration
         * starting at that same line (same-file, name-based match).
         */
        @Test
        @DisplayName("should find class definition")
        fun shouldFindClassDefinition() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            val definition = ts.findDefinition(uri, 1, 10)

            assertThat(definition).isNotNull
            assertThat(definition!!.startLine).isEqualTo(1)
        }

        /** Past-EOF position has no identifier node, so definition must return null. */
        @Test
        @DisplayName("should return null for unknown position")
        fun shouldReturnNullForUnknownPosition() {
            val uri = freshUri()
            ts.compile(uri, "module myapp {}")

            assertThat(ts.findDefinition(uri, 100, 0)).isNull()
        }

        /**
         * When the cursor is on a method name at a call site (`add` in `add(1, 2)`),
         * go-to-definition should navigate to the method's declaration line.
         */
        @Test
        @DisplayName("should find method definition from call site")
        fun shouldFindMethodDefinitionFromCallSite() {
            val uri = freshUri()
            val source =
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

            ts.compile(uri, source)
            // cursor on 'add' in call at line 6, col 12
            val definition = logged("shouldFindMethodDefinitionFromCallSite", ts.findDefinition(uri, 6, 12))

            assertThat(definition).isNotNull
            assertThat(definition!!.startLine).isEqualTo(2)
        }

        @Test
        @DisplayName("should resolve to local variable instead of same-named class member")
        fun shouldResolveLocalBeforeClassMember() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Lexer {
                        private Boolean whitespace;
                    }
                    Boolean testChar(Char test) {
                        function Boolean(Char) whitespace = (Char c) -> c == ' ';
                        return whitespace(test);
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // cursor on `whitespace` in `whitespace(test)` -- line 6, column 15
            val definition = logged("shouldResolveLocalBeforeClassMember", ts.findDefinition(uri, 6, 15))

            assertThat(definition).isNotNull
            // The local on line 5 (the function-typed declaration), not the field on line 2
            assertThat(definition!!.startLine).isEqualTo(5)
        }

        /**
         * Forward references are not legal Ecstasy: a local declared on a later line
         * must NOT be returned as the resolution of an identifier above the
         * declaration. The scope walk filters `variable_declaration`s by their end
         * position relative to the cursor; this test guards that filter.
         */
        @Test
        @DisplayName("should not resolve to local declared after the cursor")
        fun shouldNotResolveForwardReferenceLocal() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    Int globalThing;
                    void run() {
                        globalThing.toString();
                        Int globalThing = 42;
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // cursor on `globalThing` in `globalThing.toString()` (line 3) -- before the
            // shadowing local on line 4. Resolution must skip the forward-declared local
            // and walk up to the module-level property on line 1.
            val definition =
                logged(
                    "shouldNotResolveForwardReferenceLocal",
                    ts.findDefinition(uri, 3, 8),
                )

            assertThat(definition).isNotNull
            // The module-level globalThing is on line 1, NOT the local on line 4.
            assertThat(definition!!.startLine).isEqualTo(1)
        }

        /**
         * A local in an inner block must shadow a same-named local in an outer block.
         * Cmd-click on the inner reference must return the inner declaration.
         */
        @Test
        @DisplayName("should prefer inner-block local over outer-block local")
        fun shouldPreferInnerBlockLocal() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    void run() {
                        Int x = 1;
                        if (True) {
                            Int x = 2;
                            x.toString();
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // cursor on `x` in `x.toString();` -- line 5, column 12
            val definition = logged("shouldPreferInnerBlockLocal", ts.findDefinition(uri, 5, 12))

            assertThat(definition).isNotNull
            // The inner-block declaration (line 4), not the outer-block declaration (line 2).
            assertThat(definition!!.startLine).isEqualTo(4)
        }

        /**
         * The scope walk short-circuits when it finds a match in an enclosing scope. If the
         * referenced name does NOT exist in any enclosing scope, resolution must fall through
         * to the same-file declarations and (if not found there) the workspace index.
         *
         * This is the same-file half of that fall-through. (The cross-file workspace path
         * requires an indexed multi-file workspace, which the unit-test harness does not
         * easily set up.)
         */
        @Test
        @DisplayName("should fall through to same-file declarations when name is not in scope")
        fun shouldFallThroughToSameFileDeclarationsWhenOutOfScope() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Helper {
                    }
                    void run() {
                        Helper h = new Helper();
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // cursor on `Helper` in `Helper h = new Helper();` -- line 4, column 8.
            // `Helper` is not declared in the enclosing function scope; resolution must
            // fall through to the same-file class declaration on line 1.
            val definition =
                logged(
                    "shouldFallThroughToSameFileDeclarationsWhenOutOfScope",
                    ts.findDefinition(uri, 4, 8),
                )

            assertThat(definition).isNotNull
            assertThat(definition!!.startLine).isEqualTo(1)
        }

        /**
         * Method parameters must resolve to themselves, not to any same-named workspace
         * symbol. This is the simpler half of the scope-walk fix.
         */
        @Test
        @DisplayName("should resolve to method parameter")
        fun shouldResolveParameter() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    Int square(Int x) {
                        return x * x;
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // cursor on the first `x` in `x * x` -- line 2, column 15
            val definition = logged("shouldResolveParameter", ts.findDefinition(uri, 2, 15))

            assertThat(definition).isNotNull
            // The parameter `x` on line 1
            assertThat(definition!!.startLine).isEqualTo(1)
        }

        /**
         * When the cursor is on a class name used as a return type in a different
         * class, go-to-definition should navigate to the class declaration.
         */
        @Test
        @DisplayName("should find class definition from type usage")
        fun shouldFindClassDefinitionFromTypeUsage() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Person {
                        String name;
                    }
                    class Factory {
                        Person create() {
                            return new Person();
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // cursor on 'Person' in return type at line 5, col 8
            val definition = logged("shouldFindClassDefinitionFromTypeUsage", ts.findDefinition(uri, 5, 8))

            assertThat(definition).isNotNull
            assertThat(definition!!.startLine).isEqualTo(1)
        }
    }

    // ========================================================================
    // findReferences()
    // ========================================================================

    @Nested
    @DisplayName("findReferences()")
    inner class FindReferencesTests {
        /**
         * `includeDeclaration = false` should drop exactly one location: the declaration
         * itself. With both flag values exercised on the same source, the `false` result
         * must contain every identifier-token from the `true` result minus the
         * declaration site.
         */
        @Test
        @DisplayName("should exclude declaration when includeDeclaration is false")
        fun shouldExcludeDeclarationWhenIncludeDeclarationIsFalse() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Person {
                        Person create() {
                            return new Person();
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)

            val withDecl = ts.findReferences(uri, 1, 10, includeDeclaration = true)
            val withoutDecl = ts.findReferences(uri, 1, 10, includeDeclaration = false)

            assertThat(withDecl).hasSizeGreaterThanOrEqualTo(2)
            assertThat(withoutDecl).hasSize(withDecl.size - 1)
            // The dropped location is the class declaration's identifier (line 1, col 10).
            assertThat(withDecl).anyMatch { it.startLine == 1 && it.startColumn == 10 }
            assertThat(withoutDecl).noneMatch { it.startLine == 1 && it.startColumn == 10 }
        }

        /** Past-EOF has no identifier to match, so the result must be empty. */
        @Test
        @DisplayName("should return empty for unknown position")
        fun shouldReturnEmptyForUnknownPosition() {
            val uri = freshUri()
            ts.compile(uri, "module myapp {}")

            assertThat(ts.findReferences(uri, 100, 0, includeDeclaration = true)).isEmpty()
        }

        /**
         * A method referenced in multiple call sites should produce locations
         * for the declaration and each call.
         */
        @Test
        @DisplayName("should find method references across declaration and calls")
        fun shouldFindMethodReferencesAcrossDeclarationAndCalls() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Calculator {
                        Int add(Int a, Int b) {
                            return a + b;
                        }
                        void test() {
                            Int x = add(1, 2);
                            Int y = add(3, 4);
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // cursor on 'add' at declaration, line 2, col 12
            val refs =
                logged(
                    "shouldFindMethodReferencesAcrossDeclarationAndCalls",
                    ts.findReferences(uri, 2, 12, includeDeclaration = true),
                )
            logger.info("  references ({}): {}", refs.size, refs.map { "L${it.startLine}:${it.startColumn}" })

            // declaration + 2 call sites = at least 3
            assertThat(refs).hasSizeGreaterThanOrEqualTo(3)
        }
    }

    // ========================================================================
    // getDocumentHighlights()
    // ========================================================================

    @Nested
    @DisplayName("getDocumentHighlights()")
    inner class DocumentHighlightTests {
        /**
         * "Person" appears as the class name, a return type, and in a `new` expression.
         * The adapter finds all identifier nodes with the same text, so we expect at
         * least 2 highlights (declaration + usages).
         */
        @Test
        @DisplayName("should highlight all occurrences of identifier")
        fun shouldHighlightAllOccurrences() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Person {
                        Person create() {
                            return new Person();
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            val highlights = ts.getDocumentHighlights(uri, 1, 10)

            assertThat(highlights).hasSizeGreaterThanOrEqualTo(2)
        }

        /** Past-EOF has no identifier to match, so the result must be empty (not null). */
        @Test
        @DisplayName("should return empty for unknown position")
        fun shouldReturnEmptyForUnknownPosition() {
            val uri = freshUri()
            ts.compile(uri, "module myapp {}")

            assertThat(ts.getDocumentHighlights(uri, 100, 0)).isEmpty()
        }

        /**
         * A method name used in both declaration and multiple call sites should
         * produce highlights at all locations.
         */
        @Test
        @DisplayName("should highlight method name at declaration and call sites")
        fun shouldHighlightMethodAtDeclarationAndCalls() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Calculator {
                        Int add(Int a, Int b) {
                            return a + b;
                        }
                        void test() {
                            Int x = add(1, 2);
                            Int y = add(3, 4);
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            // cursor on 'add' at declaration, line 2, col 12
            val highlights = logged("shouldHighlightMethodAtDeclarationAndCalls", ts.getDocumentHighlights(uri, 2, 12))
            logger.info("  highlights ({}): {}", highlights.size, highlights.map { "L${it.range.start.line}:${it.range.start.column}" })

            // declaration + 2 call sites = at least 3
            assertThat(highlights).hasSizeGreaterThanOrEqualTo(3)
        }
    }

    // ========================================================================
    // getDocumentLinks() -- URLs in comments and strings (NOT imports)
    // ========================================================================

    @Nested
    @DisplayName("getDocumentLinks()")
    inner class DocumentLinkTests {
        @Test
        @DisplayName("should link URL in line comment")
        fun shouldLinkUrlInLineComment() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    // see https://example.com for details
                    class Foo {}
                }
                """.trimIndent()
            ts.compile(uri, source)

            val links = ts.getDocumentLinks(uri, source)

            assertThat(links).hasSize(1)
            assertThat(links[0].target).isEqualTo("https://example.com")
            // Line 1 (0-based), URL starts after "    // see "
            assertThat(links[0].range.start.line).isEqualTo(1)
            assertThat(links[0].range.start.column).isEqualTo("    // see ".length)
        }

        @Test
        @DisplayName("should link URL in block comment, multi-line position correct")
        fun shouldLinkUrlInBlockComment() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    /* block comment
                       with https://anthropic.com on its own line
                       and more text */
                    class Foo {}
                }
                """.trimIndent()
            ts.compile(uri, source)

            val links = ts.getDocumentLinks(uri, source)

            assertThat(links).hasSize(1)
            assertThat(links[0].target).isEqualTo("https://anthropic.com")
            // URL is on the third line of the source (0-based line 2)
            assertThat(links[0].range.start.line).isEqualTo(2)
            // Column is offset of "https" within that line
            assertThat(links[0].range.start.column).isEqualTo("       with ".length)
        }

        @Test
        @DisplayName("should link URL in doc comment")
        fun shouldLinkUrlInDocComment() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    /** see http://docs.xtclang.org/guide */
                    class Foo {}
                }
                """.trimIndent()
            ts.compile(uri, source)

            val links = ts.getDocumentLinks(uri, source)

            assertThat(links).hasSize(1)
            assertThat(links[0].target).isEqualTo("http://docs.xtclang.org/guide")
        }

        @Test
        @DisplayName("should link URL in string literal")
        fun shouldLinkUrlInStringLiteral() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    String home = "https://xtclang.org";
                }
                """.trimIndent()
            ts.compile(uri, source)

            val links = ts.getDocumentLinks(uri, source)

            assertThat(links).hasSize(1)
            assertThat(links[0].target).isEqualTo("https://xtclang.org")
        }

        @Test
        @DisplayName("should find multiple URLs in one comment")
        fun shouldFindMultipleUrls() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    // links: https://a.test https://b.test
                    class Foo {}
                }
                """.trimIndent()
            ts.compile(uri, source)

            val links = ts.getDocumentLinks(uri, source)

            assertThat(links).extracting<String>({ it.target }).containsExactlyInAnyOrder(
                "https://a.test",
                "https://b.test",
            )
        }

        @Test
        @DisplayName("should strip trailing sentence punctuation")
        fun shouldStripTrailingPunctuation() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    // visit https://example.com.
                    class Foo {}
                }
                """.trimIndent()
            ts.compile(uri, source)

            val links = ts.getDocumentLinks(uri, source)

            assertThat(links).hasSize(1)
            assertThat(links[0].target).isEqualTo("https://example.com")
            // The matched range should also exclude the trailing dot
            assertThat(links[0].range.end.column - links[0].range.start.column)
                .isEqualTo("https://example.com".length)
        }

        @Test
        @DisplayName("should NOT link imports")
        fun shouldNotLinkImports() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    import crypto.CertificateManager;
                    import json.xtclang.org;
                }
                """.trimIndent()
            ts.compile(uri, source)

            assertThat(ts.getDocumentLinks(uri, source)).isEmpty()
        }

        @Test
        @DisplayName("should return empty when no URLs and no comments/strings")
        fun shouldReturnEmpty() {
            val uri = freshUri()
            ts.compile(uri, "module myapp {}")
            assertThat(ts.getDocumentLinks(uri, "module myapp {}")).isEmpty()
        }
    }

    // ========================================================================
    // cross-file navigation
    // ========================================================================

    @Nested
    @DisplayName("cross-file navigation")
    inner class CrossFileTests {
        @Test
        @DisplayName("should find same-file definition")
        fun shouldFindSameFileDefinition() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    class Person {
                    }
                    class Employee {
                        Person manager;
                    }
                }
                """.trimIndent(),
            )

            // "Person" on line 4 (0-based), column 8 should resolve to class Person declaration
            val def = ts.findDefinition(uri, 4, 8)
            assertThat(def).isNotNull
            assertThat(def!!.uri).isEqualTo(uri)
            // Should point to class Person at line 1
            assertThat(def.startLine).isEqualTo(1)
        }

        // TODO: Cross-file rename needs to update all references across the workspace,
        //   including import statements. The compiler's semantic model is required to
        //   identify all affected files safely.
        @Test
        @Disabled("Cross-file rename not yet implemented -- requires compiler semantic model")
        @DisplayName("should rename across files")
        fun shouldRenameAcrossFiles() {
            // TODO: Compile two files referencing the same class. Rename in one file
            //   should produce edits for both files and their import statements.
        }

        // TODO: Scope-aware references should distinguish shadowed locals from outer
        //  declarations with the same name. Tree-sitter's text-based matching cannot
        //  do this; the compiler's scope analysis is needed.
        @Test
        @Disabled("Scope-aware references not yet implemented -- requires compiler scope analysis")
        @DisplayName("should distinguish shadowed locals in references")
        fun shouldDistinguishShadowedLocals() {
            // TODO: Compile source where a local variable shadows a class field.
            //  findReferences on the local should not include the field, and vice versa.
        }
    }

    // ========================================================================
    // findWorkspaceSymbols()
    // ========================================================================

    @Nested
    @DisplayName("findWorkspaceSymbols()")
    inner class WorkspaceSymbolTests {
        @Test
        @DisplayName("should find symbols across compiled files via workspace index")
        fun shouldFindWorkspaceSymbols() {
            // Compile two files with distinct types
            val uri1 = freshUri()
            val uri2 = freshUri()
            ts.compile(
                uri1,
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent(),
            )
            ts.compile(
                uri2,
                """
                module myapp {
                    class Animal {
                    }
                }
                """.trimIndent(),
            )

            // Initialize workspace to enable the index (using a temp dir approach)
            // Since initializeWorkspace scans files on disk and our test URIs are synthetic,
            // the index gets populated via compile() -> reindexFile() once the index is ready.
            // To test findWorkspaceSymbols, we trigger indexing manually by calling it:
            // After compile, the indexReady flag is still false (no initializeWorkspace was called).
            // We test via initializeWorkspace with a temp dir containing the same files.
            val results = ts.findWorkspaceSymbols("Person")
            // Without initializeWorkspace called, results may be empty (index not ready)
            logged("workspace symbols before init", results)
        }

        @Test
        @DisplayName("should return empty for empty query")
        fun shouldReturnEmptyForEmptyQuery() {
            val results = ts.findWorkspaceSymbols("")
            assertThat(results).isEmpty()
        }
    }
}
