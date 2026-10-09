package org.xvm.lsp.adapter.xdk

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.FileStructure
import org.xvm.compiler.BuildRepository
import org.xvm.lsp.util.ExecutionTrace
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Properties
import java.util.Set.copyOf as immutableSet

/** The compiler and its matching libraries travel together in the language server. */
internal object XdkLibraries {
    private data class Bundle(
        val repository: BuildRepository,
        val artifacts: Map<String, Artifact>,
    )

    private data class Artifact(
        val revision: String,
        val symbols: Lazy<XdkArtifactSymbols>,
    )

    /**
     * Package metadata can be displayed without constructing compiler constants or repositories.
     */
    val packagedResources: List<String> by lazy {
        Properties()
            .apply { resource("modules.properties").use { load(it) } }
            .getProperty("modules")
            .split(',')
            .sorted()
    }

    private val bundle by lazy {
        val index = Properties().apply { resource("modules.properties").use { load(it) } }
        val names =
            checkNotNull(index.getProperty("modules")) { "Bundled XDK module index is missing" }
        val repository = BuildRepository()
        val artifacts =
            names.split(',').associate { name ->
                val bytes = resource(name).use { it.readBytes() }
                val module =
                    ExecutionTrace.api("FileStructure.read(bundled-library)", name) {
                        FileStructure(bytes.inputStream()).module
                    }
                repository.storeModule(module)
                module.name to
                    Artifact(
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                        lazy { XdkArtifactSymbols.capture(resource(name).use { it.readBytes() }) },
                    )
            }
        for (module in listOf("ecstasy.xtclang.org", "mack.xtclang.org", "_native.xtclang.org")) {
            checkNotNull(repository.loadModule(module)) { "Bundled XDK is missing $module" }
        }
        Bundle(repository, artifacts)
    }

    /**
     * Every bundled library is reserved; workspace discovery must not turn it into editable source.
     */
    val moduleNames: Set<String> by lazy { immutableSet(bundle.repository.moduleNames) }

    private val configured by lazy {
        ExecutionTrace.api("EmbeddingSupport.configure") {
            EmbeddingSupport.instance().configure(bundle.repository, null)
        }
    }

    internal fun module(name: String) = bundle.repository.loadModule(name)

    internal fun revision(name: String): String = bundle.artifacts.getValue(name).revision

    internal fun symbolIndex(name: String): XdkArtifactSymbols? = bundle.artifacts[name]?.symbols?.value

    fun configure() {
        configured
    }

    private fun resource(name: String) =
        checkNotNull(XdkLibraries::class.java.getResourceAsStream("/org/xvm/lsp/xdk/$name")) {
            "Bundled XDK resource is missing: $name"
        }
}
