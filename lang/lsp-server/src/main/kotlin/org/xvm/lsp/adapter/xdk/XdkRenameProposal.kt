package org.xvm.lsp.adapter.xdk

import org.xvm.lsp.adapter.WorkspaceEdit
import java.util.List.copyOf as immutableList

/**
 * A proven edit plus any replacement for a host-owned source graph. A host must persist the graph
 * together with accepting the edit, then install it with replaceSourceModules. Creating a proposal
 * never changes the adapter configuration or the filesystem. Ordinary LSP rename cannot persist
 * arbitrary client settings, so it declines proposals requiring this additional host operation.
 */
class XdkRenameProposal internal constructor(
    val edit: WorkspaceEdit,
    sourceModules: List<XdkSourceModule>? = null,
) {
    val sourceModules: List<XdkSourceModule>? = sourceModules?.let(::immutableList)
}
