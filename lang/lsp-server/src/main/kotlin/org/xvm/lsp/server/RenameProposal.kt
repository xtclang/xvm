package org.xvm.lsp.server

import org.eclipse.lsp4j.WorkspaceEdit
import org.xvm.lsp.adapter.xdk.XdkSourceModule

/** Native Rename extension. A client must accept the graph and edit as one undoable transaction. */
data class RenameProposal(
    val edit: WorkspaceEdit,
    val graph: SourceGraphReplacement? = null,
)

data class SourceGraphReplacement(
    val before: List<SourceModuleConfiguration>,
    val after: List<SourceModuleConfiguration>,
)

/** Wire data deliberately excludes compiler objects and XdkSourceModule's filesystem helpers. */
data class SourceModuleConfiguration(
    val name: String,
    val uri: String,
    val dependencies: List<String>,
) {
    internal constructor(module: XdkSourceModule) : this(module.name, module.uri, module.dependencies.sorted())
}
