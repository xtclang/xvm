package org.xtclang.tooling.generators

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.xtclang.tooling.model.LanguageModel

/**
 * Generates package.json manifest for TextMate grammar bundles.
 *
 * The package.json format is the standard manifest for TextMate bundles, used by:
 * - VS Code
 * - IntelliJ IDEA (via TextMate plugin)
 * - Sublime Text
 * - Other editors with TextMate support
 *
 * It defines language metadata, file associations, and grammar file locations.
 */
class TextMateBundleManifestGenerator(
    private val model: LanguageModel,
    private val version: String,
) {
    companion object {
        private val json = Json { prettyPrint = true }
    }

    fun generate(): String {
        // Derive language ID from scopeName (e.g., "source.xtc" -> "xtc")
        // This ensures consistency: language ID, grammar filename, and scope all use the same identifier
        val languageId = model.scopeName.substringAfterLast(".")

        val config =
            buildJsonObject {
                put("name", "$languageId-language")
                put("displayName", "${model.name} Language")
                put("description", "${model.name} language support")
                put("version", version)
                putJsonObject("engines") {
                    put("vscode", "^1.50.0")
                }
                putJsonObject("contributes") {
                    putJsonArray("languages") {
                        addJsonObject {
                            put("id", languageId)
                            putJsonArray("aliases") {
                                add(JsonPrimitive(model.name))
                                add(JsonPrimitive(languageId.uppercase()))
                                add(JsonPrimitive(languageId))
                            }
                            putJsonArray("extensions") {
                                model.fileExtensions.forEach { add(JsonPrimitive(".$it")) }
                            }
                            put("configuration", "./language-configuration.json")
                        }
                    }
                    putJsonArray("grammars") {
                        addJsonObject {
                            put("language", languageId)
                            put("scopeName", model.scopeName)
                            put("path", "./$languageId.tmLanguage.json")
                        }
                    }
                }
            }
        return json.encodeToString(JsonObject.serializer(), config)
    }
}
