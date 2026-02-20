package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.DocumentLinkParams
import org.eclipse.lsp4j.DocumentRangeFormattingParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.FoldingRangeRequestParams
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InlayHintParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.SelectionRangeParams
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.xvm.lsp.adapter.MockXtcCompilerAdapter
import org.xvm.lsp.adapter.TreeSitterAdapter
import org.xvm.lsp.adapter.XtcCompilerAdapter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Integration test that exercises the LSP server against real `.x` source files from
 * the repository. Unlike [XtcLanguageServerTest], which uses synthetic inline snippets
 * with the [MockXtcCompilerAdapter], this test opens actual XTC standard library and
 * manual test files and verifies that each LSP capability returns meaningful results.
 *
 * ## How to run
 *
 * ```
 * ./gradlew :lang:lsp-server:test -PincludeBuildLang=true -PincludeBuildAttachLang=true \
 *     --tests "org.xvm.lsp.server.LspIntegrationTest"
 * ```
 *
 * The test **must** be run via Gradle (not directly from IntelliJ) because the build
 * passes the system property `xtc.composite.root` to the test JVM. This property points
 * to the top-level project root (found via the `version.properties` marker file, using
 * the same approach as `XdkPropertiesService.compositeRootDirectory()`), which is needed
 * to locate the real `.x` source files in `lib_ecstasy/` and `manualTests/`.
 *
 * ## Adapter selection
 *
 * At startup (`@BeforeAll`), the test tries to instantiate a [TreeSitterAdapter]. If the
 * native tree-sitter library is available (which it is when the `tree-sitter` subproject
 * has been built), all tests run against the real syntax-aware parser. If the native lib
 * is unavailable (e.g. in a CI environment without native builds), the test falls back to
 * [MockXtcCompilerAdapter] and the tests still pass -- they just exercise regex-based parsing.
 *
 * ## Test files
 *
 * | File | Path | Why |
 * |------|------|-----|
 * | `Boolean.x` | `lib_ecstasy/.../ecstasy/Boolean.x` | Enum with methods, `@Op` annotations, many self-references |
 * | `Exception.x` | `lib_ecstasy/.../ecstasy/Exception.x` | Const class, constructor, properties, methods |
 * | `Closeable.x` | `lib_ecstasy/.../ecstasy/Closeable.x` | Small interface with doc comments |
 * | `TestSimple.x` | `manualTests/.../TestSimple.x` | Module with `@Inject`, local vars |
 *
 * ## How it works
 *
 * Each test creates a fresh [XtcLanguageServer] with a mock [LanguageClient] (`@BeforeEach`),
 * then opens a real file via `didOpen` and calls the LSP method under test (hover, completion,
 * definition, etc.). Assertions verify the server returns structurally correct, non-empty results.
 *
 * Symbol positions are found dynamically using [findPosition], which locates a string in the
 * file content and converts it to a 0-based `(line, column)` position. This makes the tests
 * resilient to edits in the test files.
 *
 * @see XtcLanguageServerTest for unit tests with synthetic snippets
 */
@DisplayName("LspIntegrationTest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LspIntegrationTest {
    private lateinit var adapter: XtcCompilerAdapter
    private lateinit var testFiles: Map<String, TestFile>
    private lateinit var server: XtcLanguageServer
    private lateinit var mockClient: LanguageClient

    private var isTreeSitter: Boolean = false

    data class TestFile(
        val name: String,
        val path: Path,
        val uri: String,
        val content: String,
    )

    @BeforeAll
    fun setUpAdapter() {
        adapter = createTreeSitterAdapterOrNull() ?: MockXtcCompilerAdapter()
        isTreeSitter = adapter is TreeSitterAdapter

        val root = resolveProjectRoot()
        testFiles =
            mapOf(
                "Boolean.x" to loadTestFile(root, "lib_ecstasy/src/main/x/ecstasy/Boolean.x"),
                "Exception.x" to loadTestFile(root, "lib_ecstasy/src/main/x/ecstasy/Exception.x"),
                "Closeable.x" to loadTestFile(root, "lib_ecstasy/src/main/x/ecstasy/Closeable.x"),
                "TestSimple.x" to loadTestFile(root, "manualTests/src/main/x/TestSimple.x"),
            )
    }

    @BeforeEach
    fun setUpServer() {
        server = XtcLanguageServer(adapter)
        mockClient = mock(LanguageClient::class.java)
        server.connect(mockClient)
        server.initialize(InitializeParams()).get()
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private fun createTreeSitterAdapterOrNull(): TreeSitterAdapter? = runCatching { TreeSitterAdapter() }.getOrNull()

    private fun resolveProjectRoot(): Path {
        val root =
            System.getProperty("xtc.composite.root")
                ?: throw IllegalStateException(
                    "System property 'xtc.composite.root' not set. " +
                        "Run tests via Gradle: ./gradlew :lang:lsp-server:test",
                )
        return Paths.get(root)
    }

    private fun loadTestFile(
        root: Path,
        relativePath: String,
    ): TestFile {
        val path = root.resolve(relativePath)
        assertThat(path).describedAs("Test file: $relativePath").exists()
        val content = Files.readString(path)
        val name = path.fileName.toString()
        val uri = path.toUri().toString()
        return TestFile(name, path, uri, content)
    }

    /**
     * Find the 0-based (line, column) of an occurrence of [text] in [content].
     * Starts searching from [startIndex] (default 0) so callers can skip past
     * occurrences in comments or other non-code regions.
     */
    private fun findPosition(
        content: String,
        text: String,
        startIndex: Int = 0,
    ): Position {
        val idx = content.indexOf(text, startIndex)
        assertThat(idx).describedAs("'$text' not found in content from index $startIndex").isGreaterThanOrEqualTo(0)
        val line = content.substring(0, idx).count { it == '\n' }
        val lastNewline = content.lastIndexOf('\n', idx - 1)
        val col = idx - lastNewline - 1
        return Position(line, col)
    }

    private fun openFile(name: String): TestFile {
        val tf = testFiles.getValue(name)
        server.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(tf.uri, "xtc", 1, tf.content)),
        )
        return tf
    }

    // ========================================================================
    // Tests: didOpen and diagnostics
    // ========================================================================

    @Nested
    @DisplayName("didOpen and diagnostics")
    inner class DidOpenTests {
        @Test
        @DisplayName("should open real files without crashing")
        fun shouldOpenRealFilesWithoutCrashing() {
            for (name in testFiles.keys) {
                openFile(name)
            }
        }

        @Test
        @DisplayName("should publish diagnostics on open")
        fun shouldPublishDiagnosticsOnOpen() {
            openFile("TestSimple.x")

            val captor = ArgumentCaptor.forClass(PublishDiagnosticsParams::class.java)
            verify(mockClient, atLeastOnce()).publishDiagnostics(captor.capture())

            val published = captor.value
            assertThat(published.uri).isEqualTo(testFiles.getValue("TestSimple.x").uri)
            // Diagnostics list should exist (may be empty for valid code)
            assertThat(published.diagnostics).isNotNull()
        }
    }

    // ========================================================================
    // Tests: textDocument/hover
    // ========================================================================

    @Nested
    @DisplayName("textDocument/hover")
    inner class HoverTests {
        @Test
        @DisplayName("should return hover for enum declaration")
        fun shouldReturnHoverForEnumDeclaration() {
            val tf = openFile("Boolean.x")
            val pos = findPosition(tf.content, "Boolean")

            val hover =
                server.textDocumentService
                    .hover(HoverParams(TextDocumentIdentifier(tf.uri), pos))
                    .get()

            assertThat(hover).isNotNull()
            assertThat(hover.contents.right.value).contains("Boolean")
        }

        @Test
        @DisplayName("should return hover for const declaration")
        fun shouldReturnHoverForConstDeclaration() {
            val tf = openFile("Exception.x")
            val pos = findPosition(tf.content, "Exception")

            val hover =
                server.textDocumentService
                    .hover(HoverParams(TextDocumentIdentifier(tf.uri), pos))
                    .get()

            assertThat(hover).isNotNull()
            assertThat(hover.contents.right.value).contains("Exception")
        }

        @Test
        @DisplayName("should return null hover past end of file")
        fun shouldReturnNullHoverPastEndOfFile() {
            val tf = openFile("TestSimple.x")
            // Position well past the end of the file content
            val lastLine = tf.content.count { it == '\n' } + 10
            val pos = Position(lastLine, 0)

            val hover =
                server.textDocumentService
                    .hover(HoverParams(TextDocumentIdentifier(tf.uri), pos))
                    .get()

            assertThat(hover).isNull()
        }
    }

    // ========================================================================
    // Tests: textDocument/completion
    // ========================================================================

    @Nested
    @DisplayName("textDocument/completion")
    inner class CompletionTests {
        @Test
        @DisplayName("should return keywords and types")
        fun shouldReturnKeywordsAndTypes() {
            val tf = openFile("TestSimple.x")

            val result =
                server.textDocumentService
                    .completion(CompletionParams(TextDocumentIdentifier(tf.uri), Position(1, 0)))
                    .get()

            assertThat(result.isLeft).isTrue()
            val items = result.left
            assertThat(items).isNotEmpty()
            assertThat(items).anyMatch { it.label == "class" }
            assertThat(items).anyMatch { it.label == "String" }
        }
    }

    // ========================================================================
    // Tests: textDocument/documentSymbol
    // ========================================================================

    @Nested
    @DisplayName("textDocument/documentSymbol")
    inner class DocumentSymbolTests {
        @Test
        @DisplayName("should extract symbols from enum")
        fun shouldExtractSymbolsFromEnum() {
            val tf = openFile("Boolean.x")

            val symbols =
                server.textDocumentService
                    .documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(tf.uri)))
                    .get()

            assertThat(symbols).isNotEmpty()
            val names = symbols.map { it.right.name }
            assertThat(names).contains("Boolean")
        }

        @Test
        @DisplayName("should extract symbols from const")
        fun shouldExtractSymbolsFromConst() {
            val tf = openFile("Exception.x")

            val symbols =
                server.textDocumentService
                    .documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(tf.uri)))
                    .get()

            assertThat(symbols).isNotEmpty()
            val names = symbols.map { it.right.name }
            assertThat(names).contains("Exception")
        }

        @Test
        @DisplayName("should extract symbols from interface")
        fun shouldExtractSymbolsFromInterface() {
            val tf = openFile("Closeable.x")

            val symbols =
                server.textDocumentService
                    .documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(tf.uri)))
                    .get()

            assertThat(symbols).isNotEmpty()
            val names = symbols.map { it.right.name }
            assertThat(names).contains("Closeable")
        }

        @Test
        @DisplayName("should extract symbols from module")
        fun shouldExtractSymbolsFromModule() {
            val tf = openFile("TestSimple.x")

            val symbols =
                server.textDocumentService
                    .documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(tf.uri)))
                    .get()

            assertThat(symbols).isNotEmpty()
            val names = symbols.map { it.right.name }
            assertThat(names).contains("TestSimple")
        }
    }

    // ========================================================================
    // Tests: textDocument/definition
    // ========================================================================

    @Nested
    @DisplayName("textDocument/definition")
    inner class DefinitionTests {
        @Test
        @DisplayName("should find definition of symbol")
        fun shouldFindDefinitionOfSymbol() {
            val tf = openFile("Exception.x")
            // Skip past the doc comment to find "Exception" in the declaration: "const Exception {"
            val declStart = tf.content.indexOf("const Exception")
            val pos = findPosition(tf.content, "Exception", declStart)

            val result =
                server.textDocumentService
                    .definition(DefinitionParams(TextDocumentIdentifier(tf.uri), pos))
                    .get()

            assertThat(result.isLeft).isTrue()
            assertThat(result.left).isNotEmpty()
            assertThat(result.left.first().uri).isEqualTo(tf.uri)
        }

        @Test
        @DisplayName("should return empty for comment")
        fun shouldReturnEmptyForComment() {
            val tf = openFile("Closeable.x")
            val pos = findPosition(tf.content, "This interface is implemented")

            val result =
                server.textDocumentService
                    .definition(DefinitionParams(TextDocumentIdentifier(tf.uri), pos))
                    .get()

            assertThat(result.isLeft).isTrue()
            assertThat(result.left).isEmpty()
        }
    }

    // ========================================================================
    // Tests: textDocument/references
    // ========================================================================

    @Nested
    @DisplayName("textDocument/references")
    inner class ReferencesTests {
        @Test
        @DisplayName("should find references in file")
        fun shouldFindReferencesInFile() {
            val tf = openFile("Boolean.x")
            val pos = findPosition(tf.content, "Boolean")

            val refs =
                server.textDocumentService
                    .references(
                        ReferenceParams(
                            TextDocumentIdentifier(tf.uri),
                            pos,
                            ReferenceContext(true),
                        ),
                    ).get()

            // "Boolean" appears many times in Boolean.x (in method signatures, return types, etc.)
            assertThat(refs).isNotEmpty()
        }
    }

    // ========================================================================
    // Tests: textDocument/documentHighlight
    // ========================================================================

    @Nested
    @DisplayName("textDocument/documentHighlight")
    inner class DocumentHighlightTests {
        @Test
        @DisplayName("should highlight all occurrences of symbol")
        fun shouldHighlightAllOccurrences() {
            val tf = openFile("Boolean.x")
            val pos = findPosition(tf.content, "Boolean")

            val highlights =
                server.textDocumentService
                    .documentHighlight(DocumentHighlightParams(TextDocumentIdentifier(tf.uri), pos))
                    .get()

            assertThat(highlights).isNotNull()
            // "Boolean" appears many times in Boolean.x
            assertThat(highlights).isNotEmpty()
        }

        @Test
        @DisplayName("should return empty past end of file")
        fun shouldReturnEmptyPastEndOfFile() {
            val tf = openFile("Closeable.x")
            val pos = Position(9999, 0)

            val highlights =
                server.textDocumentService
                    .documentHighlight(DocumentHighlightParams(TextDocumentIdentifier(tf.uri), pos))
                    .get()

            assertThat(highlights).isEmpty()
        }
    }

    // ========================================================================
    // Tests: textDocument/foldingRange
    // ========================================================================

    @Nested
    @DisplayName("textDocument/foldingRange")
    inner class FoldingRangeTests {
        @Test
        @DisplayName("should return folding ranges for declarations")
        fun shouldReturnFoldingRangesForDeclarations() {
            val tf = openFile("Exception.x")

            val ranges =
                server.textDocumentService
                    .foldingRange(FoldingRangeRequestParams(TextDocumentIdentifier(tf.uri)))
                    .get()

            assertThat(ranges).isNotNull()
            assertThat(ranges).isNotEmpty()
            // At minimum, the const Exception declaration should be foldable
            assertThat(ranges).anyMatch { it.endLine > it.startLine }
        }
    }

    // ========================================================================
    // Tests: textDocument/selectionRange
    // ========================================================================

    @Nested
    @DisplayName("textDocument/selectionRange")
    inner class SelectionRangeTests {
        @Test
        @DisplayName("should return nested selection ranges")
        fun shouldReturnNestedSelectionRanges() {
            val tf = openFile("Exception.x")
            val declStart = tf.content.indexOf("const Exception")
            val pos = findPosition(tf.content, "Exception", declStart)

            val ranges =
                server.textDocumentService
                    .selectionRange(SelectionRangeParams(TextDocumentIdentifier(tf.uri), listOf(pos)))
                    .get()

            assertThat(ranges).isNotNull()
            assertThat(ranges).hasSize(1)
            // TreeSitter should return a chain of nested ranges; Mock returns a single point
            val range = ranges[0]
            assertThat(range).isNotNull()
        }
    }

    // ========================================================================
    // Tests: textDocument/formatting
    // ========================================================================

    @Nested
    @DisplayName("textDocument/formatting")
    inner class FormattingTests {
        @Test
        @DisplayName("should return formatting edits or empty")
        fun shouldReturnFormattingEditsOrEmpty() {
            val tf = openFile("TestSimple.x")

            val edits =
                server.textDocumentService
                    .formatting(
                        DocumentFormattingParams(
                            TextDocumentIdentifier(tf.uri),
                            FormattingOptions(4, true),
                        ),
                    ).get()

            // May return edits or empty list, but should not throw
            assertThat(edits).isNotNull()
        }
    }

    // ========================================================================
    // Tests: textDocument/prepareRename and rename
    // ========================================================================

    @Nested
    @DisplayName("textDocument/rename")
    inner class RenameTests {
        @Test
        @DisplayName("should prepare rename for identifier")
        fun shouldPrepareRenameForIdentifier() {
            val tf = openFile("Exception.x")
            val declStart = tf.content.indexOf("const Exception")
            val pos = findPosition(tf.content, "Exception", declStart)

            val result =
                server.textDocumentService
                    .prepareRename(PrepareRenameParams(TextDocumentIdentifier(tf.uri), pos))
                    .get()

            assertThat(result).isNotNull()
            // The result should contain "Exception" as the placeholder
            assertThat(result.second.placeholder).isEqualTo("Exception")
        }

        @Test
        @DisplayName("should rename symbol and produce edits")
        fun shouldRenameAndProduceEdits() {
            val tf = openFile("Exception.x")
            val declStart = tf.content.indexOf("const Exception")
            val pos = findPosition(tf.content, "Exception", declStart)

            val edit =
                server.textDocumentService
                    .rename(RenameParams(TextDocumentIdentifier(tf.uri), pos, "MyException"))
                    .get()

            assertThat(edit).isNotNull()
            assertThat(edit.changes).containsKey(tf.uri)
            val fileEdits = edit.changes[tf.uri]
            assertThat(fileEdits).isNotEmpty()
            assertThat(fileEdits).allMatch { it.newText == "MyException" }
        }
    }

    // ========================================================================
    // Tests: textDocument/codeAction
    // ========================================================================

    @Nested
    @DisplayName("textDocument/codeAction")
    inner class CodeActionTests {
        @Test
        @DisplayName("should return code actions without crashing")
        fun shouldReturnCodeActionsWithoutCrashing() {
            val tf = openFile("TestSimple.x")
            val range = Range(Position(0, 0), Position(0, 0))

            val actions =
                server.textDocumentService
                    .codeAction(
                        CodeActionParams(
                            TextDocumentIdentifier(tf.uri),
                            range,
                            CodeActionContext(emptyList()),
                        ),
                    ).get()

            // May return actions or empty, should not crash
            assertThat(actions).isNotNull()
        }
    }

    // ========================================================================
    // Tests: textDocument/documentLink
    // ========================================================================

    @Nested
    @DisplayName("textDocument/documentLink")
    inner class DocumentLinkTests {
        @Test
        @DisplayName("should return document links for imports")
        fun shouldReturnDocumentLinksForImports() {
            val tf = openFile("TestSimple.x")

            val links =
                server.textDocumentService
                    .documentLink(DocumentLinkParams(TextDocumentIdentifier(tf.uri)))
                    .get()

            // TestSimple.x has imports, so we should get links
            assertThat(links).isNotNull()
            // Both adapters should return links if the file has imports
            if (tf.content.contains("import ")) {
                assertThat(links).isNotEmpty()
            }
        }
    }

    // ========================================================================
    // Tests: textDocument/signatureHelp
    // ========================================================================

    @Nested
    @DisplayName("textDocument/signatureHelp")
    inner class SignatureHelpTests {
        @Test
        @DisplayName("should return signature help or null")
        fun shouldReturnSignatureHelpOrNull() {
            val tf = openFile("TestSimple.x")
            // Position at a method call -- may return null (mock) or signatures (tree-sitter)
            val pos = Position(2, 10)

            val result =
                server.textDocumentService
                    .signatureHelp(SignatureHelpParams(TextDocumentIdentifier(tf.uri), pos))
                    .get()

            // Either null or a valid SignatureHelp object -- tests protocol flow
            if (result != null) {
                assertThat(result.signatures).isNotNull()
            }
        }
    }

    // ========================================================================
    // Tests: textDocument/rangeFormatting
    // ========================================================================

    @Nested
    @DisplayName("textDocument/rangeFormatting")
    inner class RangeFormattingTests {
        @Test
        @DisplayName("should return formatting edits for range")
        fun shouldReturnFormattingEditsForRange() {
            val tf = openFile("TestSimple.x")
            val range = Range(Position(0, 0), Position(4, 0))

            val edits =
                server.textDocumentService
                    .rangeFormatting(
                        DocumentRangeFormattingParams(
                            TextDocumentIdentifier(tf.uri),
                            FormattingOptions(4, true),
                            range,
                        ),
                    ).get()

            // Should not throw; result is non-null (may be empty if file is clean)
            assertThat(edits).isNotNull()
        }
    }

    // ========================================================================
    // Tests: textDocument/inlayHint
    // ========================================================================

    @Nested
    @DisplayName("textDocument/inlayHint")
    inner class InlayHintTests {
        @Test
        @DisplayName("should return inlay hints or empty")
        fun shouldReturnInlayHintsOrEmpty() {
            val tf = openFile("TestSimple.x")
            val lastLine = tf.content.count { it == '\n' }
            val range = Range(Position(0, 0), Position(lastLine, 0))

            val hints =
                server.textDocumentService
                    .inlayHint(InlayHintParams(TextDocumentIdentifier(tf.uri), range))
                    .get()

            // No adapter implements inlay hints yet, so expect empty
            assertThat(hints).isEmpty()
        }
    }
}
