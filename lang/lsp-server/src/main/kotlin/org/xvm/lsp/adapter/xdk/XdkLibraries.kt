package org.xvm.lsp.adapter.xdk

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.FileStructure
import org.xvm.compiler.BuildRepository
import org.xvm.lsp.util.ExecutionTrace
import org.xvm.lsp.util.SessionLock
import org.xvm.lsp.util.UserTemporary
import java.io.File
import java.io.IOException
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
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
        val revisions =
            names.split(',').associate { name ->
                val bytes = resource(name).use { it.readBytes() }
                val module =
                    ExecutionTrace.api("FileStructure.read(bundled-library)", name) {
                        FileStructure(bytes.inputStream()).module
                    }
                repository.storeModule(module)
                module.name to revision(bytes)
            }
        for (module in listOf("ecstasy.xtclang.org", "mack.xtclang.org", "_native.xtclang.org")) {
            checkNotNull(repository.loadModule(module)) { "Bundled XDK is missing $module" }
        }
        Bundle(repository, revisions)
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
    internal val fingerprint: String by lazy { combined(bundle.revisions) }

    /**
     * The version of the bundled XDK, which is the server's own, as the server is built with the libraries it bundles;
     * null where the server's build did not say.
     */
    internal val version: String? by lazy {
        val info = XdkLibraries::class.java.getResourceAsStream("/lsp-version.properties") ?: return@lazy null
        info.use { Properties().apply { load(it) } }.getProperty("lsp.version")
    }

    /**
     * The revision of each bundled module's file, as [revision] has it, by the file's name: read from the bundle's files
     * alone, without the modules in them.
     */
    private val files: Map<String, String> by lazy {
        packagedResources.associateWith { name -> resource(name).use { revision(it.readBytes()) } }
    }

    /**
     * The revisions of the bundled modules' files ([files]), which a comparison with a Gradle build's XDK needs no more
     * of.
     */
    internal val revisions: Set<String> by lazy { immutableSet(files.values) }

    /**
     * The revision of the javatools jar that the server bundles, its compiler and runtime, as its build recorded it: over
     * the jar's files, with its build information taken without the commit and branch it was built at, which change with
     * every commit, as the XTC plugin's export gives a Gradle build's XDK's.
     */
    internal val javatools: String? by lazy {
        resource("modules.properties").use { Properties().apply { load(it) } }.getProperty("javatools")?.takeIf { it.isNotBlank() }
    }

    /**
     * Whether a Gradle build's XDK, whose module files have the SHA-256 [modules] and whose `javatools.jar` has the
     * revision [javatools], is the bundled one: the same module files, byte for byte, and the same compiler and runtime,
     * which a version cannot tell, as every build of a snapshot has the same. A module file or a javatools outside the
     * bundle makes it another, whatever the modules' names.
     */
    internal fun bundles(
        modules: Collection<String>,
        javatools: String?,
    ): Boolean = this.javatools != null && javatools == this.javatools && modules.toSet() == revisions

    /** The SHA-256 of a module's file, [bytes], in hex: the module's revision. */
    private fun revision(bytes: ByteArray): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    /** One revision for the modules whose [revisions] these are, by module name, in name order. */
    private fun combined(revisions: Map<String, String>): String =
        revisions.entries
            .sortedBy { it.key }
            .joinToString("\n") { (name, revision) -> "$name=$revision" }
            .let { HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }

    /**
     * A directory with the bundled libraries' files, for a launcher in a process of its own, such as `xtc test`'s for
     * an embedded test run, whose module path takes directories: [bundled] in this user's temporary root
     * ([UserTemporary]), checked again at each use, as at each launch.
     */
    internal val directory: File get() = bundled(UserTemporary.current).toFile()

    /**
     * This process's shared [SessionLock] on each bundle's directory that it used, [SessionLock.beside] it, which the
     * references keep while the process runs.
     */
    private val shares = ConcurrentHashMap<Path, FileLock>()

    /**
     * The bundle's directory in [temporary]'s root, taken once for the call, as [written] has it, which every server of
     * the bundle shares and holds a shared [SessionLock] on while it runs; it stays for the next one. Its lock is taken
     * again where its file is gone, as a cleaner of old files in the temporary directory deletes it. A server deletes
     * the directories of other bundles that no process holds ([SessionLock.unheld]), which servers of other builds left.
     */
    internal fun bundled(temporary: UserTemporary): Path {
        val root = temporary.root()
        return SessionLock.guard(root.resolve(".${PREFIX}lock")) { guarded ->
            val dir = written(temporary, root)
            val lock = SessionLock.beside(dir)
            if (!shares.containsKey(dir) || !Files.isRegularFile(lock, NOFOLLOW_LINKS)) SessionLock.share(lock)?.let { shares[dir] = it }
            if (guarded) {
                SessionLock.unheld(dir, "$PREFIX*").forEach {
                    temporary.delete(it)
                    temporary.delete(SessionLock.beside(it))
                }
            }
            dir
        }
    }

    /**
     * The bundle's directory in [root], [temporary]'s, by its fingerprint, with the bundle's files and nothing else, as
     * their revisions tell ([holds]): `xtc test` loads every module in it, so one with other files or other content, as
     * a server that was killed while it wrote it leaves, or a cleaner of old files, is written again rather than used.
     */
    private fun written(
        temporary: UserTemporary,
        root: Path,
    ): Path {
        val dir = root.resolve("$PREFIX${fingerprint.take(16)}")
        if (!holds(dir)) {
            val staging = Files.createTempDirectory(root, "${dir.fileName}.")
            files.keys.forEach { name -> resource(name).use { Files.copy(it, staging.resolve(name)) } }
            // another server may have written it meanwhile, where the guard is no lock
            if (!holds(dir)) temporary.delete(dir)
            try {
                Files.move(staging, dir)
            } catch (_: IOException) {
                temporary.delete(staging)
            }
        }
        check(holds(dir)) { "Cannot write the bundled libraries to $dir" }
        return dir
    }

    /** Whether [dir] is a directory with the bundle's files alone, each a file, not a link, of the bundle's revision. */
    private fun holds(dir: Path): Boolean {
        if (!Files.isDirectory(dir, NOFOLLOW_LINKS)) return false
        val entries = Files.list(dir).use { it.toList() }
        return entries.map { it.fileName.toString() }.toSet() == files.keys &&
            entries.all { Files.isRegularFile(it, NOFOLLOW_LINKS) && revision(Files.readAllBytes(it)) == files[it.fileName.toString()] }
    }

    /** The prefix of each bundle's directory in this user's temporary root. */
    private const val PREFIX = "xtc-lsp-xdk-"

    /** Bundled compiler repository and the SHA-256 revision of each bundled module's file, by module name. */
    private data class Bundle(
        val repository: BuildRepository,
        val revisions: Map<String, String>,
    )

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
