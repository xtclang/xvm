package org.xvm.lsp.server

import com.google.gson.Gson
import org.xvm.lsp.adapter.xdk.XdkDependency
import org.xvm.lsp.adapter.xdk.XdkLibraries
import org.xvm.lsp.protocol.LibrarySettings
import java.io.File
import java.io.IOException
import java.net.URI

/** Ordered external binaries and navigation-only sources; bundled modules cannot be replaced. */
internal data class CompilerLibraries(
    val modulePath: List<File>?,
    val sourceAttachments: Map<String, List<File>>,
) {
    fun resolve(inherited: List<XdkDependency>): List<XdkDependency> =
        try {
            resolveInputs(inherited)
        } catch (failure: IOException) {
            throw IllegalArgumentException("Cannot read library inputs: ${failure.message}", failure)
        }

    private fun resolveInputs(inherited: List<XdkDependency>): List<XdkDependency> {
        val binaries =
            modulePath
                ?.flatMap { root ->
                    require(root.exists()) {
                        "Library path does not exist: $root"
                    }
                    if (root.isDirectory) {
                        root
                            .listFiles()
                            .orEmpty()
                            .filter { it.isFile && it.extension == "xtc" }
                            .sortedBy(File::getName)
                    } else {
                        require(root.isFile && root.extension == "xtc") {
                            "Library path must be a directory or .xtc file: $root"
                        }
                        listOf(root)
                    }
                }?.map { file ->
                    try {
                        XdkDependency.fromBinary(file.readBytes())
                    } catch (
                        failure: Exception,
                    ) {
                        throw IllegalArgumentException("Cannot read library $file: ${failure.message}", failure)
                    }
                }?.also { values ->
                    require(values.none { it.module in XdkLibraries.moduleNames }) {
                        "Library overrides cannot replace the bundled XDK"
                    }
                }?.distinctBy { it.module } ?: inherited
        require(sourceAttachments.keys.all { name -> binaries.any { it.module == name } }) {
            "Every source attachment must name an effective external binary module"
        }
        return binaries.map { binary -> sourceAttachments[binary.module]?.let(binary::withSources) ?: binary }
    }

    companion object {
        /**
         * The library settings of [raw], the compiler's configuration section, or null for none: their paths, relative
         * ones against the single folder of [workspaceUris] ([LibrarySettings.files]), with each attachment's roots
         * existing source directories.
         */
        fun read(
            raw: Any?,
            workspaceUris: List<String>,
        ): CompilerLibraries? {
            val config = Gson().toJsonTree(raw)?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            val files = LibrarySettings.read(config[LibrarySettings.KEY])?.files(workspaceUris.singleOrNull()?.let(::URI)) ?: return null
            require(files.sourceAttachments.values.all { roots -> roots.all(File::isDirectory) }) {
                "Attachment roots must be existing source directories"
            }
            return CompilerLibraries(files.modulePath, files.sourceAttachments)
        }
    }
}
