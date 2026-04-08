package org.xvm.lsp.server

import org.eclipse.lsp4j.CallHierarchyIncomingCall
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams
import org.eclipse.lsp4j.CallHierarchyItem
import org.eclipse.lsp4j.CallHierarchyOutgoingCall
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams
import org.eclipse.lsp4j.CallHierarchyPrepareParams
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DeclarationParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.DocumentLink
import org.eclipse.lsp4j.DocumentLinkParams
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams
import org.eclipse.lsp4j.DocumentRangeFormattingParams
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.FoldingRange
import org.eclipse.lsp4j.FoldingRangeRequestParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.ImplementationParams
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintParams
import org.eclipse.lsp4j.LinkedEditingRangeParams
import org.eclipse.lsp4j.LinkedEditingRanges
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.ParameterInformation
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.SelectionRange
import org.eclipse.lsp4j.SelectionRangeParams
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.SignatureInformation
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.TypeDefinitionParams
import org.eclipse.lsp4j.TypeHierarchyItem
import org.eclipse.lsp4j.TypeHierarchyPrepareParams
import org.eclipse.lsp4j.TypeHierarchySubtypesParams
import org.eclipse.lsp4j.TypeHierarchySupertypesParams
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.services.TextDocumentService
import org.slf4j.LoggerFactory
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.model.fmt
import org.xvm.lsp.model.fromLsp
import org.xvm.lsp.model.toLsp
import org.xvm.lsp.model.toRange
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import org.xvm.lsp.adapter.CallHierarchyIncomingCall as AdapterCallHierarchyIncomingCall
import org.xvm.lsp.adapter.CallHierarchyItem as AdapterCallHierarchyItem
import org.xvm.lsp.adapter.CallHierarchyOutgoingCall as AdapterCallHierarchyOutgoingCall
import org.xvm.lsp.adapter.CompletionItem as AdapterCompletionItem
import org.xvm.lsp.adapter.FormattingOptions as AdapterFormattingOptions
import org.xvm.lsp.adapter.Position as AdapterPosition
import org.xvm.lsp.adapter.Range as AdapterRange
import org.xvm.lsp.adapter.SelectionRange as AdapterSelectionRange
import org.xvm.lsp.adapter.TypeHierarchyItem as AdapterTypeHierarchyItem

/**
 * Text document service for XTC Language Server.
 * Handles document synchronization and language features.
 */
class XtcTextDocumentService(
    private val server: XtcLanguageServer,
    private val adapter: Adapter,
) : TextDocumentService {
    companion object {
        private val logger = LoggerFactory.getLogger(XtcTextDocumentService::class.java)
    }

    // ConcurrentHashMap is required because didOpen/didChange/didClose write on the LSP
    // message thread, while documentSymbol, formatting, documentLink, etc. read from
    // CompletableFuture.supplyAsync handlers on the ForkJoinPool.
    private val openDocuments = ConcurrentHashMap<String, String>()

    private fun <R> supplyAsync(
        method: String,
        logParams: String,
        logResult: (R) -> String = { "completed" },
        block: () -> R,
    ): CompletableFuture<R> = server.supplyAsync(method, logParams, logResult, block)

    /**
     * LSP: textDocument/didOpen
     * @see org.eclipse.lsp4j.services.TextDocumentService.didOpen
     */
    override fun didOpen(params: DidOpenTextDocumentParams) {
        val uri = params.textDocument.uri
        val content = params.textDocument.text

        logger.info("textDocument/didOpen: {} ({} bytes)", uri, content.length)
        openDocuments[uri] = content

        val result = adapter.compile(uri, content)
        logger.info("textDocument/didOpen: compiled, {} diagnostics", result.diagnostics.size)
        server.publishDiagnostics(uri, result.diagnostics)
    }

    /**
     * LSP: textDocument/didChange
     * @see org.eclipse.lsp4j.services.TextDocumentService.didChange
     */
    override fun didChange(params: DidChangeTextDocumentParams) {
        val uri = params.textDocument.uri
        val changes = params.contentChanges
        if (changes.isNullOrEmpty()) {
            logger.warn("textDocument/didChange: no content changes for: {}", uri)
            return
        }
        val content = changes.first().text

        logger.info("textDocument/didChange: {} ({} bytes)", uri, content.length)
        openDocuments[uri] = content

        val result = adapter.compile(uri, content)
        logger.info("textDocument/didChange: compiled, {} diagnostics", result.diagnostics.size)
        server.publishDiagnostics(uri, result.diagnostics)
    }

    /**
     * LSP: textDocument/didClose
     * @see org.eclipse.lsp4j.services.TextDocumentService.didClose
     */
    override fun didClose(params: DidCloseTextDocumentParams) {
        val uri = params.textDocument.uri
        logger.info("textDocument/didClose: {}", uri)
        openDocuments.remove(uri)
        adapter.closeDocument(uri)
        server.publishDiagnostics(uri, emptyList())
    }

    /**
     * LSP: textDocument/didSave
     * @see org.eclipse.lsp4j.services.TextDocumentService.didSave
     */
    override fun didSave(params: DidSaveTextDocumentParams) {
        logger.info("textDocument/didSave: {}", params.textDocument.uri)
    }

    /**
     * LSP: textDocument/hover
     * @see org.eclipse.lsp4j.services.TextDocumentService.hover
     */
    override fun hover(params: HoverParams): CompletableFuture<Hover?> =
        supplyAsync(
            "textDocument/hover",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result -> if (result == null) "no result" else "found symbol" },
        ) {
            adapter.getHoverInfo(params.textDocument.uri, params.position.line, params.position.character)?.let {
                Hover().apply {
                    contents =
                        Either.forRight(
                            MarkupContent().apply {
                                kind = MarkupKind.MARKDOWN
                                value = it
                            },
                        )
                }
            }
        }

    /**
     * LSP: textDocument/completion
     * @see org.eclipse.lsp4j.services.TextDocumentService.completion
     */
    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> =
        supplyAsync(
            "textDocument/completion",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result -> "${result.left.size} items" },
        ) {
            val trigger = params.context?.triggerCharacter
            val items =
                adapter.getCompletions(params.textDocument.uri, params.position.line, params.position.character, trigger).map { c ->
                    CompletionItem(c.label).apply {
                        kind = toCompletionItemKind(c.kind)
                        detail = c.detail
                        insertText = c.insertText
                    }
                }
            Either.forLeft(items)
        }

    private fun toCompletionItemKind(kind: AdapterCompletionItem.CompletionKind): CompletionItemKind =
        when (kind) {
            AdapterCompletionItem.CompletionKind.CLASS -> CompletionItemKind.Class
            AdapterCompletionItem.CompletionKind.INTERFACE -> CompletionItemKind.Interface
            AdapterCompletionItem.CompletionKind.METHOD -> CompletionItemKind.Method
            AdapterCompletionItem.CompletionKind.PROPERTY -> CompletionItemKind.Property
            AdapterCompletionItem.CompletionKind.VARIABLE -> CompletionItemKind.Variable
            AdapterCompletionItem.CompletionKind.KEYWORD -> CompletionItemKind.Keyword
            AdapterCompletionItem.CompletionKind.MODULE -> CompletionItemKind.Module
        }

    /**
     * LSP: textDocument/definition
     * @see org.eclipse.lsp4j.services.TextDocumentService.definition
     */
    override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> =
        supplyAsync(
            "textDocument/definition",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result -> if (result.left.isEmpty()) "no result" else "found" },
        ) {
            adapter.findDefinition(params.textDocument.uri, params.position.line, params.position.character)?.let {
                Either.forLeft<List<Location>, List<LocationLink>>(listOf(it.toLsp()))
            } ?: Either.forLeft(emptyList())
        }

    /**
     * LSP: textDocument/references
     * @see org.eclipse.lsp4j.services.TextDocumentService.references
     */
    override fun references(params: ReferenceParams): CompletableFuture<List<Location>> =
        supplyAsync(
            "textDocument/references",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result -> "${result.size} references" },
        ) {
            adapter
                .findReferences(
                    params.textDocument.uri,
                    params.position.line,
                    params.position.character,
                    params.context.isIncludeDeclaration,
                ).map { it.toLsp() }
        }

    /**
     * LSP: textDocument/documentSymbol
     * @see org.eclipse.lsp4j.services.TextDocumentService.documentSymbol
     */
    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> =
        supplyAsync(
            "textDocument/documentSymbol",
            params.textDocument.uri,
            { result -> "${result.size} symbols" },
        ) {
            val uri = params.textDocument.uri
            val result =
                adapter.getCachedResult(uri) ?: openDocuments[uri]?.let { content ->
                    adapter.compile(uri, content)
                } ?: return@supplyAsync emptyList()

            result.symbols.map { symbol ->
                Either.forRight(toDocumentSymbol(symbol))
            }
        }

    private fun toDocumentSymbol(symbol: SymbolInfo): DocumentSymbol =
        DocumentSymbol().apply {
            name = symbol.name
            kind = symbol.kind.toLsp()
            range = symbol.location.toRange()
            selectionRange = symbol.location.toRange()
            if (symbol.typeSignature != null) {
                detail = symbol.typeSignature
            }
            if (symbol.children.isNotEmpty()) {
                children = symbol.children.map { toDocumentSymbol(it) }
            }
        }

    /**
     * LSP: textDocument/documentHighlight
     * @see org.eclipse.lsp4j.services.TextDocumentService.documentHighlight
     */
    override fun documentHighlight(params: DocumentHighlightParams): CompletableFuture<List<DocumentHighlight>> =
        supplyAsync(
            "textDocument/documentHighlight",
            "${params.textDocument.uri} pos=${params.position.fmt()}",
            { result -> "${result.size} highlights" },
        ) {
            adapter
                .getDocumentHighlights(
                    params.textDocument.uri,
                    params.position.line,
                    params.position.character,
                ).map { h ->
                    DocumentHighlight().apply {
                        range = h.range.toLsp()
                        kind = h.kind.toLsp()
                    }
                }
        }

    /**
     * LSP: textDocument/selectionRange
     * @see org.eclipse.lsp4j.services.TextDocumentService.selectionRange
     */
    override fun selectionRange(params: SelectionRangeParams): CompletableFuture<List<SelectionRange>> =
        supplyAsync(
            "textDocument/selectionRange",
            "${params.textDocument.uri} positions=${params.positions.map { it.fmt() }}",
            { result -> "${result.size} ranges" },
        ) {
            val adapterPositions = params.positions.map { AdapterPosition(it.line, it.character) }
            adapter.getSelectionRanges(params.textDocument.uri, adapterPositions).map { toLspSelectionRange(it) }
        }

    private fun toLspSelectionRange(range: AdapterSelectionRange): org.eclipse.lsp4j.SelectionRange =
        org.eclipse.lsp4j.SelectionRange().apply {
            this.range = range.range.toLsp()
            this.parent = range.parent?.let { toLspSelectionRange(it) }
        }

    /**
     * LSP: textDocument/foldingRange
     * @see org.eclipse.lsp4j.services.TextDocumentService.foldingRange
     */
    override fun foldingRange(params: FoldingRangeRequestParams): CompletableFuture<List<FoldingRange>> =
        supplyAsync(
            "textDocument/foldingRange",
            params.textDocument.uri,
            { result -> "${result.size} ranges" },
        ) {
            adapter.getFoldingRanges(params.textDocument.uri).map { r ->
                FoldingRange(r.startLine, r.endLine).apply {
                    kind = r.kind?.toLsp()
                }
            }
        }

    /**
     * LSP: textDocument/documentLink
     * @see org.eclipse.lsp4j.services.TextDocumentService.documentLink
     */
    override fun documentLink(params: DocumentLinkParams): CompletableFuture<List<DocumentLink>> =
        supplyAsync(
            "textDocument/documentLink",
            params.textDocument.uri,
            { result -> "${result.size} links" },
        ) {
            val uri = params.textDocument.uri
            val content = openDocuments[uri] ?: return@supplyAsync emptyList()
            adapter.getDocumentLinks(uri, content).map { l ->
                DocumentLink().apply {
                    range = l.range.toLsp()
                    target = l.target
                    tooltip = l.tooltip
                }
            }
        }

    /**
     * LSP: textDocument/signatureHelp
     * @see org.eclipse.lsp4j.services.TextDocumentService.signatureHelp
     */
    override fun signatureHelp(params: SignatureHelpParams): CompletableFuture<SignatureHelp?> =
        supplyAsync(
            "textDocument/signatureHelp",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result -> if (result == null) "no result" else "${result.signatures.size} signatures" },
        ) {
            adapter.getSignatureHelp(params.textDocument.uri, params.position.line, params.position.character)?.let { help ->
                SignatureHelp().apply {
                    signatures =
                        help.signatures.map { s ->
                            SignatureInformation().apply {
                                label = s.label
                                documentation = s.documentation?.let { Either.forLeft(it) }
                                parameters =
                                    s.parameters.map { p ->
                                        ParameterInformation().apply {
                                            label = Either.forLeft(p.label)
                                            documentation = p.documentation?.let { Either.forLeft(it) }
                                        }
                                    }
                            }
                        }
                    activeSignature = help.activeSignature
                    activeParameter = help.activeParameter
                }
            }
        }

    /**
     * LSP: textDocument/prepareRename
     * @see org.eclipse.lsp4j.services.TextDocumentService.prepareRename
     */
    override fun prepareRename(
        params: PrepareRenameParams,
    ): CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> =
        supplyAsync(
            "textDocument/prepareRename",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { _ -> "valid" },
        ) {
            adapter.prepareRename(params.textDocument.uri, params.position.line, params.position.character)?.let { result ->
                Either3.forSecond<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>(
                    PrepareRenameResult().apply {
                        range = result.range.toLsp()
                        placeholder = result.placeholder
                    },
                )
            } ?: throw ResponseErrorException(
                ResponseError(
                    ResponseErrorCode.InvalidParams,
                    "Rename not allowed at this position",
                    null,
                ),
            )
        }

    /**
     * LSP: textDocument/rename
     * @see org.eclipse.lsp4j.services.TextDocumentService.rename
     */
    override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit?> =
        supplyAsync(
            "textDocument/rename",
            "${params.textDocument.uri} at ${params.position.fmt()} -> '${params.newName}'",
            { result -> if (result == null) "no edit" else "${result.changes?.size ?: 0} files changed" },
        ) {
            adapter
                .rename(
                    params.textDocument.uri,
                    params.position.line,
                    params.position.character,
                    params.newName,
                )?.let { edit ->
                    WorkspaceEdit().apply {
                        changes =
                            edit.changes.mapValues { (_, edits) ->
                                edits.map { e ->
                                    TextEdit().apply {
                                        range = e.range.toLsp()
                                        newText = e.newText
                                    }
                                }
                            }
                    }
                }
        }

    /**
     * LSP: textDocument/codeAction
     * @see org.eclipse.lsp4j.services.TextDocumentService.codeAction
     */
    override fun codeAction(params: CodeActionParams): CompletableFuture<List<Either<Command, CodeAction>>> =
        supplyAsync(
            "textDocument/codeAction",
            "${params.textDocument.uri} range=${params.range.fmt()} diagnostics=${params.context.diagnostics?.size ?: 0}",
            { result -> "${result.size} actions" },
        ) {
            val adapterDiagnostics = params.context.diagnostics.map { Diagnostic.fromLsp(params.textDocument.uri, it) }

            adapter
                .getCodeActions(
                    params.textDocument.uri,
                    toAdapterRange(params.range),
                    adapterDiagnostics,
                ).map { a ->
                    Either.forRight(
                        CodeAction().apply {
                            title = a.title
                            kind = a.kind.toLsp()
                            isPreferred = a.isPreferred
                            a.edit?.let { e ->
                                edit =
                                    WorkspaceEdit().apply {
                                        changes =
                                            e.changes.mapValues { (_, edits) ->
                                                edits.map { te ->
                                                    TextEdit().apply {
                                                        this.range = te.range.toLsp()
                                                        newText = te.newText
                                                    }
                                                }
                                            }
                                    }
                            }
                        },
                    )
                }
        }

    /**
     * LSP: textDocument/semanticTokens/full
     * @see org.eclipse.lsp4j.services.TextDocumentService.semanticTokensFull
     */
    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens?> =
        supplyAsync(
            "textDocument/semanticTokens/full",
            params.textDocument.uri,
            { result -> if (result == null) "no tokens" else "${result.data.size} items" },
        ) {
            adapter.getSemanticTokens(params.textDocument.uri)?.let { tokens ->
                SemanticTokens().apply {
                    data = tokens.data
                }
            }
        }

    /**
     * LSP: textDocument/inlayHint
     * @see org.eclipse.lsp4j.services.TextDocumentService.inlayHint
     */
    override fun inlayHint(params: InlayHintParams): CompletableFuture<List<InlayHint>> =
        supplyAsync(
            "textDocument/inlayHint",
            "${params.textDocument.uri} range=${params.range.fmt()}",
            { result -> "${result.size} hints" },
        ) {
            adapter.getInlayHints(params.textDocument.uri, toAdapterRange(params.range)).map { h ->
                InlayHint().apply {
                    position = Position(h.position.line, h.position.column)
                    label = Either.forLeft(h.label)
                    kind = h.kind.toLsp()
                    paddingLeft = h.paddingLeft
                    paddingRight = h.paddingRight
                }
            }
        }

    /**
     * LSP: textDocument/formatting
     * @see org.eclipse.lsp4j.services.TextDocumentService.formatting
     */
    override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<TextEdit>> =
        supplyAsync(
            "textDocument/formatting",
            params.textDocument.uri,
            { result -> "${result.size} edits" },
        ) {
            val uri = params.textDocument.uri
            val content = openDocuments[uri] ?: return@supplyAsync emptyList()
            val options = toAdapterFormattingOptions(params.options)
            adapter.formatDocument(uri, content, options).map { e ->
                TextEdit().apply {
                    range = e.range.toLsp()
                    newText = e.newText
                }
            }
        }

    /**
     * LSP: textDocument/rangeFormatting
     * @see org.eclipse.lsp4j.services.TextDocumentService.rangeFormatting
     */
    override fun rangeFormatting(params: DocumentRangeFormattingParams): CompletableFuture<List<TextEdit>> =
        supplyAsync(
            "textDocument/rangeFormatting",
            "${params.textDocument.uri} range=${params.range.fmt()}",
            { result -> "${result.size} edits" },
        ) {
            val uri = params.textDocument.uri
            val content = openDocuments[uri] ?: return@supplyAsync emptyList()
            val options = toAdapterFormattingOptions(params.options)
            adapter.formatRange(uri, content, toAdapterRange(params.range), options).map { e ->
                TextEdit().apply {
                    this.range = e.range.toLsp()
                    newText = e.newText
                }
            }
        }

    /**
     * LSP: textDocument/declaration
     * @see org.eclipse.lsp4j.services.TextDocumentService.declaration
     */
    override fun declaration(params: DeclarationParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> =
        supplyAsync(
            "textDocument/declaration",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result -> if (result.left.isEmpty()) "no result" else "found" },
        ) {
            adapter.findDeclaration(params.textDocument.uri, params.position.line, params.position.character)?.let {
                Either.forLeft<List<Location>, List<LocationLink>>(listOf(it.toLsp()))
            } ?: Either.forLeft(emptyList())
        }

    /**
     * LSP: textDocument/typeDefinition
     * @see org.eclipse.lsp4j.services.TextDocumentService.typeDefinition
     */
    override fun typeDefinition(params: TypeDefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> =
        supplyAsync(
            "textDocument/typeDefinition",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result -> if (result.left.isEmpty()) "no result" else "found" },
        ) {
            adapter.findTypeDefinition(params.textDocument.uri, params.position.line, params.position.character)?.let {
                Either.forLeft<List<Location>, List<LocationLink>>(listOf(it.toLsp()))
            } ?: Either.forLeft(emptyList())
        }

    /**
     * LSP: textDocument/implementation
     * @see org.eclipse.lsp4j.services.TextDocumentService.implementation
     */
    override fun implementation(params: ImplementationParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> =
        supplyAsync(
            "textDocument/implementation",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result -> "${result.left.size} locations" },
        ) {
            Either.forLeft(
                adapter.findImplementation(params.textDocument.uri, params.position.line, params.position.character).map { it.toLsp() },
            )
        }

    /**
     * LSP: typeHierarchy/prepareTypeHierarchy
     * @see org.eclipse.lsp4j.services.TextDocumentService.prepareTypeHierarchy
     */
    override fun prepareTypeHierarchy(params: TypeHierarchyPrepareParams): CompletableFuture<List<TypeHierarchyItem>> =
        supplyAsync(
            "typeHierarchy/prepare",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result -> "${result.size} items" },
        ) {
            adapter.prepareTypeHierarchy(params.textDocument.uri, params.position.line, params.position.character).map {
                it.toLsp(params.textDocument.uri)
            }
        }

    /**
     * LSP: typeHierarchy/supertypes
     * @see org.eclipse.lsp4j.services.TextDocumentService.typeHierarchySupertypes
     */
    override fun typeHierarchySupertypes(params: TypeHierarchySupertypesParams): CompletableFuture<List<TypeHierarchyItem>> =
        supplyAsync(
            "typeHierarchy/supertypes",
            params.item.name,
            { result -> "${result.size} items" },
        ) {
            adapter.getSupertypes(params.item.toAdapter()).map { it.toLsp(params.item.uri) }
        }

    /**
     * LSP: typeHierarchy/subtypes
     * @see org.eclipse.lsp4j.services.TextDocumentService.typeHierarchySubtypes
     */
    override fun typeHierarchySubtypes(params: TypeHierarchySubtypesParams): CompletableFuture<List<TypeHierarchyItem>> =
        supplyAsync(
            "typeHierarchy/subtypes",
            params.item.name,
            { result -> "${result.size} items" },
        ) {
            adapter.getSubtypes(params.item.toAdapter()).map { it.toLsp(params.item.uri) }
        }

    /**
     * LSP: callHierarchy/prepare
     * @see org.eclipse.lsp4j.services.TextDocumentService.prepareCallHierarchy
     */
    override fun prepareCallHierarchy(params: CallHierarchyPrepareParams): CompletableFuture<List<CallHierarchyItem>> =
        supplyAsync(
            "callHierarchy/prepare",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result -> "${result.size} items" },
        ) {
            adapter
                .prepareCallHierarchy(
                    params.textDocument.uri,
                    params.position.line,
                    params.position.character,
                ).map { it.toLspCallItem() }
        }

    /**
     * LSP: callHierarchy/incomingCalls
     * @see org.eclipse.lsp4j.services.TextDocumentService.callHierarchyIncomingCalls
     */
    override fun callHierarchyIncomingCalls(params: CallHierarchyIncomingCallsParams): CompletableFuture<List<CallHierarchyIncomingCall>> =
        supplyAsync(
            "callHierarchy/incomingCalls",
            params.item.name,
            { result -> "${result.size} calls" },
        ) {
            adapter.getIncomingCalls(params.item.toAdapterCallItem()).map { c ->
                CallHierarchyIncomingCall().apply {
                    from = c.from.toLspCallItem()
                    fromRanges = c.fromRanges.map { it.toLsp() }
                }
            }
        }

    /**
     * LSP: callHierarchy/outgoingCalls
     * @see org.eclipse.lsp4j.services.TextDocumentService.callHierarchyOutgoingCalls
     */
    override fun callHierarchyOutgoingCalls(params: CallHierarchyOutgoingCallsParams): CompletableFuture<List<CallHierarchyOutgoingCall>> =
        supplyAsync(
            "callHierarchy/outgoingCalls",
            params.item.name,
            { result -> "${result.size} calls" },
        ) {
            adapter.getOutgoingCalls(params.item.toAdapterCallItem()).map { c ->
                CallHierarchyOutgoingCall().apply {
                    to = c.to.toLspCallItem()
                    fromRanges = c.fromRanges.map { it.toLsp() }
                }
            }
        }

    /**
     * LSP: textDocument/codeLens
     * @see org.eclipse.lsp4j.services.TextDocumentService.codeLens
     */
    override fun codeLens(params: CodeLensParams): CompletableFuture<List<CodeLens>> =
        supplyAsync(
            "textDocument/codeLens",
            params.textDocument.uri,
            { result -> "${result.size} lenses" },
        ) {
            adapter.getCodeLenses(params.textDocument.uri).map { l ->
                CodeLens().apply {
                    range = l.range.toLsp()
                    l.command?.let { cmd ->
                        command = Command(cmd.title, cmd.command)
                    }
                }
            }
        }

    /**
     * LSP: textDocument/onTypeFormatting
     * @see org.eclipse.lsp4j.services.TextDocumentService.onTypeFormatting
     */
    override fun onTypeFormatting(params: DocumentOnTypeFormattingParams): CompletableFuture<List<TextEdit>> =
        supplyAsync(
            "textDocument/onTypeFormatting",
            "${params.textDocument.uri} at ${params.position.fmt()} ch='${params.ch}'",
            { result -> "${result.size} edits" },
        ) {
            val options =
                AdapterFormattingOptions(
                    tabSize = params.options.tabSize,
                    insertSpaces = params.options.isInsertSpaces,
                )
            adapter
                .onTypeFormatting(
                    params.textDocument.uri,
                    params.position.line,
                    params.position.character,
                    params.ch,
                    options,
                ).map { e ->
                    TextEdit().apply {
                        range = e.range.toLsp()
                        newText = e.newText
                    }
                }
        }

    /**
     * LSP: textDocument/linkedEditingRange
     * @see org.eclipse.lsp4j.services.TextDocumentService.linkedEditingRange
     */
    override fun linkedEditingRange(params: LinkedEditingRangeParams): CompletableFuture<LinkedEditingRanges> =
        supplyAsync(
            "textDocument/linkedEditingRange",
            "${params.textDocument.uri} at ${params.position.fmt()}",
            { result -> "${result.ranges?.size ?: 0} ranges" },
        ) {
            adapter
                .getLinkedEditingRanges(
                    params.textDocument.uri,
                    params.position.line,
                    params.position.character,
                )?.let { result ->
                    LinkedEditingRanges().apply {
                        ranges = result.ranges.map { it.toLsp() }
                        wordPattern = result.wordPattern
                    }
                } ?: LinkedEditingRanges()
        }

    // ====================================================================
    // Conversion helpers for hierarchy types
    // ====================================================================

    private fun AdapterTypeHierarchyItem.toLsp(defaultUri: String): org.eclipse.lsp4j.TypeHierarchyItem {
        val resolvedUri = this.uri.ifEmpty { defaultUri }
        return org.eclipse.lsp4j
            .TypeHierarchyItem(
                this.name,
                this.kind.toLsp(),
                resolvedUri,
                this.range.toLsp(),
                this.selectionRange.toLsp(),
            ).apply {
                this.detail = this@toLsp.detail
            }
    }

    private fun org.eclipse.lsp4j.TypeHierarchyItem.toAdapter(): AdapterTypeHierarchyItem =
        AdapterTypeHierarchyItem(
            name = name,
            kind = SymbolInfo.SymbolKind.CLASS,
            uri = uri,
            range = toAdapterRange(range),
            selectionRange = toAdapterRange(selectionRange),
            detail = detail,
        )

    private fun AdapterCallHierarchyItem.toLspCallItem(): org.eclipse.lsp4j.CallHierarchyItem {
        val result =
            org.eclipse.lsp4j.CallHierarchyItem(
                this.name,
                this.kind.toLsp(),
                this.uri,
                this.range.toLsp(),
                this.selectionRange.toLsp(),
            )
        result.detail = this.detail
        return result
    }

    private fun org.eclipse.lsp4j.CallHierarchyItem.toAdapterCallItem(): AdapterCallHierarchyItem =
        AdapterCallHierarchyItem(
            name = name,
            kind = SymbolInfo.SymbolKind.METHOD,
            uri = uri,
            range = toAdapterRange(range),
            selectionRange = toAdapterRange(selectionRange),
            detail = detail,
        )

    private fun toAdapterRange(range: org.eclipse.lsp4j.Range) =
        AdapterRange(
            AdapterPosition(range.start.line, range.start.character),
            AdapterPosition(range.end.line, range.end.character),
        )

    private fun toAdapterFormattingOptions(lsp: org.eclipse.lsp4j.FormattingOptions) =
        AdapterFormattingOptions(
            tabSize = lsp.tabSize,
            insertSpaces = lsp.isInsertSpaces,
            trimTrailingWhitespace = lsp.isTrimTrailingWhitespace,
            insertFinalNewline = lsp.isInsertFinalNewline,
        )
}
