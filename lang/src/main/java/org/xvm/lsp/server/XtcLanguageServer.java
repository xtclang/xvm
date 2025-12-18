package org.xvm.lsp.server;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xvm.lsp.adapter.XtcCompilerAdapter;
import org.xvm.lsp.model.CompilationResult;
import org.xvm.lsp.model.Diagnostic;
import org.xvm.lsp.model.SymbolInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * XTC Language Server implementation using LSP4J.
 */
public class XtcLanguageServer implements LanguageServer, LanguageClientAware {

    private static final Logger LOG = LoggerFactory.getLogger(XtcLanguageServer.class);

    private final XtcCompilerAdapter adapter;
    private final XtcTextDocumentService textDocumentService;
    private final XtcWorkspaceService workspaceService;

    private @Nullable LanguageClient client;
    private boolean initialized = false;

    public XtcLanguageServer(final @NonNull XtcCompilerAdapter adapter) {
        this.adapter = adapter;
        this.textDocumentService = new XtcTextDocumentService();
        this.workspaceService = new XtcWorkspaceService();
    }

    @Override
    public void connect(final LanguageClient client) {
        this.client = client;
        LOG.info("Connected to language client");
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(final InitializeParams params) {
        LOG.info("Initializing XTC Language Server");

        final ServerCapabilities capabilities = new ServerCapabilities();

        // Text document sync
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);

        // Hover support
        capabilities.setHoverProvider(true);

        // Completion support
        final CompletionOptions completionOptions = new CompletionOptions();
        completionOptions.setTriggerCharacters(List.of(".", ":", "<"));
        completionOptions.setResolveProvider(false);
        capabilities.setCompletionProvider(completionOptions);

        // Definition support
        capabilities.setDefinitionProvider(true);

        // References support
        capabilities.setReferencesProvider(true);

        // Document symbol support
        capabilities.setDocumentSymbolProvider(true);

        this.initialized = true;
        LOG.info("XTC Language Server initialized");

        return CompletableFuture.completedFuture(new InitializeResult(capabilities));
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        LOG.info("Shutting down XTC Language Server");
        this.initialized = false;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        LOG.info("Exiting XTC Language Server");
        System.exit(initialized ? 1 : 0);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    /**
     * Publish diagnostics to the client.
     */
    private void publishDiagnostics(final @NonNull String uri, final @NonNull List<Diagnostic> diagnostics) {
        if (client == null) {
            return;
        }

        final List<org.eclipse.lsp4j.Diagnostic> lspDiagnostics = diagnostics.stream()
                .map(this::toLspDiagnostic)
                .toList();

        client.publishDiagnostics(new PublishDiagnosticsParams(uri, lspDiagnostics));
    }

    private org.eclipse.lsp4j.Diagnostic toLspDiagnostic(final @NonNull Diagnostic diag) {
        final org.eclipse.lsp4j.Diagnostic lspDiag = new org.eclipse.lsp4j.Diagnostic();
        lspDiag.setRange(toRange(diag.location()));
        lspDiag.setSeverity(toLspSeverity(diag.severity()));
        lspDiag.setMessage(diag.message());
        lspDiag.setSource(diag.source());
        if (diag.code() != null) {
            lspDiag.setCode(diag.code());
        }
        return lspDiag;
    }

    private org.eclipse.lsp4j.DiagnosticSeverity toLspSeverity(final Diagnostic.Severity severity) {
        return switch (severity) {
            case ERROR -> org.eclipse.lsp4j.DiagnosticSeverity.Error;
            case WARNING -> org.eclipse.lsp4j.DiagnosticSeverity.Warning;
            case INFORMATION -> org.eclipse.lsp4j.DiagnosticSeverity.Information;
            case HINT -> org.eclipse.lsp4j.DiagnosticSeverity.Hint;
        };
    }

    private Range toRange(final org.xvm.lsp.model.Location loc) {
        return new Range(
                new Position(loc.startLine(), loc.startColumn()),
                new Position(loc.endLine(), loc.endColumn()));
    }

    private Location toLspLocation(final org.xvm.lsp.model.Location loc) {
        return new Location(loc.uri(), toRange(loc));
    }

    // ========================================================================
    // Text Document Service
    // ========================================================================

    private class XtcTextDocumentService implements TextDocumentService {

        private final Map<String, String> openDocuments = new ConcurrentHashMap<>();

        @Override
        public void didOpen(final DidOpenTextDocumentParams params) {
            final String uri = params.getTextDocument().getUri();
            final String content = params.getTextDocument().getText();

            LOG.debug("Document opened: {}", uri);
            openDocuments.put(uri, content);

            // Compile and publish diagnostics
            final CompilationResult result = adapter.compile(uri, content);
            publishDiagnostics(uri, result.diagnostics());
        }

        @Override
        public void didChange(final DidChangeTextDocumentParams params) {
            final String uri = params.getTextDocument().getUri();
            // We use full sync, so there's only one change with the full content
            final String content = params.getContentChanges().getFirst().getText();

            LOG.debug("Document changed: {}", uri);
            openDocuments.put(uri, content);

            // Recompile and publish diagnostics
            final CompilationResult result = adapter.compile(uri, content);
            publishDiagnostics(uri, result.diagnostics());
        }

        @Override
        public void didClose(final DidCloseTextDocumentParams params) {
            final String uri = params.getTextDocument().getUri();
            LOG.debug("Document closed: {}", uri);
            openDocuments.remove(uri);

            // Clear diagnostics
            publishDiagnostics(uri, List.of());
        }

        @Override
        public void didSave(final DidSaveTextDocumentParams params) {
            LOG.debug("Document saved: {}", params.getTextDocument().getUri());
        }

        @Override
        public CompletableFuture<Hover> hover(final HoverParams params) {
            final String uri = params.getTextDocument().getUri();
            final int line = params.getPosition().getLine();
            final int column = params.getPosition().getCharacter();

            return CompletableFuture.supplyAsync(() -> {
                final var hoverInfo = adapter.getHoverInfo(uri, line, column);
                if (hoverInfo.isEmpty()) {
                    return null;
                }

                final Hover hover = new Hover();
                final MarkupContent content = new MarkupContent();
                content.setKind(MarkupKind.MARKDOWN);
                content.setValue(hoverInfo.get());
                hover.setContents(content);
                return hover;
            });
        }

        @Override
        public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
                final CompletionParams params) {

            final String uri = params.getTextDocument().getUri();
            final int line = params.getPosition().getLine();
            final int column = params.getPosition().getCharacter();

            return CompletableFuture.supplyAsync(() -> {
                final var completions = adapter.getCompletions(uri, line, column);

                final List<CompletionItem> items = completions.stream()
                        .map(c -> {
                            final CompletionItem item = new CompletionItem(c.label());
                            item.setKind(toCompletionItemKind(c.kind()));
                            item.setDetail(c.detail());
                            item.setInsertText(c.insertText());
                            return item;
                        })
                        .toList();

                return Either.forLeft(items);
            });
        }

        private CompletionItemKind toCompletionItemKind(
                final XtcCompilerAdapter.CompletionItem.CompletionKind kind) {
            return switch (kind) {
                case CLASS -> CompletionItemKind.Class;
                case INTERFACE -> CompletionItemKind.Interface;
                case METHOD -> CompletionItemKind.Method;
                case PROPERTY -> CompletionItemKind.Property;
                case VARIABLE -> CompletionItemKind.Variable;
                case KEYWORD -> CompletionItemKind.Keyword;
                case MODULE -> CompletionItemKind.Module;
            };
        }

        @Override
        public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
                final DefinitionParams params) {

            final String uri = params.getTextDocument().getUri();
            final int line = params.getPosition().getLine();
            final int column = params.getPosition().getCharacter();

            return CompletableFuture.supplyAsync(() -> {
                final var definition = adapter.findDefinition(uri, line, column);
                if (definition.isEmpty()) {
                    return Either.forLeft(List.of());
                }
                return Either.forLeft(List.of(toLspLocation(definition.get())));
            });
        }

        @Override
        public CompletableFuture<List<? extends Location>> references(final ReferenceParams params) {
            final String uri = params.getTextDocument().getUri();
            final int line = params.getPosition().getLine();
            final int column = params.getPosition().getCharacter();
            final boolean includeDeclaration = params.getContext().isIncludeDeclaration();

            return CompletableFuture.supplyAsync(() -> {
                final var refs = adapter.findReferences(uri, line, column, includeDeclaration);
                return refs.stream()
                        .map(XtcLanguageServer.this::toLspLocation)
                        .toList();
            });
        }

        @Override
        public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
                final DocumentSymbolParams params) {

            final String uri = params.getTextDocument().getUri();
            final String content = openDocuments.get(uri);

            return CompletableFuture.supplyAsync(() -> {
                if (content == null) {
                    return List.of();
                }

                final CompilationResult result = adapter.compile(uri, content);
                return result.symbols().stream()
                        .map(this::toDocumentSymbol)
                        .map(Either::<SymbolInformation, DocumentSymbol>forRight)
                        .toList();
            });
        }

        private DocumentSymbol toDocumentSymbol(final SymbolInfo symbol) {
            final DocumentSymbol docSymbol = new DocumentSymbol();
            docSymbol.setName(symbol.name());
            docSymbol.setKind(toSymbolKind(symbol.kind()));
            docSymbol.setRange(toRange(symbol.location()));
            docSymbol.setSelectionRange(toRange(symbol.location()));
            if (symbol.typeSignature() != null) {
                docSymbol.setDetail(symbol.typeSignature());
            }
            if (!symbol.children().isEmpty()) {
                docSymbol.setChildren(symbol.children().stream()
                        .map(this::toDocumentSymbol)
                        .toList());
            }
            return docSymbol;
        }

        private SymbolKind toSymbolKind(final SymbolInfo.SymbolKind kind) {
            return switch (kind) {
                case MODULE -> SymbolKind.Module;
                case PACKAGE -> SymbolKind.Package;
                case CLASS -> SymbolKind.Class;
                case INTERFACE -> SymbolKind.Interface;
                case ENUM -> SymbolKind.Enum;
                case MIXIN -> SymbolKind.Class;
                case SERVICE -> SymbolKind.Class;
                case CONST -> SymbolKind.Constant;
                case METHOD -> SymbolKind.Method;
                case PROPERTY -> SymbolKind.Property;
                case PARAMETER -> SymbolKind.Variable;
                case TYPE_PARAMETER -> SymbolKind.TypeParameter;
                case CONSTRUCTOR -> SymbolKind.Constructor;
            };
        }
    }

    // ========================================================================
    // Workspace Service
    // ========================================================================

    private static class XtcWorkspaceService implements WorkspaceService {
        @Override
        public void didChangeConfiguration(final org.eclipse.lsp4j.DidChangeConfigurationParams params) {
            LOG.debug("Configuration changed");
        }

        @Override
        public void didChangeWatchedFiles(final org.eclipse.lsp4j.DidChangeWatchedFilesParams params) {
            LOG.debug("Watched files changed: {}", params.getChanges().size());
        }
    }
}
