package org.xvm.lsp.adapter;

import org.jspecify.annotations.NonNull;
import org.xvm.lsp.model.CompilationResult;
import org.xvm.lsp.model.SymbolInfo;

import java.util.List;
import java.util.Optional;

/**
 * Interface for adapting XTC compiler operations into clean, immutable results.
 *
 * <p>The adapter layer isolates the LSP server from the compiler's mutable internals.
 * All results are immutable and thread-safe.
 */
public interface XtcCompilerAdapter {

    /**
     * Compile a source file and return the result.
     *
     * @param uri     the document URI
     * @param content the source code content
     * @return compilation result with diagnostics and symbols
     */
    @NonNull CompilationResult compile(@NonNull String uri, @NonNull String content);

    /**
     * Find the symbol at a specific position.
     *
     * @param uri    the document URI
     * @param line   0-based line number
     * @param column 0-based column number
     * @return the symbol at that position, if any
     */
    @NonNull Optional<SymbolInfo> findSymbolAt(
            @NonNull String uri,
            int line,
            int column);

    /**
     * Get hover information for a position.
     *
     * @param uri    the document URI
     * @param line   0-based line number
     * @param column 0-based column number
     * @return hover text (markdown), if available
     */
    @NonNull Optional<String> getHoverInfo(
            @NonNull String uri,
            int line,
            int column);

    /**
     * Get completion suggestions at a position.
     *
     * @param uri    the document URI
     * @param line   0-based line number
     * @param column 0-based column number
     * @return list of completion items
     */
    @NonNull List<CompletionItem> getCompletions(
            @NonNull String uri,
            int line,
            int column);

    /**
     * Find the definition of the symbol at a position.
     *
     * @param uri    the document URI
     * @param line   0-based line number
     * @param column 0-based column number
     * @return location of the definition, if found
     */
    @NonNull Optional<org.xvm.lsp.model.Location> findDefinition(
            @NonNull String uri,
            int line,
            int column);

    /**
     * Find all references to the symbol at a position.
     *
     * @param uri              the document URI
     * @param line             0-based line number
     * @param column           0-based column number
     * @param includeDeclaration whether to include the declaration itself
     * @return list of reference locations
     */
    @NonNull List<org.xvm.lsp.model.Location> findReferences(
            @NonNull String uri,
            int line,
            int column,
            boolean includeDeclaration);

    /**
     * Completion item for code completion.
     */
    record CompletionItem(
            @NonNull String label,
            @NonNull CompletionKind kind,
            @NonNull String detail,
            @NonNull String insertText
    ) {
        public enum CompletionKind {
            CLASS, INTERFACE, METHOD, PROPERTY, VARIABLE, KEYWORD, MODULE
        }
    }
}
