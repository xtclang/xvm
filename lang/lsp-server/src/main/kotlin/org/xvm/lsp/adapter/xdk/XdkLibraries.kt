package org.xvm.lsp.adapter.xdk

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.FileStructure
import org.xvm.compiler.BuildRepository
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Properties
import java.util.Set.copyOf as immutableSet

/** The compiler and its matching libraries travel together in the language server. */
internal object XdkLibraries {
    private data class Bundle(
        val repository: BuildRepository,
        val revisions: Map<String, String>,
    )

    private val bundle by lazy {
        val index = Properties().apply { resource("modules.properties").use { load(it) } }
        val names = checkNotNull(index.getProperty("modules")) { "Bundled XDK module index is missing" }
        val repository = BuildRepository()
        val revisions =
            names.split(',').associate { name ->
                val bytes = resource(name).use { it.readBytes() }
                val module = FileStructure(bytes.inputStream()).module
                repository.storeModule(module)
                module.name to HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
            }
        for (module in listOf("ecstasy.xtclang.org", "mack.xtclang.org", "_native.xtclang.org")) {
            checkNotNull(repository.loadModule(module)) { "Bundled XDK is missing $module" }
        }
        Bundle(repository, revisions)
    }

    /** Every bundled library is reserved; workspace discovery must not turn it into editable source. */
    val moduleNames: Set<String> by lazy { immutableSet(bundle.repository.moduleNames) }

    private val configured by lazy { EmbeddingSupport.instance().configure(bundle.repository, null) }

    internal fun module(name: String) = bundle.repository.loadModule(name)

    internal fun revision(name: String): String = bundle.revisions.getValue(name)

    fun configure() {
        configured
    }

    private fun resource(name: String) =
        checkNotNull(XdkLibraries::class.java.getResourceAsStream("/org/xvm/lsp/xdk/$name")) {
            "Bundled XDK resource is missing: $name"
        }
}
