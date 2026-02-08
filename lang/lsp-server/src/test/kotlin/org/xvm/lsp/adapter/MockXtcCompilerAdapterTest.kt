package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.SymbolInfo

@DisplayName("MockXtcCompilerAdapter")
class MockXtcCompilerAdapterTest {
    private lateinit var adapter: MockXtcCompilerAdapter

    @BeforeEach
    fun setUp() {
        adapter = MockXtcCompilerAdapter()
    }

    @Nested
    @DisplayName("compile()")
    inner class CompileTests {
        @Test
        @DisplayName("should parse module declaration")
        fun shouldParseModuleDeclaration() {
            val source =
                """
                module myapp {
                }
                """.trimIndent()

            val result = adapter.compile("file:///test.x", source)

            assertThat(result.success).isTrue()
            assertThat(result.diagnostics).isEmpty()
            assertThat(result.symbols)
                .anyMatch { it.name == "myapp" && it.kind == SymbolInfo.SymbolKind.MODULE }
        }

        @Test
        @DisplayName("should parse class declaration")
        fun shouldParseClassDeclaration() {
            val source =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            val result = adapter.compile("file:///test.x", source)

            assertThat(result.success).isTrue()
            assertThat(result.symbols)
                .anyMatch { it.name == "Person" && it.kind == SymbolInfo.SymbolKind.CLASS }
        }

        @Test
        @DisplayName("should parse interface declaration")
        fun shouldParseInterfaceDeclaration() {
            val source =
                """
                module myapp {
                    interface Runnable {
                    }
                }
                """.trimIndent()

            val result = adapter.compile("file:///test.x", source)

            assertThat(result.symbols)
                .anyMatch { it.name == "Runnable" && it.kind == SymbolInfo.SymbolKind.INTERFACE }
        }

        @Test
        @DisplayName("should parse service declaration")
        fun shouldParseServiceDeclaration() {
            val source =
                """
                module myapp {
                    service UserService {
                    }
                }
                """.trimIndent()

            val result = adapter.compile("file:///test.x", source)

            assertThat(result.symbols)
                .anyMatch { it.name == "UserService" && it.kind == SymbolInfo.SymbolKind.SERVICE }
        }

        @Test
        @DisplayName("should parse method declaration")
        fun shouldParseMethodDeclaration() {
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

            val result = adapter.compile("file:///test.x", source)

            assertThat(result.symbols)
                .anyMatch { it.name == "getName" && it.kind == SymbolInfo.SymbolKind.METHOD }
        }

        @Test
        @DisplayName("should detect ERROR markers")
        fun shouldDetectErrorMarkers() {
            val source =
                """
                module myapp {
                    // ERROR: undefined variable 'x'
                }
                """.trimIndent()

            val result = adapter.compile("file:///test.x", source)

            assertThat(result.success).isFalse()
            assertThat(result.diagnostics)
                .hasSize(1)
                .allMatch { it.severity == Diagnostic.Severity.ERROR }
                .anyMatch { it.message.contains("undefined variable") }
        }

        @Test
        @DisplayName("should detect unmatched braces")
        fun shouldDetectUnmatchedBraces() {
            val source =
                """
                module myapp {
                    class Person {
                """.trimIndent()

            val result = adapter.compile("file:///test.x", source)

            assertThat(result.diagnostics)
                .anyMatch { it.message.contains("Unmatched") }
        }
    }

    @Nested
    @DisplayName("getHoverInfo()")
    inner class HoverTests {
        @Test
        @DisplayName("should return hover info for class")
        fun shouldReturnHoverInfoForClass() {
            val source =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            adapter.compile("file:///test.x", source)
            val hover = adapter.getHoverInfo("file:///test.x", 1, 10)

            assertThat(hover).isNotNull()
            assertThat(hover).contains("class Person")
        }

        @Test
        @DisplayName("should return null for unknown position")
        fun shouldReturnNullForUnknownPosition() {
            val source =
                """
                module myapp {
                }
                """.trimIndent()

            adapter.compile("file:///test.x", source)
            val hover = adapter.getHoverInfo("file:///test.x", 100, 0)

            assertThat(hover).isNull()
        }
    }

    @Nested
    @DisplayName("getCompletions()")
    inner class CompletionTests {
        @Test
        @DisplayName("should return keywords")
        fun shouldReturnKeywords() {
            val completions = adapter.getCompletions("file:///test.x", 0, 0)

            assertThat(completions)
                .anyMatch { it.label == "class" }
                .anyMatch { it.label == "interface" }
                .anyMatch { it.label == "module" }
        }

        @Test
        @DisplayName("should return built-in types")
        fun shouldReturnBuiltInTypes() {
            val completions = adapter.getCompletions("file:///test.x", 0, 0)

            assertThat(completions)
                .anyMatch { it.label == "String" }
                .anyMatch { it.label == "Int" }
                .anyMatch { it.label == "Boolean" }
        }

        @Test
        @DisplayName("should include document symbols")
        fun shouldIncludeDocumentSymbols() {
            val source =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            adapter.compile("file:///test.x", source)
            val completions = adapter.getCompletions("file:///test.x", 3, 0)

            assertThat(completions).anyMatch { it.label == "Person" }
        }
    }

    @Nested
    @DisplayName("findDefinition()")
    inner class DefinitionTests {
        @Test
        @DisplayName("should find definition of class")
        fun shouldFindDefinitionOfClass() {
            val source =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            adapter.compile("file:///test.x", source)
            val definition = adapter.findDefinition("file:///test.x", 1, 10)

            assertThat(definition).isNotNull()
            assertThat(definition!!.startLine).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("getDocumentHighlights()")
    inner class DocumentHighlightTests {
        @Test
        @DisplayName("should highlight all occurrences of identifier")
        fun shouldHighlightAllOccurrences() {
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

            adapter.compile("file:///test.x", source)
            val highlights = adapter.getDocumentHighlights("file:///test.x", 1, 10)

            assertThat(highlights).hasSizeGreaterThanOrEqualTo(3) // declaration + 2 references
        }

        @Test
        @DisplayName("should return empty for unknown position")
        fun shouldReturnEmptyForUnknownPosition() {
            val source = "module myapp {}"
            adapter.compile("file:///test.x", source)
            val highlights = adapter.getDocumentHighlights("file:///test.x", 100, 0)

            assertThat(highlights).isEmpty()
        }
    }

    @Nested
    @DisplayName("getFoldingRanges()")
    inner class FoldingRangeTests {
        @Test
        @DisplayName("should find brace-delimited blocks")
        fun shouldFindBraceBlocks() {
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

            adapter.compile("file:///test.x", source)
            val ranges = adapter.getFoldingRanges("file:///test.x")

            // module block, class block, method block
            assertThat(ranges).hasSizeGreaterThanOrEqualTo(3)
        }

        @Test
        @DisplayName("should detect import blocks")
        fun shouldDetectImportBlocks() {
            val source =
                """
                module myapp {
                    import foo.Bar;
                    import baz.Qux;
                    class Person {
                    }
                }
                """.trimIndent()

            adapter.compile("file:///test.x", source)
            val ranges = adapter.getFoldingRanges("file:///test.x")

            assertThat(ranges)
                .anyMatch { it.kind == XtcCompilerAdapter.FoldingRange.FoldingKind.IMPORTS }
        }
    }

    @Nested
    @DisplayName("rename()")
    inner class RenameTests {
        @Test
        @DisplayName("should prepare rename for identifier")
        fun shouldPrepareRename() {
            val source =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            adapter.compile("file:///test.x", source)
            val result = adapter.prepareRename("file:///test.x", 1, 10)

            assertThat(result).isNotNull()
            assertThat(result!!.placeholder).isEqualTo("Person")
        }

        @Test
        @DisplayName("should rename all occurrences")
        fun shouldRenameAllOccurrences() {
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

            adapter.compile("file:///test.x", source)
            val edit = adapter.rename("file:///test.x", 1, 10, "Human")

            assertThat(edit).isNotNull()
            val edits = edit!!.changes["file:///test.x"]
            assertThat(edits).isNotNull()
            assertThat(edits!!).hasSizeGreaterThanOrEqualTo(3)
            assertThat(edits).allMatch { it.newText == "Human" }
        }

        @Test
        @DisplayName("should return null for unknown position")
        fun shouldReturnNullForUnknownPosition() {
            val source = "module myapp {}"
            adapter.compile("file:///test.x", source)
            val result = adapter.prepareRename("file:///test.x", 100, 0)

            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("getCodeActions()")
    inner class CodeActionTests {
        @Test
        @DisplayName("should suggest organize imports when unsorted")
        fun shouldSuggestOrganizeImports() {
            val source =
                """
                module myapp {
                    import foo.Zebra;
                    import bar.Alpha;
                    class Person {
                    }
                }
                """.trimIndent()

            adapter.compile("file:///test.x", source)
            val actions =
                adapter.getCodeActions(
                    "file:///test.x",
                    XtcCompilerAdapter.Range(
                        XtcCompilerAdapter.Position(0, 0),
                        XtcCompilerAdapter.Position(0, 0),
                    ),
                    emptyList(),
                )

            assertThat(actions).anyMatch { it.title == "Organize Imports" }
        }

        @Test
        @DisplayName("should not suggest organize imports when already sorted")
        fun shouldNotSuggestWhenSorted() {
            val source =
                """
                module myapp {
                    import bar.Alpha;
                    import foo.Zebra;
                    class Person {
                    }
                }
                """.trimIndent()

            adapter.compile("file:///test.x", source)
            val actions =
                adapter.getCodeActions(
                    "file:///test.x",
                    XtcCompilerAdapter.Range(
                        XtcCompilerAdapter.Position(0, 0),
                        XtcCompilerAdapter.Position(0, 0),
                    ),
                    emptyList(),
                )

            assertThat(actions).noneMatch { it.title == "Organize Imports" }
        }
    }

    @Nested
    @DisplayName("formatDocument()")
    inner class FormatTests {
        @Test
        @DisplayName("should remove trailing whitespace")
        fun shouldRemoveTrailingWhitespace() {
            val source = "module myapp {   \n    class Person {  \n    }\n}"
            val options =
                XtcCompilerAdapter.FormattingOptions(
                    tabSize = 4,
                    insertSpaces = true,
                    trimTrailingWhitespace = true,
                )

            val edits = adapter.formatDocument("file:///test.x", source, options)

            assertThat(edits).hasSizeGreaterThanOrEqualTo(2)
            assertThat(edits).allMatch { it.newText.isEmpty() }
        }

        @Test
        @DisplayName("should insert final newline")
        fun shouldInsertFinalNewline() {
            val source = "module myapp {}"
            val options =
                XtcCompilerAdapter.FormattingOptions(
                    tabSize = 4,
                    insertSpaces = true,
                    insertFinalNewline = true,
                )

            val edits = adapter.formatDocument("file:///test.x", source, options)

            assertThat(edits).anyMatch { it.newText == "\n" }
        }

        @Test
        @DisplayName("should return empty for clean file")
        fun shouldReturnEmptyForCleanFile() {
            val source = "module myapp {}\n"
            val options =
                XtcCompilerAdapter.FormattingOptions(
                    tabSize = 4,
                    insertSpaces = true,
                )

            val edits = adapter.formatDocument("file:///test.x", source, options)

            assertThat(edits).isEmpty()
        }
    }

    @Nested
    @DisplayName("getDocumentLinks()")
    inner class DocumentLinkTests {
        @Test
        @DisplayName("should find import links")
        fun shouldFindImportLinks() {
            val source =
                """
                module myapp {
                    import foo.Bar;
                    import baz.Qux;
                }
                """.trimIndent()

            val links = adapter.getDocumentLinks("file:///test.x", source)

            assertThat(links).hasSize(2)
            assertThat(links).anyMatch { it.tooltip == "import foo.Bar" }
            assertThat(links).anyMatch { it.tooltip == "import baz.Qux" }
        }

        @Test
        @DisplayName("should return empty when no imports")
        fun shouldReturnEmptyWhenNoImports() {
            val source = "module myapp {}"

            val links = adapter.getDocumentLinks("file:///test.x", source)

            assertThat(links).isEmpty()
        }
    }

    @Nested
    @DisplayName("findReferences()")
    inner class FindReferencesTests {
        @Test
        @DisplayName("should return declaration when included")
        fun shouldReturnDeclarationWhenIncluded() {
            val source =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            adapter.compile("file:///test.x", source)
            val refs = adapter.findReferences("file:///test.x", 1, 10, includeDeclaration = true)

            assertThat(refs).hasSize(1)
            assertThat(refs[0].startLine).isEqualTo(1)
        }

        @Test
        @DisplayName("should return empty when declaration excluded")
        fun shouldReturnEmptyWhenDeclarationExcluded() {
            val source =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            adapter.compile("file:///test.x", source)
            val refs = adapter.findReferences("file:///test.x", 1, 10, includeDeclaration = false)

            assertThat(refs).isEmpty()
        }

        @Test
        @DisplayName("should return empty for unknown position")
        fun shouldReturnEmptyForUnknownPosition() {
            val source = "module myapp {}"
            adapter.compile("file:///test.x", source)
            val refs = adapter.findReferences("file:///test.x", 100, 0, includeDeclaration = true)

            assertThat(refs).isEmpty()
        }
    }

    @Nested
    @DisplayName("formatRange()")
    inner class FormatRangeTests {
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

            val edits = adapter.formatRange("file:///test.x", source, range, options)

            // Only lines 1 and 2 should have edits (not line 0 or line 3)
            assertThat(edits).isNotEmpty()
            assertThat(edits).allMatch { it.range.start.line in 1..2 }
        }

        @Test
        @DisplayName("should not insert final newline for range")
        fun shouldNotInsertFinalNewlineForRange() {
            val source = "module myapp {}"
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

            val edits = adapter.formatRange("file:///test.x", source, range, options)

            // Range formatting should NOT insert final newline
            assertThat(edits).noneMatch { it.newText == "\n" }
        }
    }
}
