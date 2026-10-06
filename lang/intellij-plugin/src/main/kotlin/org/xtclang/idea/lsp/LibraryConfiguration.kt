package org.xtclang.idea.lsp

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

internal data class LibraryOptions(
    val modulePath: List<String>? = null,
    val sourceAttachments: List<LibraryAttachment> = emptyList(),
)

internal data class LibraryAttachment(
    val module: String,
    val roots: List<String>,
)

/** Library settings share the existing compiler owner and preserve unrelated graph/service fields. */
internal object LibraryConfiguration {
    private val gson = GsonBuilder().serializeNulls().setPrettyPrinting().create()

    fun read(content: String?): LibraryOptions =
        content
            ?.takeIf(String::isNotBlank)
            ?.let {
                JsonParser
                    .parseString(it)
                    .asJsonObject
                    .getAsJsonObject("xtc")
                    ?.getAsJsonObject("compiler")
                    ?.get("libraries")
            }?.takeUnless { it.isJsonNull }
            ?.let { element ->
                require(element.isJsonObject) { "Libraries must be an object" }
                val value = element.asJsonObject

                fun paths(raw: JsonElement): List<String> {
                    require(raw.isJsonArray) { "Library paths must be an array" }
                    return raw.asJsonArray.map { path ->
                        require(path.isJsonPrimitive && path.asJsonPrimitive.isString) { "Library paths must be strings" }
                        path.asString
                    }
                }
                LibraryOptions(
                    value["modulePath"]?.takeUnless { it.isJsonNull }?.let(::paths),
                    value["sourceAttachments"]
                        ?.asJsonArray
                        ?.map { attachment ->
                            val entry = attachment.asJsonObject
                            LibraryAttachment(entry["module"].asString, paths(entry["roots"]))
                        }.orEmpty(),
                )
            } ?: LibraryOptions()

    fun configure(
        content: String?,
        options: LibraryOptions,
        base: URI,
    ): String {
        fun validate(
            paths: List<String>,
            sources: Boolean,
        ) {
            val resolved =
                paths.map { text ->
                    require(text.isNotBlank()) { "Library paths must not be blank" }
                    val uri = base.resolve(URI(text))
                    require(uri.scheme == "file" && uri.query == null && uri.fragment == null) { "Library paths must be local files" }
                    val path =
                        Path
                            .of(uri)
                            .toFile()
                            .canonicalFile
                            .toPath()
                    require(
                        if (sources) {
                            Files.isDirectory(path)
                        } else {
                            Files.isDirectory(path) ||
                                (Files.isRegularFile(path) && text.endsWith(".xtc"))
                        },
                    ) {
                        "${if (sources) "Source directory" else "Library file or directory"} does not exist: $text"
                    }
                    path
                }
            require(resolved.distinct().size == resolved.size) { "Duplicate library path" }
        }
        options.modulePath?.let { validate(it, false) }
        require(
            options.sourceAttachments
                .map {
                    it.module
                }.distinct()
                .size == options.sourceAttachments.size,
        ) { "Duplicate source attachment module" }
        options.sourceAttachments.forEach {
            require(it.module.isNotBlank() && it.roots.isNotEmpty()) { "Attachments need a module name and source roots" }
            validate(it.roots, true)
        }
        val settings = content?.takeIf(String::isNotBlank)?.let { JsonParser.parseString(it).asJsonObject } ?: JsonObject()
        val xtc = settings.getAsJsonObject("xtc") ?: JsonObject().also { settings.add("xtc", it) }
        val compiler = xtc.getAsJsonObject("compiler") ?: JsonObject().also { xtc.add("compiler", it) }
        compiler.add("libraries", gson.toJsonTree(options))
        return gson.toJson(settings)
    }
}
