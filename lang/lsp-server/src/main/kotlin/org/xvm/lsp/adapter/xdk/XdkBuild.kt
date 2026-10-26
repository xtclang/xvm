package org.xvm.lsp.adapter.xdk

import com.google.gson.GsonBuilder
import org.xvm.lsp.model.Diagnostic
import java.io.File
import java.security.MessageDigest
import java.util.HexFormat

/** One source module's outcome in an embedded build. */
data class XdkBuiltModule(
    val name: String,
    val uri: String,
    val succeeded: Boolean,
    /** A dependency failed, so this module was not compiled. */
    val skipped: Boolean,
    /** The written .xtc file, when the module compiled. */
    val artifact: String?,
    /** An earlier compile of exactly these sources and dependency revisions supplied the artifact. */
    val reused: Boolean = false,
    /** SHA-256 identity of the written artifact. */
    val revision: String? = null,
)

/**
 * Outcome of compiling source modules to disk. [modulePath] lists the directories that together
 * hold the built modules and their binary dependencies, which is what a run needs, and
 * [workingDirectory] is the workspace folder that owns them. [compiled] is the text of each source
 * file of the built modules exactly as compiled, by its canonical file, which a debugger matches to
 * the editor's text once the file is edited.
 */
data class XdkBuildResult(
    val succeeded: Boolean,
    val cancelled: Boolean,
    val modules: List<XdkBuiltModule>,
    val diagnostics: List<Diagnostic>,
    val modulePath: List<String>,
    val workingDirectory: String,
    val compiled: Map<File, String> = emptyMap(),
)

/** What an embedded build keeps of one module from the closure compile. */
internal class XdkBuildModule(
    val module: XdkSourceModule,
    /** Present only when the module compiled. */
    val artifact: XdkDependency?,
    val diagnostics: List<Diagnostic>,
    val skipped: Boolean,
    val reused: Boolean,
    /** SHA-256 of each source document's text, by URI, exactly as compiled. */
    val sources: Map<String, String>,
    /** Each source file's text, by its canonical file, exactly as compiled. */
    val texts: Map<File, String>,
    /** Revision of every module this one was compiled against, by name. */
    val dependencies: Map<String, String>,
)

/**
 * Embedded build output: writes what one closure compile of a captured source snapshot produced into
 * `build/xtc/embedded/lib` under the owning workspace folder, the binary dependencies it compiled against
 * into `build/xtc/embedded/dependencies`, and a source manifest into `build/xtc/embedded/manifest.json`.
 * These are kept apart from Gradle's outputs. A module that did not compile has no artifact here, so an
 * earlier one is never run in its place.
 */
internal object XdkBuild {
    fun write(
        modules: List<XdkBuildModule>,
        binaries: XdkDependencies,
        folder: File,
    ): XdkBuildResult {
        val root = File(folder, "build/xtc/embedded")
        val lib = File(root, "lib").apply { mkdirs() }
        val dependencies = File(root, "dependencies").apply { mkdirs() }
        binaries.modules.values.forEach {
            File(dependencies, artifactName(it.module)).writeBytes(it.bytes())
        }
        val built =
            modules.map { outcome ->
                val file = File(lib, artifactName(outcome.module.name))
                outcome.artifact?.let { file.writeBytes(it.bytes()) } ?: file.delete()
                XdkBuiltModule(
                    outcome.module.name,
                    outcome.module.uri,
                    succeeded = outcome.artifact != null,
                    skipped = outcome.skipped,
                    artifact = file.path.takeIf { outcome.artifact != null },
                    reused = outcome.reused,
                    revision = outcome.artifact?.revision,
                )
            }
        File(root, "manifest.json").writeText(manifest(modules, built))
        return XdkBuildResult(
            succeeded = modules.all { it.artifact != null },
            cancelled = false,
            modules = built,
            diagnostics = modules.flatMap { it.diagnostics },
            modulePath = listOf(lib.path, dependencies.path),
            workingDirectory = folder.path,
            compiled = modules.flatMap { it.texts.entries }.associate { it.key to it.value },
        )
    }

    /** A cancelled build writes nothing. */
    fun cancelled(folder: File): XdkBuildResult {
        val root = File(folder, "build/xtc/embedded")
        return XdkBuildResult(
            succeeded = false,
            cancelled = true,
            modules = emptyList(),
            diagnostics = emptyList(),
            modulePath = listOf(File(root, "lib").path, File(root, "dependencies").path),
            workingDirectory = folder.path,
        )
    }

    /** A build that cannot start, e.g. because the source graph is misconfigured, reports why. */
    fun failed(
        folder: File,
        diagnostic: Diagnostic,
    ): XdkBuildResult = cancelled(folder).copy(cancelled = false, diagnostics = listOf(diagnostic))

    fun sha256(text: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))

    private fun artifactName(module: String) = module.substringBefore('.') + ".xtc"

    private fun manifest(
        modules: List<XdkBuildModule>,
        built: List<XdkBuiltModule>,
    ): String =
        GsonBuilder().setPrettyPrinting().create().toJson(
            mapOf(
                "modules" to
                    modules.zip(built).map { (outcome, module) ->
                        linkedMapOf(
                            "name" to module.name,
                            "uri" to module.uri,
                            "succeeded" to module.succeeded,
                            "reused" to module.reused,
                            "revision" to module.revision,
                            "sources" to outcome.sources.toSortedMap(),
                            "dependencies" to outcome.dependencies.toSortedMap(),
                        )
                    },
            ),
        )
}

/** A source module with an entry method the runner can call, proven from its compiled artifact. */
data class XdkRunnableTarget(
    val module: String,
    val uri: String,
    val method: String,
)
