package org.xvm.lsp.adapter.xdk

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.DirRepository
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.asm.LinkedRepository
import org.xvm.compiler.Source
import org.xvm.lsp.model.Diagnostic
import java.io.File

/** One source module's outcome in an embedded build. */
data class XdkBuiltModule(
    val name: String,
    val uri: String,
    val succeeded: Boolean,
    /** A dependency failed, so this module was not compiled. */
    val skipped: Boolean,
    /** The written .xtc file, when the module compiled. */
    val artifact: String?,
)

/**
 * Outcome of compiling source modules to disk. [modulePath] lists the directories that together
 * hold the built modules and their binary dependencies, which is what a run needs, and
 * [workingDirectory] is the workspace folder that owns them.
 */
data class XdkBuildResult(
    val succeeded: Boolean,
    val cancelled: Boolean,
    val modules: List<XdkBuiltModule>,
    val diagnostics: List<Diagnostic>,
    val modulePath: List<String>,
    val workingDirectory: String,
)

/**
 * Embedded build: compiles source modules in dependency order into `build/xtc/embedded/lib` under
 * the owning workspace folder, and writes the binary dependencies they compile against into
 * `build/xtc/embedded/dependencies`. These are kept apart from Gradle's outputs. Runs only on the
 * serialized compiler worker, because the embedded compiler is shared.
 */
internal object XdkBuild {
    private const val MAX_ERRORS = 500

    fun build(
        order: List<XdkSourceModule>,
        dependencies: XdkDependencies,
        folder: File,
        rebuild: Boolean,
        cancelled: () -> Boolean,
    ): XdkBuildResult {
        val root = File(folder, "build/xtc/embedded")
        val lib = File(root, "lib").apply { mkdirs() }
        val binaries = File(root, "dependencies").apply { mkdirs() }
        dependencies.modules.values.forEach {
            File(binaries, it.module.substringBefore('.') + ".xtc").writeBytes(it.bytes())
        }
        if (rebuild) {
            order.forEach { File(lib, it.name.substringBefore('.') + ".xtc").delete() }
        }

        val output = DirRepository(lib, false)
        val input = LinkedRepository(output, DirRepository(binaries, true))
        val failed = mutableSetOf<String>()
        val modules = mutableListOf<XdkBuiltModule>()
        val diagnostics = mutableListOf<Diagnostic>()
        for (module in order) {
            if (cancelled()) {
                return XdkBuildResult(false, true, modules, diagnostics, listOf(lib.path, binaries.path), folder.path)
            }
            if (module.dependencies.any(failed::contains)) {
                failed += module.name
                modules += XdkBuiltModule(module.name, module.uri, succeeded = false, skipped = true, artifact = null)
                continue
            }

            val errors = ErrorList(MAX_ERRORS)
            val compiled =
                EmbeddingSupport.instance().compile(
                    module.root,
                    input,
                    output,
                    ErrorListener.cancellable(errors, cancelled),
                )
            val source = Source(module.root.readText(), module.uri)
            errors.errors.mapTo(diagnostics) { it.toDiagnostic(source, emptyMap(), emptyMap()) }
            if (!compiled) failed += module.name
            modules +=
                XdkBuiltModule(
                    module.name,
                    module.uri,
                    succeeded = compiled,
                    skipped = false,
                    artifact = File(lib, module.name.substringBefore('.') + ".xtc").takeIf { compiled }?.path,
                )
        }
        return XdkBuildResult(failed.isEmpty(), false, modules, diagnostics, listOf(lib.path, binaries.path), folder.path)
    }
}

/** A source module with an entry method the runner can call, proven from its compiled artifact. */
data class XdkRunnableTarget(
    val module: String,
    val uri: String,
    val method: String,
)
