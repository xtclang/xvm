package org.xtclang.idea.lsp

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Immutable language-service preferences; source graphs remain owned by CompilerSettings. */
internal data class LanguageServiceConfiguration(
    val textSynchronization: String = "full",
    val saveFormatting: String = "editor",
    val inlayHints: Boolean = true,
    val referenceCodeLens: Boolean = true,
) {
    init {
        require(textSynchronization in setOf("full", "incremental")) {
            "Text synchronization must be full or incremental"
        }
        require(saveFormatting in setOf("editor", "server")) {
            "Save formatting must be owned by the editor or server"
        }
    }

    fun initializationOptions(nativeFormatOnSave: Boolean = false): Map<String, Any> =
        mapOf(
            "xtcDocumentSync" to
                mapOf(
                    "incremental" to (textSynchronization == "incremental"),
                    "formatOnSave" to (saveFormatting == "server" && !nativeFormatOnSave),
                ),
        )

    companion object {
        private val gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()

        private fun objectValue(content: String?): JsonObject =
            if (content.isNullOrBlank()) {
                JsonObject()
            } else {
                runCatching { JsonParser.parseString(content).asJsonObject }
                    .getOrElse {
                        throw IllegalArgumentException(
                            "Language-service settings must contain a JSON object",
                            it,
                        )
                    }
            }

        fun section(content: String?): JsonObject? =
            objectValue(content)
                .get("xtc")
                ?.takeUnless { it.isJsonNull }
                ?.let { xtc ->
                    require(xtc.isJsonObject) { "Ecstasy settings must be an object" }
                    xtc.asJsonObject
                        .get("languageService")
                        ?.takeUnless { it.isJsonNull }
                        ?.let {
                            require(it.isJsonObject) {
                                "Language-service settings must be an object"
                            }
                            it.asJsonObject.deepCopy()
                        }
                }

        fun read(
            global: String?,
            project: String? = null,
        ): LanguageServiceConfiguration {
            val defaults = section(global)
            val override = section(project)

            fun value(name: String) = override?.get(name) ?: defaults?.get(name)

            fun string(
                name: String,
                fallback: String,
            ): String =
                value(name)?.let {
                    require(it.isJsonPrimitive && it.asJsonPrimitive.isString) {
                        "$name must be a string"
                    }
                    it.asString
                } ?: fallback

            fun boolean(name: String): Boolean =
                value(name)?.let {
                    require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) {
                        "$name must be a boolean"
                    }
                    it.asBoolean
                } ?: true
            return LanguageServiceConfiguration(
                string("textSynchronization", "full"),
                string("saveFormatting", "editor"),
                boolean("inlayHints"),
                boolean("referenceCodeLens"),
            )
        }

        /**
         * Compare only our section, then merge into current content to preserve graph/Undo edits.
         */
        fun replace(
            content: String?,
            expected: JsonObject?,
            replacement: LanguageServiceConfiguration?,
        ): String {
            require(section(content) == expected) {
                "Language-service settings changed while this page was open. Reset before applying."
            }
            val settings = objectValue(content)
            val xtc =
                settings["xtc"]?.takeUnless { it.isJsonNull }?.asJsonObject
                    ?: JsonObject().also { settings.add("xtc", it) }
            if (replacement == null) {
                xtc.remove("languageService")
            } else {
                xtc.add("languageService", gson.toJsonTree(replacement))
            }
            return gson.toJson(settings)
        }
    }
}
