package org.xvm.lsp.protocol

import org.eclipse.lsp4j.WorkspaceEdit

/**
 * A rename's edit, `xtc/rename` and `xtc/renameFiles`, paired with any source-graph change that must be applied in the
 * same transaction: a client must accept the [graph] and [edit] as one undoable transaction. [scope] says which source
 * graph the server read.
 */
data class RenameProposal(
    val edit: WorkspaceEdit,
    val graph: SourceGraphReplacement? = null,
    val scope: RenameScope? = null,
)

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
