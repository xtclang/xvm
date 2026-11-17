package org.xvm.lsp.adapter.xdk

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.FileStructure
import org.xvm.compiler.BuildRepository
import org.xvm.lsp.util.ExecutionTrace
import org.xvm.lsp.util.SessionLock
import java.io.File
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Properties
import java.util.Set.copyOf as immutableSet

/** The compiler and its matching libraries travel together in the language server. */
internal object XdkLibraries {
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

    /** One revision for the whole bundle: equal exactly when every bundled module is byte for byte the same. */
    internal val fingerprint: String by lazy {
        val revisions =
            bundle.artifacts.entries
                .sortedBy { it.key }
                .joinToString("\n") { (name, artifact) -> "$name=${artifact.revision}" }
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(revisions.toByteArray()))
    }

    /**
     * A directory with the bundled libraries' files, for a launcher in a process of its own, such as `xtc test`'s for
     * an embedded test run, whose module path takes directories: written once for the bundle, under the temporary
     * directory, by its fingerprint, and complete once its `.complete` file is there.
     */
    internal val directory: File get() = shared.directory

    /**
     * [directory], which every server of the bundle shares and holds a shared [SessionLock] on, [SessionLock.beside]
     * it, while it runs; it stays for the next one. Making it, a server deletes those of other bundles that no process holds ([SessionLock.unheld]),
     * which servers of other builds left.
     */
    private val shared by lazy {
        val temporary = Path.of(System.getProperty("java.io.tmpdir"))
        val dir = temporary.resolve("$PREFIX${fingerprint.take(16)}").toFile()
        SessionLock.guard(temporary.resolve(".${PREFIX}lock")).use { guard ->
            if (!File(dir, COMPLETE).isFile) {
                val staging = Files.createTempDirectory(temporary, "${dir.name}.").toFile()
                packagedResources.forEach { name -> resource(name).use { input -> File(staging, name).outputStream().use(input::copyTo) } }
                File(staging, COMPLETE).writeText(fingerprint)
                // another server may have written it meanwhile, or left it incomplete
                if (!File(dir, COMPLETE).isFile) dir.deleteRecursively()
                if (!staging.renameTo(dir)) staging.deleteRecursively()
            }
            val lock = SessionLock.share(SessionLock.beside(dir.toPath()))
            if (guard != null) {
                SessionLock.unheld(dir.toPath(), "$PREFIX*").forEach {
                    it.toFile().deleteRecursively()
                    Files.deleteIfExists(SessionLock.beside(it))
                }
            }
            Shared(dir, lock)
        }
    }

    /** The bundle's directory, and this process's shared hold on it, which the reference keeps while the process runs. */
    private class Shared(
        val directory: File,
        val lock: FileLock?,
    )

    /** The prefix of each bundle's directory in the temporary directory. */
    private const val PREFIX = "xtc-lsp-xdk-"

    /** The file that marks [directory] complete. */
    private const val COMPLETE = ".complete"

    /** Bundled compiler repository and the immutable artifacts used to reopen its modules. */
    private data class Bundle(
        val repository: BuildRepository,
        val artifacts: Map<String, Artifact>,
    )

    /** Library artifact revision with lazily materialized detached symbols. */
    private data class Artifact(
        val revision: String,
        val symbols: Lazy<XdkArtifactSymbols>,
    )

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
