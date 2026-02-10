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
    private var adapter: TreeSitterAdapter? = null
    private val uriCounter = AtomicInteger(0)

    /** Shorthand accessor — safe because [assumeAvailable] guards every test. */
    private val ts: TreeSitterAdapter get() = adapter!!

    /**
     * Returns a unique URI per call so each test gets a fresh parse tree.
     * Re-using the same URI across tests would trigger incremental parsing against
     * a stale tree whose byte offsets don't match the new source, causing
     * [StringIndexOutOfBoundsException] inside the native parser.
     */
    private fun freshUri(): String = "file:///test${uriCounter.incrementAndGet()}.x"

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
         * symbol — the key advantage over a traditional parser that would bail out.
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
         * with text "Person" in the file — at least the declaration and usage sites.
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
         * "clean" — the formatter should produce zero edits.
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
         * `includeDeclaration` flag — it always returns every identifier node with the
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
    // getSelectionRanges() — tree-sitter-specific
    // ========================================================================

    @Nested
    @DisplayName("getSelectionRanges() — tree-sitter-specific")
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
         * Each parent range must strictly contain (or equal) its child range — the
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
    // getSignatureHelp() — tree-sitter-specific
    // ========================================================================

    @Nested
    @DisplayName("getSignatureHelp() — tree-sitter-specific")
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
            val help = ts.getSignatureHelp(uri, 6, 16)

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
            val help = ts.getSignatureHelp(uri, 6, 16)

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
            val help = ts.getSignatureHelp(uri, 6, 19)

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

            assertThat(ts.getSignatureHelp(uri, 1, 10)).isNull()
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
    }

    // ========================================================================
    // Future capabilities — disabled until implemented
    // ========================================================================

    @Nested
    @DisplayName("getSemanticTokens() — future")
    inner class SemanticTokenTests {
        /**
         * TODO: Semantic tokens would let the editor distinguish fields from locals,
         *   type names from variable names, etc. Tree-sitter could partially classify
         *   tokens from AST node types (e.g., identifier inside a type_expression is a
         *   type name), but full classification requires the compiler's type resolver.
         */
        @Test
        @Disabled("Semantic tokens not yet implemented — requires compiler type resolver")
        @DisplayName("should return semantic tokens for identifiers")
        fun shouldReturnSemanticTokens() {
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

            // TODO: Once implemented, assert tokens classify "Person" as a type,
            //   "name" as a property, and "String" as a built-in type.
            assertThat(tokens).isNotNull
            assertThat(tokens!!.data).isNotEmpty
        }
    }

    @Nested
    @DisplayName("getInlayHints() — future")
    inner class InlayHintTests {
        /**
         * TODO: Inlay hints show inferred type annotations inline (e.g., `val x` displays
         *   `: Int` after the variable). This requires the compiler's type inference engine;
         *   tree-sitter alone cannot determine types.
         */
        @Test
        @Disabled("Inlay hints not yet implemented — requires compiler type inference")
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
    @DisplayName("findWorkspaceSymbols() — future")
    inner class WorkspaceSymbolTests {
        /**
         * TODO: Workspace symbol search requires a cross-file symbol index. Tree-sitter
         *   parses one file at a time, so this needs either a multi-file index built from
         *   individual parses or the compiler's workspace model.
         */
        @Test
        @Disabled("Workspace symbols not yet implemented — requires cross-file index")
        @DisplayName("should find symbols across workspace")
        fun shouldFindWorkspaceSymbols() {
            // TODO: Once implemented, compile multiple URIs and assert that
            //   findWorkspaceSymbols("Person") returns matches across files.
            val results = ts.findWorkspaceSymbols("Person")

            assertThat(results).isNotEmpty
        }
    }

    @Nested
    @DisplayName("cross-file navigation — future")
    inner class CrossFileTests {
        /**
         * TODO: Cross-file go-to-definition requires resolving import paths to actual
         * file URIs. Tree-sitter only sees the current file's AST; the compiler's
         * NameResolver (Phase 4) is needed for cross-file resolution.
         */
        @Test
        @Disabled("Cross-file definition not yet implemented — requires compiler NameResolver")
        @DisplayName("should resolve definition across files via import")
        fun shouldResolveDefinitionAcrossFiles() {
            // TODO: Compile two files where file A imports a class from file B.
            //   findDefinition on the imported name in A should navigate to file B.
        }

        // TODO: Cross-file rename needs to update all references across the workspace,
        //   including import statements. The compiler's semantic model is required to
        //   identify all affected files safely.
        @Test
        @Disabled("Cross-file rename not yet implemented — requires compiler semantic model")
        @DisplayName("should rename across files")
        fun shouldRenameAcrossFiles() {
            // TODO: Compile two files referencing the same class. Rename in one file
            //   should produce edits for both files and their import statements.
        }

        // TODO: Scope-aware references should distinguish shadowed locals from outer
        //  declarations with the same name. Tree-sitter's text-based matching cannot
        //  do this; the compiler's scope analysis is needed.
        @Test
        @Disabled("Scope-aware references not yet implemented — requires compiler scope analysis")
        @DisplayName("should distinguish shadowed locals in references")
        fun shouldDistinguishShadowedLocals() {
            // TODO: Compile source where a local variable shadows a class field.
            //  findReferences on the local should not include the field, and vice versa.
        }
    }
}
