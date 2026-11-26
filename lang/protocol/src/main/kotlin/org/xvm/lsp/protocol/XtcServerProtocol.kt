package org.xvm.lsp.protocol

import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageServer
import java.util.concurrent.CompletableFuture

/**
 * The requests and notifications of the language server's `xtc/` extension of LSP, beside LSP's own: the language
 * server implements them, and the IntelliJ plugin calls them through LSP4IJ, whose proxy of the server this interface
 * is. The VS Code extension sends the same messages from its own TypeScript declarations. Clients that cannot persist
 * compiler graph changes keep LSP's own Rename.
 *
 * lsp4j finds each message by its annotation here, which an implementation must not repeat: an endpoint that finds a
 * message's name on two methods does not start.
 */
interface XtcServerProtocol : LanguageServer {
    /** The server's state, as a support bundle and the settings pages show it: its adapter, version, logs and work. */
    @JsonRequest("xtc/languageServiceStatus")
    fun languageServiceStatus(): CompletableFuture<Map<String, Any?>>

    /** The server's work in hand: the requests it has not answered, and its compiler's jobs. */
    @JsonRequest("xtc/busy")
    fun busy(): CompletableFuture<Map<String, Any?>>

    /** A bounded archive of the server's recent logs and traces, for a support bundle. */
    @JsonRequest("xtc/exportLogs")
    fun exportLogs(): CompletableFuture<Map<String, String>>

    /** The source modules the compiler works with, for the host's configuration views and explicit overrides. */
    @JsonRequest("xtc/compilerSourceModules")
    fun compilerSourceModules(): CompletableFuture<List<SourceModuleConfiguration>>

    /**
     * Modules under the given folders that import the current source graph, directly or through each other. Nothing is
     * installed: the host adds the ones it wants to its explicit source modules.
     */
    @JsonRequest("xtc/discoverConsumers")
    fun discoverConsumers(params: DiscoverConsumersParams): CompletableFuture<ConsumerDiscovery>

    /** A rename's edit, with the source-graph change the host must apply in the same transaction. */
    @JsonRequest("xtc/rename")
    fun renameProposal(params: RenameParams): CompletableFuture<RenameProposal?>

    /** The edit, and source-graph change, that renaming or moving files needs. */
    @JsonRequest("xtc/renameFiles")
    fun renameFilesProposal(params: RenameFilesParams): CompletableFuture<RenameProposal?>

    /** Embedded build through the compiler's queue, into the workspace's embedded output directory. */
    @JsonRequest("xtc/build")
    fun build(params: BuildParams): CompletableFuture<BuildResponse>

    /**
     * Embedded run: builds, then runs in the server's execution worker; its output and exit come as `xtc/runOutput` and
     * `xtc/runExited` ([XtcClientProtocol]) for the client-chosen [RunParams.runId].
     */
    @JsonRequest("xtc/run")
    fun run(params: RunParams): CompletableFuture<RunResponse>

    /** Stops a run; its `xtc/runExited` still follows. False when no such run is active. */
    @JsonRequest("xtc/stopRun")
    fun stopRun(params: StopRunParams): CompletableFuture<Boolean>

    /** Console input for a run, or its end. False when no such run is building or running. */
    @JsonRequest("xtc/runInput")
    fun runInput(params: RunInputParams): CompletableFuture<Boolean>

    /** The Run lenses of one document, for clients that mark the same lines elsewhere, such as IntelliJ's gutter. */
    @JsonRequest("xtc/runLenses")
    fun runLenses(params: TextDocumentIdentifier): CompletableFuture<List<RunLens>>

    /** The xunit tests of each source module, from its latest analysis, as a tree under the module. */
    @JsonRequest("xtc/tests")
    fun tests(): CompletableFuture<List<ModuleTests>>

    /** Starts a debug session in the server; its DAP messages travel as `xtc/debugMessage` notifications both ways. */
    @JsonRequest("xtc/debugStart")
    fun debugStart(): CompletableFuture<DebugSessionResponse>

    /** One DAP message from the client to a debug session. */
    @JsonNotification("xtc/debugMessage")
    fun debugMessage(params: DebugMessageParams)

    /** Ends a debug session, which stops the program it launched. */
    @JsonNotification("xtc/debugEnd")
    fun debugEnd(params: DebugSessionParams)
}
