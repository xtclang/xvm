package org.xvm.lsp.server

import com.google.gson.Gson
import com.google.gson.JsonElement
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.net.URI

/** Strict editor configuration; absent values preserve the host graph and an empty list clears it. */
internal object CompilerConfiguration {
    const val SECTION = "xtc.compiler"
    const val INITIALIZATION_KEY = "xtcCompiler"
    private val gson = Gson()

    fun initial(options: Any?): JsonElement? = objectValue(options)?.get(INITIALIZATION_KEY)

    fun changed(settings: Any?): JsonElement? = objectValue(objectValue(settings)?.get("xtc"))?.get("compiler")

    fun modules(
        raw: Any?,
        workspaceUris: List<String>,
    ): List<XdkSourceModule>? {
        val config = objectValue(raw) ?: return null
        val entries = config.get("sourceModules") ?: return null
        require(entries.isJsonArray) { "sourceModules must be an array" }
        return entries.asJsonArray.map { entry ->
            require(entry.isJsonObject) { "Each source module must be an object" }
            val module = entry.asJsonObject
            val name = string(module.get("name"), "name")
            val root = URI.create(string(module.get("uri"), "uri"))
            val uri =
                if (root.isAbsolute) {
                    root
                } else {
                    require(
                        workspaceUris.size == 1,
                    ) { "Relative source URIs require exactly one workspace folder; use file URIs otherwise" }
                    URI.create(workspaceUris.single().trimEnd('/') + "/").resolve(root)
                }
            require(uri.scheme == "file") { "Source module roots must use file URIs" }
            val dependencies =
                module
                    .get("dependencies")
                    ?.let { values ->
                        require(values.isJsonArray) { "dependencies must be an array of module names" }
                        values.asJsonArray.map { string(it, "dependency") }.toSet()
                    }.orEmpty()
            XdkSourceModule(name, uri.toString(), dependencies)
        }
    }

    private fun objectValue(raw: Any?) =
        gson.toJsonTree(raw)?.takeUnless { it.isJsonNull }?.let {
            require(it.isJsonObject) { "Compiler configuration must be an object" }
            it.asJsonObject
        }

    private fun string(
        value: JsonElement?,
        field: String,
    ): String {
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString && value.asString.isNotBlank()) {
            "$field must be a non-blank string"
        }
        return value.asString
    }
}
