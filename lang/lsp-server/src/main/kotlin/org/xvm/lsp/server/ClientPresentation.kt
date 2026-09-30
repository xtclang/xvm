package org.xvm.lsp.server

import java.nio.file.Path
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.SymbolKind

/** Immutable wire-format choices, independent of compiler semantics and editor preferences. */
internal data class ClientPresentation(
    val markdownHover: Boolean = false,
    val hierarchicalSymbols: Boolean = false,
    val documentKinds: Set<SymbolKind> = legacyKinds,
    val workspaceKinds: Set<SymbolKind> = legacyKinds,
    val diagnosticVersions: Boolean = false,
    val diagnosticRelatedInformation: Boolean = false,
    val workspaceConfiguration: Boolean = false,
    val completionKinds: Set<CompletionItemKind> = legacyCompletionKinds,
    val actionLiterals: Boolean = false,
    val preferredActions: Boolean = false,
    val applyEdit: Boolean = false,
) {
    fun hover(markdown: String): MarkupContent =
        if (markdownHover) MarkupContent(MarkupKind.MARKDOWN, markdown)
        else MarkupContent(MarkupKind.PLAINTEXT, plainText(markdown))

    fun symbolKind(kind: SymbolKind, workspace: Boolean = false): SymbolKind {
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

    val codeActions: Boolean
        get() = actionLiterals || applyEdit

    companion object {
        const val APPLY_CODE_ACTION = "xtc.applyCodeAction"
        private val legacyCompletionKinds =
            CompletionItemKind.entries
                .filter { it.value <= CompletionItemKind.Reference.value }
                .toSet()
        private val legacyKinds = SymbolKind.entries.filter { it.value <= 18 }.toSet()

        fun read(params: InitializeParams): ClientPresentation {
            val text = params.capabilities?.textDocument
            return ClientPresentation(
                text?.hover?.contentFormat?.firstOrNull {
                    it == MarkupKind.MARKDOWN || it == MarkupKind.PLAINTEXT
                } == MarkupKind.MARKDOWN,
                text?.documentSymbol?.hierarchicalDocumentSymbolSupport == true,
                text?.documentSymbol?.symbolKind?.valueSet?.toSet() ?: legacyKinds,
                params.capabilities?.workspace?.symbol?.symbolKind?.valueSet?.toSet()
                    ?: legacyKinds,
                text?.publishDiagnostics?.versionSupport == true,
                text?.publishDiagnostics?.relatedInformation == true,
                params.capabilities?.workspace?.configuration == true,
                text?.completion?.completionItemKind?.valueSet?.toSet() ?: legacyCompletionKinds,
                text?.codeAction?.codeActionLiteralSupport != null,
                text?.codeAction?.isPreferredSupport == true,
                params.capabilities?.workspace?.applyEdit == true,
            )
        }

        /**
         * The empty kind selects everything; unknown client kinds are not invented or broadened.
         */
        fun matchesActionKind(kind: String, only: List<String>?): Boolean =
            only == null || only.any { it.isEmpty() || kind == it || kind.startsWith("$it.") }

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
                .map { it.replace(Regex("^#{1,6}\\s+"), "").replace("`", "") }
                .joinToString("\n")
    }
}
