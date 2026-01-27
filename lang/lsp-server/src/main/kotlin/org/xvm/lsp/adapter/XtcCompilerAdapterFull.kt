package org.xvm.lsp.adapter

import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
import java.util.concurrent.CompletableFuture

/**
 * Full-featured XTC Compiler Adapter interface.
 *
 * This interface defines ALL operations needed for a complete IDE experience,
 * including LSP features, semantic analysis, and debugging support (DAP).
 *
 * // TODO LSP: This interface is NOT YET IMPLEMENTED!
 * // No class currently implements XtcCompilerAdapterFull.
 * // The LSP server uses the basic XtcCompilerAdapter with MockXtcCompilerAdapter.
 * //
 * // Implementation requires completing the parallel compiler:
 * // - Phase 1-3 (Lexer/AST/Parser): Enables basic compile(), findSymbolAt()
 * // - Phase 4 (Symbols): Enables findDefinition(), findReferences(), rename()
 * // - Phase 5-7 (Types/Analysis/CodeGen): Enables all remaining methods
 * //
 * // See: doc/plans/PLAN_LSP_PARALLEL_COMPILER.md
 *
 * Implementation priority:
 * 1. Phase 1: Basic LSP (compile, hover, completion, definition) - DONE in skeleton
 * 2. Phase 2: Advanced LSP (rename, code actions, formatting, semantic tokens)
 * 3. Phase 3: Workspace features (workspace symbols, file watching)
 * 4. Phase 4: Debugging (DAP integration)
 */
@Suppress("unused")
interface XtcCompilerAdapterFull {
    // ========================================================================
    // PHASE 1: Basic LSP (Current skeleton implements these)
    // ========================================================================

    /** Compile a source file and return diagnostics + symbols. */
    fun compile(
        uri: String,
        content: String,
    ): CompilationResult

    /** Find the symbol at a specific position. */
    fun findSymbolAt(
        uri: String,
        line: Int,
        column: Int,
    ): SymbolInfo?

    /** Get hover information (type signature, documentation). */
    fun getHoverInfo(
        uri: String,
        line: Int,
        column: Int,
    ): String?

    /** Get completion suggestions at a position. */
    fun getCompletions(
        uri: String,
        line: Int,
        column: Int,
    ): List<CompletionItem>

    /** Find the definition of the symbol at a position. */
    fun findDefinition(
        uri: String,
        line: Int,
        column: Int,
    ): Location?

    /** Find all references to the symbol at a position. */
    fun findReferences(
        uri: String,
        line: Int,
        column: Int,
        includeDeclaration: Boolean,
    ): List<Location>

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
     * @return map of URI -> list of text edits, or null if rename not possible
     */
    fun rename(
        uri: String,
        line: Int,
        column: Int,
        newName: String,
    ): Map<String, List<TextEdit>>?

    /** Check if rename is valid and return the current symbol name. */
    fun prepareRename(
        uri: String,
        line: Int,
        column: Int,
    ): RenameInfo?

    /** Get available code actions (quick fixes, refactorings) at a position. */
    fun getCodeActions(
        uri: String,
        range: Location,
        diagnostics: List<Diagnostic>,
    ): List<CodeAction>

    /** Format an entire document. */
    fun formatDocument(
        uri: String,
        content: String,
    ): List<TextEdit>

    /** Format a range within a document. */
    fun formatRange(
        uri: String,
        content: String,
        range: Location,
    ): List<TextEdit>

    /**
     * Get semantic tokens for syntax highlighting beyond TextMate.
     * Returns tokens with type (keyword, variable, type, etc.) and modifiers.
     *
     * // TODO LSP: Semantic tokens should REPLACE TextMate grammars for highlighting.
     * // TextMate is regex-based and can't distinguish:
     * // - Type names vs variable names (both are identifiers)
     * // - Method definitions vs method calls
     * // - Local variables vs parameters vs fields
     * //
     * // With parallel lexer (Phase 1) + symbols (Phase 4), we can provide:
     * // - Accurate token classification
     * // - Modifier information (declaration, definition, readonly, etc.)
     * // - Consistent highlighting across all editors
     * //
     * // See: PLAN_LSP_PARALLEL_LEXER.md for semantic token integration
     */
    fun getSemanticTokens(
        uri: String,
        content: String,
    ): SemanticTokens

    /** Get signature help when typing function arguments. */
    fun getSignatureHelp(
        uri: String,
        line: Int,
        column: Int,
    ): SignatureHelp?

    /** Get folding ranges for code folding. */
    fun getFoldingRanges(
        uri: String,
        content: String,
    ): List<FoldingRange>

    /** Get inlay hints (inline type hints, parameter names). */
    fun getInlayHints(
        uri: String,
        range: Location,
    ): List<InlayHint>

    /** Get call hierarchy - who calls this, what does this call. */
    fun prepareCallHierarchy(
        uri: String,
        line: Int,
        column: Int,
    ): CallHierarchyItem?

    fun getIncomingCalls(item: CallHierarchyItem): List<CallHierarchyItem>

    fun getOutgoingCalls(item: CallHierarchyItem): List<CallHierarchyItem>

    /** Get type hierarchy - supertypes and subtypes. */
    fun prepareTypeHierarchy(
        uri: String,
        line: Int,
        column: Int,
    ): TypeHierarchyItem?

    fun getSupertypes(item: TypeHierarchyItem): List<TypeHierarchyItem>

    fun getSubtypes(item: TypeHierarchyItem): List<TypeHierarchyItem>

    // ========================================================================
    // PHASE 3: Workspace Features
    // ========================================================================

    /** Search for symbols across the entire workspace. */
    fun findWorkspaceSymbols(query: String): List<SymbolInfo>

    /** Initialize the workspace with project root(s). */
    fun initializeWorkspace(workspaceFolders: List<String>)

    /** Handle file created/changed/deleted events. */
    fun onFileChanged(
        uri: String,
        changeType: FileChangeType,
    )

    /** Get project configuration (module dependencies, build settings). */
    fun getProjectInfo(uri: String): ProjectInfo?

    // ========================================================================
    // PHASE 4: Debug Adapter Protocol (DAP) Support
    // ========================================================================

    /**
     * Start a debug session.
     *
     * @param config debug launch configuration
     * @return session ID for subsequent operations
     */
    fun startDebugSession(config: DebugConfig): CompletableFuture<DebugSession>

    /** Set breakpoints in a file. */
    fun setBreakpoints(
        uri: String,
        breakpoints: List<SourceBreakpoint>,
    ): List<Breakpoint>

    /** Set function breakpoints (break when entering a named function). */
    fun setFunctionBreakpoints(breakpoints: List<FunctionBreakpoint>): List<Breakpoint>

    /** Set exception breakpoints (break on caught/uncaught exceptions). */
    fun setExceptionBreakpoints(filters: List<String>)

    /** Continue execution (after hitting breakpoint). */
    fun continueExecution(
        session: DebugSession,
        threadId: Long,
    )

    /** Step over (next line). */
    fun stepOver(
        session: DebugSession,
        threadId: Long,
    )

    /** Step into (enter function). */
    fun stepInto(
        session: DebugSession,
        threadId: Long,
    )

    /** Step out (exit current function). */
    fun stepOut(
        session: DebugSession,
        threadId: Long,
    )

    /** Pause execution. */
    fun pause(
        session: DebugSession,
        threadId: Long,
    )

    /** Get all threads in the debug session. */
    fun getThreads(session: DebugSession): List<ThreadInfo>

    /** Get stack trace for a thread. */
    fun getStackTrace(
        session: DebugSession,
        threadId: Long,
    ): List<StackFrame>

    /** Get scopes (local, closure, global) for a stack frame. */
    fun getScopes(
        session: DebugSession,
        frameId: Long,
    ): List<Scope>

    /** Get variables in a scope or expand a structured variable. */
    fun getVariables(
        session: DebugSession,
        variablesReference: Long,
    ): List<Variable>

    /** Evaluate an expression in the current debug context. */
    fun evaluate(
        session: DebugSession,
        expression: String,
        frameId: Long?,
        context: EvaluateContext,
    ): EvaluateResult

    /** Set the value of a variable. */
    fun setVariable(
        session: DebugSession,
        variablesReference: Long,
        name: String,
        value: String,
    ): String?

    /** Get completions in the debug REPL. */
    fun getDebugCompletions(
        session: DebugSession,
        frameId: Long?,
        text: String,
        column: Int,
    ): List<CompletionItem>

    /** Terminate the debug session. */
    fun terminateDebugSession(session: DebugSession)

    // ========================================================================
    // Supporting Types
    // ========================================================================

    data class CompletionItem(
        val label: String,
        val kind: CompletionKind,
        val detail: String,
        val insertText: String,
        val documentation: String? = null,
        val additionalEdits: List<TextEdit>? = null,
    ) {
        enum class CompletionKind {
            CLASS,
            INTERFACE,
            METHOD,
            PROPERTY,
            VARIABLE,
            KEYWORD,
            MODULE,
            FIELD,
            CONSTRUCTOR,
            ENUM,
            ENUM_MEMBER,
            CONSTANT,
            FUNCTION,
            SNIPPET,
            TYPE_PARAMETER,
            MIXIN,
            SERVICE,
        }
    }

    data class TextEdit(
        val range: Location,
        val newText: String,
    )

    data class RenameInfo(
        val range: Location,
        val placeholder: String,
    )

    data class CodeAction(
        val title: String,
        val kind: CodeActionKind,
        val diagnostics: List<Diagnostic>? = null,
        val edit: Map<String, List<TextEdit>>? = null,
        val command: String? = null,
    ) {
        enum class CodeActionKind {
            QUICK_FIX,
            REFACTOR,
            REFACTOR_EXTRACT,
            REFACTOR_INLINE,
            REFACTOR_REWRITE,
            SOURCE,
            SOURCE_ORGANIZE_IMPORTS,
        }
    }

    data class SemanticTokens(
        val data: IntArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SemanticTokens) return false
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    data class SignatureHelp(
        val signatures: List<SignatureInfo>,
        val activeSignature: Int,
        val activeParameter: Int,
    )

    data class SignatureInfo(
        val label: String,
        val documentation: String? = null,
        val parameters: List<ParameterInfo>,
    )

    data class ParameterInfo(
        val label: String,
        val documentation: String? = null,
    )

    data class FoldingRange(
        val startLine: Int,
        val endLine: Int,
        val kind: FoldingKind,
    ) {
        enum class FoldingKind { COMMENT, IMPORTS, REGION }
    }

    data class InlayHint(
        val position: Location,
        val label: String,
        val kind: InlayKind,
    ) {
        enum class InlayKind { TYPE, PARAMETER }
    }

    data class CallHierarchyItem(
        val name: String,
        val kind: SymbolInfo.SymbolKind,
        val uri: String,
        val range: Location,
        val detail: String? = null,
    )

    data class TypeHierarchyItem(
        val name: String,
        val kind: SymbolInfo.SymbolKind,
        val uri: String,
        val range: Location,
        val detail: String? = null,
    )

    enum class FileChangeType { CREATED, CHANGED, DELETED }

    data class ProjectInfo(
        val moduleName: String,
        val dependencies: List<String>,
        val sourceRoot: String,
        val buildDir: String? = null,
    )

    // ========================================================================
    // Debug Types (DAP)
    // ========================================================================

    data class DebugConfig(
        val type: String, // "xtc"
        val request: String, // "launch" or "attach"
        val program: String, // module to debug
        val args: List<String>? = null, // program arguments
        val cwd: String? = null, // working directory
        val env: Map<String, String>? = null, // environment variables
        val stopOnEntry: Boolean = false, // break at first line
        val noDebug: Boolean = false, // run without debugging
    )

    data class DebugSession(
        val id: String,
        val name: String,
        val isRunning: Boolean,
    )

    data class Breakpoint(
        val id: Int,
        val verified: Boolean,
        val message: String? = null,
        val location: Location,
    )

    data class SourceBreakpoint(
        val line: Int,
        val column: Int? = null,
        val condition: String? = null,
        val hitCondition: String? = null,
        val logMessage: String? = null,
    )

    data class FunctionBreakpoint(
        val name: String,
        val condition: String? = null,
        val hitCondition: String? = null,
    )

    data class ThreadInfo(
        val id: Long,
        val name: String,
    )

    data class StackFrame(
        val id: Long,
        val name: String,
        val uri: String,
        val line: Int,
        val column: Int,
        val moduleId: String? = null,
    )

    data class Scope(
        val name: String,
        val variablesReference: Long,
        val expensive: Boolean, // true if fetching variables is slow
    )

    data class Variable(
        val name: String,
        val value: String,
        val type: String,
        val variablesReference: Long, // >0 if expandable
        val evaluateName: String? = null, // expression to evaluate this variable
    )

    data class EvaluateResult(
        val result: String,
        val type: String,
        val variablesReference: Long,
    )

    enum class EvaluateContext { WATCH, REPL, HOVER, CLIPBOARD }
}
