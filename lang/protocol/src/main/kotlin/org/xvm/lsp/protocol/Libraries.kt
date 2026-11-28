package org.xvm.lsp.protocol

import com.google.gson.JsonElement
import java.io.File
import java.net.URI

/**
 * The compiler's library settings, `libraries` in the configuration section `xtc.compiler`
 * ([ConfigurationSections.COMPILER]), which the IntelliJ plugin's settings write and the language server reads:
 * [modulePath] lists the binary modules' files and folders, in order, in place of the build model's, which a null
 * keeps; [sourceAttachments] attach source folders to binary modules of it, for navigation. Each path is a local file
 * URI, or a path relative to the workspace folder, as [files] resolves it.
 */
data class LibrarySettings(
    val modulePath: List<String>? = null,
    val sourceAttachments: List<LibraryAttachment> = emptyList(),
) {
    /**
     * The settings' paths as the canonical local files that they name, a relative one against [base], the workspace
     * folder, which is null when there is not exactly one. Fails with an [IllegalArgumentException] on a path that is
     * blank, names no local file, or comes twice in its list, on a relative path without a [base], and on an attachment
     * without a module name or roots, or to a module that another one names.
     */
    fun files(base: URI?): LibraryFiles {
        val modules = modulePath?.let { files(it, base) }
        val attachments =
            sourceAttachments.map { attachment ->
                require(attachment.module.isNotBlank()) { "Source attachment needs a module name" }
                require(attachment.roots.isNotEmpty()) { "Source attachment needs ordered roots" }
                attachment.module to files(attachment.roots, base)
            }
        require(attachments.map { it.first }.distinct().size == attachments.size) { "Duplicate source attachment module" }
        return LibraryFiles(modules, attachments.toMap())
    }

    companion object {
        /** The key of the settings in the compiler's section. */
        const val KEY = "libraries"

        /** The keys of the settings under [KEY]. */
        private val SETTINGS = setOf("modulePath", "sourceAttachments")

        /**
         * The settings that [libraries], the value of [KEY] in the compiler's section, holds, or null for none, as an
         * absent or null value is. Fails with an [IllegalArgumentException] on a value that does not have their shape.
         * Keys that are no setting go to [unknown], which refuses them unless given, as the server does; the IntelliJ
         * plugin's settings page logs them instead and reads the rest, so that a stray key never keeps it from opening.
         */
        fun read(
            libraries: JsonElement?,
            unknown: (Set<String>) -> Unit = { keys -> require(keys.isEmpty()) { "Unknown library setting: ${keys.joinToString()}" } },
        ): LibrarySettings? {
            val value = libraries?.takeUnless { it.isJsonNull } ?: return null
            require(value.isJsonObject) { "libraries must be an object" }
            val settings = value.asJsonObject
            (settings.keySet() - SETTINGS).takeIf { it.isNotEmpty() }?.let(unknown)
            val attachments =
                settings["sourceAttachments"]?.let { entries ->
                    require(entries.isJsonArray) { "sourceAttachments must be an array" }
                    entries.asJsonArray.map { entry ->
                        require(entry.isJsonObject) { "Each source attachment must be an object" }
                        val name = entry.asJsonObject["module"]
                        require(name?.isJsonPrimitive == true && name.asJsonPrimitive.isString) { "Source attachment needs a module name" }
                        val roots = requireNotNull(entry.asJsonObject["roots"]) { "Source attachment needs ordered roots" }
                        LibraryAttachment(name.asString, paths(roots))
                    }
                }
            return LibrarySettings(settings["modulePath"]?.takeUnless { it.isJsonNull }?.let(::paths), attachments.orEmpty())
        }

        private fun paths(values: JsonElement): List<String> {
            require(values.isJsonArray) { "Library paths must be an array" }
            return values.asJsonArray.map { element ->
                require(element.isJsonPrimitive && element.asJsonPrimitive.isString) { "Library paths must be non-blank strings" }
                element.asString
            }
        }

        private fun files(
            paths: List<String>,
            base: URI?,
        ): List<File> =
            paths.map { file(it, base) }.also { files -> require(files.distinct().size == files.size) { "Duplicate library path" } }

        private fun file(
            text: String,
            base: URI?,
        ): File {
            require(text.isNotBlank()) { "Library paths must be non-blank strings" }
            val path = URI.create(text)
            val resolved =
                if (path.isAbsolute) {
                    path
                } else {
                    val folder = requireNotNull(base) { "Relative library paths require exactly one workspace folder" }
                    URI(folder.toString().trimEnd('/') + "/").resolve(path)
                }
            require(resolved.scheme == "file" && resolved.query == null && resolved.fragment == null) {
                "Library paths must use local file URIs"
            }
            return File(resolved).canonicalFile
        }
    }
}

/** The source roots, in order, attached to the binary [module] for navigation. */
data class LibraryAttachment(
    val module: String,
    val roots: List<String>,
)

/** [LibrarySettings]' paths as the canonical local files that they name, by [LibrarySettings.files]. */
data class LibraryFiles(
    val modulePath: List<File>?,
    val sourceAttachments: Map<String, List<File>>,
)
