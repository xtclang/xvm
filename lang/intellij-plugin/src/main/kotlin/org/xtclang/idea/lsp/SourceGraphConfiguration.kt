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
