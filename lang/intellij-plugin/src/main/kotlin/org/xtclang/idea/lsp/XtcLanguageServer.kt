package org.xtclang.idea.lsp

import com.google.gson.JsonObject
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageServer
import java.util.concurrent.CompletableFuture

/** Standard Rename remains available to clients that cannot persist compiler graph changes. */
interface XtcLanguageServer : LanguageServer {
    @JsonRequest("xtc/languageServiceStatus")
    fun languageServiceStatus(): CompletableFuture<Map<String, Any?>>

    /** The server's work in hand: the requests it has not answered, and its compiler's jobs. */
    @JsonRequest("xtc/busy")
    fun busy(): CompletableFuture<Map<String, Any?>>

    @JsonRequest("xtc/exportLogs")
    fun exportLogs(): CompletableFuture<Map<String, String>>

    @JsonRequest("xtc/compilerSourceModules")
    fun compilerSourceModules(): CompletableFuture<List<SourceModuleConfiguration>>

    @JsonRequest("xtc/discoverConsumers")
    fun discoverConsumers(params: DiscoverConsumersParams): CompletableFuture<ConsumerDiscovery>

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

    @JsonRequest("xtc/runLenses")
    fun runLenses(params: TextDocumentIdentifier): CompletableFuture<List<RunLens>>

    @JsonRequest("xtc/runnableTargets")
    fun runnableTargets(): CompletableFuture<List<RunnableTarget>>

    /** The xunit tests of each source module, from its latest analysis, as a tree under the module. */
    @JsonRequest("xtc/tests")
    fun tests(): CompletableFuture<List<ModuleTests>>

    /** Starts a debug session in the server; its DAP messages travel as `xtc/debugMessage` notifications both ways. */
    @JsonRequest("xtc/debugStart")
    fun debugStart(): CompletableFuture<DebugSessionResponse>

    /** One DAP message from the IDE to a debug session. */
    @JsonNotification("xtc/debugMessage")
    fun debugMessage(params: DebugMessageParams)

    /** Ends a debug session, which stops the program it launched. */
    @JsonNotification("xtc/debugEnd")
    fun debugEnd(params: DebugSessionParams)
}

/** `xtc/debugStart` result: the ID of the new debug session. */
data class DebugSessionResponse(
    val sessionId: String,
)

/** `xtc/debugEnd` parameters. */
data class DebugSessionParams(
    val sessionId: String,
)

/**
 * `xtc/debugTask` parameters: the task that runs a Gradle-mode or command-line debug session's ([sessionId]) module
 * under the debugger, as [buildMode] says: the module's Gradle task for `gradle`, `xtc run` for `commandLine`. Its
 * [module], [method] and [args], with the launcher's `--debug` options [debug], which the Gradle task takes as the
 * Gradle property `xtcDebug`. The program's XDK has the debugger's agent.
 */
data class DebugTaskParams(
    val sessionId: String,
    val module: String,
    val method: String?,
    val args: List<String>,
    val debug: String,
    val buildMode: String? = null,
    /** The module's tests the task runs instead, as `testXtc` or `xtc test` take them, all of them for an empty list. */
    val tests: List<TestSelector>? = null,
)

/** The end of an `xtc/debugTask`: the task's exit code, when it has one. */
data class DebugTaskResult(
    val exitCode: Int? = null,
)

/** `xtc/debugMessage` parameters: one Debug Adapter Protocol message of a session, in either direction. */
data class DebugMessageParams(
    val sessionId: String,
    val message: JsonObject,
)

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

/**
 * `xtc/run` parameters. [args] reach the entry method as given; the client chooses [runId]. With [tests], xunit runs
 * the module's tests they select instead, all of them for an empty list, and the server reports their execution as
 * `xtc/testEvent` notifications ([TestEventParams]), apart from the output.
 */
data class RunParams(
    val module: String,
    val method: String? = null,
    val args: List<String> = emptyList(),
    val workingDirectory: String? = null,
    val runId: String? = null,
    /** The module's name, which the server uses when [module] is a root URI that no longer names a module. */
    val moduleName: String? = null,
    val tests: List<TestSelector>? = null,
)

/** A test, or a container of tests, to run: its xunit [selector] and its [kind], as [ServerTest] gives them. */
data class TestSelector(
    val selector: String = "",
    val kind: String = ServerTest.TEST,
)

/** A source module's tests, under the module, as `xtc/tests` gives them. */
data class ModuleTests(
    val module: String = "",
    val uri: String = "",
    val tests: ServerTest = ServerTest(),
)

/**
 * A test of a module, or a container of tests: its xunit [selector], which runs it alone, its own [name], its
 * [kind], and its declaration's name at [location].
 */
data class ServerTest(
    val selector: String = "",
    val name: String = "",
    val kind: String = MODULE,
    val location: TestLocation? = null,
    val children: List<ServerTest> = emptyList(),
) {
    companion object {
        const val MODULE = "module"
        const val PACKAGE = "package"
        const val CLASS = "class"
        const val TEST = "test"
    }
}

/** Where a test's declaration is, zero-based. */
data class TestLocation(
    val uri: String = "",
    val startLine: Int = 0,
    val startColumn: Int = 0,
    val endLine: Int = 0,
    val endColumn: Int = 0,
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

/** `xtc/testEvent` parameters: an [event] of the tests that the run [runId] runs, as xunit reported it. */
data class TestEventParams(
    val runId: String = "",
    val event: TestEvent? = null,
)

/** A run's single terminal outcome: the entry method's exit code, or the reason it failed. */
data class RunExitedParams(
    val runId: String = "",
    val exitCode: Long? = null,
    val error: String? = null,
)

/** A Run lens of a document: the module it runs and the zero-based line it is on. */
data class RunLens(
    val module: String = "",
    val line: Int = 0,
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

/** Folders to search for modules that import the current source graph. */
data class DiscoverConsumersParams(
    val roots: List<String>,
)

/** Consumers found under the searched folders, and modules there whose names would collide in the graph. */
data class ConsumerDiscovery(
    val modules: List<SourceModuleConfiguration> = emptyList(),
    val conflicts: List<SourceModuleConfiguration> = emptyList(),
)
