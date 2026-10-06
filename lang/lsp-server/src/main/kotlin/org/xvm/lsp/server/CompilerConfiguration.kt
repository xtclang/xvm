package org.xvm.lsp.server

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import org.xvm.lsp.adapter.xdk.XdkBuildModel
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.net.URI

/**
 * Strict editor configuration; absent values preserve the host graph and an empty list clears it.
 */
internal object CompilerConfiguration {
    const val SECTION = "xtc.compiler"
    const val INITIALIZATION_KEY = "xtcCompiler"
    private val gson = GsonBuilder().serializeNulls().create()

    fun initial(options: Any?): JsonElement? = objectValue(options)?.get(INITIALIZATION_KEY)

    fun changed(settings: Any?): JsonElement? = objectValue(objectValue(settings)?.get("xtc"))?.get("compiler")

    /** Explicit presentation notifications do not request or replace the compiler graph. */
    fun presentationOnly(settings: Any?): Boolean =
        objectValue(objectValue(settings)?.get("xtc"))?.keySet()?.let { sections ->
            sections.isNotEmpty() &&
                sections.all { it in setOf("formatting", "presentation", "languageService") }
        } == true

    fun automatic(raw: Any?): Boolean = objectValue(raw)?.get("sourceModules")?.isJsonNull == true

    fun buildModel(
        raw: Any?,
        includeExplicit: Boolean = false,
    ): XdkBuildModel? {
        val config = objectValue(raw) ?: return null
        if (!includeExplicit && config["sourceModules"]?.let { !it.isJsonNull } == true) return null
        val models = config["buildModels"]?.takeUnless { it.isJsonNull } ?: return null
        require(models.isJsonArray) { "buildModels must be an array" }
        return models.asJsonArray
            .takeIf { !it.isEmpty }
            ?.let {
                XdkBuildModel.read(
                    it.map { model ->
                        require(model.isJsonObject) { "Build models must be objects" }
                        model.asJsonObject
                    },
                )
            }
    }

    fun modules(
        raw: Any?,
        workspaceUris: List<String>,
    ): List<XdkSourceModule>? {
        val config = objectValue(raw) ?: return null
        val entries = config.get("sourceModules") ?: return null
        if (entries.isJsonNull) return null
        require(entries.isJsonArray) { "sourceModules must be an array" }
        return entries.asJsonArray.map { entry ->
            require(entry.isJsonObject) { "Each source module must be an object" }
            val module = entry.asJsonObject
            val name = string(module.get("name"), "name")

            fun resolve(value: String): String {
                val path = URI.create(value)
                val resolved =
                    if (path.isAbsolute) {
                        path
                    } else {
                        require(workspaceUris.size == 1) {
                            "Relative source/resource URIs require exactly one workspace folder; use file URIs otherwise"
                        }
                        URI.create(workspaceUris.single().trimEnd('/') + "/").resolve(path)
                    }
                require(resolved.scheme == "file") {
                    "Source and resource roots must use file URIs"
                }
                return resolved.toString()
            }
            val uri = resolve(string(module.get("uri"), "uri"))
            val resources =
                module
                    .get("resourceRoots")
                    ?.takeUnless { it.isJsonNull }
                    ?.let { values ->
                        require(values.isJsonArray) {
                            "resourceRoots must be an array of file URIs"
                        }
                        values.asJsonArray.map { resolve(string(it, "resource root")) }
                    }
            val dependencies =
                module
                    .get("dependencies")
                    ?.let { values ->
                        require(values.isJsonArray) {
                            "dependencies must be an array of module names"
                        }
                        values.asJsonArray.map { string(it, "dependency") }.toSet()
                    }.orEmpty()
            XdkSourceModule(name, uri, dependencies, resources)
        }
    }

    private fun objectValue(raw: Any?) =
        gson
            .toJsonTree(raw)
            ?.takeUnless { it.isJsonNull }
            ?.let {
                require(it.isJsonObject) { "Compiler configuration must be an object" }
                it.asJsonObject
            }

    private fun string(
        value: JsonElement?,
        field: String,
    ): String {
        require(
            value != null &&
                value.isJsonPrimitive &&
                value.asJsonPrimitive.isString &&
                value.asString.isNotBlank(),
        ) {
            "$field must be a non-blank string"
        }
        return value.asString
    }
}
