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
 * Generates VS Code package.json for TextMate bundle distribution.
 *
 * This file is required for VS Code and IntelliJ's TextMate plugin to recognize
 * the grammar bundle. It defines language metadata and file associations.
 */
class VSCodePackageGenerator(
    private val model: LanguageModel,
    private val version: String = "1.0.0",
) {
    companion object {
        private val json = Json { prettyPrint = true }
    }

    fun generate(): String {
        val config =
            buildJsonObject {
                put("name", "${model.name.lowercase()}-language")
                put("displayName", "${model.name} Language")
                put("description", "${model.name} language support")
                put("version", version)
                putJsonObject("engines") {
                    put("vscode", "^1.50.0")
                }
                putJsonObject("contributes") {
                    putJsonArray("languages") {
                        addJsonObject {
                            put("id", model.name.lowercase())
                            putJsonArray("aliases") {
                                add(JsonPrimitive(model.name))
                                add(JsonPrimitive(model.name.uppercase()))
                                add(JsonPrimitive(model.name.lowercase()))
                            }
                            putJsonArray("extensions") {
                                model.fileExtensions.forEach { add(JsonPrimitive(".$it")) }
                            }
                            put("configuration", "./language-configuration.json")
                        }
                    }
                    putJsonArray("grammars") {
                        addJsonObject {
                            put("language", model.name.lowercase())
                            put("scopeName", model.scopeName)
                            put("path", "./${model.name.lowercase()}.tmLanguage.json")
                        }
                    }
                }
            }
        return json.encodeToString(JsonObject.serializer(), config)
    }
}
