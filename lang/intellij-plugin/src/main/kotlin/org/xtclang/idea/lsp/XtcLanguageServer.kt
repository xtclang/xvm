package org.xtclang.idea.lsp

import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageServer
import java.util.concurrent.CompletableFuture

/** Standard Rename remains available to clients that cannot persist compiler graph changes. */
interface XtcLanguageServer : LanguageServer {
    @JsonRequest("xtc/languageServiceStatus")
    fun languageServiceStatus(): CompletableFuture<Map<String, Any?>>

    @JsonRequest("xtc/exportLogs")
    fun exportLogs(): CompletableFuture<Map<String, String>>

    @JsonRequest("xtc/compilerSourceModules")
    fun compilerSourceModules(): CompletableFuture<List<SourceModuleConfiguration>>

    @JsonRequest("xtc/rename")
    fun renameProposal(params: RenameParams): CompletableFuture<RenameProposal?>

    @JsonRequest("xtc/renameFiles")
    fun renameFilesProposal(params: RenameFilesParams): CompletableFuture<RenameProposal?>

    /** Embedded build through the compiler's queue, into the workspace's embedded output directory. */
    @JsonRequest("xtc/build")
    fun build(params: BuildParams): CompletableFuture<BuildResponse>

    /** Embedded run: builds, then runs in the server's execution worker, reporting by [RunParams.runId]. */
    @JsonRequest("xtc/run")
    fun run(params: RunParams): CompletableFuture<RunResponse>

    /** Stops a run; its `xtc/runExited` still follows. False when no such run is active. */
    @JsonRequest("xtc/stopRun")
    fun stopRun(params: StopRunParams): CompletableFuture<Boolean>

    /** Console input for a run, or its end. False when no such run is building or running. */
    @JsonRequest("xtc/runInput")
    fun runInput(params: RunInputParams): CompletableFuture<Boolean>

    @JsonRequest("xtc/runnableTargets")
    fun runnableTargets(): CompletableFuture<List<RunnableTarget>>
}

/** `xtc/build` parameters: a module name or member URI, or every source module when [module] is absent. */
data class BuildParams(
    val module: String? = null,
    val rebuild: Boolean = false,
)

/** One module's outcome in an embedded build. */
data class BuiltModule(
    val name: String = "",
    val uri: String = "",
    val succeeded: Boolean = false,
    /** A dependency failed, so this module was not compiled. */
    val skipped: Boolean = false,
    val artifact: String? = null,
)

/** `xtc/build` result. Diagnostics are grouped by document, in the shape of `publishDiagnostics`. */
data class BuildResponse(
    val succeeded: Boolean = false,
    val cancelled: Boolean = false,
    val modules: List<BuiltModule> = emptyList(),
    val diagnostics: List<DocumentDiagnostics> = emptyList(),
    val outputDirectory: String? = null,
)

/**
 * A document's build diagnostics. These mirror LSP's shapes rather than reuse lsp4j's, whose `Diagnostic`
 * message type differs between the lsp4j the plugin compiles against and the one LSP4IJ bundles.
 */
data class DocumentDiagnostics(
    val uri: String = "",
    val diagnostics: List<BuildDiagnostic> = emptyList(),
)

/** A compiler diagnostic: zero-based [range], LSP [severity] (1 error to 4 hint), plain-text [message]. */
data class BuildDiagnostic(
    val range: BuildRange = BuildRange(),
    val severity: Int? = null,
    val message: String = "",
    val code: String? = null,
)

data class BuildRange(
    val start: BuildPosition = BuildPosition(),
    val end: BuildPosition = BuildPosition(),
)

data class BuildPosition(
    val line: Int = 0,
    val character: Int = 0,
)

/** `xtc/run` parameters. [args] reach the entry method as given; the client chooses [runId]. */
data class RunParams(
    val module: String,
    val method: String? = null,
    val args: List<String> = emptyList(),
    val workingDirectory: String? = null,
    val runId: String? = null,
)

/** The build outcome, and the run's ID when the build succeeded and the run started. */
data class RunResponse(
    val runId: String? = null,
    val build: BuildResponse = BuildResponse(),
)

data class StopRunParams(
    val runId: String,
)

/** Console input for a run, with its line ends, or [end] for the end of input. */
data class RunInputParams(
    val runId: String,
    val text: String? = null,
    val end: Boolean? = null,
)

data class RunOutputParams(
    val runId: String = "",
    val text: String = "",
)

/** A run's single terminal outcome: the entry method's exit code, or the reason it failed. */
data class RunExitedParams(
    val runId: String = "",
    val exitCode: Long? = null,
    val error: String? = null,
)

/** A source module whose latest compilation declares a callable entry point. */
data class RunnableTarget(
    val module: String = "",
    val uri: String = "",
    val method: String = "run",
)

/** Server rename edit paired with any source-graph change that must be applied in the same transaction. */
data class RenameProposal(
    val edit: WorkspaceEdit,
    val graph: SourceGraphReplacement? = null,
)

/** Expected and proposed source graphs for a guarded configuration replacement. */
data class SourceGraphReplacement(
    val before: List<SourceModuleConfiguration>,
    val after: List<SourceModuleConfiguration>,
)

/** Wire representation of a source module, excluding server-only compiler and filesystem state. */
data class SourceModuleConfiguration(
    val name: String,
    val uri: String,
    val dependencies: List<String> = emptyList(),
    val resourceRoots: List<String>? = null,
)
