package org.xtclang.tooling.generators

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonArrayBuilder
import org.xtclang.tooling.model.LanguageModel

/**
 * Generates VS Code language configuration files (language-configuration.json)
 *
 * VS Code language configuration is supported by:
 * - Visual Studio Code
 * - VS Code forks (VSCodium, Cursor, Windsurf, etc.)
 * - Monaco Editor (web-based)
 * - GitHub Codespaces
 * - Gitpod
 * - code-server
 * - Any editor using Monaco or VS Code extension API
 *
 * The configuration file defines:
 * - Comment styles (line and block)
 * - Bracket pairs for auto-matching
 * - Auto-closing pairs
 * - Surrounding pairs
 * - Folding markers
 * - Word patterns
 * - Indentation rules
 */
class VSCodeConfigGenerator(private val model: LanguageModel) {

    fun generate(): String {
        val config = buildJsonObject {
            // Comments
            putJsonObject("comments") {
                put("lineComment", "//")
                putJsonArray("blockComment") {
                    add(JsonPrimitive("/*"))
                    add(JsonPrimitive("*/"))
                }
            }

            // Brackets
            putJsonArray("brackets") {
                addJsonArray { add(JsonPrimitive("[")); add(JsonPrimitive("]")) }
                addJsonArray { add(JsonPrimitive("{")); add(JsonPrimitive("}")) }
                addJsonArray { add(JsonPrimitive("(")); add(JsonPrimitive(")")) }
                addJsonArray { add(JsonPrimitive("<")); add(JsonPrimitive(">")) }
            }

            // Auto-closing pairs
            putJsonArray("autoClosingPairs") {
                addJsonObject { put("open", "{"); put("close", "}") }
                addJsonObject { put("open", "["); put("close", "]") }
                addJsonObject { put("open", "("); put("close", ")") }
                addJsonObject { put("open", "<"); put("close", ">") }
                addJsonObject { put("open", "\""); put("close", "\""); putJsonArray("notIn") { add(JsonPrimitive("string")) } }
                addJsonObject { put("open", "'"); put("close", "'"); putJsonArray("notIn") { add(JsonPrimitive("string")) } }
                addJsonObject { put("open", "/*"); put("close", " */") }
            }

            // Surrounding pairs
            putJsonArray("surroundingPairs") {
                addJsonArray { add(JsonPrimitive("{")); add(JsonPrimitive("}")) }
                addJsonArray { add(JsonPrimitive("[")); add(JsonPrimitive("]")) }
                addJsonArray { add(JsonPrimitive("(")); add(JsonPrimitive(")")) }
                addJsonArray { add(JsonPrimitive("<")); add(JsonPrimitive(">")) }
                addJsonArray { add(JsonPrimitive("\"")); add(JsonPrimitive("\"")) }
                addJsonArray { add(JsonPrimitive("'")); add(JsonPrimitive("'")) }
            }

            // Folding markers
            putJsonObject("folding") {
                putJsonObject("markers") {
                    put("start", "^\\s*//\\s*#?region\\b")
                    put("end", "^\\s*//\\s*#?endregion\\b")
                }
            }

            // Word pattern - what constitutes a "word" for selection
            put("wordPattern", "[a-zA-Z_][a-zA-Z0-9_]*")

            // Indentation rules
            putJsonObject("indentationRules") {
                // Increase indent after { or ( at end of line
                put("increaseIndentPattern", "^.*\\{[^}\"']*$|^.*\\([^)\"']*$")
                // Decrease indent before } or )
                put("decreaseIndentPattern", "^\\s*(\\}|\\)).*$")
            }

            // On enter rules for smart formatting
            putJsonArray("onEnterRules") {
                // Continue line comment
                addJsonObject {
                    put("beforeText", "^\\s*//.*$")
                    putJsonObject("action") {
                        put("indent", "none")
                        put("appendText", "// ")
                    }
                }
                // Continue block comment
                addJsonObject {
                    put("beforeText", "^\\s*/\\*\\*(?!/)([^*]|\\*(?!/))*$")
                    putJsonObject("action") {
                        put("indent", "none")
                        put("appendText", " * ")
                    }
                }
                // Close block comment
                addJsonObject {
                    put("beforeText", "^\\s*\\*/$")
                    put("afterText", "^\\s*\\*/$")
                    putJsonObject("action") {
                        put("indent", "none")
                        put("removeText", 1)
                    }
                }
            }
        }

        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), config)
    }
}

// Extension functions for cleaner JSON building
private fun JsonObjectBuilder.putJsonArray(key: String, block: JsonArrayBuilder.() -> Unit) {
    put(key, buildJsonArray(block))
}

private fun JsonObjectBuilder.putJsonObject(key: String, block: JsonObjectBuilder.() -> Unit) {
    put(key, buildJsonObject(block))
}

private fun JsonArrayBuilder.addJsonObject(block: JsonObjectBuilder.() -> Unit) {
    add(buildJsonObject(block))
}

private fun JsonArrayBuilder.addJsonArray(block: JsonArrayBuilder.() -> Unit) {
    add(buildJsonArray(block))
}
