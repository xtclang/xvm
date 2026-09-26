package org.xvm.lsp.adapter.xdk

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.FileStructure
import org.xvm.compiler.BuildRepository
import java.util.Properties
import java.util.Set.copyOf as immutableSet

/** The compiler and its matching libraries travel together in the language server. */
internal object XdkLibraries {
    private val repository by lazy {
        val index = Properties().apply { resource("modules.properties").use { load(it) } }
        val names = checkNotNull(index.getProperty("modules")) { "Bundled XDK module index is missing" }
        val repository = BuildRepository()
        for (name in names.split(',')) {
            resource(name).use { repository.storeModule(FileStructure(it).module) }
        }
        for (module in listOf("ecstasy.xtclang.org", "mack.xtclang.org", "_native.xtclang.org")) {
            checkNotNull(repository.loadModule(module)) { "Bundled XDK is missing $module" }
        }
        repository
    }

    /** Every bundled library is reserved; workspace discovery must not turn it into editable source. */
    val moduleNames: Set<String> by lazy { immutableSet(repository.moduleNames) }

    private val configured by lazy { EmbeddingSupport.instance().configure(repository, null) }

    fun configure() {
        configured
    }

    private fun resource(name: String) =
        checkNotNull(XdkLibraries::class.java.getResourceAsStream("/org/xvm/lsp/xdk/$name")) {
            "Bundled XDK resource is missing: $name"
        }
}
