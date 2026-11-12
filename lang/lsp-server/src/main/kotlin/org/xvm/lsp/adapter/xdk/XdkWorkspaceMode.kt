package org.xvm.lsp.adapter.xdk

import org.slf4j.LoggerFactory
import org.xvm.asm.Constants.ECSTASY_MODULE
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A workspace that holds the XDK's own sources: when its modules declare `ecstasy.xtclang.org`, the bundled libraries
 * it declares are its own modules, analyzed from its sources, and every other module of it is compiled against them,
 * as the compiler's library repository puts the server's dependencies ahead of the bundled libraries. Every module
 * imports ecstasy without saying so, so each depends on the workspace's. The bootstrap modules stay bundled: ecstasy
 * compiles against mack's binary, and _native's declarations must match the runtime that the server carries.
 *
 * `-Dxtc.lsp.xdkWorkspace=false` keeps every library bundled, as a heap too small to compile ecstasy from source does.
 * Embedded builds and runs do not run in such a workspace: the server's runtime is the bundled XDK's, not its sources'.
 */
internal object XdkWorkspaceMode {
    const val PROPERTY = "xtc.lsp.xdkWorkspace"

    /** Why an XDK workspace has no embedded builds or runs. */
    const val NO_EMBEDDED_RUNS =
        "This workspace holds the XDK's own sources: the server builds and runs with the bundled XDK's runtime, not " +
            "with these sources, so build and run them with Gradle (Ecstasy build mode: Gradle)"

    /** The bundled modules that a workspace's sources never replace. */
    val BOOTSTRAP = setOf("mack.xtclang.org", "_native.xtclang.org")

    private val logger = LoggerFactory.getLogger(XdkWorkspaceMode::class.java)
    private val smallHeapLogged = AtomicBoolean()

    /** Whether a workspace whose modules declare [names] is the XDK's own. */
    fun applies(names: Collection<String>): Boolean {
        if (ECSTASY_MODULE !in names || System.getProperty(PROPERTY)?.toBooleanStrictOrNull() == false) return false
        if (XdkLibrarySources.fitsLibraries()) return true
        if (smallHeapLogged.compareAndSet(false, true)) {
            logger.warn(
                "The workspace holds the XDK's own sources, but compiling ecstasy from them needs a maximum heap of 1 GB, " +
                    "and this server has {} MB: its libraries stay bundled",
                Runtime.getRuntime().maxMemory() shr 20,
            )
        }
        return false
    }

    /** Whether the module [name] of an XDK workspace is one of its own: not a bootstrap module. */
    fun replaces(name: String): Boolean = name in XdkLibraries.moduleNames && name !in BOOTSTRAP
}
