package org.xtclang.idea.lsp

import com.google.gson.GsonBuilder
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
        val settings = JsonParser.parseString(content).asJsonObject
        val compiler = requireNotNull(settings.getAsJsonObject("xtc")?.getAsJsonObject("compiler")) { "Compiler settings are required" }
        val modules = requireNotNull(compiler["sourceModules"]) { "Configured source modules are required" }
        require(modules.isJsonArray) { "Configured source modules must be explicit" }
        val current = modules.asJsonArray.map { gson.fromJson(it, SourceModuleConfiguration::class.java) }
        require(canonical(current, base) == canonical(expected, base)) { "Compiler source graph changed; rename was not applied" }
        compiler.add("sourceModules", gson.toJsonTree(replacement))
        return gson.toJson(settings)
    }

    private fun canonical(
        modules: List<SourceModuleConfiguration>,
        base: URI,
    ): Set<Triple<String, URI, Set<String>>> {
        require(modules.map { it.name }.distinct().size == modules.size) { "Duplicate source module names" }
        val result = modules.map { Triple(it.name, base.resolve(it.uri).normalize(), it.dependencies.orEmpty().toSet()) }
        require(result.map { it.second }.distinct().size == modules.size) { "Duplicate source module roots" }
        return result.toSet()
    }
}
