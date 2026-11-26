package org.xtclang.idea.lsp

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.xvm.lsp.protocol.LibrarySettings
import java.net.URI

/**
 * Library settings share the existing compiler owner and preserve unrelated graph/service fields. Their shape and paths
 * are the protocol's [LibrarySettings], which the server reads as this writes them.
 */
internal object LibraryConfiguration {
    private val gson = GsonBuilder().serializeNulls().setPrettyPrinting().create()

    fun read(content: String?): LibrarySettings =
        content
            ?.takeIf(String::isNotBlank)
            ?.let {
                JsonParser
                    .parseString(it)
                    .asJsonObject
                    .getAsJsonObject("xtc")
                    ?.getAsJsonObject("compiler")
                    ?.get(LibrarySettings.KEY)
            }?.let(LibrarySettings::read) ?: LibrarySettings()

    /**
     * [content] with [options] as its library settings, once each of their paths, relative ones against [base], names
     * an existing library: a module path entry a folder or an .xtc file, an attachment's root a folder.
     */
    fun configure(
        content: String?,
        options: LibrarySettings,
        base: URI,
    ): String {
        val files = options.files(base)
        options.modulePath.orEmpty().zip(files.modulePath.orEmpty()).forEach { (text, file) ->
            require(file.isDirectory || (file.isFile && text.endsWith(".xtc"))) { "Library file or directory does not exist: $text" }
        }
        options.sourceAttachments.forEach { attachment ->
            attachment.roots.zip(files.sourceAttachments.getValue(attachment.module)).forEach { (text, file) ->
                require(file.isDirectory) { "Source directory does not exist: $text" }
            }
        }
        val settings = content?.takeIf(String::isNotBlank)?.let { JsonParser.parseString(it).asJsonObject } ?: JsonObject()
        val xtc = settings.getAsJsonObject("xtc") ?: JsonObject().also { settings.add("xtc", it) }
        val compiler = xtc.getAsJsonObject("compiler") ?: JsonObject().also { xtc.add("compiler", it) }
        compiler.add(LibrarySettings.KEY, gson.toJsonTree(options))
        return gson.toJson(settings)
    }
}
