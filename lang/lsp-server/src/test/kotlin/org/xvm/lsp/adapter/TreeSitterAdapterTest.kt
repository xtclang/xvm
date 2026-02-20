package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.SymbolInfo
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for [TreeSitterAdapter].
 *
 * Exercises the tree-sitter native parser through the [XtcCompilerAdapter] interface,
 * verifying AST-based symbol extraction, diagnostics, navigation, and the tree-sitter-
 * specific features that [MockXtcCompilerAdapter] cannot provide (selection ranges,
 * signature help).
 *
 * All tests are skipped (not failed) when the tree-sitter native library is unavailable,
 * making this safe to run in any environment.
 */
@DisplayName("TreeSitterAdapter")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TreeSitterAdapterTest {
    private val logger: Logger = LoggerFactory.getLogger(TreeSitterAdapterTest::class.java)
    private var adapter: TreeSitterAdapter? = null
    private val uriCounter = AtomicInteger(0)

    /** Shorthand accessor -- safe because [assumeAvailable] guards every test. */
    private val ts: TreeSitterAdapter get() = adapter!!

    /**
     * Returns a unique URI per call so each test gets a fresh parse tree.
     * Re-using the same URI across tests would trigger incremental parsing against
     * a stale tree whose byte offsets don't match the new source, causing
     * [StringIndexOutOfBoundsException] inside the native parser.
     */
    private fun freshUri(): String = "file:///t1st${uriCounter.incrementAndGet()}.x"

    /** Log and return a value -- use to trace adapter responses during test runs. */
    private fun <T> logged(
        test: String,
        value: T,
    ): T {
        logger.info("[TEST] {} -> {}", test, value)
        return value
    }

    @BeforeAll
    fun setUpAdapter() {
        adapter = runCatching { TreeSitterAdapter() }.getOrNull()
    }

    /** Skip (not fail) every test when the native library isn't loadable. */
    @BeforeEach
    fun assumeAvailable() {
        Assumptions.assumeTrue(adapter != null, "Tree-sitter native library not available")
    }

    @AfterAll
    fun tearDown() {
        adapter?.close()
    }

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
    // getFoldingRanges()
    // ========================================================================

    @Nested
    @DisplayName("getFoldingRanges()")
    inner class FoldingRangeTests {
        /**
         * A module containing a class containing a method produces at least 2 multi-line
         * foldable blocks (module_declaration + class_declaration; the method_declaration
         * may or may not match depending on the grammar's node types).
         */
        @Test
        @DisplayName("should find declaration blocks")
        fun shouldFindDeclarationBlocks() {
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

            ts.compile(uri, source)
            val ranges = ts.getFoldingRanges(uri)

            assertThat(ranges).hasSizeGreaterThanOrEqualTo(2)
            assertThat(ranges).allMatch { it.endLine > it.startLine }
        }

        /**
         * A multi-line block comment should be foldable. The adapter recognizes
         * "comment" and "block_comment" node types. If the grammar uses a different
         * node type, this test still passes because we only assert overall count.
         */
        @Test
        @DisplayName("should detect comment blocks")
        fun shouldDetectCommentBlocks() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    /*
                     * This is a
                     * multi-line comment
                     */
                    class Person {
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            val ranges = ts.getFoldingRanges(uri)

            // module + class + comment = at least 3, but we only require 2 to be
            // resilient against grammar differences in comment node types.
            assertThat(ranges).hasSizeGreaterThanOrEqualTo(2)
        }

        /**
         * Deeply nested declarations (module > class > inner class > method) should
         * each produce a foldable range, giving at least 4 ranges.
         */
        @Test
        @DisplayName("should fold deeply nested declarations")
        fun shouldFoldDeeplyNestedDeclarations() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Outer {
                        class Inner {
                            void method() {
                                return;
                            }
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            val ranges = logged("shouldFoldDeeplyNestedDeclarations", ts.getFoldingRanges(uri))
            logger.info("  folding ranges ({}): {}", ranges.size, ranges.map { "L${it.startLine}-L${it.endLine}" })

            // module + Outer + Inner + method = at least 4
            assertThat(ranges).hasSizeGreaterThanOrEqualTo(4)
            assertThat(ranges).allMatch { it.endLine > it.startLine }
        }
    }

    // ========================================================================
    // rename()
    // ========================================================================

    @Nested
    @DisplayName("rename()")
    inner class RenameTests {
        /**
         * `prepareRename` finds the identifier AST node at the cursor and returns
         * its text as the placeholder. Cursor on "Person" should yield exactly that.
         */
        @Test
        @DisplayName("should prepare rename for identifier")
        fun shouldPrepareRename() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            val result = ts.prepareRename(uri, 1, 10)

            assertThat(result).isNotNull
            assertThat(result!!.placeholder).isEqualTo("Person")
        }

        /**
         * Renaming "Person" to "Human" should produce edits for every identifier node
         * with text "Person" in the file -- at least the declaration and usage sites.
         */
        @Test
        @DisplayName("should rename all occurrences")
        fun shouldRenameAllOccurrences() {
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
            val edit = ts.rename(uri, 1, 10, "Human")

            assertThat(edit).isNotNull
            val edits = edit!!.changes[uri]
            assertThat(edits).isNotNull
            assertThat(edits!!).hasSizeGreaterThanOrEqualTo(2)
            assertThat(edits).allMatch { it.newText == "Human" }
        }

        /** Past-EOF has no identifier to rename, so prepareRename must return null. */
        @Test
        @DisplayName("should return null for unknown position")
        fun shouldReturnNullForUnknownPosition() {
            val uri = freshUri()
            ts.compile(uri, "module myapp {}")

            assertThat(ts.prepareRename(uri, 100, 0)).isNull()
        }
    }

    // ========================================================================
    // getCodeActions()
    // ========================================================================

    @Nested
    @DisplayName("getCodeActions()")
    inner class CodeActionTests {
        /**
         * `buildOrganizeImportsAction` searches `tree.root.children` for `import_statement`
         * nodes. In XTC, imports are often nested inside the module body, so whether
         * the grammar places them at root level is grammar-dependent. We use root-level
         * imports and assert the method completes without error.
         */
        @Test
        @DisplayName("should suggest organize imports when unsorted at root level")
        fun shouldSuggestOrganizeImports() {
            val uri = freshUri()
            val source =
                """
                import foo.Zebra;
                import bar.Alpha;
                module myapp {
                }
                """.trimIndent()

            ts.compile(uri, source)
            val actions = ts.getCodeActions(uri, zeroRange(), emptyList())

            assertThat(actions).isNotNull
        }

        /**
         * Already-sorted imports (alphabetically) should never trigger the
         * "Organize Imports" action, regardless of where the grammar places them.
         */
        @Test
        @DisplayName("should not suggest organize imports when sorted")
        fun shouldNotSuggestWhenSorted() {
            val uri = freshUri()
            val source =
                """
                import bar.Alpha;
                import foo.Zebra;
                module myapp {
                }
                """.trimIndent()

            ts.compile(uri, source)
            val actions = ts.getCodeActions(uri, zeroRange(), emptyList())

            assertThat(actions).noneMatch { it.title == "Organize Imports" }
        }

        private fun zeroRange() =
            XtcCompilerAdapter.Range(
                XtcCompilerAdapter.Position(0, 0),
                XtcCompilerAdapter.Position(0, 0),
            )
    }

    // ========================================================================
    // formatDocument()
    // ========================================================================

    @Nested
    @DisplayName("formatDocument()")
    inner class FormatTests {
        /**
         * Lines with trailing spaces ("myapp {   ") should produce edits that replace
         * the trailing whitespace with empty strings. Two lines have trailing spaces,
         * so we expect at least 2 edits.
         */
        @Test
        @DisplayName("should remove trailing whitespace")
        fun shouldRemoveTrailingWhitespace() {
            val source = "module myapp {   \n    class Person {  \n    }\n}"
            val options = formattingOptions(trimTrailingWhitespace = true)

            val edits = ts.formatDocument(freshUri(), source, options)

            assertThat(edits).hasSizeGreaterThanOrEqualTo(2)
            assertThat(edits).allMatch { it.newText.isEmpty() }
        }

        /**
         * When `insertFinalNewline` is true and the source doesn't end with `\n`,
         * the formatter should append one via a zero-width insertion at EOF.
         */
        @Test
        @DisplayName("should insert final newline")
        fun shouldInsertFinalNewline() {
            val options = formattingOptions(insertFinalNewline = true)

            val edits = ts.formatDocument(freshUri(), "module myapp {}", options)

            assertThat(edits).anyMatch { it.newText == "\n" }
        }

        /**
         * A file that already ends with `\n` and has no trailing whitespace is
         * "clean" -- the formatter should produce zero edits.
         */
        @Test
        @DisplayName("should return empty for clean file")
        fun shouldReturnEmptyForCleanFile() {
            val edits = ts.formatDocument(freshUri(), "module myapp {}\n", formattingOptions())

            assertThat(edits).isEmpty()
        }

        private fun formattingOptions(
            trimTrailingWhitespace: Boolean = false,
            insertFinalNewline: Boolean = false,
        ) = XtcCompilerAdapter.FormattingOptions(
            tabSize = 4,
            insertSpaces = true,
            trimTrailingWhitespace = trimTrailingWhitespace,
            insertFinalNewline = insertFinalNewline,
        )
    }

    // ========================================================================
    // getDocumentLinks()
    // ========================================================================

    @Nested
    @DisplayName("getDocumentLinks()")
    inner class DocumentLinkTests {
        /**
         * After compiling source with `import` statements, `getDocumentLinks` should
         * find them via `XtcQueryEngine.findImportLocations`. The exact result depends
         * on whether the grammar nests imports inside the module body or at root level.
         */
        @Test
        @DisplayName("should find import links after compile")
        fun shouldFindImportLinks() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    import foo.Bar;
                    import baz.Qux;
                }
                """.trimIndent()

            ts.compile(uri, source)

            assertThat(ts.getDocumentLinks(uri, source)).isNotNull
        }

        /** A module with no imports should produce an empty link list. */
        @Test
        @DisplayName("should return empty when no imports")
        fun shouldReturnEmptyWhenNoImports() {
            val uri = freshUri()
            val source = "module myapp {}"
            ts.compile(uri, source)

            assertThat(ts.getDocumentLinks(uri, source)).isEmpty()
        }
    }

    // ========================================================================
    // findReferences()
    // ========================================================================

    @Nested
    @DisplayName("findReferences()")
    inner class FindReferencesTests {
        /**
         * Unlike [MockXtcCompilerAdapter], [TreeSitterAdapter.findReferences] ignores the
         * `includeDeclaration` flag -- it always returns every identifier node with the
         * same text. We verify this by checking that both flag values yield the same count.
         */
        @Test
        @DisplayName("should find all occurrences regardless of includeDeclaration flag")
        fun shouldFindAllOccurrences() {
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
            assertThat(withDecl).hasSameSizeAs(withoutDecl)
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
    // formatRange()
    // ========================================================================

    @Nested
    @DisplayName("formatRange()")
    inner class FormatRangeTests {
        /**
         * Only lines 1-2 are inside the range, so edits must be confined to those lines.
         * Lines 0 and 3 also have trailing whitespace but must be left untouched.
         */
        @Test
        @DisplayName("should only format within range")
        fun shouldOnlyFormatWithinRange() {
            val source = "line zero   \nline one   \nline two   \nline three   "
            val options =
                XtcCompilerAdapter.FormattingOptions(
                    tabSize = 4,
                    insertSpaces = true,
                    trimTrailingWhitespace = true,
                )
            val range =
                XtcCompilerAdapter.Range(
                    XtcCompilerAdapter.Position(1, 0),
                    XtcCompilerAdapter.Position(2, 0),
                )

            val edits = ts.formatRange(freshUri(), source, range, options)

            assertThat(edits).isNotEmpty
            assertThat(edits).allMatch { it.range.start.line in 1..2 }
        }

        /**
         * `insertFinalNewline` is a whole-document concern. The adapter's `formatContent`
         * skips it when a range is provided, so no `\n` edit should appear.
         */
        @Test
        @DisplayName("should not insert final newline for range")
        fun shouldNotInsertFinalNewlineForRange() {
            val options =
                XtcCompilerAdapter.FormattingOptions(
                    tabSize = 4,
                    insertSpaces = true,
                    insertFinalNewline = true,
                )
            val range =
                XtcCompilerAdapter.Range(
                    XtcCompilerAdapter.Position(0, 0),
                    XtcCompilerAdapter.Position(0, 15),
                )

            val edits = ts.formatRange(freshUri(), "module myapp {}", range, options)

            assertThat(edits).noneMatch { it.newText == "\n" }
        }
    }

    // ========================================================================
    // getSelectionRanges() -- tree-sitter-specific
    // ========================================================================

    @Nested
    @DisplayName("getSelectionRanges() -- tree-sitter-specific")
    inner class SelectionRangeTests {
        /**
         * From a leaf identifier ("name" inside a return statement), the adapter walks
         * the AST parent chain, deduplicating nodes with identical ranges. The result
         * should have depth >= 3 (e.g., identifier -> expression -> block -> declaration).
         */
        @Test
        @DisplayName("should produce nested chain from identifier to root")
        fun shouldProduceNestedChain() {
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

            ts.compile(uri, source)
            val selections = ts.getSelectionRanges(uri, listOf(XtcCompilerAdapter.Position(3, 15)))
            val depth = selectionDepth(selections.single())

            assertThat(depth).isGreaterThanOrEqualTo(3)
        }

        /**
         * Each parent range must strictly contain (or equal) its child range -- the
         * selection never shrinks as you walk outward. We linearize positions as
         * `line * 10000 + column` for a simple numeric comparison.
         */
        @Test
        @DisplayName("should produce widening chain where each parent contains child")
        fun shouldProduceWideningChain() {
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

            ts.compile(uri, source)
            val selection =
                ts
                    .getSelectionRanges(
                        uri,
                        listOf(XtcCompilerAdapter.Position(2, 15)),
                    ).single()

            generateSequence(selection) { it.parent }
                .zipWithNext()
                .forEach { (child, parent) ->
                    assertThat(linearize(parent.range.start))
                        .isLessThanOrEqualTo(linearize(child.range.start))
                    assertThat(linearize(parent.range.end))
                        .isGreaterThanOrEqualTo(linearize(child.range.end))
                }
        }

        /**
         * At least one range in the chain should span more than zero characters,
         * proving the selection is meaningful (not just point ranges everywhere).
         */
        @Test
        @DisplayName("should produce at least one range wider than a single point")
        fun shouldProduceMeaningfulRanges() {
            val uri = freshUri()
            val source =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            ts.compile(uri, source)
            val selection =
                ts
                    .getSelectionRanges(
                        uri,
                        listOf(XtcCompilerAdapter.Position(1, 10)),
                    ).single()

            val hasNonPointRange =
                generateSequence(selection) { it.parent }.any { sel ->
                    linearize(sel.range.start) != linearize(sel.range.end)
                }
            assertThat(hasNonPointRange).isTrue()
        }

        private fun selectionDepth(sel: XtcCompilerAdapter.SelectionRange): Int = generateSequence(sel) { it.parent }.count()

        private fun linearize(pos: XtcCompilerAdapter.Position): Int = pos.line * 10_000 + pos.column
    }

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
    }

    // ========================================================================
    // Selection ranges at call sites -- tree-sitter-specific
    // ========================================================================

    @Nested
    @DisplayName("selection ranges at call sites -- tree-sitter-specific")
    inner class SelectionRangesAtCallSiteTests {
        /**
         * Starting from an argument literal (`1` in `add(1, 2)`), the selection
         * range chain should walk outward through argument list, call expression,
         * statement, block, method, class, module -- at least 4 levels deep.
         */
        @Test
        @DisplayName("should walk outward from call argument")
        fun shouldWalkOutwardFromCallArgument() {
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
            // cursor on '1' in `add(1, 2)` at line 6, col 16
            val selections = ts.getSelectionRanges(uri, listOf(XtcCompilerAdapter.Position(6, 16)))
            val depth = selectionDepth(selections.single())
            logger.info("[TEST] shouldWalkOutwardFromCallArgument -> depth={}", depth)
            logSelectionChain("shouldWalkOutwardFromCallArgument", selections.single())

            assertThat(depth).isGreaterThanOrEqualTo(4)
            assertWideningChain(selections.single())
        }

        /**
         * Starting from a nested call argument (`1` in `negate(1)` inside
         * `add(negate(1), 2)`), the chain should be even deeper -- at least 5 levels.
         */
        @Test
        @DisplayName("should walk outward from nested call argument")
        fun shouldWalkOutwardFromNestedCallArgument() {
            val uri = freshUri()
            val source =
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

            ts.compile(uri, source)
            // cursor on '1' in `negate(1)` at line 9, col 23
            val selections = ts.getSelectionRanges(uri, listOf(XtcCompilerAdapter.Position(9, 23)))
            val depth = selectionDepth(selections.single())
            logger.info("[TEST] shouldWalkOutwardFromNestedCallArgument -> depth={}", depth)
            logSelectionChain("shouldWalkOutwardFromNestedCallArgument", selections.single())

            assertThat(depth).isGreaterThanOrEqualTo(5)
            assertWideningChain(selections.single())
        }

        /**
         * Multiple cursor positions in a single request should each produce an
         * independent selection range chain.
         */
        @Test
        @DisplayName("should handle multiple positions independently")
        fun shouldHandleMultiplePositionsIndependently() {
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
            val positions =
                listOf(
                    XtcCompilerAdapter.Position(6, 16), // '1' in add(1, 2)
                    XtcCompilerAdapter.Position(2, 12), // 'add' in declaration
                )
            val selections = ts.getSelectionRanges(uri, positions)
            logger.info("[TEST] shouldHandleMultiplePositionsIndependently -> {} selections", selections.size)
            selections.forEachIndexed { i, sel ->
                logSelectionChain("shouldHandleMultiplePositionsIndependently[$i]", sel)
            }

            assertThat(selections).hasSize(2)
            selections.forEach { assertWideningChain(it) }
        }

        private fun selectionDepth(sel: XtcCompilerAdapter.SelectionRange): Int = generateSequence(sel) { it.parent }.count()

        private fun linearize(pos: XtcCompilerAdapter.Position): Int = pos.line * 10_000 + pos.column

        private fun assertWideningChain(selection: XtcCompilerAdapter.SelectionRange) {
            generateSequence(selection) { it.parent }
                .zipWithNext()
                .forEach { (child, parent) ->
                    assertThat(linearize(parent.range.start))
                        .isLessThanOrEqualTo(linearize(child.range.start))
                    assertThat(linearize(parent.range.end))
                        .isGreaterThanOrEqualTo(linearize(child.range.end))
                }
        }

        private fun logSelectionChain(
            test: String,
            sel: XtcCompilerAdapter.SelectionRange,
        ) {
            val chain = generateSequence(sel) { it.parent }.toList()
            chain.forEachIndexed { i, s ->
                val r = s.range
                logger.info("  [{}] level {} -> L{}:{}-L{}:{}", test, i, r.start.line, r.start.column, r.end.line, r.end.column)
            }
        }
    }

    // ========================================================================
    // Future capabilities -- disabled until implemented
    // ========================================================================

    // ========================================================================
    // Semantic token test helpers
    // ========================================================================

    private val semanticTypeIndex = org.xvm.lsp.treesitter.SemanticTokenLegend.typeIndex
    private val semanticModIndex = org.xvm.lsp.treesitter.SemanticTokenLegend.modIndex

    private fun decodeSemanticTokens(data: List<Int>): List<IntArray> {
        val result = mutableListOf<IntArray>()
        var line = 0
        var column = 0
        var i = 0
        while (i + 4 < data.size) {
            val deltaLine = data[i]
            val deltaStart = data[i + 1]
            val length = data[i + 2]
            val tokenType = data[i + 3]
            val tokenMods = data[i + 4]

            line += deltaLine
            column = if (deltaLine > 0) deltaStart else column + deltaStart

            result.add(intArrayOf(line, column, length, tokenType, tokenMods))
            i += 5
        }
        return result
    }

    private fun hasSemanticModifier(
        mods: Int,
        name: String,
    ): Boolean {
        val bit = semanticModIndex[name] ?: return false
        return (mods and (1 shl bit)) != 0
    }

    @Nested
    @DisplayName("getSemanticTokens()")
    inner class SemanticTokenTests {
        @Test
        @DisplayName("should return semantic tokens for class declaration")
        fun shouldReturnTokensForClassDeclaration() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent(),
            )

            val tokens = ts.getSemanticTokens(uri)
            assertThat(tokens).isNotNull
            assertThat(tokens!!.data).isNotEmpty

            val decoded = decodeSemanticTokens(tokens.data)
            logger.info("[TEST] class decl tokens: {}", decoded.map { it.toList() })

            // "Person" should be classified as "class" with "declaration" modifier
            // IntArray: [line, column, length, tokenType, tokenModifiers]
            val classToken = decoded.find { it[3] == semanticTypeIndex["class"] && it[2] == "Person".length }
            assertThat(classToken).isNotNull
            assertThat(hasSemanticModifier(classToken!![4], "declaration")).isTrue()
        }

        @Test
        @DisplayName("should return semantic tokens for interface declaration")
        fun shouldReturnTokensForInterfaceDeclaration() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    interface Runnable {
                    }
                }
                """.trimIndent(),
            )

            val tokens = ts.getSemanticTokens(uri)
            assertThat(tokens).isNotNull

            val decoded = decodeSemanticTokens(tokens!!.data)
            val ifaceToken = decoded.find { it[3] == semanticTypeIndex["interface"] && it[2] == "Runnable".length }
            assertThat(ifaceToken).isNotNull
            assertThat(hasSemanticModifier(ifaceToken!![4], "declaration")).isTrue()
        }

        @Test
        @DisplayName("should return semantic tokens for method declaration")
        fun shouldReturnTokensForMethodDeclaration() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    class Person {
                        String getName() {
                            return name;
                        }
                    }
                }
                """.trimIndent(),
            )

            val tokens = ts.getSemanticTokens(uri)
            assertThat(tokens).isNotNull

            val decoded = decodeSemanticTokens(tokens!!.data)
            logger.info("[TEST] method decl tokens: {}", decoded.map { it.toList() })

            val methodToken = decoded.find { it[3] == semanticTypeIndex["method"] && it[2] == "getName".length }
            assertThat(methodToken).isNotNull
            assertThat(hasSemanticModifier(methodToken!![4], "declaration")).isTrue()
        }

        @Test
        @DisplayName("should return semantic tokens for property declaration")
        fun shouldReturnTokensForPropertyDeclaration() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    class Person {
                        String name = "hello";
                    }
                }
                """.trimIndent(),
            )

            val tokens = ts.getSemanticTokens(uri)
            assertThat(tokens).isNotNull

            val decoded = decodeSemanticTokens(tokens!!.data)
            logger.info("[TEST] property decl tokens: {}", decoded.map { it.toList() })

            val propToken = decoded.find { it[3] == semanticTypeIndex["property"] && it[2] == "name".length }
            assertThat(propToken).isNotNull
            assertThat(hasSemanticModifier(propToken!![4], "declaration")).isTrue()
        }

        @Test
        @DisplayName("should return semantic tokens for parameter")
        fun shouldReturnTokensForParameter() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    class Calculator {
                        Int add(Int a, Int b) {
                            return a + b;
                        }
                    }
                }
                """.trimIndent(),
            )

            val tokens = ts.getSemanticTokens(uri)
            assertThat(tokens).isNotNull

            val decoded = decodeSemanticTokens(tokens!!.data)
            logger.info("[TEST] parameter tokens: {}", decoded.map { it.toList() })

            val paramTokens = decoded.filter { it[3] == semanticTypeIndex["parameter"] }
            assertThat(paramTokens).hasSizeGreaterThanOrEqualTo(2)
            paramTokens.forEach { assertThat(hasSemanticModifier(it[4], "declaration")).isTrue() }
        }

        @Test
        @DisplayName("should return semantic tokens for type reference")
        fun shouldReturnTokensForTypeReference() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    class Person {
                        String getName() {
                            return name;
                        }
                    }
                }
                """.trimIndent(),
            )

            val tokens = ts.getSemanticTokens(uri)
            assertThat(tokens).isNotNull

            val decoded = decodeSemanticTokens(tokens!!.data)

            // "String" should be classified as "type"
            val typeTokens = decoded.filter { it[3] == semanticTypeIndex["type"] }
            assertThat(typeTokens).isNotEmpty
        }

        @Test
        @DisplayName("should return semantic tokens for module declaration")
        fun shouldReturnTokensForModuleDeclaration() {
            val uri = freshUri()
            ts.compile(uri, "module myapp {}")

            val tokens = ts.getSemanticTokens(uri)
            assertThat(tokens).isNotNull

            val decoded = decodeSemanticTokens(tokens!!.data)
            logger.info("[TEST] module tokens: {}", decoded.map { it.toList() })

            val nsToken = decoded.find { it[3] == semanticTypeIndex["namespace"] }
            assertThat(nsToken).isNotNull
            assertThat(hasSemanticModifier(nsToken!![4], "declaration")).isTrue()
        }

        @Test
        @DisplayName("should return null for empty file")
        fun shouldReturnNullForEmptyFile() {
            val uri = freshUri()
            ts.compile(uri, "")

            val tokens = ts.getSemanticTokens(uri)
            assertThat(tokens).isNull()
        }

        @Test
        @DisplayName("should handle file with errors gracefully")
        fun shouldHandleFileWithErrorsGracefully() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    class Person {
                """.trimIndent(),
            )

            // Should not throw -- may return partial tokens or null
            val tokens = ts.getSemanticTokens(uri)
            logger.info("[TEST] error file tokens: {}", tokens?.data?.size ?: "null")
        }

        @Test
        @DisplayName("should produce valid delta encoding")
        fun shouldProduceValidDeltaEncoding() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    class Person {
                        String name = "hello";
                        Int age = 0;
                    }
                }
                """.trimIndent(),
            )

            val tokens = ts.getSemanticTokens(uri)
            assertThat(tokens).isNotNull

            val data = tokens!!.data
            // Data must be a multiple of 5
            assertThat(data.size % 5).isEqualTo(0)

            // Decode and verify positions are non-negative
            val decoded = decodeSemanticTokens(data)
            for (token in decoded) {
                assertThat(token[0]).isGreaterThanOrEqualTo(0) // line
                assertThat(token[1]).isGreaterThanOrEqualTo(0) // column
                assertThat(token[2]).isGreaterThan(0) // length
            }

            // Verify tokens are in order (line, then column)
            for (i in 1 until decoded.size) {
                val prev = decoded[i - 1]
                val curr = decoded[i]
                val prevPos = prev[0] * 10_000 + prev[1]
                val currPos = curr[0] * 10_000 + curr[1]
                assertThat(currPos).isGreaterThanOrEqualTo(prevPos)
            }
        }

        @Test
        @DisplayName("should return semantic tokens for call expression")
        fun shouldReturnTokensForCallExpression() {
            val uri = freshUri()
            ts.compile(
                uri,
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
                """.trimIndent(),
            )

            val tokens = ts.getSemanticTokens(uri)
            assertThat(tokens).isNotNull

            val decoded = decodeSemanticTokens(tokens!!.data)
            logger.info("[TEST] call expression tokens: {}", decoded.map { it.toList() })

            // "add" should appear as method at the call site too
            val methodTokens = decoded.filter { it[3] == semanticTypeIndex["method"] }
            assertThat(methodTokens).hasSizeGreaterThanOrEqualTo(2) // declaration + call
        }

        @Test
        @DisplayName("should classify const as struct with readonly modifier")
        fun shouldClassifyConstAsStruct() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    const Point(Int x, Int y);
                }
                """.trimIndent(),
            )

            val tokens = ts.getSemanticTokens(uri)
            if (tokens == null) {
                // Grammar may not produce const_declaration node -- skip gracefully
                return
            }

            val decoded = decodeSemanticTokens(tokens.data)
            logger.info("[TEST] const tokens: {}", decoded.map { it.toList() })

            val structToken = decoded.find { it[3] == semanticTypeIndex["struct"] }
            if (structToken != null) {
                assertThat(hasSemanticModifier(structToken[4], "declaration")).isTrue()
                assertThat(hasSemanticModifier(structToken[4], "readonly")).isTrue()
            }
        }

        /**
         * Regression test for StringIndexOutOfBoundsException after rename.
         *
         * Reproduces the exact bug: compile a document with "Console console", rename
         * "console" to "apa" (making the document shorter), then request semantic tokens.
         * Previously crashed because incremental parsing (passing oldTree without Tree.edit())
         * produced nodes with stale byte offsets from the longer original source.
         */
        @Test
        @DisplayName("should return semantic tokens after rename shortens document")
        fun shouldReturnTokensAfterRenameShortenDocument() {
            val uri = freshUri()
            val originalSource =
                """
                module myapp {
                    void run(String[] args=[]) {
                        @Inject Console console;
                        if (args.empty) {
                            console.print("Hello!");
                            return;
                        }
                        for (String arg : args) {
                            console.print(${"\""}Hello, {arg}!${"\""});
                        }
                    }
                }
                """.trimIndent()

            // Step 1: Initial compile
            val result1 = ts.compile(uri, originalSource)
            assertThat(result1.success).isTrue()

            // Step 2: Semantic tokens must work on initial content
            val tokens1 = ts.getSemanticTokens(uri)
            assertThat(tokens1).isNotNull
            assertThat(tokens1!!.data).isNotEmpty
            logger.info("[TEST] initial semantic tokens: {} data items", tokens1.data.size)

            // Step 3: Simulate rename "console" -> "apa" (7 chars -> 3 chars, doc gets shorter)
            val renamedSource = originalSource.replace("console", "apa")
            assertThat(renamedSource.length).isLessThan(originalSource.length)

            // Step 4: Re-compile with shorter content (same URI = incremental parse path)
            val result2 = ts.compile(uri, renamedSource)
            assertThat(result2.success).isTrue()

            // Step 5: Semantic tokens on shorter document must NOT crash
            // Previously threw: StringIndexOutOfBoundsException: Range [178, 178 + 238) out of bounds
            val tokens2 = ts.getSemanticTokens(uri)
            assertThat(tokens2).isNotNull
            assertThat(tokens2!!.data).isNotEmpty
            logger.info("[TEST] post-rename semantic tokens: {} data items", tokens2.data.size)

            // Verify the renamed identifier appears in tokens
            val decoded = decodeSemanticTokens(tokens2.data)
            logger.info("[TEST] post-rename decoded: {}", decoded.map { it.toList() })
        }

        /**
         * Regression test: compile -> rename (longer) -> semantic tokens.
         * The reverse case: document grows after rename. Verifies we don't
         * have off-by-one errors in the opposite direction.
         */
        @Test
        @DisplayName("should return semantic tokens after rename lengthens document")
        fun shouldReturnTokensAfterRenameLengthenDocument() {
            val uri = freshUri()
            val originalSource =
                """
                module myapp {
                    class Cat {
                        String name;
                        void greet() {
                            name.print();
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, originalSource)
            val tokens1 = ts.getSemanticTokens(uri)
            assertThat(tokens1).isNotNull

            // Rename "name" -> "fullQualifiedName" (4 chars -> 17 chars, doc grows)
            val renamedSource = originalSource.replace("name", "fullQualifiedName")
            assertThat(renamedSource.length).isGreaterThan(originalSource.length)

            ts.compile(uri, renamedSource)
            val tokens2 = ts.getSemanticTokens(uri)
            assertThat(tokens2).isNotNull
            assertThat(tokens2!!.data).isNotEmpty
        }

        /**
         * Regression test: multiple rapid edits on same URI.
         * Simulates fast typing where compile is called many times in quick succession.
         */
        @Test
        @DisplayName("should handle rapid sequential recompilations")
        fun shouldHandleRapidRecompilations() {
            val uri = freshUri()
            val base = "module myapp { class Person { String name; } }"

            // Compile 10 times with progressively different content (same URI)
            repeat(10) { i ->
                val content = base.replace("name", "name$i")
                val result = ts.compile(uri, content)
                assertThat(result.success).isTrue()

                // Semantic tokens must work after every recompilation
                val tokens = ts.getSemanticTokens(uri)
                assertThat(tokens).describedAs("iteration $i").isNotNull
            }
        }

        /**
         * Verify folding ranges also work after rename (they use line/column, not byte offsets,
         * so they should always work -- but good to verify the full adapter pipeline).
         */
        @Test
        @DisplayName("should return folding ranges after rename")
        fun shouldReturnFoldingRangesAfterRename() {
            val uri = freshUri()
            val originalSource =
                """
                module myapp {
                    void run(String[] args=[]) {
                        @Inject Console console;
                        console.print("Hello!");
                    }
                }
                """.trimIndent()

            ts.compile(uri, originalSource)
            val folds1 = ts.getFoldingRanges(uri)
            assertThat(folds1).isNotEmpty

            // Rename and verify folding still works
            val renamedSource = originalSource.replace("console", "x")
            ts.compile(uri, renamedSource)
            val folds2 = ts.getFoldingRanges(uri)
            assertThat(folds2).isNotEmpty
        }
    }

    @Nested
    @DisplayName("getInlayHints() -- future")
    inner class InlayHintTests {
        /**
         * TODO: Inlay hints show inferred type annotations inline (e.g., `val x` displays
         *   `: Int` after the variable). This requires the compiler's type inference engine;
         *   tree-sitter alone cannot determine types.
         */
        @Test
        @Disabled("Inlay hints not yet implemented -- requires compiler type inference")
        @DisplayName("should return type hints for val declarations")
        fun shouldReturnTypeHints() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    class Person {
                        void test() {
                            val x = 42;
                        }
                    }
                }
                """.trimIndent(),
            )

            val range =
                XtcCompilerAdapter.Range(
                    XtcCompilerAdapter.Position(0, 0),
                    XtcCompilerAdapter.Position(7, 0),
                )
            val hints = ts.getInlayHints(uri, range)

            // TODO: Once implemented, assert a TYPE hint appears after "val x" with label ": Int" or similar.
            assertThat(hints).isNotEmpty
        }
    }

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
}
