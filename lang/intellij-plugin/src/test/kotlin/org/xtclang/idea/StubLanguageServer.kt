package org.xtclang.idea

import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import com.redhat.devtools.lsp4ij.server.CannotStartProcessException
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import com.redhat.devtools.lsp4ij.server.definition.extension.ServerExtensionPointBean
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticRegistrationOptions
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentDiagnosticParams
import org.eclipse.lsp4j.DocumentDiagnosticReport
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpOptions
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceDiagnosticParams
import org.eclipse.lsp4j.WorkspaceDiagnosticReport
import org.eclipse.lsp4j.WorkspaceDocumentDiagnosticReport
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.WorkspaceFullDocumentDiagnosticReport
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import org.xtclang.idea.lsp.CompilerSettings
import org.xtclang.idea.lsp.XtcClientFeatures
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Future

/**
 * A language server for light tests, in the test's JVM: it answers completion with the test's [completions] that start
 * with the word before the caret, each with a detail, as the real server does, and signature help with its [signature],
 * and records the methods it is asked for. With [completionDocumentation] it resolves completion items, and gives an
 * item its documentation only then, as the real server does. With [diagnostics] it has a diagnostic provider with a
 * workspace report, as the real server does: a document's pull reports its [pulled] diagnostics, and the workspace
 * report has the [reported] ones. The client it connects to is the [client] that LSP4IJ makes, by default LSP4IJ's
 * own. Each connection runs [connecting] as it starts, on LSP4IJ's thread, where a test can hold it. [LightEditor]
 * connects it as the Ecstasy server, with the plugin's own client features, so LSP4IJ's code runs as it does against
 * the real server.
 */
class StubLanguageServer(
    val completions: List<String> = emptyList(),
    /** The documentation that completionItem/resolve gives each of the [completions], by label. */
    val completionDocumentation: Map<String, String> = emptyMap(),
    val signature: SignatureHelp? = null,
    val diagnostics: Boolean = false,
    val client: (Project) -> LanguageClientImpl = ::LanguageClientImpl,
    val connecting: () -> Unit = {},
) : LanguageServer,
    LanguageClientAware,
    TextDocumentService,
    WorkspaceService {
    /** The methods the client has asked for, in order. */
    val requests = CopyOnWriteArrayList<String>()

    /** Each open document's text, as the client has sent it. */
    val texts = ConcurrentHashMap<String, String>()

    /** The caret's line, as the client has sent it, of each completion the client has asked for. */
    val completionLines = CopyOnWriteArrayList<String>()

    /** The caret's line, as the client has sent it, of each signature help the client has asked for. */
    val signatureLines = CopyOnWriteArrayList<String>()

    /** The label of each completion item the client has asked to resolve, in order. */
    val resolvedCompletions = CopyOnWriteArrayList<String>()

    /** What a pull of a document's diagnostics reports, by the document's URI; none for a document not here. */
    val pulled = ConcurrentHashMap<String, List<Diagnostic>>()

    /** The documents of the workspace report and their diagnostics, by URI, which the report has in URI order. */
    val reported = ConcurrentHashMap<String, List<Diagnostic>>()

    /** Every document a workspace report has had; a later report gives one that is gone from [reported] no diagnostics. */
    private val everReported = ConcurrentHashMap.newKeySet<String>()

    /** How the server resolves a code action, as codeAction/resolve answers: by default, as it came. */
    @Volatile
    var resolve: (CodeAction) -> CodeAction = { it }

    /** How the server answers textDocument/rename: by default, with no edit. */
    @Volatile
    var renameEdit: (RenameParams) -> WorkspaceEdit? = { null }

    /** The client of each connection that LSP4IJ has made to the server, in order. */
    val clients = CopyOnWriteArrayList<LanguageClient>()

    /** The client of the latest connection, for the requests the server sends it. */
    val languageClient: LanguageClient
        get() = clients.last()

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> =
        CompletableFuture.completedFuture(
            InitializeResult(
                ServerCapabilities().apply {
                    setTextDocumentSync(TextDocumentSyncKind.Full)
                    completionProvider = CompletionOptions(completionDocumentation.isNotEmpty(), listOf("."))
                    signatureHelpProvider = SignatureHelpOptions(listOf("(", ","))
                    if (diagnostics) {
                        diagnosticProvider = DiagnosticRegistrationOptions(true, true).apply { identifier = "stub" }
                    }
                },
            ),
        )

    override fun connect(client: LanguageClient) {
        clients += client
    }

    override fun shutdown(): CompletableFuture<Any> = CompletableFuture.completedFuture(null)

    override fun exit() = Unit

    override fun getTextDocumentService(): TextDocumentService = this

    override fun getWorkspaceService(): WorkspaceService = this

    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        requests += "textDocument/completion"
        val caret = params.position
        val line = texts[params.textDocument.uri]?.lines()?.getOrNull(caret.line).orEmpty()
        completionLines += line
        // as the real server does: the items that start with the word before the caret, each replacing that word
        val word = line.take(caret.character).takeLastWhile { it.isLetterOrDigit() || it == '_' }
        val start = Position(caret.line, caret.character - word.length)
        val items =
            completions.filter { it.startsWith(word) }.map { label ->
                CompletionItem(label).apply {
                    kind = CompletionItemKind.Class
                    detail = "class $label"
                    textEdit = Either.forLeft(TextEdit(Range(start, caret), label))
                }
            }
        return CompletableFuture.completedFuture(Either.forLeft(items))
    }

    override fun resolveCompletionItem(unresolved: CompletionItem): CompletableFuture<CompletionItem> {
        requests += "completionItem/resolve"
        resolvedCompletions += unresolved.label
        return CompletableFuture.completedFuture(
            unresolved.apply { documentation = completionDocumentation[label]?.let { Either.forLeft(it) } },
        )
    }

    override fun signatureHelp(params: SignatureHelpParams): CompletableFuture<SignatureHelp?> {
        signatureLines += texts[params.textDocument.uri]?.lines()?.getOrNull(params.position.line).orEmpty()
        requests += "textDocument/signatureHelp"
        return CompletableFuture.completedFuture(signature)
    }

    override fun diagnostic(params: DocumentDiagnosticParams): CompletableFuture<DocumentDiagnosticReport> {
        requests += "textDocument/diagnostic"
        val items = pulled[params.textDocument.uri].orEmpty()
        return CompletableFuture.completedFuture(DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(items)))
    }

    override fun diagnostic(params: WorkspaceDiagnosticParams): CompletableFuture<WorkspaceDiagnosticReport> {
        requests += "workspace/diagnostic"
        val current = reported.toMap()
        everReported += current.keys
        // as the real server does (DiagnosticReports.workspace), a document no longer reported gets an empty report, so
        // that no client keeps its errors: LSP leaves open what a client does with a document a report leaves out. The
        // server sends it once to each client, or again to one that names its old result ID; the stub, which gives no
        // result IDs, sends it each time
        val items =
            everReported.sorted().map { uri ->
                WorkspaceDocumentDiagnosticReport(WorkspaceFullDocumentDiagnosticReport(current[uri].orEmpty(), uri, null))
            }
        return CompletableFuture.completedFuture(WorkspaceDiagnosticReport(items))
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        texts[params.textDocument.uri] = params.textDocument.text
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        params.contentChanges.lastOrNull()?.let { texts[params.textDocument.uri] = it.text }
    }

    override fun didClose(params: DidCloseTextDocumentParams) = Unit

    override fun didSave(params: DidSaveTextDocumentParams) = Unit

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) = Unit

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) = Unit

    override fun resolveCodeAction(unresolved: CodeAction): CompletableFuture<CodeAction> {
        requests += "codeAction/resolve"
        return CompletableFuture.completedFuture(resolve(unresolved))
    }

    override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit?> {
        requests += "textDocument/rename"
        return CompletableFuture.completedFuture(renameEdit(params))
    }

    /**
     * LSP4IJ's extension for the stub as the Ecstasy server, in place of the plugin's. Its factory, which LSP4IJ keeps
     * as the extension's, serves this server alone: a wrapper that LSP4IJ starts for an earlier test's extension, even
     * one disposed with it, connects to that test's server, never to a later test's.
     */
    fun extension(): ServerExtensionPointBean {
        val extension =
            ServerExtensionPointBean().apply {
                id = CompilerSettings.SERVER_ID
                name = "Ecstasy Language Server"
                factoryClass = Factory::class.java.name
                pluginDescriptor = DefaultPluginDescriptor(PluginId.getId("org.xtclang.idea.test"), javaClass.classLoader)
            }
        // LSP4IJ makes the factory from its class name and keeps it; made now, it takes this server
        synchronized(StubLanguageServer) {
            making = this
            try {
                check(extension.instance is Factory)
            } finally {
                making = null
            }
        }
        return extension
    }

    private companion object {
        /** The server of the [Factory] that [extension] is making. */
        var making: StubLanguageServer? = null
    }

    /** LSP4IJ's factory for the stub, in place of the plugin's XtcLanguageServerFactory, for the server of its [extension]. */
    class Factory : LanguageServerFactory {
        private val server = checkNotNull(making) { "a stub factory is made only by StubLanguageServer.extension()" }

        override fun createConnectionProvider(project: Project): StreamConnectionProvider = Connection(server)

        override fun createLanguageClient(project: Project): LanguageClientImpl = server.client(project)

        override fun createClientFeatures(): LSPClientFeatures = XtcClientFeatures()
    }

    /** A loopback socket between LSP4IJ and the stub, whose launcher listens on its own thread. */
    private class Connection(
        private val server: StubLanguageServer,
    ) : StreamConnectionProvider {
        // LSP4IJ can stop a connection on another thread while it starts, as when a test ends before its server has
        // connected. The stop throws nothing into LSP4IJ's removal of the server, which would then leave the disposed
        // wrapper among the project's started servers, and a start that a stop came before never reaches the server
        private val lock = Any()

        @Volatile
        private var client: Socket? = null

        /** The server's launcher, once it listens; guarded by [lock]. */
        private var listening: Future<Void>? = null

        /** Guarded by [lock]. */
        private var stopped = false

        override fun start() {
            server.connecting()
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
                val accepted = CompletableFuture.supplyAsync { listener.accept() }
                val socket = Socket(InetAddress.getLoopbackAddress(), listener.localPort)
                val end = accepted.get()
                synchronized(lock) {
                    if (stopped) {
                        socket.close()
                        end.close()
                        throw CannotStartProcessException("The connection was stopped as it started")
                    }
                    val launcher = LSPLauncher.createServerLauncher(server, end.getInputStream(), end.getOutputStream())
                    client = socket
                    listening = launcher.startListening()
                    server.connect(launcher.remoteProxy)
                }
            }
        }

        override fun getInputStream(): InputStream = checkNotNull(client).getInputStream()

        override fun getOutputStream(): OutputStream = checkNotNull(client).getOutputStream()

        // TODO LSP4IJ: UP77 — handleRemoved leaves all of a server's wrappers started when one dispose throws,
        // so a stop, even one during a start, must not throw
        override fun stop() {
            synchronized(lock) {
                stopped = true
                client?.close()
                listening?.cancel(true)
            }
        }
    }
}
