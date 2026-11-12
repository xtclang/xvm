package org.xvm.lsp.adapter.xdk

import org.slf4j.LoggerFactory
import org.xvm.asm.Constants.ECSTASY_MODULE
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A workspace's own module replaces the bundled library of its name: a module that declares `json.xtclang.org` is
 * analyzed from the workspace's sources, and every module that imports json is compiled against it, as the compiler's
 * library repository puts the server's own modules ahead of the bundled libraries. The XVM repository is such a
 * workspace, whose `lib_*` folders replace every bundled library; a project with its own copy of one is another. A
 * replaced `ecstasy.xtclang.org` is a dependency of every other module, which imports it without saying so. The
 * bootstrap modules always stay bundled: ecstasy compiles against mack's binary, and _native's declarations must match
 * the runtime that the server carries.
 *
 * `-Dxtc.lsp.replaceLibraries=false` keeps every library bundled and leaves such modules out, as a heap too small to
 * compile ecstasy from source does in a workspace that has ecstasy's sources. Embedded builds and runs do not run in a
 * workspace that replaces a library: the server's runtime and libraries are the bundled XDK's, not the workspace's.
 */
internal object XdkLibraryReplacement {
    const val PROPERTY = "xtc.lsp.replaceLibraries"

    /** Why a workspace that replaces bundled libraries has no embedded builds or runs. */
    const val NO_EMBEDDED_RUNS =
        "This workspace's own modules replace bundled libraries: the server builds and runs with the bundled XDK's " +
            "runtime and libraries, not these, so build and run them with Gradle (Ecstasy build mode: Gradle)"

    /** The bundled modules that a workspace's sources never replace. */
    val BOOTSTRAP = setOf("mack.xtclang.org", "_native.xtclang.org")

    private val logger = LoggerFactory.getLogger(XdkLibraryReplacement::class.java)
    private val smallHeapLogged = AtomicBoolean()

    /** Whether a source module named [name] replaces a bundled library. */
    fun replaces(name: String): Boolean = name in XdkLibraries.moduleNames && name !in BOOTSTRAP

    /**
     * [modules] under the rule: those that replace a bundled library stay, and each other module depends on a
     * replaced ecstasy; or, with replacement off, those modules and the dependencies on them are left out.
     */
    fun apply(modules: List<XdkSourceModule>): List<XdkSourceModule> {
        val replacing = modules.filter { replaces(it.name) }.map { it.name }.toSet()
        return when {
            replacing.isEmpty() -> modules

            !enabled(replacing) ->
                modules.filter { it.name !in replacing }.map { it.withDependencies(it.dependencies - replacing) }

            ECSTASY_MODULE !in replacing -> modules

            else -> modules.map { if (it.name == ECSTASY_MODULE) it else it.withDependencies(it.dependencies + ECSTASY_MODULE) }
        }
    }

    private fun XdkSourceModule.withDependencies(names: Set<String>) =
        if (names == dependencies) this else XdkSourceModule(name, uri, names, resourceRoots)

    private fun enabled(names: Collection<String>): Boolean {
        if (System.getProperty(PROPERTY)?.toBooleanStrictOrNull() == false) return false
        if (ECSTASY_MODULE !in names || XdkLibrarySources.fitsLibraries()) return true
        if (smallHeapLogged.compareAndSet(false, true)) {
            logger.warn(
                "The workspace has ecstasy's sources, but compiling ecstasy from them needs a maximum heap of 1 GB, and " +
                    "this server has {} MB: its libraries stay bundled",
                Runtime.getRuntime().maxMemory() shr 20,
            )
        }
        return false
    }
}
