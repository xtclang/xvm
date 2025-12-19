package org.xtclang.tooling.generators

import org.xtclang.tooling.model.*

/**
 * Generates LSP (Language Server Protocol) server implementation.
 * 
 * The LSP server provides:
 * - Code completion
 * - Go to definition
 * - Find references
 * - Hover information
 * - Diagnostics
 * - Document symbols
 * - Workspace symbols
 * - Code actions
 * - Formatting
 */
class LspServerGenerator(private val model: LanguageModel) {
    
    private val packageName = "org.xtclang.lsp"
    private val languageName = model.name
    private val languageNameCap = model.name.replaceFirstChar { it.uppercase() }
    
    /**
     * Generate the main LSP server class
     */
    fun generateServer(): String = """
package $packageName;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.*;

import java.io.*;
import java.util.concurrent.*;

/**
 * ${languageName} Language Server
 * 
 * This server implements the Language Server Protocol to provide
 * IDE features for the ${languageName} programming language.
 */
public class ${languageNameCap}LanguageServer implements LanguageServer, LanguageClientAware {
    
    private LanguageClient client;
    private final ${languageNameCap}TextDocumentService textDocumentService;
    private final ${languageNameCap}WorkspaceService workspaceService;
    private final ExecutorService executor;
    
    private int errorCode = 1;
    
    public ${languageNameCap}LanguageServer() {
        this.textDocumentService = new ${languageNameCap}TextDocumentService(this);
        this.workspaceService = new ${languageNameCap}WorkspaceService(this);
        this.executor = Executors.newCachedThreadPool();
    }
    
    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        ServerCapabilities capabilities = new ServerCapabilities();
        
        // Text document sync
        TextDocumentSyncOptions syncOptions = new TextDocumentSyncOptions();
        syncOptions.setOpenClose(true);
        syncOptions.setChange(TextDocumentSyncKind.Incremental);
        syncOptions.setSave(new SaveOptions(true));
        capabilities.setTextDocumentSync(syncOptions);
        
        // Completion
        CompletionOptions completionOptions = new CompletionOptions();
        completionOptions.setResolveProvider(true);
        completionOptions.setTriggerCharacters(java.util.Arrays.asList(".", ":", "@", "<"));
        capabilities.setCompletionProvider(completionOptions);
        
        // Hover
        capabilities.setHoverProvider(true);
        
        // Go to definition
        capabilities.setDefinitionProvider(true);
        
        // Find references
        capabilities.setReferencesProvider(true);
        
        // Document symbols
        capabilities.setDocumentSymbolProvider(true);
        
        // Workspace symbols
        capabilities.setWorkspaceSymbolProvider(true);
        
        // Code actions
        CodeActionOptions codeActionOptions = new CodeActionOptions();
        codeActionOptions.setCodeActionKinds(java.util.Arrays.asList(
            CodeActionKind.QuickFix,
            CodeActionKind.Refactor,
            CodeActionKind.RefactorExtract,
            CodeActionKind.RefactorInline,
            CodeActionKind.RefactorRewrite,
            CodeActionKind.Source,
            CodeActionKind.SourceOrganizeImports
        ));
        capabilities.setCodeActionProvider(codeActionOptions);
        
        // Rename
        RenameOptions renameOptions = new RenameOptions();
        renameOptions.setPrepareProvider(true);
        capabilities.setRenameProvider(renameOptions);
        
        // Formatting
        capabilities.setDocumentFormattingProvider(true);
        capabilities.setDocumentRangeFormattingProvider(true);
        
        // Folding
        capabilities.setFoldingRangeProvider(true);
        
        // Semantic tokens
        SemanticTokensWithRegistrationOptions semanticTokensOptions = 
            new SemanticTokensWithRegistrationOptions();
        semanticTokensOptions.setFull(true);
        semanticTokensOptions.setRange(true);
        semanticTokensOptions.setLegend(new SemanticTokensLegend(
            java.util.Arrays.asList(
                "namespace", "type", "class", "enum", "interface", "struct",
                "typeParameter", "parameter", "variable", "property", "enumMember",
                "event", "function", "method", "macro", "keyword", "modifier",
                "comment", "string", "number", "regexp", "operator", "decorator"
            ),
            java.util.Arrays.asList(
                "declaration", "definition", "readonly", "static", "deprecated",
                "abstract", "async", "modification", "documentation", "defaultLibrary"
            )
        ));
        capabilities.setSemanticTokensProvider(semanticTokensOptions);
        
        ServerInfo serverInfo = new ServerInfo("${languageName} Language Server", "1.0.0");
        
        return CompletableFuture.completedFuture(new InitializeResult(capabilities, serverInfo));
    }
    
    @Override
    public void initialized(InitializedParams params) {
        // Register file watchers
        if (client != null) {
            DidChangeWatchedFilesRegistrationOptions watchOptions = 
                new DidChangeWatchedFilesRegistrationOptions(
                    java.util.Arrays.asList(
                        ${model.fileExtensions.joinToString(",\n                        ") { 
                            "new FileSystemWatcher(\"**/*.$it\")" 
                        }}
                    )
                );
            client.registerCapability(new RegistrationParams(
                java.util.Arrays.asList(
                    new Registration("file-watcher", "workspace/didChangeWatchedFiles", watchOptions)
                )
            ));
        }
    }
    
    @Override
    public CompletableFuture<Object> shutdown() {
        errorCode = 0;
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void exit() {
        executor.shutdown();
        System.exit(errorCode);
    }
    
    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }
    
    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }
    
    @Override
    public void connect(LanguageClient client) {
        this.client = client;
    }
    
    public LanguageClient getClient() {
        return client;
    }
    
    public ExecutorService getExecutor() {
        return executor;
    }
    
    public void publishDiagnostics(String uri, java.util.List<Diagnostic> diagnostics) {
        if (client != null) {
            client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
        }
    }
    
    public void logMessage(MessageType type, String message) {
        if (client != null) {
            client.logMessage(new MessageParams(type, message));
        }
    }
    
    // Main entry point
    public static void main(String[] args) throws Exception {
        ${languageNameCap}LanguageServer server = new ${languageNameCap}LanguageServer();
        
        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(
            server,
            System.in,
            System.out
        );
        
        LanguageClient client = launcher.getRemoteProxy();
        server.connect(client);
        
        launcher.startListening().get();
    }
}
""".trimIndent()
    
    /**
     * Generate TextDocumentService implementation
     */
    fun generateTextDocumentService(): String = """
package $packageName;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

import java.util.*;
import java.util.concurrent.*;

/**
 * Handles text document events and provides document-level features.
 */
public class ${languageNameCap}TextDocumentService implements TextDocumentService {
    
    private final ${languageNameCap}LanguageServer server;
    private final ${languageNameCap}DocumentManager documentManager;
    private final ${languageNameCap}CompletionProvider completionProvider;
    private final ${languageNameCap}DiagnosticProvider diagnosticProvider;
    
    public ${languageNameCap}TextDocumentService(${languageNameCap}LanguageServer server) {
        this.server = server;
        this.documentManager = new ${languageNameCap}DocumentManager();
        this.completionProvider = new ${languageNameCap}CompletionProvider(documentManager);
        this.diagnosticProvider = new ${languageNameCap}DiagnosticProvider(documentManager);
    }
    
    // =========================================================================
    // Document Synchronization
    // =========================================================================
    
    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String content = params.getTextDocument().getText();
        
        documentManager.openDocument(uri, content);
        validateDocument(uri);
    }
    
    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        
        for (TextDocumentContentChangeEvent change : params.getContentChanges()) {
            if (change.getRange() != null) {
                documentManager.updateDocument(uri, change.getRange(), change.getText());
            } else {
                documentManager.setDocumentContent(uri, change.getText());
            }
        }
        
        validateDocument(uri);
    }
    
    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        documentManager.closeDocument(uri);
        server.publishDiagnostics(uri, Collections.emptyList());
    }
    
    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        validateDocument(uri);
    }
    
    private void validateDocument(String uri) {
        server.getExecutor().submit(() -> {
            List<Diagnostic> diagnostics = diagnosticProvider.validate(uri);
            server.publishDiagnostics(uri, diagnostics);
        });
    }
    
    // =========================================================================
    // Completion
    // =========================================================================
    
    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
            CompletionParams params) {
        return CompletableFuture.supplyAsync(() -> {
            List<CompletionItem> items = completionProvider.getCompletions(
                params.getTextDocument().getUri(),
                params.getPosition()
            );
            return Either.forLeft(items);
        }, server.getExecutor());
    }
    
    @Override
    public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem item) {
        return CompletableFuture.supplyAsync(() -> {
            return completionProvider.resolveItem(item);
        }, server.getExecutor());
    }
    
    // =========================================================================
    // Hover
    // =========================================================================
    
    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            Position position = params.getPosition();
            
            // Get the word at position
            String word = documentManager.getWordAtPosition(uri, position);
            if (word == null || word.isEmpty()) {
                return null;
            }
            
            // Build hover content
            MarkupContent content = new MarkupContent();
            content.setKind(MarkupKind.MARKDOWN);
            
            // Check if it's a keyword
            if (isKeyword(word)) {
                content.setValue("**Keyword**: `" + word + "`");
                return new Hover(content);
            }
            
            // TODO: Look up symbol information from AST
            content.setValue("**Identifier**: `" + word + "`");
            return new Hover(content);
        }, server.getExecutor());
    }
    
    private boolean isKeyword(String word) {
        return ${languageNameCap}Keywords.KEYWORDS.contains(word);
    }
    
    // =========================================================================
    // Go to Definition
    // =========================================================================
    
    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
            DefinitionParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            Position position = params.getPosition();
            
            // TODO: Implement actual symbol resolution
            List<Location> locations = new ArrayList<>();
            return Either.forLeft(locations);
        }, server.getExecutor());
    }
    
    // =========================================================================
    // Find References
    // =========================================================================
    
    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            Position position = params.getPosition();
            
            // TODO: Implement reference finding
            List<Location> references = new ArrayList<>();
            return references;
        }, server.getExecutor());
    }
    
    // =========================================================================
    // Document Symbols
    // =========================================================================
    
    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
            DocumentSymbolParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            
            // TODO: Parse document and extract symbols
            List<Either<SymbolInformation, DocumentSymbol>> symbols = new ArrayList<>();
            return symbols;
        }, server.getExecutor());
    }
    
    // =========================================================================
    // Code Actions
    // =========================================================================
    
    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        return CompletableFuture.supplyAsync(() -> {
            List<Either<Command, CodeAction>> actions = new ArrayList<>();
            
            // Add quick fixes for diagnostics
            for (Diagnostic diagnostic : params.getContext().getDiagnostics()) {
                // TODO: Generate appropriate fixes
            }
            
            return actions;
        }, server.getExecutor());
    }
    
    // =========================================================================
    // Formatting
    // =========================================================================
    
    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            
            // TODO: Implement formatting
            List<TextEdit> edits = new ArrayList<>();
            return edits;
        }, server.getExecutor());
    }
    
    @Override
    public CompletableFuture<List<? extends TextEdit>> rangeFormatting(
            DocumentRangeFormattingParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            Range range = params.getRange();
            
            // TODO: Implement range formatting
            List<TextEdit> edits = new ArrayList<>();
            return edits;
        }, server.getExecutor());
    }
    
    // =========================================================================
    // Rename
    // =========================================================================
    
    @Override
    public CompletableFuture<Either<Range, PrepareRenameResult>> prepareRename(
            PrepareRenameParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            Position position = params.getPosition();
            
            Range wordRange = documentManager.getWordRangeAtPosition(uri, position);
            if (wordRange != null) {
                return Either.forLeft(wordRange);
            }
            return null;
        }, server.getExecutor());
    }
    
    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            Position position = params.getPosition();
            String newName = params.getNewName();
            
            // TODO: Find all occurrences and create edit
            WorkspaceEdit edit = new WorkspaceEdit();
            return edit;
        }, server.getExecutor());
    }
    
    // =========================================================================
    // Folding Ranges
    // =========================================================================
    
    @Override
    public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            
            // TODO: Calculate folding ranges from AST
            List<FoldingRange> ranges = new ArrayList<>();
            return ranges;
        }, server.getExecutor());
    }
    
    // =========================================================================
    // Semantic Tokens
    // =========================================================================
    
    @Override
    public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            
            // TODO: Generate semantic tokens from AST
            List<Integer> data = new ArrayList<>();
            return new SemanticTokens(data);
        }, server.getExecutor());
    }
}
""".trimIndent()
    
    /**
     * Generate keywords class with all language keywords
     */
    fun generateKeywords(): String = """
package $packageName;

import java.util.*;

/**
 * ${languageName} language keywords.
 */
public final class ${languageNameCap}Keywords {
    
    public static final Set<String> KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        ${model.keywords.sorted().joinToString(",\n        ") { "\"$it\"" }}
    )));
    
    public static final Set<String> TYPE_KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "module", "package", "class", "interface", "mixin", "service", "const", "enum", "typedef"
    )));
    
    public static final Set<String> MODIFIER_KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "public", "protected", "private", "static", "abstract", "final", "immutable"
    )));
    
    public static final Set<String> CONTROL_KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "if", "else", "switch", "case", "default", "for", "while", "do", "foreach",
        "break", "continue", "return", "try", "catch", "finally", "throw", "assert"
    )));
    
    public static final Set<String> BUILTIN_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "Bit", "Boolean", "Byte", "Char", "Dec", "Float",
        "Int", "Int8", "Int16", "Int32", "Int64", "Int128", "IntN",
        "UInt", "UInt8", "UInt16", "UInt32", "UInt64", "UInt128", "UIntN",
        "String", "Object", "Enum", "Exception",
        "Array", "List", "Set", "Map", "Range", "Interval", "Tuple",
        "Function", "Method", "Property", "Type", "Class",
        "Const", "Service", "Module", "Package",
        "Nullable", "Orderable", "Hashable", "Stringable",
        "Iterator", "Iterable", "Collection", "Sequence", "Void"
    )));
    
    private ${languageNameCap}Keywords() {}
}
""".trimIndent()
    
    /**
     * Generate completion provider
     */
    fun generateCompletionProvider(): String = """
package $packageName;

import org.eclipse.lsp4j.*;

import java.util.*;

/**
 * Provides code completion for ${languageName}.
 */
public class ${languageNameCap}CompletionProvider {
    
    private final ${languageNameCap}DocumentManager documentManager;
    
    public ${languageNameCap}CompletionProvider(${languageNameCap}DocumentManager documentManager) {
        this.documentManager = documentManager;
    }
    
    public List<CompletionItem> getCompletions(String uri, Position position) {
        List<CompletionItem> items = new ArrayList<>();
        
        String prefix = documentManager.getWordBeforePosition(uri, position);
        if (prefix == null) prefix = "";
        
        // Add keyword completions
        for (String keyword : ${languageNameCap}Keywords.KEYWORDS) {
            if (keyword.startsWith(prefix)) {
                CompletionItem item = new CompletionItem(keyword);
                item.setKind(CompletionItemKind.Keyword);
                item.setDetail("Keyword");
                items.add(item);
            }
        }
        
        // Add builtin type completions
        for (String type : ${languageNameCap}Keywords.BUILTIN_TYPES) {
            if (type.startsWith(prefix)) {
                CompletionItem item = new CompletionItem(type);
                item.setKind(CompletionItemKind.Class);
                item.setDetail("Built-in type");
                items.add(item);
            }
        }
        
        // Add snippet completions
        items.addAll(getSnippetCompletions(prefix));
        
        // TODO: Add context-aware completions from AST
        
        return items;
    }
    
    public CompletionItem resolveItem(CompletionItem item) {
        // Add documentation for the item
        String label = item.getLabel();
        
        if (${languageNameCap}Keywords.KEYWORDS.contains(label)) {
            MarkupContent docs = new MarkupContent();
            docs.setKind(MarkupKind.MARKDOWN);
            docs.setValue(getKeywordDocumentation(label));
            item.setDocumentation(docs);
        }
        
        return item;
    }
    
    private List<CompletionItem> getSnippetCompletions(String prefix) {
        List<CompletionItem> snippets = new ArrayList<>();
        
        // Module snippet
        if ("module".startsWith(prefix)) {
            CompletionItem item = new CompletionItem("module");
            item.setKind(CompletionItemKind.Snippet);
            item.setDetail("Module declaration");
            item.setInsertText("module \${1:ModuleName} {\\n\\t\$0\\n}");
            item.setInsertTextFormat(InsertTextFormat.Snippet);
            snippets.add(item);
        }
        
        // Class snippet
        if ("class".startsWith(prefix)) {
            CompletionItem item = new CompletionItem("class");
            item.setKind(CompletionItemKind.Snippet);
            item.setDetail("Class declaration");
            item.setInsertText("class \${1:ClassName} {\\n\\t\$0\\n}");
            item.setInsertTextFormat(InsertTextFormat.Snippet);
            snippets.add(item);
        }
        
        // Interface snippet
        if ("interface".startsWith(prefix)) {
            CompletionItem item = new CompletionItem("interface");
            item.setKind(CompletionItemKind.Snippet);
            item.setDetail("Interface declaration");
            item.setInsertText("interface \${1:InterfaceName} {\\n\\t\$0\\n}");
            item.setInsertTextFormat(InsertTextFormat.Snippet);
            snippets.add(item);
        }
        
        // If snippet
        if ("if".startsWith(prefix)) {
            CompletionItem item = new CompletionItem("if");
            item.setKind(CompletionItemKind.Snippet);
            item.setDetail("If statement");
            item.setInsertText("if (\${1:condition}) {\\n\\t\$0\\n}");
            item.setInsertTextFormat(InsertTextFormat.Snippet);
            snippets.add(item);
        }
        
        // For snippet
        if ("for".startsWith(prefix)) {
            CompletionItem item = new CompletionItem("for");
            item.setKind(CompletionItemKind.Snippet);
            item.setDetail("For loop");
            item.setInsertText("for (Int \${1:i} = 0; \$1 < \${2:count}; \$1++) {\\n\\t\$0\\n}");
            item.setInsertTextFormat(InsertTextFormat.Snippet);
            snippets.add(item);
        }
        
        return snippets;
    }
    
    private String getKeywordDocumentation(String keyword) {
        switch (keyword) {
            case "module": return "Declares a module - the top-level organizational unit in ${languageName}.";
            case "class": return "Declares a class - a template for creating objects.";
            case "interface": return "Declares an interface - a contract that classes can implement.";
            case "mixin": return "Declares a mixin - reusable functionality that can be incorporated into classes.";
            case "service": return "Declares a service - an asynchronous processing unit with isolated state.";
            case "const": return "Declares a const class - an immutable value class.";
            case "enum": return "Declares an enumeration - a fixed set of named values.";
            case "if": return "Conditional statement - executes code if a condition is true.";
            case "for": return "Loop statement - repeats code a specific number of times.";
            case "while": return "Loop statement - repeats code while a condition is true.";
            case "return": return "Returns a value from a method.";
            case "assert": return "Validates that a condition is true, throwing an exception if not.";
            default: return "Keyword: " + keyword;
        }
    }
}
""".trimIndent()

    /**
     * Generate all server files as a map
     */
    fun generateAll(): Map<String, String> = mapOf(
        "${languageNameCap}LanguageServer.java" to generateServer(),
        "${languageNameCap}TextDocumentService.java" to generateTextDocumentService(),
        "${languageNameCap}Keywords.java" to generateKeywords(),
        "${languageNameCap}CompletionProvider.java" to generateCompletionProvider()
    )
}
