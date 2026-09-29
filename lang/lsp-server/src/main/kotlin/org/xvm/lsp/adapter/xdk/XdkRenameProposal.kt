package org.xvm.lsp.adapter.xdk

import java.util.List.copyOf as immutableList
import org.xvm.lsp.adapter.WorkspaceEdit

/**
 * A proven edit plus any replacement for a host-owned source graph. A host must persist the graph
 * together with accepting the edit, then install it with replaceSourceModules. Creating a proposal
 * never changes the adapter configuration or the filesystem. Ordinary LSP rename cannot persist
 * arbitrary client settings, so it declines proposals requiring this additional host operation.
 */
class XdkRenameProposal
internal constructor(
    val edit: WorkspaceEdit,
    sourceModules: List<XdkSourceModule>? = null,
    previousSourceModules: List<XdkSourceModule>? = null,
    val scope: XdkRenameScope? = null,
) {
    val sourceModules: List<XdkSourceModule>? = sourceModules?.let(::immutableList)
    val previousSourceModules: List<XdkSourceModule>? = previousSourceModules?.let(::immutableList)
}

/**
 * The source boundary actually checked by a project rename. Absolute roots may be outside IDE
 * workspace folders. Hosts must register every intended consumer; this is not a promise about
 * unregistered repositories, binaries or reflective string references. Null proposal scope means a
 * local module proof, not an empty or globally complete graph. This receipt describes the input
 * snapshot; it is not authorization to apply an edit after its document versions become stale.
 */
class XdkRenameScope
internal constructor(
    val boundary: Boundary,
    modules: List<XdkSourceModule>,
    sourceUris: List<String>,
    val revision: String,
) {
    enum class Boundary {
        CONFIGURED_GRAPH,
        DISCOVERED_GRAPH,
    }

    val modules: List<XdkSourceModule> = immutableList(modules)
    val sourceUris: List<String> = immutableList(sourceUris)
}
