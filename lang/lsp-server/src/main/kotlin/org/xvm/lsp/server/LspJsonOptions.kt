package org.xvm.lsp.server

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Reads typed values out of LSP4J `initializationOptions` / `workspace/configuration`
 * payloads, which arrive as either `Map<*, *>` or Gson `JsonObject` / `JsonElement`
 * depending on how the client serialises them. Returns `null` when a key is absent,
 * mistyped, or can't be coerced -- callers supply their own defaults.
 */
internal object LspJsonOptions {
    /** True iff [raw] is a JSON-object-shaped value (Map, JsonObject, or JsonElement holding an object). */
    fun isObject(raw: Any?): Boolean = raw is Map<*, *> || raw is JsonObject || (raw is JsonElement && raw.isJsonObject)

    /** Read [key] as a list of non-blank strings; returns `emptyList()` if missing or wrong shape. */
    fun stringList(
        raw: Any?,
        key: String,
    ): List<String> {
        val value = lookup(raw, key) ?: return emptyList()
        return when (value) {
            is List<*> -> value.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
            is JsonArray -> value.mapNotNull { stringOrNull(it) }
            is JsonElement -> if (value.isJsonArray) value.asJsonArray.mapNotNull { stringOrNull(it) } else emptyList()
            else -> emptyList()
        }
    }

    /** Read [key] as an [Int]; coerces from numeric primitives or numeric strings, else returns null. */
    fun int(
        raw: Any?,
        key: String,
    ): Int? =
        when (val value = lookup(raw, key)) {
            is Number -> {
                value.toInt()
            }

            is String -> {
                value.toIntOrNull()
            }

            is JsonElement -> {
                value.takeUnless { it.isJsonNull }?.let {
                    when {
                        it.isJsonPrimitive && it.asJsonPrimitive.isNumber -> it.asInt
                        it.isJsonPrimitive && it.asJsonPrimitive.isString -> it.asString.toIntOrNull()
                        else -> null
                    }
                }
            }

            else -> {
                null
            }
        }

    /** Read [key] as a [Boolean]; coerces from boolean primitives or `"true"`/`"false"` strings. */
    fun boolean(
        raw: Any?,
        key: String,
    ): Boolean? =
        when (val value = lookup(raw, key)) {
            is Boolean -> {
                value
            }

            is String -> {
                value.toBooleanStrictOrNull()
            }

            is JsonElement -> {
                value.takeUnless { it.isJsonNull }?.let {
                    when {
                        it.isJsonPrimitive && it.asJsonPrimitive.isBoolean -> it.asBoolean
                        it.isJsonPrimitive && it.asJsonPrimitive.isString -> it.asString.toBooleanStrictOrNull()
                        else -> null
                    }
                }
            }

            else -> {
                null
            }
        }

    private fun lookup(
        raw: Any?,
        key: String,
    ): Any? =
        when (raw) {
            null -> null
            is Map<*, *> -> raw[key]
            is JsonObject -> raw.get(key)
            is JsonElement -> if (raw.isJsonObject) raw.asJsonObject.get(key) else null
            else -> null
        }

    private fun stringOrNull(elem: JsonElement?): String? =
        elem
            ?.takeUnless { it.isJsonNull }
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf(String::isNotEmpty)
}
