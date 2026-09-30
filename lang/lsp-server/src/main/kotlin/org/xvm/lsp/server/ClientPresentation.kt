package org.xvm.lsp.server

import java.nio.file.Path
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

    companion object {
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
            )
        }

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
