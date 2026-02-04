package org.xvm.lsp.adapter

import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.util.WorkInProgress

/**
 * Stub implementation of the XTC compiler adapter.
 *
 * Minimal placeholder for future full compiler integration.
 * Returns null/empty for all operations. Use for testing LSP infrastructure
 * without tree-sitter or regex parsing dependencies.
 *
 * To use: `./gradlew :lang:lsp-server:fatJar -Plsp.adapter=compiler`
 *
 * @see AbstractXtcCompilerAdapter for shared functionality
 * @see TreeSitterAdapter for syntax-level features
 * @see MockXtcCompilerAdapter for regex-based testing
 */
@WorkInProgress("Awaiting full compiler integration")
class XtcCompilerAdapterStub : AbstractXtcCompilerAdapter() {
    override val displayName: String = "Compiler (stub)"

    // TODO: Integrate with XTC compiler to produce real diagnostics and symbols.
    //       Needs: Lexer/Parser from Phase 1-3 for accurate AST
    override fun compile(
        uri: String,
        content: String,
    ): CompilationResult = CompilationResult.success(uri, emptyList())

    // TODO: Use compiler's symbol table to find symbol at position.
    //       Needs: Phase 4 NameResolver for symbol resolution
    override fun findSymbolAt(
        uri: String,
        line: Int,
        column: Int,
    ): SymbolInfo? = null

    // TODO: Provide type-aware completions from compiler's type system.
    //       Needs: Phase 5 TypeResolver for member completion after '.'
    //       Should include: local vars, members, imported types, smart ranking
    override fun getCompletions(
        uri: String,
        line: Int,
        column: Int,
    ): List<XtcCompilerAdapter.CompletionItem> = emptyList()

    // TODO: Resolve definition across files using compiler's symbol table.
    //       Needs: Phase 4 NameResolver for cross-file navigation
    //       Should handle: imports, inherited members, overloads
    override fun findDefinition(
        uri: String,
        line: Int,
        column: Int,
    ): Location? = null

    // TODO: Find all references using compiler's semantic model.
    //       Needs: Phase 4 NameResolver + workspace-wide index
    //       Should include: usages across all files, inherited references
    override fun findReferences(
        uri: String,
        line: Int,
        column: Int,
        includeDeclaration: Boolean,
    ): List<Location> = emptyList()
}
