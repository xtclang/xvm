package org.xvm.lsp.server

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.xvm.lsp.adapter.xdk.XdkDependency
import org.xvm.lsp.adapter.xdk.XdkLibraries
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
        fun read(
            raw: Any?,
            workspaceUris: List<String>,
        ): CompilerLibraries? {
            val config = Gson().toJsonTree(raw)?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            val value = config["libraries"]?.takeUnless { it.isJsonNull } ?: return null
            require(value.isJsonObject) {
                "libraries must be an object"
            }
            val settings = value.asJsonObject
            require(settings.keySet().all { it in setOf("modulePath", "sourceAttachments") }) {
                "Unknown library setting"
            }

            fun paths(values: JsonElement): List<File> {
                require(values.isJsonArray) {
                    "Library paths must be an array"
                }
                val paths =
                    values.asJsonArray.map { element ->
                        require(
                            element.isJsonPrimitive && element.asJsonPrimitive.isString &&
                                element.asString.isNotBlank(),
                        ) {
                            "Library paths must be non-blank strings"
                        }
                        val path = URI(element.asString)
                        val resolved =
                            if (path.isAbsolute) {
                                path
                            } else {
                                require(workspaceUris.size == 1) {
                                    "Relative library paths require exactly one workspace folder"
                                }
                                URI(workspaceUris.single().trimEnd('/') + "/").resolve(path)
                            }
                        require(resolved.scheme == "file" && resolved.query == null && resolved.fragment == null) {
                            "Library paths must use local file URIs"
                        }
                        File(resolved).canonicalFile
                    }
                require(paths.distinct().size == paths.size) {
                    "Duplicate library path"
                }
                return paths
            }
            val modules = settings["modulePath"]?.takeUnless { it.isJsonNull }?.let(::paths)
            val attachments =
                settings["sourceAttachments"]
                    ?.let { entries ->
                        require(entries.isJsonArray) {
                            "sourceAttachments must be an array"
                        }
                        entries.asJsonArray
                            .map { entry ->
                                require(entry.isJsonObject) {
                                    "Each source attachment must be an object"
                                }
                                val item: JsonObject = entry.asJsonObject
                                val name = item["module"]
                                require(
                                    name?.isJsonPrimitive == true && name.asJsonPrimitive.isString &&
                                        name.asString.isNotBlank(),
                                ) {
                                    "Source attachment needs a module name"
                                }
                                val roots = paths(requireNotNull(item["roots"]) { "Source attachment needs ordered roots" })
                                require(roots.isNotEmpty() && roots.all(File::isDirectory)) {
                                    "Attachment roots must be existing source directories"
                                }
                                name.asString to roots
                            }.also { values ->
                                require(values.map { it.first }.distinct().size == values.size) {
                                    "Duplicate source attachment module"
                                }
                            }.toMap()
                    }.orEmpty()
            return CompilerLibraries(modules, attachments)
        }
    }
}
