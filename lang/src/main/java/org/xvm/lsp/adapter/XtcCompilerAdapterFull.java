package org.xvm.lsp.adapter;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xvm.lsp.model.CompilationResult;
import org.xvm.lsp.model.Diagnostic;
import org.xvm.lsp.model.Location;
import org.xvm.lsp.model.SymbolInfo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Full-featured XTC Compiler Adapter interface.
 *
 * <p>This interface defines ALL operations needed for a complete IDE experience,
 * including LSP features, semantic analysis, and debugging support (DAP).
 *
 * <p>Implementation priority:
 * <ol>
 *   <li>Phase 1: Basic LSP (compile, hover, completion, definition) - DONE in skeleton</li>
 *   <li>Phase 2: Advanced LSP (rename, code actions, formatting, semantic tokens)</li>
 *   <li>Phase 3: Workspace features (workspace symbols, file watching)</li>
 *   <li>Phase 4: Debugging (DAP integration)</li>
 * </ol>
 */
public interface XtcCompilerAdapterFull {

    // ========================================================================
    // PHASE 1: Basic LSP (Current skeleton implements these)
    // ========================================================================

    /**
     * Compile a source file and return diagnostics + symbols.
     */
    @NonNull CompilationResult compile(@NonNull String uri, @NonNull String content);

    /**
     * Find the symbol at a specific position.
     */
    @NonNull Optional<SymbolInfo> findSymbolAt(@NonNull String uri, int line, int column);

    /**
     * Get hover information (type signature, documentation).
     */
    @NonNull Optional<String> getHoverInfo(@NonNull String uri, int line, int column);

    /**
     * Get completion suggestions at a position.
     */
    @NonNull List<CompletionItem> getCompletions(@NonNull String uri, int line, int column);

    /**
     * Find the definition of the symbol at a position.
     */
    @NonNull Optional<Location> findDefinition(@NonNull String uri, int line, int column);

    /**
     * Find all references to the symbol at a position.
     */
    @NonNull List<Location> findReferences(@NonNull String uri, int line, int column, boolean includeDeclaration);

    // ========================================================================
    // PHASE 2: Advanced LSP Features
    // ========================================================================

    /**
     * Rename a symbol across all files.
     *
     * @param uri      the document containing the symbol
     * @param line     position line
     * @param column   position column
     * @param newName  the new name for the symbol
     * @return map of URI -> list of text edits, or empty if rename not possible
     */
    @NonNull Optional<Map<String, List<TextEdit>>> rename(
            @NonNull String uri, int line, int column, @NonNull String newName);

    /**
     * Check if rename is valid and return the current symbol name.
     */
    @NonNull Optional<RenameInfo> prepareRename(@NonNull String uri, int line, int column);

    /**
     * Get available code actions (quick fixes, refactorings) at a position.
     */
    @NonNull List<CodeAction> getCodeActions(
            @NonNull String uri,
            @NonNull Location range,
            @NonNull List<Diagnostic> diagnostics);

    /**
     * Format an entire document.
     */
    @NonNull List<TextEdit> formatDocument(@NonNull String uri, @NonNull String content);

    /**
     * Format a range within a document.
     */
    @NonNull List<TextEdit> formatRange(
            @NonNull String uri, @NonNull String content, @NonNull Location range);

    /**
     * Get semantic tokens for syntax highlighting beyond TextMate.
     * Returns tokens with type (keyword, variable, type, etc.) and modifiers.
     */
    @NonNull SemanticTokens getSemanticTokens(@NonNull String uri, @NonNull String content);

    /**
     * Get signature help when typing function arguments.
     */
    @NonNull Optional<SignatureHelp> getSignatureHelp(@NonNull String uri, int line, int column);

    /**
     * Get folding ranges for code folding.
     */
    @NonNull List<FoldingRange> getFoldingRanges(@NonNull String uri, @NonNull String content);

    /**
     * Get inlay hints (inline type hints, parameter names).
     */
    @NonNull List<InlayHint> getInlayHints(@NonNull String uri, @NonNull Location range);

    /**
     * Get call hierarchy - who calls this, what does this call.
     */
    @NonNull Optional<CallHierarchyItem> prepareCallHierarchy(@NonNull String uri, int line, int column);

    @NonNull List<CallHierarchyItem> getIncomingCalls(@NonNull CallHierarchyItem item);

    @NonNull List<CallHierarchyItem> getOutgoingCalls(@NonNull CallHierarchyItem item);

    /**
     * Get type hierarchy - supertypes and subtypes.
     */
    @NonNull Optional<TypeHierarchyItem> prepareTypeHierarchy(@NonNull String uri, int line, int column);

    @NonNull List<TypeHierarchyItem> getSupertypes(@NonNull TypeHierarchyItem item);

    @NonNull List<TypeHierarchyItem> getSubtypes(@NonNull TypeHierarchyItem item);

    // ========================================================================
    // PHASE 3: Workspace Features
    // ========================================================================

    /**
     * Search for symbols across the entire workspace.
     */
    @NonNull List<SymbolInfo> findWorkspaceSymbols(@NonNull String query);

    /**
     * Initialize the workspace with project root(s).
     */
    void initializeWorkspace(@NonNull List<String> workspaceFolders);

    /**
     * Handle file created/changed/deleted events.
     */
    void onFileChanged(@NonNull String uri, @NonNull FileChangeType changeType);

    /**
     * Get project configuration (module dependencies, build settings).
     */
    @NonNull Optional<ProjectInfo> getProjectInfo(@NonNull String uri);

    // ========================================================================
    // PHASE 4: Debug Adapter Protocol (DAP) Support
    // ========================================================================

    /**
     * Start a debug session.
     *
     * @param config debug launch configuration
     * @return session ID for subsequent operations
     */
    @NonNull CompletableFuture<DebugSession> startDebugSession(@NonNull DebugConfig config);

    /**
     * Set breakpoints in a file.
     */
    @NonNull List<Breakpoint> setBreakpoints(@NonNull String uri, @NonNull List<SourceBreakpoint> breakpoints);

    /**
     * Set function breakpoints (break when entering a named function).
     */
    @NonNull List<Breakpoint> setFunctionBreakpoints(@NonNull List<FunctionBreakpoint> breakpoints);

    /**
     * Set exception breakpoints (break on caught/uncaught exceptions).
     */
    void setExceptionBreakpoints(@NonNull List<String> filters);

    /**
     * Continue execution (after hitting breakpoint).
     */
    void continue_(@NonNull DebugSession session, long threadId);

    /**
     * Step over (next line).
     */
    void stepOver(@NonNull DebugSession session, long threadId);

    /**
     * Step into (enter function).
     */
    void stepInto(@NonNull DebugSession session, long threadId);

    /**
     * Step out (exit current function).
     */
    void stepOut(@NonNull DebugSession session, long threadId);

    /**
     * Pause execution.
     */
    void pause(@NonNull DebugSession session, long threadId);

    /**
     * Get all threads in the debug session.
     */
    @NonNull List<ThreadInfo> getThreads(@NonNull DebugSession session);

    /**
     * Get stack trace for a thread.
     */
    @NonNull List<StackFrame> getStackTrace(@NonNull DebugSession session, long threadId);

    /**
     * Get scopes (local, closure, global) for a stack frame.
     */
    @NonNull List<Scope> getScopes(@NonNull DebugSession session, long frameId);

    /**
     * Get variables in a scope or expand a structured variable.
     */
    @NonNull List<Variable> getVariables(@NonNull DebugSession session, long variablesReference);

    /**
     * Evaluate an expression in the current debug context.
     */
    @NonNull EvaluateResult evaluate(
            @NonNull DebugSession session,
            @NonNull String expression,
            @Nullable Long frameId,
            @NonNull EvaluateContext context);

    /**
     * Set the value of a variable.
     */
    @NonNull Optional<String> setVariable(
            @NonNull DebugSession session,
            long variablesReference,
            @NonNull String name,
            @NonNull String value);

    /**
     * Get completions in the debug REPL.
     */
    @NonNull List<CompletionItem> getDebugCompletions(
            @NonNull DebugSession session,
            @Nullable Long frameId,
            @NonNull String text,
            int column);

    /**
     * Terminate the debug session.
     */
    void terminateDebugSession(@NonNull DebugSession session);

    // ========================================================================
    // Supporting Types
    // ========================================================================

    record CompletionItem(
            @NonNull String label,
            @NonNull CompletionKind kind,
            @NonNull String detail,
            @NonNull String insertText,
            @Nullable String documentation,
            @Nullable List<TextEdit> additionalEdits
    ) {
        public enum CompletionKind {
            CLASS, INTERFACE, METHOD, PROPERTY, VARIABLE, KEYWORD, MODULE,
            FIELD, CONSTRUCTOR, ENUM, ENUM_MEMBER, CONSTANT, FUNCTION,
            SNIPPET, TYPE_PARAMETER, MIXIN, SERVICE
        }
    }

    record TextEdit(@NonNull Location range, @NonNull String newText) {}

    record RenameInfo(@NonNull Location range, @NonNull String placeholder) {}

    record CodeAction(
            @NonNull String title,
            @NonNull CodeActionKind kind,
            @Nullable List<Diagnostic> diagnostics,
            @Nullable Map<String, List<TextEdit>> edit,
            @Nullable String command
    ) {
        public enum CodeActionKind {
            QUICK_FIX, REFACTOR, REFACTOR_EXTRACT, REFACTOR_INLINE,
            REFACTOR_REWRITE, SOURCE, SOURCE_ORGANIZE_IMPORTS
        }
    }

    record SemanticTokens(@NonNull int[] data) {}

    record SignatureHelp(
            @NonNull List<SignatureInfo> signatures,
            int activeSignature,
            int activeParameter
    ) {}

    record SignatureInfo(
            @NonNull String label,
            @Nullable String documentation,
            @NonNull List<ParameterInfo> parameters
    ) {}

    record ParameterInfo(@NonNull String label, @Nullable String documentation) {}

    record FoldingRange(int startLine, int endLine, @NonNull FoldingKind kind) {
        public enum FoldingKind { COMMENT, IMPORTS, REGION }
    }

    record InlayHint(@NonNull Location position, @NonNull String label, @NonNull InlayKind kind) {
        public enum InlayKind { TYPE, PARAMETER }
    }

    record CallHierarchyItem(
            @NonNull String name,
            SymbolInfo.SymbolKind kind,
            @NonNull String uri,
            @NonNull Location range,
            @Nullable String detail
    ) {}

    record TypeHierarchyItem(
            @NonNull String name,
            SymbolInfo.SymbolKind kind,
            @NonNull String uri,
            @NonNull Location range,
            @Nullable String detail
    ) {}

    enum FileChangeType { CREATED, CHANGED, DELETED }

    record ProjectInfo(
            @NonNull String moduleName,
            @NonNull List<String> dependencies,
            @NonNull String sourceRoot,
            @Nullable String buildDir
    ) {}

    // ========================================================================
    // Debug Types (DAP)
    // ========================================================================

    record DebugConfig(
            @NonNull String type,           // "xtc"
            @NonNull String request,        // "launch" or "attach"
            @NonNull String program,        // module to debug
            @Nullable List<String> args,    // program arguments
            @Nullable String cwd,           // working directory
            @Nullable Map<String, String> env,  // environment variables
            boolean stopOnEntry,            // break at first line
            boolean noDebug                 // run without debugging
    ) {}

    record DebugSession(
            @NonNull String id,
            @NonNull String name,
            boolean isRunning
    ) {}

    record Breakpoint(
            int id,
            boolean verified,
            @Nullable String message,
            @NonNull Location location
    ) {}

    record SourceBreakpoint(
            int line,
            @Nullable Integer column,
            @Nullable String condition,
            @Nullable String hitCondition,
            @Nullable String logMessage
    ) {}

    record FunctionBreakpoint(
            @NonNull String name,
            @Nullable String condition,
            @Nullable String hitCondition
    ) {}

    record ThreadInfo(long id, @NonNull String name) {}

    record StackFrame(
            long id,
            @NonNull String name,
            @NonNull String uri,
            int line,
            int column,
            @Nullable String moduleId
    ) {}

    record Scope(
            @NonNull String name,
            long variablesReference,
            boolean expensive  // true if fetching variables is slow
    ) {}

    record Variable(
            @NonNull String name,
            @NonNull String value,
            @NonNull String type,
            long variablesReference,  // >0 if expandable
            @Nullable String evaluateName  // expression to evaluate this variable
    ) {}

    record EvaluateResult(
            @NonNull String result,
            @NonNull String type,
            long variablesReference
    ) {}

    enum EvaluateContext { WATCH, REPL, HOVER, CLIPBOARD }
}
