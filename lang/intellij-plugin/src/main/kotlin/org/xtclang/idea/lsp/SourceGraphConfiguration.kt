package org.xtclang.idea.lsp

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.net.URI

/** Pure settings transformation, shared by initial application, Undo and Redo. */
internal object SourceGraphConfiguration {
    private val gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()

    /** A missing or null graph selects discovery; an empty array is explicitly empty. */
    fun read(content: String?): List<SourceModuleConfiguration>? {
        val settings = parse(content)
        val compiler = child(settings, "xtc")?.let { child(it, "compiler") } ?: return null
        val modules = compiler["sourceModules"]?.takeUnless { it.isJsonNull } ?: return null
        require(modules.isJsonArray) { "Source modules must be an array" }
        return modules.asJsonArray.map { module ->
            val entry = objectValue(module)
            val dependencies = entry["dependencies"]?.takeUnless { it.isJsonNull }
            require(dependencies == null || dependencies.isJsonArray) {
                "Source dependencies must be an array"
            }
            SourceModuleConfiguration(
                stringValue(entry["name"]),
                stringValue(entry["uri"]),
                dependencies?.asJsonArray?.map(::stringValue).orEmpty(),
            )
        }
    }

    /** Preserve unrelated LSP4IJ settings, including artifact and formatting configuration. */
    fun configure(content: String?, modules: List<SourceModuleConfiguration>?, base: URI): String {
        modules?.let {
            it.forEach { module ->
                require(
                    module.name.isNotBlank() &&
                        module.uri.isNotBlank() &&
                        module.dependencies.all(String::isNotBlank)
                ) {
                    "Source module names, roots and dependencies must be non-blank strings"
                }
            }
            canonical(it, base)
        }
        val settings = parse(content)
        val xtc = child(settings, "xtc") ?: JsonObject().also { settings.add("xtc", it) }
        val compiler = child(xtc, "compiler") ?: JsonObject().also { xtc.add("compiler", it) }
        compiler.add("sourceModules", gson.toJsonTree(modules))
        return gson.toJson(settings)
    }

    private fun child(parent: JsonObject, name: String): JsonObject? =
        parent[name]?.takeUnless { it.isJsonNull }?.let(::objectValue)

    private fun parse(content: String?): JsonObject =
        try {
            if (content.isNullOrBlank()) JsonObject()
            else objectValue(JsonParser.parseString(content))
        } catch (failure: JsonParseException) {
            throw IllegalArgumentException("Invalid compiler settings JSON", failure)
        }

    fun replace(
        content: String,
        expected: List<SourceModuleConfiguration>,
        replacement: List<SourceModuleConfiguration>,
        base: URI,
    ): String {
        val settings =
            try {
                objectValue(JsonParser.parseString(content))
            } catch (failure: JsonParseException) {
                throw IllegalArgumentException("Invalid compiler settings JSON", failure)
            }
        val compiler = objectValue(objectValue(settings["xtc"])["compiler"])
        val modules =
            requireNotNull(compiler["sourceModules"]) { "Configured source modules are required" }
        require(modules.isJsonArray) { "Configured source modules must be explicit" }
        val current =
            modules.asJsonArray.map { module ->
                val entry = objectValue(module)
                val dependencies =
                    entry["dependencies"]
                        ?.takeUnless { it.isJsonNull }
                        ?.let {
                            require(it.isJsonArray) { "Source dependencies must be an array" }
                            it.asJsonArray.map(::stringValue)
                        }
                        .orEmpty()
                SourceModuleConfiguration(
                    stringValue(entry["name"]),
                    stringValue(entry["uri"]),
                    dependencies,
                )
            }
        require(canonical(current, base) == canonical(expected, base)) {
            "Compiler source graph changed; rename was not applied"
        }
        compiler.add("sourceModules", gson.toJsonTree(replacement))
        return gson.toJson(settings)
    }

    private fun objectValue(value: JsonElement?): JsonObject {
        require(value?.isJsonObject == true) { "Compiler settings must contain an object" }
        return value.asJsonObject
    }

    private fun stringValue(value: JsonElement?): String {
        require(
            value?.isJsonPrimitive == true &&
                value.asJsonPrimitive.isString &&
                value.asString.isNotBlank()
        ) {
            "Source module names, roots and dependencies must be non-blank strings"
        }
        return value.asString
    }

    private fun canonical(
        modules: List<SourceModuleConfiguration>,
        base: URI,
    ): Set<Triple<String, URI, Set<String>>> {
        require(modules.map { it.name }.distinct().size == modules.size) {
            "Duplicate source module names"
        }
        val result = modules.map {
            Triple(it.name, base.resolve(it.uri).normalize(), it.dependencies.orEmpty().toSet())
        }
        require(result.map { it.second }.distinct().size == modules.size) {
            "Duplicate source module roots"
        }
        return result.toSet()
    }
}
