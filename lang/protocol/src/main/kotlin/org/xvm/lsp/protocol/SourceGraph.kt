package org.xvm.lsp.protocol

import org.eclipse.lsp4j.WorkspaceEdit
import java.security.MessageDigest
import java.util.HexFormat

/**
 * A rename's edit, `xtc/rename` and `xtc/renameFiles`, paired with any source-graph change that must be applied in the
 * same transaction: a client must accept the [graph] and [edit] as one undoable transaction. [scope] says which source
 * graph the server read.
 */
data class RenameProposal(
    val edit: WorkspaceEdit,
    val graph: SourceGraphReplacement? = null,
    val scope: RenameScope? = null,
    /**
     * The hash ([textHash]) of the text that each document the [edit] changes had as the server computed its changes,
     * an editor's buffer or the file on the disk, by the URI the edit names it with. A client applies the edit only to
     * documents that still have that text: a file on the disk can change after the server read it with no document
     * version to say so, and an edit of other text puts its changes in the wrong places. Null when the server proves no
     * text, as for an adapter whose edit changes only the open document that the rename was asked in.
     */
    val textHashes: Map<String, String>? = null,
) {
    companion object {
        /**
         * The hash of [text] that [textHashes] names: the SHA-256 of its UTF-8, as lowercase hex, with each of its line
         * breaks written as `\n`. An LSP position counts lines, and characters within a line, so an edit applies alike to
         * texts that differ only in the kind of their line breaks, as an IntelliJ document does to its file with CRLF
         * ones. VS Code's extension hashes the same way.
         */
        fun textHash(text: CharSequence): String =
            HexFormat.of().formatHex(
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(
                        text
                            .toString()
                            .replace("\r\n", "\n")
                            .replace('\r', '\n')
                            .toByteArray(Charsets.UTF_8),
                    ),
            )
    }
}

/**
 * Expected and proposed source graphs, for a guarded configuration replacement: clients apply the replacement only if
 * the expected graph is still current.
 */
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

/** The source graph that a rename read; it does not claim knowledge of consumers outside these source roots. */
data class RenameScope(
    val boundary: String,
    val modules: List<SourceModuleConfiguration>,
    val sourceUris: List<String>,
    val revision: String,
)
