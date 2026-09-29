package org.xvm.lsp.server

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import java.net.URI
import org.xvm.lsp.adapter.xdk.XdkSourceModule

/**
 * Strict editor configuration; absent values preserve the host graph and an empty list clears it.
 */
internal object CompilerConfiguration {
    const val SECTION = "xtc.compiler"
    const val INITIALIZATION_KEY = "xtcCompiler"
    private val gson = GsonBuilder().serializeNulls().create()

    fun initial(options: Any?): JsonElement? = objectValue(options)?.get(INITIALIZATION_KEY)

    fun changed(settings: Any?): JsonElement? =
        objectValue(objectValue(settings)?.get("xtc"))?.get("compiler")

    fun automatic(raw: Any?): Boolean = objectValue(raw)?.get("sourceModules")?.isJsonNull == true

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
                    if (path.isAbsolute) path
                    else {
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
                    }
                    .orEmpty()
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
                value.asString.isNotBlank()
        ) {
            "$field must be a non-blank string"
        }
        return value.asString
    }
}
