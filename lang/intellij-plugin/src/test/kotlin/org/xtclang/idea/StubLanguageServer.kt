package org.xtclang.idea

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpOptions
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
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
 * with the word before the caret, as the real server does, and signature help with its [signature], and records the
 * methods it is asked for. [LightEditor] connects it as the Ecstasy server, with the plugin's own client features, so
 * LSP4IJ's code runs as it does against the real server.
 */
class StubLanguageServer(
    val completions: List<String> = emptyList(),
    val signature: SignatureHelp? = null,
) : LanguageServer,
    TextDocumentService,
    WorkspaceService {
    /** The methods the client has asked for, in order. */
    val requests = CopyOnWriteArrayList<String>()

    /** Each open document's text, as the client has sent it. */
    val texts = ConcurrentHashMap<String, String>()

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> =
        CompletableFuture.completedFuture(
            InitializeResult(
                ServerCapabilities().apply {
                    setTextDocumentSync(TextDocumentSyncKind.Full)
                    completionProvider = CompletionOptions(false, listOf("."))
                    signatureHelpProvider = SignatureHelpOptions(listOf("(", ","))
                },
            ),
        )

    override fun shutdown(): CompletableFuture<Any> = CompletableFuture.completedFuture(null)

    override fun exit() = Unit

    override fun getTextDocumentService(): TextDocumentService = this

    override fun getWorkspaceService(): WorkspaceService = this

    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        requests += "textDocument/completion"
        val caret = params.position
        val line = texts[params.textDocument.uri]?.lines()?.getOrNull(caret.line).orEmpty()
        // as the real server does: the items that start with the word before the caret, each replacing that word
        val word = line.take(caret.character).takeLastWhile { it.isLetterOrDigit() || it == '_' }
        val start = Position(caret.line, caret.character - word.length)
        val items =
            completions.filter { it.startsWith(word) }.map { label ->
                CompletionItem(label).apply {
                    kind = CompletionItemKind.Class
                    textEdit = Either.forLeft(TextEdit(Range(start, caret), label))
                }
            }
        return CompletableFuture.completedFuture(Either.forLeft(items))
    }

    override fun signatureHelp(params: SignatureHelpParams): CompletableFuture<SignatureHelp?> {
        requests += "textDocument/signatureHelp"
        return CompletableFuture.completedFuture(signature)
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

    /** The server a test connects, which [Factory] hands LSP4IJ. */
    internal companion object {
        @Volatile
        var connected: StubLanguageServer? = null
    }

    /** LSP4IJ's factory for the stub, in place of the plugin's XtcLanguageServerFactory. */
    class Factory : LanguageServerFactory {
        override fun createConnectionProvider(project: Project): StreamConnectionProvider = Connection(checkNotNull(connected))

        override fun createLanguageClient(project: Project): LanguageClientImpl = LanguageClientImpl(project)

        override fun createClientFeatures(): LSPClientFeatures = XtcClientFeatures()
    }

    /** A loopback socket between LSP4IJ and the stub, whose launcher listens on its own thread. */
    private class Connection(
        private val server: StubLanguageServer,
    ) : StreamConnectionProvider {
        private lateinit var client: Socket
        private lateinit var listening: Future<Void>

        override fun start() {
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
                val accepted = CompletableFuture.supplyAsync { listener.accept() }
                client = Socket(InetAddress.getLoopbackAddress(), listener.localPort)
                val end = accepted.get()
                listening = LSPLauncher.createServerLauncher(server, end.getInputStream(), end.getOutputStream()).startListening()
            }
        }

        override fun getInputStream(): InputStream = client.getInputStream()

        override fun getOutputStream(): OutputStream = client.getOutputStream()

        override fun stop() {
            client.close()
            listening.cancel(true)
        }
    }
}
