package org.xvm.lsp.server

import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.DiagnosticTag
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InsertTextMode
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.SymbolKind
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Immutable wire-format choices, independent of compiler semantics and editor preferences. */
internal data class ClientPresentation(
    val markdownHover: Boolean = false,
    val hierarchicalSymbols: Boolean = false,
    val documentKinds: Set<SymbolKind> = legacyKinds,
    val workspaceKinds: Set<SymbolKind> = legacyKinds,
    val diagnosticVersions: Boolean = false,
    val diagnosticRelatedInformation: Boolean = false,
    val diagnosticTags: Set<DiagnosticTag> = emptySet(),
    val workspaceConfiguration: Boolean = false,
    val completionKinds: Set<CompletionItemKind> = legacyCompletionKinds,
    val completionSnippets: Boolean = false,
    val inlineCompletion: Boolean = false,
    val completionAsIs: Boolean = false,
    val completionAdjustIndentation: Boolean = false,
    val actionLiterals: Boolean = false,
    val preferredActions: Boolean = false,
    val applyEdit: Boolean = false,
    val linkTooltips: Boolean = false,
    val signatureActiveParameters: Boolean = false,
    val pullDiagnosticRelatedInformation: Boolean = false,
    val pullDiagnosticTags: Set<DiagnosticTag> = emptySet(),
    /**
     * The client asks for quick fixes per diagnostic, with only `quickfix`, and hides them from the list its automatic
     * requests without only fill, as its initialization option `xtcCodeActions.quickFixesPerDiagnostic` says.
     */
    val quickFixesPerDiagnostic: Boolean = false,
    /**
     * The client waits for an Automatic code action request where it must not wait long, so such a request waits for
     * its full answer only [automaticWait], and then answers from what is proved already, as its initialization option
     * `xtcCodeActions.automaticFromCache` says.
     */
    val automaticFromCache: Boolean = false,
    /** How long such a request waits for its full answer: `xtcCodeActions.automaticWaitMillis`, if the client says. */
    val automaticWait: Duration = AUTOMATIC_WAIT,
) {
    val codeActions: Boolean
        get() = actionLiterals || applyEdit

    fun hover(markdown: String): MarkupContent =
        if (markdownHover) {
            MarkupContent(MarkupKind.MARKDOWN, markdown)
        } else {
            MarkupContent(MarkupKind.PLAINTEXT, plainText(markdown))
        }

    fun symbolKind(
        kind: SymbolKind,
        workspace: Boolean = false,
    ): SymbolKind {
        val supported = if (workspace) workspaceKinds else documentKinds
        return kind.takeIf { it in supported }
            ?: SymbolKind.Variable.takeIf { it in supported }
            ?: supported.minByOrNull { it.value }
            ?: SymbolKind.Variable
    }

    fun completionKind(kind: CompletionItemKind): CompletionItemKind =
        kind.takeIf { it in completionKinds }
            ?: CompletionItemKind.Text.takeIf { it in completionKinds }
            ?: completionKinds.minByOrNull { it.value }
            ?: CompletionItemKind.Text

    /** Templates contain source indentation. Avoid a second indent by clients that always adjust it. */
    fun completionTemplate(text: String): String {
        if (completionAsIs || !completionAdjustIndentation) return text
        val newline = lineBreaks.find(text)?.value ?: "\n"
        val lines = text.split(newline)
        val indent = lines.drop(1).filter(String::isNotBlank).minOfOrNull { it.takeWhile(Char::isWhitespace).length } ?: 0
        return (lines.take(1) + lines.drop(1).map { it.drop(indent.coerceAtMost(it.takeWhile(Char::isWhitespace).length)) })
            .joinToString(newline)
    }

    companion object {
        const val APPLY_CODE_ACTION = "xtc.applyCodeAction"

        /**
         * How long an Automatic code action request of a client that waits for it waits for its full answer, unless the
         * client says: about as long as a wait for the lightbulb goes unnoticed, enough for the proofs of an unchanged
         * revision; an edit's first compile takes longer.
         */
        val AUTOMATIC_WAIT = 500.milliseconds
        private val lineBreaks = Regex("\\r\\n|\\r|\\n")
        private val legacyCompletionKinds =
            CompletionItemKind.entries
                .filter { it.value <= CompletionItemKind.Reference.value }
                .toSet()
        private val legacyKinds = SymbolKind.entries.filter { it.value <= 18 }.toSet()

        fun read(params: InitializeParams): ClientPresentation {
            val text = params.capabilities?.textDocument
            val codeActions = LspJsonOptions.lookup(params.initializationOptions, "xtcCodeActions")
            return ClientPresentation(
                markdownHover =
                    text?.hover?.contentFormat?.firstOrNull {
                        it == MarkupKind.MARKDOWN || it == MarkupKind.PLAINTEXT
                    } == MarkupKind.MARKDOWN,
                hierarchicalSymbols =
                    text?.documentSymbol?.hierarchicalDocumentSymbolSupport == true,
                documentKinds =
                    text
                        ?.documentSymbol
                        ?.symbolKind
                        ?.valueSet
                        ?.toSet() ?: legacyKinds,
                workspaceKinds =
                    params.capabilities
                        ?.workspace
                        ?.symbol
                        ?.symbolKind
                        ?.valueSet
                        ?.toSet()
                        ?: legacyKinds,
                diagnosticVersions = text?.publishDiagnostics?.versionSupport == true,
                diagnosticRelatedInformation = text?.publishDiagnostics?.relatedInformation == true,
                diagnosticTags =
                    text
                        ?.publishDiagnostics
                        ?.tagSupport
                        ?.let { if (it.isLeft) null else it.right }
                        ?.valueSet
                        .orEmpty()
                        .toSet(),
                workspaceConfiguration = params.capabilities?.workspace?.configuration == true,
                completionKinds =
                    text
                        ?.completion
                        ?.completionItemKind
                        ?.valueSet
                        ?.toSet()
                        ?: legacyCompletionKinds,
                actionLiterals = text?.codeAction?.codeActionLiteralSupport != null,
                completionSnippets = text?.completion?.completionItem?.snippetSupport == true,
                inlineCompletion = text?.inlineCompletion != null,
                completionAsIs =
                    text
                        ?.completion
                        ?.completionItem
                        ?.insertTextModeSupport
                        ?.valueSet
                        ?.contains(InsertTextMode.AsIs) == true,
                completionAdjustIndentation = text?.completion?.insertTextMode == InsertTextMode.AdjustIndentation,
                preferredActions = text?.codeAction?.isPreferredSupport == true,
                applyEdit = params.capabilities?.workspace?.applyEdit == true,
                linkTooltips = text?.documentLink?.tooltipSupport == true,
                signatureActiveParameters =
                    text?.signatureHelp?.signatureInformation?.activeParameterSupport == true,
                pullDiagnosticRelatedInformation = text?.diagnostic?.relatedInformation == true,
                pullDiagnosticTags =
                    text
                        ?.diagnostic
                        ?.tagSupport
                        ?.valueSet
                        .orEmpty()
                        .toSet(),
                quickFixesPerDiagnostic = LspJsonOptions.boolean(codeActions, "quickFixesPerDiagnostic") == true,
                automaticFromCache = LspJsonOptions.boolean(codeActions, "automaticFromCache") == true,
                automaticWait = LspJsonOptions.int(codeActions, "automaticWaitMillis")?.coerceAtLeast(0)?.milliseconds ?: AUTOMATIC_WAIT,
            )
        }

        /**
         * The empty kind selects everything; unknown client kinds are not invented or broadened.
         */
        fun matchesActionKind(
            kind: String,
            only: List<String>?,
        ): Boolean = only == null || only.any { it.isEmpty() || kind == it || kind.startsWith("$it.") }

        /** Workspace folders take precedence, including an explicitly empty list. */
        fun workspaceUris(params: InitializeParams): List<String> =
            params.workspaceFolders?.map { it.uri }
                ?: params.rootUri?.let(::listOf)
                ?: params.rootPath?.let { listOf(Path.of(it).toUri().toString()) }
                ?: emptyList()

        // Adapter hover uses fenced declarations, inline code and headings. Preserve their text.
        private fun plainText(markdown: String): String =
            markdown
                .lineSequence()
                .filterNot { it.trimStart().startsWith("```") }
                .joinToString("\n") { it.replace(Regex("^#{1,6}\\s+"), "").replace("`", "") }
    }
}
